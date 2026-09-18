package nl.asymmetrics.droidshows;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.HashMap;
import java.util.Map;

/** Binds every screen's top progress bar to the app-wide SyncProgress, so a
 *  running sync/update stays visible (and keeps moving) no matter which
 *  screen the user is on, until the work finishes. Screens that already have
 *  a bar (R.id.top_progress) reuse it; every other screen gets one injected
 *  at the top. */
public class DroidShowsApp extends Application {

	private final Map<Activity, SyncProgress.Listener> bindings = new HashMap<Activity, SyncProgress.Listener>();

	@Override
	public void onCreate() {
		super.onCreate();
		registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
			public void onActivityCreated(Activity a, Bundle b) {}
			public void onActivityStarted(Activity a) {}
			public void onActivityResumed(Activity a) { bind(a); }
			public void onActivityPaused(Activity a) { unbind(a); }
			public void onActivityStopped(Activity a) {}
			public void onActivitySaveInstanceState(Activity a, Bundle b) {}
			public void onActivityDestroyed(Activity a) { unbind(a); }
		});
	}

	private void bind(final Activity activity) {
		if (bindings.containsKey(activity)) return;
		LinearProgressIndicator bar =
			(LinearProgressIndicator) activity.findViewById(R.id.top_progress);
		if (bar == null) {
			ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
			if (content == null) return;
			bar = new LinearProgressIndicator(activity);
			bar.setId(R.id.top_progress);
			bar.setVisibility(View.GONE);
			content.addView(bar, 0, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		final LinearProgressIndicator b = bar;
		SyncProgress.Listener l = new SyncProgress.Listener() {
			public void onSyncProgress(int progress, int max, boolean indeterminate, boolean active) {
				if (!active) {
					b.setVisibility(View.GONE);
					return;
				}
				b.setIndeterminate(indeterminate);
				if (!indeterminate) {
					b.setMax(Math.max(1, max));
					b.setProgress(progress);
				}
				b.setVisibility(View.VISIBLE);
			}
		};
		bindings.put(activity, l);
		SyncProgress.addListener(l);
	}

	private void unbind(Activity activity) {
		SyncProgress.Listener l = bindings.remove(activity);
		if (l != null) SyncProgress.removeListener(l);
	}
}
