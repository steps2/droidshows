package app.tvmovie.tracker.provider;

import android.util.Log;

/**
 * Small HTML helpers for provider data. TVMaze returns HTML in its
 * "summary" fields (e.g. "&lt;p&gt;Based on...&lt;/p&gt;"), which must be
 * stripped before being stored/displayed.
 */
public class HtmlUtil {

	private static final String TAG = "HtmlUtil";

	/**
	 * Strip HTML tags from a string. Null-safe: returns "" for null input.
	 * Uses android.text.Html when available and falls back to a plain
	 * regex tag stripper on any error.
	 */
	@SuppressWarnings("deprecation")
	public static String stripHtml(String html) {
		if (html == null) {
			return "";
		}
		try {
			return android.text.Html.fromHtml(html).toString().trim();
		} catch (Exception e) {
			Log.e(TAG, "fromHtml failed, using regex fallback: " + e.getMessage());
			return html.replaceAll("<[^>]*>", "").trim();
		}
	}
}
