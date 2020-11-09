import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import twitter4j.HashtagEntity;
import twitter4j.Status; 

public class MovieReviewProcessor {
	final String moviesURL="https://api.themoviedb.org/3/discover/movie";
	final String[] sentimentText = {"Negative", "Negative", "Neutral",
			"Positive", "Positive"};
	HttpURLConnection sendRequest(String movieurl) throws IOException {
		URL url = new URL(movieurl);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setDoOutput(true);
		con.setConnectTimeout(5000);
		con.setReadTimeout(10000);
		Map<String, String> parameters = new HashMap<>();
		parameters.put("primary_release_date.gte", "2018-12-21");
		parameters.put("sort_by", "popularity.desc");
		parameters.put("api_key", "<insert key here>");
		parameters.put("page", "1");
		DataOutputStream out = new DataOutputStream(con.getOutputStream());
		out.writeBytes(ParameterStringBuilder.getParamsString(parameters));
		out.flush();
		out.close();
		return con;
	}

	ArrayList<String> getMovieTitles(HttpURLConnection con) throws IOException, ParseException {
		BufferedReader in = new BufferedReader(
				new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer content = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			content.append(inputLine);
		}
		in.close();
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(content.toString());
		System.out.println("JSON : " + obj.toString());
		JSONObject json = (JSONObject) obj;
		JSONArray arr = (JSONArray) json.get("results");
		ArrayList<String> titleList = new ArrayList<String>();
		for (int i = 0; i < 10; i++)
		{
			JSONObject result = (JSONObject) arr.get(i);
			String title = (String) result.get("title");
			titleList.add(title);
		}
		con.disconnect();
		return titleList;
	}

	static String generateHashtag(String title) {
		return title.replaceAll("\\s+", "").toLowerCase();
	}

	static double getScore(Status tweet, String title) {
		final double movieScore = 0.75;
		final double defaultScore = 0.25;
		final double maxScore = 2;
		HashtagEntity[] hashtagentities = tweet.getHashtagEntities();
		for(HashtagEntity hashtag : hashtagentities) {
			if(hashtag.getText().toLowerCase().contains(MovieReviewProcessor.generateHashtag(title))) {
				return maxScore;
			}
			if(hashtag.getText().contains("movie") ) {
				return movieScore;
			}
		}

		return defaultScore;
	}
	
	HashMap<String,String> computeSentiment(ArrayList<String> movies){
		HashMap<String, String> sentiment = new HashMap<String, String>();
		NLP.init();
		ArrayList<Status> tweets = new ArrayList<Status>();
		for(String title : movies) {
			tweets = TweetManager.getTweets(title + " filter:safe");
			System.out.println("Title--- " + title + " tweets--- " + tweets.size());
			HashMap<Integer, Double> sentimentCount = new HashMap<Integer, Double>();
			for(Status tweet : tweets) {
				int findSentimentVal = NLP.findSentiment(tweet.getText());
				double score = MovieReviewProcessor.getScore(tweet, title);
				if(sentimentCount.containsKey(findSentimentVal)) {
					double sentimentVal = sentimentCount.get(findSentimentVal);
					sentimentCount.put(findSentimentVal, sentimentVal+score);
				}else {
					sentimentCount.put(findSentimentVal, score);
				}
			} 
			double maxVal = 0;
			int maxSentiment = 0;
			for (Map.Entry<Integer, Double> entry : sentimentCount.entrySet()) {
				System.out.println("title *** " + title + " sentiment *** " + sentimentText[entry.getKey()] + " count *** " + entry.getValue());
				if(entry.getValue() > maxVal) {
					maxVal = entry.getValue();
					maxSentiment = entry.getKey();
				}
			}
			if(sentimentCount.size() > 0) {
				sentiment.put(title, sentimentText[maxSentiment]);
			}

		}
		return sentiment;

	}
	public static void main(String[] args) throws IOException, ParseException {
		MovieReviewProcessor mrp = new MovieReviewProcessor();
		HttpURLConnection con = mrp.sendRequest(mrp.moviesURL);
		String message = con.getResponseMessage();
		System.out.println("Message: " + message);
		int status = con.getResponseCode();
		System.out.println("Status: " + status);
		System.out.println("Con: "+ con);
		if (status == HttpURLConnection.HTTP_MOVED_TEMP
				|| status == HttpURLConnection.HTTP_MOVED_PERM) {
			String location = con.getHeaderField("Location");
			System.out.println("Location: " + location);
			con = mrp.sendRequest(location);
		}
		ArrayList<String> movieList = new ArrayList<String>();
		movieList = mrp.getMovieTitles(con);
		for(String title : movieList) {
			System.out.println("Title: " + title);
		}

		HashMap<String,String> sentiment = new HashMap<String,String>();
		sentiment = mrp.computeSentiment(movieList);

		for (Map.Entry<String, String> entry : sentiment.entrySet()) {
			System.out.println(entry.getKey()+" : "+entry.getValue());
		}
	}

}
