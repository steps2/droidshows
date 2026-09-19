package app.tvmovie.tracker;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Home-screen "Up next" widget. Content is pushed by WidgetUpdater
 *  whenever library data changes; the update period is 0 (no polling). */
public class UpNextWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		WidgetUpdater.refresh(context);
	}
}
