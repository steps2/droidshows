package app.tvmovie.tracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import com.google.android.material.appbar.MaterialToolbar;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.tvmovie.tracker.DroidShowsApp;
import app.tvmovie.tracker.R;
import app.tvmovie.tracker.utils.SQLiteStore;

/**
 * Month calendar of unwatched, already-aired-or-upcoming episodes from
 * Watching TV shows. Days with episodes are marked with a dot; tapping a
 * day lists that day's episodes and tapping one opens the show.
 */
public class CalendarActivity extends Activity {

	private SQLiteStore db;
	private int year, month;					// month: 0-11
	private int selectedDay = -1;
	private Map<Integer, List<String[]>> airings;	// day -> {serieId, label}
	private GridView grid;
	private ListView list;

	private static class DayCell {
		int day;			// 0 = blank
		boolean marked;
		boolean isToday;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		app.tvmovie.tracker.ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.calendar);
		MaterialToolbar toolbar = (MaterialToolbar) findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { finish(); }
		});
		db = SQLiteStore.getInstance(this);
		Calendar now = Calendar.getInstance();
		year = now.get(Calendar.YEAR);
		month = now.get(Calendar.MONTH);
		if (savedInstanceState != null) {
			year = savedInstanceState.getInt("year", year);
			month = savedInstanceState.getInt("month", month);
			selectedDay = savedInstanceState.getInt("day", -1);
		} else {
			selectedDay = now.get(Calendar.DAY_OF_MONTH);
		}
		grid = (GridView) findViewById(R.id.cal_grid);
		list = (ListView) findViewById(R.id.cal_list);
		grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DayCell cell = (DayCell) parent.getItemAtPosition(position);
				if (cell.day > 0) {
					selectedDay = cell.day;
					renderGrid();
					renderList();
				}
			}
		});
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				List<String[]> dayAirings = airings.get(selectedDay);
				if (dayAirings != null && position < dayAirings.size()) {
					Intent i = new Intent(CalendarActivity.this, ViewSerie.class);
					i.putExtra("serieId", dayAirings.get(position)[0]);
					startActivity(i);
				}
			}
		});
		((Button) findViewById(R.id.cal_prev)).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { shiftMonth(-1); }
		});
		((Button) findViewById(R.id.cal_next)).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { shiftMonth(1); }
		});
		setupWeekdayHeader();
		rebuild();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("year", year);
		outState.putInt("month", month);
		outState.putInt("day", selectedDay);
	}

	private void shiftMonth(int delta) {
		month += delta;
		if (month < 0) { month = 11; year--; }
		if (month > 11) { month = 0; year++; }
		selectedDay = 1;
		rebuild();
	}

	private void setupWeekdayHeader() {
		GridView header = (GridView) findViewById(R.id.cal_weekdays);
		Calendar cal = Calendar.getInstance();
		int first = cal.getFirstDayOfWeek();
		String[] names = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
		List<String> labels = new ArrayList<String>();
		for (int i = 0; i < 7; i++)
			labels.add(names[((first - 1 + i) % 7) + 1]);
		header.setAdapter(new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, labels) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				tv.setGravity(Gravity.CENTER);
				tv.setTextSize(12);
				return tv;
			}
		});
	}

	private void rebuild() {
		((TextView) findViewById(R.id.cal_title)).setText(
				new DateFormatSymbols(Locale.getDefault()).getMonths()[month] + " " + year);
		DroidShowsApp.beginOperation(true, 0);
		new Thread(new Runnable() {
			public void run() {
				final Map<Integer, List<String[]>> data = queryMonth();
				runOnUiThread(new Runnable() {
					public void run() {
						try {
							if (!isFinishing()) {
								airings = data;
								renderGrid();
								renderList();
							}
						} finally {
							DroidShowsApp.endOperation();
						}
					}
				});
			}
		}).start();
	}

	private Map<Integer, List<String[]>> queryMonth() {
		Map<Integer, List<String[]>> map = new HashMap<Integer, List<String[]>>();
		String prefix = String.format(Locale.US, "%04d-%02d-", year, month + 1);
		Cursor c = null;
		try {
			c = db.Query("SELECT e.firstAired, s.id, s.serieName, e.seasonNumber, e.episodeNumber, e.episodeName "
				+ "FROM episodes e JOIN series s ON s.id=e.serieId "
				+ "WHERE (s.passiveStatus=0 OR s.passiveStatus IS NULL) AND s.mediaType=0 "
				+ "AND e.seen=0 AND e.seasonNumber<>0 AND e.firstAired LIKE '" + prefix + "%' "
				+ "ORDER BY e.firstAired, s.serieName COLLATE NOCASE");
			if (c != null && c.moveToFirst()) {
				do {
					String aired = c.getString(0);
					int day = 0;
					try {
						day = Integer.parseInt(aired.substring(8, 10));
					} catch (Exception e) { continue; }
					String label = c.getString(2) + " \u00b7 S" + pad(c.getString(3))
						+ "E" + pad(c.getString(4));
					String title = c.getString(5);
					if (title != null && !title.trim().isEmpty())
						label += " \u00b7 " + title.trim();
					List<String[]> dayList = map.get(day);
					if (dayList == null) {
						dayList = new ArrayList<String[]>();
						map.put(day, dayList);
					}
					dayList.add(new String[] { c.getString(1), label });
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(SQLiteStore.TAG, e.getMessage());
		}
		if (c != null) c.close();
		return map;
	}

	private String pad(String n) {
		try {
			int v = Integer.parseInt(n);
			return v < 10 ? "0" + v : String.valueOf(v);
		} catch (NumberFormatException e) {
			return n != null ? n : "";
		}
	}

	private void renderGrid() {
		Calendar cal = Calendar.getInstance();
		cal.set(year, month, 1);
		int firstWeekday = cal.getFirstDayOfWeek();
		int offset = (cal.get(Calendar.DAY_OF_WEEK) - firstWeekday + 7) % 7;
		int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
		Calendar today = Calendar.getInstance();
		boolean thisMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month;
		final List<DayCell> cells = new ArrayList<DayCell>();
		for (int i = 0; i < offset; i++) {
			DayCell blank = new DayCell();
			blank.day = 0;
			cells.add(blank);
		}
		for (int d = 1; d <= daysInMonth; d++) {
			DayCell cell = new DayCell();
			cell.day = d;
			cell.marked = airings != null && airings.containsKey(d);
			cell.isToday = thisMonth && d == today.get(Calendar.DAY_OF_MONTH);
			cells.add(cell);
		}
		grid.setAdapter(new BaseAdapter() {
			public int getCount() { return cells.size(); }
			public Object getItem(int position) { return cells.get(position); }
			public long getItemId(int position) { return position; }
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv;
				if (convertView instanceof TextView) {
					tv = (TextView) convertView;
				} else {
					tv = new TextView(CalendarActivity.this);
					tv.setGravity(Gravity.CENTER);
					tv.setPadding(0, dp(6), 0, dp(6));
				}
				DayCell cell = cells.get(position);
				if (cell.day == 0) {
					tv.setText("");
				} else {
					tv.setText(cell.marked ? cell.day + " \u25cf" : String.valueOf(cell.day));
					tv.setTypeface(null, (cell.isToday || cell.day == selectedDay)
							? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
				}
				return tv;
			}
		});
	}

	private void renderList() {
		List<String> labels = new ArrayList<String>();
		if (airings != null && airings.containsKey(selectedDay)) {
			for (String[] a : airings.get(selectedDay))
				labels.add(a[1]);
		}
		if (labels.isEmpty())
			labels.add(getString(R.string.calendar_no_airings));
		list.setAdapter(new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, labels));
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
	}
}
