package app.tvmovie.tracker;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/* Applies the user's theme choice (Automatic / Light / Dark / AMOLED).
 * On Android 12+ the Material You dynamic colors from the wallpaper are used;
 * on older devices the static Material 3 palette is the fallback.
 * The AMOLED theme always keeps pure-black surfaces, with the dynamic
 * (or fallback) accent color on top. Must be called before super.onCreate(). */
public class ThemeHelper {
	public static final String PREF_NAME = "DroidShowsPref";
	public static final String THEME_PREF_NAME = "theme";
	public static final int THEME_AUTOMATIC = 0;
	public static final int THEME_LIGHT = 1;
	public static final int THEME_DARK = 2;
	public static final int THEME_AMOLED = 3;

	public static void applyTheme(Activity activity) {
		SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, 0);
		int themeMode = prefs.getInt(THEME_PREF_NAME, THEME_AUTOMATIC);
		if (themeMode == THEME_AMOLED) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			activity.setTheme(R.style.Theme_TVMovieTracker_Amoled);
			DynamicColors.applyToActivityIfAvailable(activity,
				new DynamicColorsOptions.Builder()
					.setThemeOverlay(R.style.ThemeOverlay_TVMovieTracker_AmoledDynamic)
					.build());
		} else {
			AppCompatDelegate.setDefaultNightMode(themeMode == THEME_LIGHT
				? AppCompatDelegate.MODE_NIGHT_NO
				: themeMode == THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES
				: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
			activity.setTheme(R.style.Theme_TVMovieTracker);
			DynamicColors.applyToActivityIfAvailable(activity);
		}
	}
}
