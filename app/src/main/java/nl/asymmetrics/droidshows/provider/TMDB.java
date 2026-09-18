package nl.asymmetrics.droidshows.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import nl.asymmetrics.droidshows.thetvdb.model.Episode;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal TMDB (https://api.themoviedb.org/3) client for the Movies feature.
 * Movies reuse the series/episodes tables: a movie is one Serie row with
 * mediaType = 1 plus a single pseudo-episode (season 1, episode 1).
 */
public class TMDB {

	private static final String TAG = "TMDB";
	private static final String BASE = "https://api.themoviedb.org/3";
	private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/";
	private static final String USER_AGENT = "DroidShows/14.12";

	private final String apiKey;

	public TMDB(String apiKey) {
		this.apiKey = apiKey;
	}

	/**
	 * Search movies by title. Returns null on connection/auth failure
	 * (401 is treated as failure, same as the old search contract).
	 */
	public List<Serie> searchMovies(String query) {
		try {
			String encoded = URLEncoder.encode(query, "UTF-8");
			String url = BASE + "/search/movie?api_key=" + apiKey
				+ "&query=" + encoded + "&include_adult=false";
			String json = fetchJson(url);
			if (json == null) return null;
			JSONObject root = new JSONObject(json);
			return parseMovieList(root.optJSONArray("results"));
		} catch (Exception e) {
			Log.e(TAG, "searchMovies failed: " + e.getMessage());
			return null;
		}
	}

	/**
	 * The week's trending movies. Returns null on connection/auth failure.
	 */
	public List<Serie> getTrendingMovies() {
		String url = BASE + "/trending/movie/week?api_key=" + apiKey + "&include_adult=false";
		String json = fetchJson(url);
		if (json == null) return null;
		try {
			JSONObject root = new JSONObject(json);
			return parseMovieList(root.optJSONArray("results"));
		} catch (Exception e) {
			Log.e(TAG, "getTrendingMovies failed: " + e.getMessage());
			return null;
		}
	}

	private List<Serie> parseMovieList(JSONArray results) {
		List<Serie> movies = new ArrayList<Serie>();
		if (results != null) {
			for (int i = 0; i < results.length(); i++) {
				JSONObject m = results.optJSONObject(i);
				if (m == null) continue;
				Serie s = new Serie();
				String id = String.valueOf(m.optInt("id", 0));
				s.setId(id);
				s.setSerieId(id);
				s.setSerieName(m.optString("title", ""));
				s.setOverview(m.optString("overview", ""));
				s.setFirstAired(m.optString("release_date", ""));
				s.setPoster(imageUrl(m.optString("poster_path", null), "w500"));
				s.setLanguage("");
				s.setMediaType(1);
				movies.add(s);
			}
		}
		return movies;
	}

	/**
	 * Fetch full movie details plus credits. Builds the Serie with its single
	 * pseudo-episode (season 1 / episode 1 representing the film itself).
	 * Returns null on failure.
	 */
	public Serie getMovie(String tmdbId) {
		try {
			String url = BASE + "/movie/" + tmdbId + "?api_key=" + apiKey
				+ "&append_to_response=credits";
			String json = fetchJson(url);
			if (json == null) return null;
			JSONObject m = new JSONObject(json);

			Serie s = new Serie();
			String id = String.valueOf(m.optInt("id", 0));
			String title = m.optString("title", "");
			String release = m.optString("release_date", "");
			String overview = m.optString("overview", "");

			s.setId(id);
			s.setSerieId(id);
			s.setSerieName(title);
			s.setOverview(overview);
			s.setFirstAired(release);
			s.setLanguage("");
			String backdrop = imageUrl(m.optString("backdrop_path", null), "w780");
			s.setPoster(imageUrl(m.optString("poster_path", null), "w500"));
			s.setFanart(backdrop);
			s.setBanner(backdrop);
			s.setRating(String.valueOf(m.optDouble("vote_average", 0.0)));
			s.setRuntime(m.isNull("runtime") ? "" : String.valueOf(m.optInt("runtime", 0)));

			List<String> genres = new ArrayList<String>();
			JSONArray ga = m.optJSONArray("genres");
			if (ga != null) {
				for (int i = 0; i < ga.length(); i++) {
					JSONObject g = ga.optJSONObject(i);
					if (g != null) genres.add(g.optString("name", ""));
				}
			}
			s.setGenres(genres);

			List<String> actors = new ArrayList<String>();
			JSONObject credits = m.optJSONObject("credits");
			if (credits != null) {
				JSONArray cast = credits.optJSONArray("cast");
				if (cast != null) {
					int n = Math.min(cast.length(), 10);
					for (int i = 0; i < n; i++) {
						JSONObject c = cast.optJSONObject(i);
						if (c != null) actors.add(c.optString("name", ""));
					}
				}
			}
			s.setActors(actors);

			s.setImdbId(m.optString("imdb_id", ""));
			s.setStatus(m.optString("status", ""));
			s.setMediaType(1);
			s.setTvmazeId("");

			Episode ep = new Episode();
			ep.setId(id);
			ep.setSeasonNumber(1);
			ep.setEpisodeNumber(1);
			ep.setEpisodeName(title);
			ep.setFirstAired(release);
			ep.setOverview(overview);
			s.setEpisodes(Collections.singletonList(ep));
			s.setNSeasons(Arrays.asList(1));

			return s;
		} catch (Exception e) {
			Log.e(TAG, "getMovie failed: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Build a TMDB image URL, null/empty-safe (returns "" when no path).
	 */
	public static String imageUrl(String path, String size) {
		if (path == null || path.length() == 0) return "";
		return IMAGE_BASE + size + path;
	}

	private String fetchJson(String urlStr) {
		HttpsURLConnection conn = null;
		try {
			URL url = new URL(urlStr);
			conn = (HttpsURLConnection) url.openConnection();
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setRequestProperty("User-Agent", USER_AGENT);
			conn.setRequestProperty("Accept", "application/json");
			int code = conn.getResponseCode();
			if (code != 200) {
				if (code != 401) Log.e(TAG, "fetchJson HTTP " + code);
				return null;
			}
			InputStream in = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) sb.append(line).append('\n');
			reader.close();
			return sb.toString();
		} catch (IOException e) {
			// never log the URL: it carries the API key
			Log.e(TAG, "fetchJson failed: " + e.getMessage());
			return null;
		} finally {
			if (conn != null) conn.disconnect();
		}
	}
}
