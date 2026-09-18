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

	/** Reference-counted tracking of in-flight operations that drive the
	 *  progress bar. Overlapping syncs (e.g. a per-show update starting while
	 *  "update all" is still running) used to clobber each other: one
	 *  operation finishing hid the bar while another was still running.
	 *  Every beginOperation() must be paired with endOperation(), in a
	 *  finally block; the bar only hides when the last operation ends.
	 *  Thread-safe. */
	private static final Object OP_LOCK = new Object();
	private static int activeOperations = 0;

	/** Mark the start of an operation that drives the progress bar. The bar
	 *  is shown (with the first operation's parameters while several
	 *  overlap) and stays visible until the last active operation ends. */
	public static void beginOperation(boolean indeterminate, int max) {
		synchronized (OP_LOCK) {
			if (activeOperations == 0)
				SyncProgress.show(indeterminate, max);
			activeOperations++;
		}
	}

	/** Mark the end of an operation started with beginOperation(). Hides the
	 *  bar only when no operations remain in flight. Safe to call when no
	 *  operation is active. */
	public static void endOperation() {
		synchronized (OP_LOCK) {
			if (activeOperations > 0)
				activeOperations--;
			if (activeOperations == 0)
				SyncProgress.hide();
		}
	}

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
