package nl.asymmetrics.droidshows.provider;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.asymmetrics.droidshows.thetvdb.model.Episode;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import android.os.SystemClock;
import android.util.Log;

/**
 * TVMaze (https://api.tvmaze.com) provider - JSON-based replacement for the
 * dead TheTVDB v1 XML API. TVMaze is free, needs no API key, and rate limits
 * to roughly 20 requests per 10 seconds (HTTP 429 when exceeded).
 *
 * Contracts (kept from the old TheTVDB class):
 * - searchShows / getShow return null on any failure (connection error)
 * - specials live in season 0 (see the note above mapEpisode)
 */
public class TVMaze {

	private static final String TAG = "TVMaze";
	private static final String BASE = "https://api.tvmaze.com";

	/**
	 * Shared API throttle: TVMaze's free tier allows ~20 requests per
	 * 10 seconds. All TVMaze HTTP calls (from any thread) go through
	 * throttleApi() so at least MIN_API_INTERVAL_MS elapses between them.
	 */
	private static final Object API_LOCK = new Object();
	private static long lastApiCallMs = 0;
	private static final long MIN_API_INTERVAL_MS = 600;

	private static void throttleApi() {
		synchronized (API_LOCK) {
			long wait = MIN_API_INTERVAL_MS - (SystemClock.uptimeMillis() - lastApiCallMs);
			if (wait > 0) {
				try {
					API_LOCK.wait(wait);
				} catch (InterruptedException e) {
					// ignore; a slightly early call is harmless
				}
			}
			lastApiCallMs = SystemClock.uptimeMillis();
		}
	}

	public TVMaze() {
		// no API key needed
	}

	/**
	 * Search shows by name. Returns null on connection/parsing failure.
	 *
	 * @throws JsonFetcher.RateLimitException on HTTP 429 so callers can
	 *         back off and retry instead of treating it as a dead show.
	 */
	public List<Serie> searchShows(String query) throws JsonFetcher.RateLimitException {
		throttleApi();
		List<Serie> results = new ArrayList<Serie>();
		try {
			String encoded = URLEncoder.encode(query, "UTF-8");
			JSONArray hits = JsonFetcher.getJsonArray(BASE + "/search/shows?q=" + encoded);
			for (int i = 0; i < hits.length(); i++) {
				JSONObject entry = hits.optJSONObject(i);
				if (entry == null) {
					continue;
				}
				JSONObject show = entry.optJSONObject("show");
				if (show == null) {
					continue;
				}

				Serie serie = new Serie();
				String id = String.valueOf(show.optInt("id", 0));
				serie.setId(id);
				serie.setSerieId(id);
				serie.setSerieName(show.optString("name", ""));
				serie.setOverview(HtmlUtil.stripHtml(show.optString("summary", null)));
				serie.setFirstAired(show.optString("premiered", ""));
				serie.setPoster(imageUrl(show));
				serie.setLanguage(show.optString("language", ""));
				serie.setTvmazeId(id);
				serie.setMediaType(0);
				results.add(serie);
			}
		} catch (JsonFetcher.RateLimitException e) {
			throw e;
		} catch (Exception e) {
			Log.e(TAG, "searchShows failed: " + e.getMessage());
			return null;
		}
		return results;
	}

	/**
	 * Shows airing today (US schedule), de-duplicated by show. Used by
	 * Discover as a "what's on" feed. Returns null on failure.
	 *
	 * @throws JsonFetcher.RateLimitException on HTTP 429 so callers can
	 *         back off and retry instead of treating it as a dead feed.
	 */
	public List<Serie> getScheduleShows() throws JsonFetcher.RateLimitException {
		throttleApi();
		List<Serie> results = new ArrayList<Serie>();
		try {
			String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
				.format(new java.util.Date());
			JSONArray eps = JsonFetcher.getJsonArray(BASE + "/schedule?country=US&date=" + date);
			java.util.LinkedHashMap<String, Serie> seen = new java.util.LinkedHashMap<String, Serie>();
			for (int i = 0; i < eps.length(); i++) {
				JSONObject entry = eps.optJSONObject(i);
				if (entry == null) continue;
				JSONObject show = entry.optJSONObject("show");
				if (show == null) continue;
				String id = String.valueOf(show.optInt("id", 0));
				if (id.equals("0") || seen.containsKey(id)) continue;
				Serie serie = new Serie();
				serie.setId(id);
				serie.setSerieId(id);
				serie.setSerieName(show.optString("name", ""));
				serie.setOverview(HtmlUtil.stripHtml(show.optString("summary", null)));
				serie.setFirstAired(show.optString("premiered", ""));
				serie.setPoster(imageUrl(show));
				serie.setLanguage(show.optString("language", ""));
				serie.setTvmazeId(id);
				serie.setMediaType(0);
				JSONObject network = show.optJSONObject("network");
				if (network == null) network = show.optJSONObject("webChannel");
				if (network != null) serie.setNetwork(network.optString("name", ""));
				seen.put(id, serie);
			}
			results.addAll(seen.values());
		} catch (JsonFetcher.RateLimitException e) {
			throw e;
		} catch (Exception e) {
			Log.e(TAG, "getScheduleShows failed: " + e.getMessage());
			return null;
		}
		return results;
	}

	/**
	 * Fetch a full show record (with cast) plus all its episodes.
	 * Returns null on any failure.
	 *
	 * @throws JsonFetcher.RateLimitException on HTTP 429 so callers can
	 *         back off and retry instead of treating it as a dead show.
	 */
	public Serie getShow(String tvmazeId) throws JsonFetcher.RateLimitException {
		throttleApi();
		try {
			JSONObject show = JsonFetcher.getJsonObject(BASE + "/shows/" + tvmazeId + "?embed=cast");
			throttleApi();
			JSONArray eps = JsonFetcher.getJsonArray(BASE + "/shows/" + tvmazeId + "/episodes?specials=1");

			Serie serie = new Serie();
			String id = String.valueOf(show.optInt("id", 0));
			serie.setId(id);
			serie.setSerieId(id);
			serie.setSerieName(show.optString("name", ""));
			serie.setOverview(HtmlUtil.stripHtml(show.optString("summary", null)));
			serie.setFirstAired(show.optString("premiered", ""));
			serie.setImdbId(optStringPath(show, "externals", "imdb"));
			serie.setNetwork(networkName(show));
			serie.setStatus(show.optString("status", ""));
			serie.setRating(ratingAverage(show));
			serie.setRuntime(show.isNull("runtime") ? "" : String.valueOf(show.optInt("runtime", 0)));
			serie.setGenres(stringList(show.optJSONArray("genres")));
			String poster = imageUrl(show);
			serie.setPoster(poster);
			// TVMaze has no separate banner/fanart artwork; reuse the poster.
			serie.setFanart(poster);
			serie.setBanner("");
			serie.setLanguage(show.optString("language", ""));
			JSONObject schedule = show.optJSONObject("schedule");
			if (schedule != null) {
				serie.setAirsDayOfWeek(joinStrings(schedule.optJSONArray("days"), ", "));
				serie.setAirsTime(schedule.optString("time", ""));
			}
			serie.setLastUpdated(String.valueOf(show.optLong("updated", 0)));
			serie.setActors(castNames(show));
			serie.setTvmazeId(id);
			serie.setMediaType(0);

			List<Episode> episodes = new ArrayList<Episode>();
			for (int i = 0; i < eps.length(); i++) {
				JSONObject ep = eps.optJSONObject(i);
				if (ep == null) {
					continue;
				}
				episodes.add(mapEpisode(ep));
			}
			serie.setEpisodes(episodes);
			serie.setNSeasons(distinctSeasons(episodes));

			return serie;
		} catch (JsonFetcher.RateLimitException e) {
			throw e;
		} catch (Exception e) {
			Log.e(TAG, "getShow failed: " + e.getMessage());
			return null;
		}
	}

	/*
	 * SPECIALS VERIFICATION (checked against the real API on 2026-09-15):
	 *
	 * GET https://api.tvmaze.com/shows/{id}/episodes?specials=1 was tried for
	 * show 82 (Game of Thrones: 12 specials) and show 210 (Doctor Who: 101
	 * specials). TVMaze does NOT use season 0 for specials. Instead,
	 * specials arrive with:
	 *   - "type" = "significant_special" or "insignificant_special"
	 *     (regular episodes are "type" = "regular")
	 *   - "season" = the season the special is associated with (non-zero,
	 *     e.g. GoT's "Inside Game of Thrones" lists season 1)
	 *   - "number" = null (regular episodes have a real number)
	 *
	 * The app's convention is seasonNumber 0 = specials (see
	 * DroidShows.includeSpecialsOption, which toggles "AND seasonNumber <> 0"
	 * in the queries). Therefore any episode whose type is not "regular"
	 * - or whose episode number is null - is translated to seasonNumber 0
	 * below. The episodeNumber of specials is 0 (TVMaze reports null).
	 */
	private Episode mapEpisode(JSONObject ep) {
		Episode episode = new Episode();

		episode.setId(String.valueOf(ep.optInt("id", 0)));

		String name = ep.optString("name", "");
		// keep the old convention: blank episode names become a single space
		episode.setEpisodeName(name.length() == 0 ? " " : name);

		String type = ep.optString("type", "regular");
		boolean isSpecial = !"regular".equalsIgnoreCase(type) || ep.isNull("number");
		int season = isSpecial ? 0 : ep.optInt("season", 0);
		episode.setSeasonNumber(season);
		episode.setEpisodeNumber(ep.isNull("number") ? 0 : ep.optInt("number", 0));

		episode.setFirstAired(ep.optString("airdate", ""));
		episode.setOverview(HtmlUtil.stripHtml(ep.optString("summary", null)));
		episode.setFilename(imageUrl(ep, "medium"));

		return episode;
	}

	/**
	 * Resolve an old TheTVDB series id to the TVMaze show id, for migrating
	 * existing DB rows. Returns String.valueOf(show.id), or null when the
	 * lookup fails or the id is unknown (TVMaze answers 404 for those).
	 *
	 * Note: this endpoint answers with a 301 redirect to the canonical show
	 * URL; JsonFetcher follows redirects so the lookup still succeeds.
	 *
	 * @throws JsonFetcher.RateLimitException on HTTP 429 so callers can
	 *         back off and retry instead of treating it as a dead show.
	 */
	public String resolveTVDBId(String tvdbId) throws JsonFetcher.RateLimitException {
		throttleApi();
		try {
			JSONObject show = JsonFetcher.getJsonObject(BASE + "/lookup/shows?thetvdb=" + tvdbId);
			if (show == null) {
				return null;
			}
			return String.valueOf(show.optInt("id", 0));
		} catch (JsonFetcher.RateLimitException e) {
			throw e;
		} catch (Exception e) {
			Log.e(TAG, "resolveTVDBId failed: " + e.getMessage());
			return null;
		}
	}

	// ---- private helpers -------------------------------------------------

	/**
	 * Pick the best poster URL from a TVMaze "image" object: original first,
	 * medium as fallback, "" when there is no image. Null-safe.
	 */
	private static String imageUrl(JSONObject obj) {
		return imageUrl(obj, "original");
	}

	private static String imageUrl(JSONObject obj, String preferred) {
		if (obj == null) {
			return "";
		}
		JSONObject image = obj.optJSONObject("image");
		if (image == null) {
			return "";
		}
		String url = image.optString(preferred, "");
		if (url.length() == 0 && !"medium".equals(preferred)) {
			url = image.optString("medium", "");
		}
		return url;
	}

	/** network name, falling back to webChannel name; "" when unknown. */
	private static String networkName(JSONObject show) {
		String name = optStringPath(show, "network", "name");
		if (name.length() == 0) {
			name = optStringPath(show, "webChannel", "name");
		}
		return name;
	}

	/** show.rating.average as a String; "" when missing. */
	private static String ratingAverage(JSONObject show) {
		JSONObject rating = show.optJSONObject("rating");
		if (rating == null || rating.isNull("average")) {
			return "";
		}
		return String.valueOf(rating.optDouble("average", 0.0));
	}

	/** Names from _embedded.cast[].person.name; empty list when missing. */
	private static List<String> castNames(JSONObject show) {
		List<String> actors = new ArrayList<String>();
		JSONObject embedded = show.optJSONObject("_embedded");
		if (embedded == null) {
			return actors;
		}
		JSONArray cast = embedded.optJSONArray("cast");
		if (cast == null) {
			return actors;
		}
		for (int i = 0; i < cast.length(); i++) {
			JSONObject member = cast.optJSONObject(i);
			if (member == null) {
				continue;
			}
			String name = optStringPath(member, "person", "name");
			if (name.length() > 0) {
				actors.add(name);
			}
		}
		return actors;
	}

	/** Null-safe nested optString: obj -> child key -> string key. */
	private static String optStringPath(JSONObject obj, String childKey, String stringKey) {
		if (obj == null) {
			return "";
		}
		JSONObject child = obj.optJSONObject(childKey);
		if (child == null) {
			return "";
		}
		return child.optString(stringKey, "");
	}

	/** Convert a JSONArray of strings to a List<String>. */
	private static List<String> stringList(JSONArray arr) {
		List<String> list = new ArrayList<String>();
		if (arr != null) {
			for (int i = 0; i < arr.length(); i++) {
				list.add(arr.optString(i, ""));
			}
		}
		return list;
	}

	/** Join a JSONArray of strings with the given separator. */
	private static String joinStrings(JSONArray arr, String sep) {
		if (arr == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arr.length(); i++) {
			String s = arr.optString(i, "").trim();
			if (s.length() == 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(sep);
			}
			sb.append(s);
		}
		return sb.toString();
	}

	/** Distinct season numbers across episodes, sorted ascending. */
	private static List<Integer> distinctSeasons(List<Episode> episodes) {
		List<Integer> seasons = new ArrayList<Integer>();
		for (int i = 0; i < episodes.size(); i++) {
			int n = episodes.get(i).getSeasonNumber();
			if (!seasons.contains(Integer.valueOf(n))) {
				seasons.add(Integer.valueOf(n));
			}
		}
		Collections.sort(seasons);
		return seasons;
	}
}
