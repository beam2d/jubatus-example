package example.twitterstreaminglocation;

import static example.twitterstreaminglocation.JubatusClassifierHelper.list;
import static example.twitterstreaminglocation.JubatusClassifierHelper.newDatum;
import static example.twitterstreaminglocation.JubatusClassifierHelper.newTupleStringDatum;
import static example.twitterstreaminglocation.JubatusClassifierHelper.newTupleStringString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import twitter4j.FilterQuery;
import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.auth.BasicAuthorization;

import us.jubat.classifier.ClassifierClient;
import us.jubat.classifier.Datum;

public class LocationTrainerApp {
	// Twitter Configuration (fill in your account information here)
	private String twitterUserName = "";
	private String twitterPassword = "";

	// Jubatus Configuration
	private String host = "127.0.0.1";
	private int port = 9199;
	// required only when using distributed mode
	private String instanceName = "";

	class Trainer extends StatusAdapter {
		private final ClassifierClient client;
		private final LocationFence[] locations;

		public Trainer(LocationFence[] locations) throws Exception {
			client = new ClassifierClient(host, port, 10);
			this.locations = locations;
		}

		@Override
		public void onStatus(Status status) {
			GeoLocation coordinates = status.getGeoLocation();
			if (coordinates == null) {
				return;
			}

			LocationFence loc = null;
			for (LocationFence l : this.locations) {
				if (l.isInside(coordinates.getLongitude(),
						coordinates.getLatitude())) {
					loc = l;
					break;
				}
			}
			if (loc == null) {
				// Unknown location
				return;
			}

			String detaggedText = removeHashtagsFromTweet(status.getText(),
					status.getHashtagEntities());

			// Create datum for Jubatus
			Datum d = newDatum();
			d.string_values.add(newTupleStringString("text", detaggedText));

			// Send training data to Jubatus
			String label = loc.getName();
			client.train(instanceName, list(newTupleStringDatum(label, d)));

			System.out.println(label + " " + detaggedText);
		}
	}

	class LocationFence {
		private final String name;
		private final double longitude1;
		private final double latitude1;
		private final double longitude2;
		private final double latitude2;

		public LocationFence(String name, //
				double longitude1, double latitude1, //
				double longitude2, double latitude2) {
			super();
			this.name = name;
			this.longitude1 = longitude1;
			this.latitude1 = latitude1;
			this.longitude2 = longitude2;
			this.latitude2 = latitude2;
		}

		public boolean isInside(double longitude, double latitude) {
			return this.longitude1 <= longitude && //
					longitude <= this.longitude2 && //
					this.latitude1 <= latitude && //
					latitude <= this.latitude2;
		}

		public String getName() {
			return this.name;
		}

		public double[][] getCoordinates() {
			return new double[][] { new double[] { longitude1, latitude1 },
					new double[] { longitude2, latitude2 } };
		}
	}

	public String removeHashtagsFromTweet(String tweet, HashtagEntity[] hashtags) {
		Map<Integer, Integer> indices = new HashMap<Integer, Integer>();
		for (HashtagEntity hashtag : hashtags) {
			indices.put(hashtag.getStart(), hashtag.getEnd());
		}

		int pos = 0;
		StringBuilder textBuf = new StringBuilder();

		for (int begin : new TreeSet<Integer>(indices.keySet())) {
			textBuf.append(tweet.substring(pos, begin));
			pos = indices.get(begin);
		}
		textBuf.append(tweet.substring(pos));

		return textBuf.toString();
	}

	public void trainTweets() throws Exception {
		LocationFence tokyo = new LocationFence("Tokyo", //
				138.946381, 35.523285, 139.953232, 35.906849);
		LocationFence hokkaido = new LocationFence("Hokkaido", //
				139.546509, 41.393294, 145.742798, 45.729191);
		LocationFence kyusyu = new LocationFence("Kyusyu", //
				129.538879, 31.147006, 131.856995, 33.934245);

		LocationFence[] locations = new LocationFence[] { tokyo, hokkaido,
				kyusyu };
		List<double[]> requestCoordinates = new ArrayList<double[]>();
		for (LocationFence l : locations) {
			requestCoordinates.addAll(Arrays.asList(l.getCoordinates()));
		}

		TwitterStream stream = new TwitterStreamFactory()
				.getInstance(new BasicAuthorization(twitterUserName,
						twitterPassword));
		stream.addListener(new Trainer(locations));
		FilterQuery filter = new FilterQuery();
		filter.locations(requestCoordinates.toArray(new double[][] {}));
		stream.filter(filter);
	}

	public static void main(String[] args) throws Exception {
		new LocationTrainerApp().trainTweets();
		System.exit(0);
	}
}
