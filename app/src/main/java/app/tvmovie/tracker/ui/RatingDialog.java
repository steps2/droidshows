package app.tvmovie.tracker.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RatingBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import app.tvmovie.tracker.R;

/**
 * 5-star (half-star steps) personal rating dialog. Stores 0-10 in the
 * database (stars * 2); 0 means "not rated". Shared by the details screen
 * and the series long-press menu.
 */
public class RatingDialog {

	public interface OnRated {
		void onRated(double rating10);
	}

	public static void show(final Activity activity, String title, double current10, final OnRated callback) {
		final RatingBar bar = new RatingBar(activity);
		bar.setNumStars(5);
		bar.setStepSize(0.5f);
		bar.setRating((float) (current10 / 2.0));
		bar.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		LinearLayout layout = new LinearLayout(activity);
		layout.setOrientation(LinearLayout.HORIZONTAL);
		layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
		int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad / 2, pad, pad / 2);
		layout.addView(bar);
		new MaterialAlertDialogBuilder(activity)
				.setTitle(title)
				.setView(layout)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						callback.onRated(bar.getRating() * 2.0);
					}
				})
				.setNeutralButton(R.string.rate_clear, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						callback.onRated(0);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/** "★★★★½ (4.5)" style label for a 0-10 rating. */
	public static String starsLabel(double rating10) {
		StringBuilder sb = new StringBuilder();
		double stars = rating10 / 2.0;
		int full = (int) stars;
		boolean half = (stars - full) >= 0.5;
		for (int i = 0; i < full; i++) sb.append('\u2605');
		if (half) sb.append('\u00bd');
		int shown = full + (half ? 1 : 0);
		for (int i = shown; i < 5; i++) sb.append('\u2606');
		sb.append(" (").append(trim(stars)).append(')');
		return sb.toString();
	}

	private static String trim(double v) {
		if (v == Math.floor(v)) return String.valueOf((int) v);
		return String.valueOf(v);
	}
}
