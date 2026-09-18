package app.tvmovie.tracker.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import app.tvmovie.tracker.BuildConfig;

/**
 * Static HTTP helpers for talking to the JSON-based provider APIs
 * (TVMaze: https://api.tvmaze.com - free, no auth, rate limit ~20 req/10s).
 *
 * The rate-limit failure mode is surfaced as a dedicated exception so that
 * callers can tell "slow down and retry" apart from other I/O failures:
 * this is a public static nested class of JsonFetcher
 * (i.e. JsonFetcher.RateLimitException).
 */
public class JsonFetcher {

	private static final String TAG = "JsonFetcher";
	private static final String USER_AGENT = "TVMovieTracker/" + BuildConfig.VERSION_NAME;
	private static final int CONNECT_TIMEOUT_MS = 10000;
	private static final int READ_TIMEOUT_MS = 10000;

	/**
	 * Thrown when the server answers with HTTP 429 (rate limit exceeded).
	 * Defined as a public static nested class of JsonFetcher.
	 */
	public static class RateLimitException extends IOException {
		private static final long serialVersionUID = 1L;

		public RateLimitException(String message) {
			super(message);
		}
	}

	/**
	 * GET the given URL and parse the response body as a JSONObject.
	 *
	 * @throws RateLimitException on HTTP 429
	 * @throws IOException on any other non-200 response or I/O problem,
	 *         including invalid JSON (the code is part of the message)
	 */
	public static JSONObject getJsonObject(String url) throws IOException {
		String body = fetch(url);
		try {
			return new JSONObject(body);
		} catch (JSONException e) {
			throw new IOException("Invalid JSON object from " + url + ": " + e.getMessage());
		}
	}

	/**
	 * GET the given URL and parse the response body as a JSONArray.
	 *
	 * @throws RateLimitException on HTTP 429
	 * @throws IOException on any other non-200 response or I/O problem,
	 *         including invalid JSON (the code is part of the message)
	 */
	public static JSONArray getJsonArray(String url) throws IOException {
		String body = fetch(url);
		try {
			return new JSONArray(body);
		} catch (JSONException e) {
			throw new IOException("Invalid JSON array from " + url + ": " + e.getMessage());
		}
	}

	/**
	 * Perform the HTTP GET and return the response body as a UTF-8 string.
	 */
	private static String fetch(String urlStr) throws IOException {
		HttpsURLConnection conn = null;
		BufferedReader reader = null;
		try {
			URL url = new URL(urlStr);
			conn = (HttpsURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(true);
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", USER_AGENT);
			conn.setRequestProperty("Accept", "application/json");

			int code = conn.getResponseCode();
			if (code == 429) {
				throw new RateLimitException("HTTP 429 Too Many Requests: " + urlStr);
			}
			if (code != 200) {
				throw new IOException("HTTP " + code + " fetching " + urlStr);
			}

			InputStream in = conn.getInputStream();
			reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
			return sb.toString();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					Log.e(TAG, "Error closing reader: " + e.getMessage());
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}
}
