/*	AsyncInfo might have the same issue as updateAllSeries() had when show order is changed during iteration...
 * 	Get list of series in another way?
 */

package nl.asymmetrics.droidshows;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nl.asymmetrics.droidshows.R;
import nl.asymmetrics.droidshows.provider.JsonFetcher;
import nl.asymmetrics.droidshows.provider.TMDB;
import nl.asymmetrics.droidshows.provider.TVMaze;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import nl.asymmetrics.droidshows.thetvdb.model.TVShowItem;
import nl.asymmetrics.droidshows.ui.AddSerie;
import nl.asymmetrics.droidshows.ui.AddMovie;
import nl.asymmetrics.droidshows.ui.BounceListView;
import nl.asymmetrics.droidshows.ui.IconView;
import nl.asymmetrics.droidshows.ui.SerieSeasons;
import nl.asymmetrics.droidshows.ui.ViewEpisode;
import nl.asymmetrics.droidshows.ui.ViewSerie;
import nl.asymmetrics.droidshows.utils.SQLiteStore;
import nl.asymmetrics.droidshows.utils.Update;
import nl.asymmetrics.droidshows.utils.Utils;
import nl.asymmetrics.droidshows.utils.SQLiteStore.NextEpisode;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.animation.OvershootInterpolator;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ToggleButton;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import nl.asymmetrics.droidshows.ui.HamburgerDrawable;

public class DroidShows extends AppCompatActivity
{
	private static final String TAG = "DroidShows";

	// Load a vector menu icon through AppCompatResources so vectors render on
	// the whole minSdk-14 range.
	private android.graphics.drawable.Drawable menuIcon(int resId) {
		return AppCompatResources.getDrawable(this, resId);
	}

	// Android hides icons in the overflow (⋮) popup unless opted in;
	// reflection keeps this working with framework and androidx menus alike.
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			try {
				Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", boolean.class);
				m.setAccessible(true);
				m.invoke(menu, true);
			} catch (Exception e) {
				Log.w("DroidShows", "Could not enable menu icons", e);
			}
		}
		return super.onMenuOpened(featureId, menu);
	}

	// defined in build.gradle either "" for release or "_DEBUG" for debug build
	// used to allow different configurations between debung and release to protect production data
	public static final String CONFIG_SUFFIX = BuildConfig.CONFIG_SUFFIX;

	public static final String BACKUP_DIR = "/TVMovie Tracker" + CONFIG_SUFFIX;

	/* Menu Items */
	private static final int UNDO_MENU_ITEM = Menu.FIRST;
	private static final int FILTER_MENU_ITEM = UNDO_MENU_ITEM + 1;
	private static final int SEEN_MENU_ITEM = FILTER_MENU_ITEM + 1;
	private static final int SORT_MENU_ITEM = SEEN_MENU_ITEM + 1;
	private static final int TOGGLE_ARCHIVE_MENU_ITEM = SORT_MENU_ITEM + 1;
	private static final int LOG_MODE_ITEM = TOGGLE_ARCHIVE_MENU_ITEM + 1;
	private static final int SEARCH_MENU_ITEM = LOG_MODE_ITEM + 1;
	private static final int ADD_SERIE_MENU_ITEM = SEARCH_MENU_ITEM + 1;
	private static final int UPDATEALL_MENU_ITEM = ADD_SERIE_MENU_ITEM + 1;
	private static final int OPTIONS_MENU_ITEM = UPDATEALL_MENU_ITEM + 1;
	private static final int EXIT_MENU_ITEM = OPTIONS_MENU_ITEM + 1;
	private static final int BACKUP_NOW_MENU_ITEM = EXIT_MENU_ITEM + 1;
	private static final int DISCOVER_MENU_ITEM = BACKUP_NOW_MENU_ITEM + 1;
	private static final int REQ_RESTORE_BACKUP = 1001;
	private static final int REQ_BACKUP_NOW = 1002;
	/* Context Menus */
	private static final int VIEW_SEASONS_CONTEXT = Menu.FIRST;
	private static final int VIEW_SERIEDETAILS_CONTEXT = VIEW_SEASONS_CONTEXT + 1;
	private static final int VIEW_EPISODEDETAILS_CONTEXT = VIEW_SERIEDETAILS_CONTEXT + 1;
	private static final int EXT_RESOURCES_CONTEXT = VIEW_EPISODEDETAILS_CONTEXT + 1;
	private static final int MARK_NEXT_EPISODE_AS_SEEN_CONTEXT = EXT_RESOURCES_CONTEXT + 1;
	private static final int TOGGLE_ARCHIVED_CONTEXT = MARK_NEXT_EPISODE_AS_SEEN_CONTEXT + 1;
	private static final int PIN_CONTEXT = TOGGLE_ARCHIVED_CONTEXT + 1;
	private static final int UPDATE_CONTEXT = PIN_CONTEXT + 1;
	private static final int SYNOPSIS_LANGUAGE = UPDATE_CONTEXT + 1;
	private static final int DELETE_CONTEXT = SYNOPSIS_LANGUAGE + 1;
	private static AlertDialog m_AlertDlg;
	private volatile int updateAllDone = 0;
	private boolean swipeTriggered = false;	// kept for the update dialog logic; the pull gesture is removed
	private volatile boolean updatingAll = false;
	public static SeriesAdapter seriesAdapter;
	private static BounceListView listView = null;
	private static String backFromSeasonSerieId;
	private Utils utils = new Utils();
	private Update updateDS;
	private static final String PREF_NAME = "DroidShowsPref";
	private SharedPreferences sharedPrefs;
	private static final String AUTO_BACKUP_PREF_NAME = "auto_backup";
	private static boolean autoBackup;
	private static final String BACKUP_FOLDER_PREF_NAME = "backup_folder" + CONFIG_SUFFIX;	
	private static String backupFolder;
	private static final String BACKUP_VERSIONING_PREF_NAME = "backup_versioning";
	private static boolean backupVersioning;
	private static final String EXCLUDE_SEEN_PREF_NAME = "exclude_seen";
	private static boolean excludeSeen;
	private static final String SORT_PREF_NAME = "sort";
	private static final int SORT_BY_NAME = 0;
	private static final int SORT_BY_UNSEEN = 1;
	private static int sortOption;
	private static final String LATEST_SEASON_PREF_NAME = "last_season";
	private static final int UPDATE_ALL_SEASONS = 0;
	private static final int UPDATE_LATEST_SEASON_ONLY = 1;
	private static int latestSeasonOption;
	private static final String INCLUDE_SPECIALS_NAME = "include_specials";
	public static boolean includeSpecialsOption;
	private static final String FULL_LINE_CHECK_NAME = "full_line";
	public static boolean fullLineCheckOption;
	private static final String LARGE_POSTERS_NAME = "large_posters";
	private static final int LARGE_POSTERS_HEIGHT = 270;
	public static boolean largePostersOption;
	private static final String SWITCH_SWIPE_DIRECTION = "switch_swipe_direction";
	public static boolean switchSwipeDirection;
	private static final String LAST_STATS_UPDATE_NAME = "last_stats_update";
	private static String lastStatsUpdateCurrent;
	private static final String LAST_STATS_UPDATE_ARCHIVE_NAME = "last_stats_update_archive";
	private static String lastStatsUpdateArchive;
	public static final String TMDB_API_KEY_NAME = "tmdb_api_key";
	public static int mediaType = 0;	// 0 = TV Shows, 1 = Movies
	private static final String LANGUAGE_CODE_NAME = "language";
	private static final String SHOW_NEXT_AIRING = "show_next_airing";
	public static boolean showNextAiring;
	private static final String MARK_FROM_LAST_WATCHED = "mark_from_last_watched";
	public static boolean markFromLastWatched;
	public static String langCode;
	private static final String PINNED_SHOWS_NAME = "pinned_shows";
	private static List<String> pinnedShows = new ArrayList<String>();
	private static final String FILTER_NETWORKS_NAME = "filter_networks";
	private static boolean filterNetworks;
	private static final String NETWORKS_NAME = "networks";
	private static List<String> networks = new ArrayList<String>();
	public static Thread deleteTh = null;
	public static Thread updateShowTh = null;
	public static Thread updateAllShowsTh = null;
	private String dialogMsg;
	public static SQLiteStore db;
	public static List<TVShowItem> series;
	private static List<String[]> undo = new ArrayList<String[]>();
	/* Swipe-reveal row actions (beta 33): one open row at a time, tracked here */
	private View openSwipeRow = null;
	private int swipeActionWidthPx;
	private int swipeTouchSlop;
	/* Tab-strip swipe (beta 34): detected in dispatchTouchEvent, because the
	 * tabs themselves consume touches and a view-level listener never fires. */
	private TabLayout tabStrip = null;
	private boolean tabArmed = false;
	private float tabDownX, tabDownY;
	private static AsyncInfo asyncInfo;
	private static EditText searchV;
	private InputMethodManager keyboard;
	private int padding;
	public static int showArchive;
	private Vibrator vib = null;
	private TVShowItem lastSerie;
	private static View main;
	public static boolean logMode = false;
	public static String removeEpisodeFromLog = "";
	private DrawerLayout drawerLayout;
	private NavigationView navView;
	private ActionBarDrawerToggle drawerToggle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		installCrashReporter();
		// Apply the saved theme (plus Material You dynamic colors) before the window is created.
		ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		if (!isTaskRoot()) {	// Prevent multiple instances: https://stackoverflow.com/a/11042163
			final Intent intent = getIntent();
			if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(intent.getAction())) {
				finish();
				return;
			}
		}
		setContentView(R.layout.main);
		main = findViewById(R.id.main);
		db = SQLiteStore.getInstance(this);
		// One-time move of poster files from the old files-dir location to the
		// cache dir (plus the matching DB path rewrite), split library and
		// Discover posters, then prune the Discover cache to its size cap.
		// cache to its size cap. Runs off the UI thread: file I/O + DB write.
		new Thread(new Runnable() {
			public void run() {
				try {
					File oldDir = new File(getApplicationContext().getFilesDir(), "thumbs");
					if (oldDir.exists()) {
						Utils.migratePosterCache(getApplicationContext());
						db.execQuery("UPDATE series SET posterThumb = REPLACE(posterThumb, '/files/thumbs', '/cache/thumbs/library')");
					}
					// library posters -> thumbs/library (never pruned),
					// everything else -> thumbs/discover (capped)
					Utils.organizePosterCache(getApplicationContext(), db);
					Utils.prunePosterCache(getApplicationContext());
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "poster cache migration failed", e);
				}
			}
		}).start();
		if (savedInstanceState != null) {
			showArchive = savedInstanceState.getInt("showArchive");
			mediaType = savedInstanceState.getInt("mediaType", 0);
		}
		setupDrawer();
		float swipeDensity = getApplicationContext().getResources().getDisplayMetrics().density;
		swipeActionWidthPx = (int) (96 * swipeDensity + 0.5f);
		tabSwitchDistancePx = swipeActionWidthPx * 2;	// keep swiping past the actions to switch tab
		swipeTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
		setupToolbar();
		showLastCrashIfAny();

		// Preferences
		sharedPrefs = getSharedPreferences(PREF_NAME, 0);
		autoBackup = sharedPrefs.getBoolean(AUTO_BACKUP_PREF_NAME, false);
		backupFolder = sharedPrefs.getString(BACKUP_FOLDER_PREF_NAME, Environment.getExternalStorageDirectory() + BACKUP_DIR);
		// Migrate the stored folder if it still points at the old default location;
		// a user-customized folder is left untouched.
		String oldDefaultBackupFolder = Environment.getExternalStorageDirectory() + "/DroidShows" + CONFIG_SUFFIX;
		if (backupFolder.equals(oldDefaultBackupFolder)) {
			backupFolder = Environment.getExternalStorageDirectory() + BACKUP_DIR;
			sharedPrefs.edit().putString(BACKUP_FOLDER_PREF_NAME, backupFolder).apply();
		}
		
		backupVersioning = sharedPrefs.getBoolean(BACKUP_VERSIONING_PREF_NAME, true);
		excludeSeen = sharedPrefs.getBoolean(EXCLUDE_SEEN_PREF_NAME, false);
		sortOption = sharedPrefs.getInt(SORT_PREF_NAME, SORT_BY_NAME);
		latestSeasonOption = sharedPrefs.getInt(LATEST_SEASON_PREF_NAME, UPDATE_LATEST_SEASON_ONLY);
		includeSpecialsOption = sharedPrefs.getBoolean(INCLUDE_SPECIALS_NAME, false);
		fullLineCheckOption = sharedPrefs.getBoolean(FULL_LINE_CHECK_NAME, false);
		largePostersOption = sharedPrefs.getBoolean(LARGE_POSTERS_NAME, false);
		switchSwipeDirection = sharedPrefs.getBoolean(SWITCH_SWIPE_DIRECTION, false);
		lastStatsUpdateCurrent = sharedPrefs.getString(LAST_STATS_UPDATE_NAME, "");
		lastStatsUpdateArchive = sharedPrefs.getString(LAST_STATS_UPDATE_ARCHIVE_NAME, "");
		langCode = sharedPrefs.getString(LANGUAGE_CODE_NAME, getString(R.string.lang_code));
		showNextAiring = sharedPrefs.getBoolean(SHOW_NEXT_AIRING, false);
		markFromLastWatched = sharedPrefs.getBoolean(MARK_FROM_LAST_WATCHED, false);
		String pinnedShowsStr = sharedPrefs.getString(PINNED_SHOWS_NAME, "");
		if (!pinnedShowsStr.isEmpty())
			pinnedShows = new ArrayList<String>(Arrays.asList(pinnedShowsStr.replace("[", "").replace("]", "").split(", ")));
		filterNetworks = sharedPrefs.getBoolean(FILTER_NETWORKS_NAME, false);
		String networksStr = sharedPrefs.getString(NETWORKS_NAME, "");

		// Update database
		updateDS = new Update(db);
		boolean needsMig = updateDS.needsUpdate();
		if (needsMig) {
			backupBlocking(backupFolder);	// must finish before the migration below
			if (updateDS.updateDroidShows())
				db.updateShowStats();
			else {
				String error = getString(R.string.messages_error_dbupdate);
				Log.e(SQLiteStore.TAG, error);
				Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
			}
		}
		if (needsMig)
			migrateLibraryToTVMaze();

		if (!networksStr.isEmpty())
			networks = new ArrayList<String>(Arrays.asList(networksStr.replace("[", "").replace("]", "").split(", ")));
		series = Collections.synchronizedList(new ArrayList<TVShowItem>());
		seriesAdapter = new SeriesAdapter(this, R.layout.row, series);
		listView = (BounceListView) findViewById(android.R.id.list);
		listView.setEmptyView(findViewById(android.R.id.empty));
		listView.setAdapter(seriesAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				onListItemClick((ListView) parent, view, position, id);
			}
		});
		/* Row-swipe detection (beta 34): the listener sits on the ListView,
		 * the only place that reliably sees the whole gesture. */
		listView.setOnTouchListener(listSwipeListener);
		listView.setDivider(null);
		listView.setOverscrollHeader(getResources().getDrawable(R.drawable.shape_gradient_ring));
		if (savedInstanceState != null) {
			getSeries((savedInstanceState.getBoolean("searching") ? 2 : showArchive));
		} else {
			getSeries();
		}
		registerForContextMenu(listView);
		searchV = (EditText) findViewById(R.id.search_text);
		searchV.setFocusable(true);
		searchV.setFocusableInTouchMode(true);
		searchV.addTextChangedListener(new TextWatcher() {
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				seriesAdapter.getFilter().filter(s);
			}
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void afterTextChanged(Editable s) {}
		});
		keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		padding = (int) (6 * (getApplicationContext().getResources().getDisplayMetrics().densityDpi / 160f));
		vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	}

	/* Navigation drawer: TV Shows / Movies, backed by a NavigationView. */
	private void setupDrawer() {
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		navView = (NavigationView) findViewById(R.id.nav_view);
		navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
			@Override public boolean onNavigationItemSelected(MenuItem item) {
				if (item.getItemId() == R.id.nav_discover) {
					drawerLayout.closeDrawer(navView);
					startActivity(new Intent(DroidShows.this, nl.asymmetrics.droidshows.ui.DiscoverActivity.class));
					return true;
				}
				int position = (item.getItemId() == R.id.nav_movies) ? 1 : 0;
				drawerLayout.closeDrawer(navView);
				if (position != mediaType) { mediaType = position; getSeries(); }
				return true;
			}
		});
		navView.setCheckedItem(mediaType == 1 ? R.id.nav_movies : R.id.nav_tv);
	}

	/* Toolbar, tabs and drawer toggle must be set up in onCreate (not in
	 * onCreateOptionsMenu): with the NoActionBar Material3 theme the framework
	 * never calls onCreateOptionsMenu until a support action bar exists. */
	private void setupToolbar() {
		MaterialToolbar toolbar = (MaterialToolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		final TabLayout modeTabs = (TabLayout) findViewById(R.id.mode_tabs);
		modeTabs.clearOnTabSelectedListeners();
		TabLayout.Tab tab = modeTabs.getTabAt(logMode ? 2 : showArchive);
		if (tab != null) tab.select();
		modeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			public void onTabSelected(TabLayout.Tab tab) {
				int position = tab.getPosition();
				logMode = position == 2;
				showArchive = (position == 2 ? showArchive : position);
				if (logMode) clearFilter(null);
				closeOpenSwipeRow();
				getSeries();
			}
			public void onTabUnselected(TabLayout.Tab tab) {}
			public void onTabReselected(TabLayout.Tab tab) {}
		});
		/* Keep a reference for the activity-level tab-strip swipe detection. */
		tabStrip = modeTabs;
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
		// Hamburger-to-X indicator instead of the stock hamburger-to-arrow (beta-3 behavior preserved).
		HamburgerDrawable hamburger = new HamburgerDrawable(this);
		hamburger.setColor(drawerToggle.getDrawerArrowDrawable().getColor());
		drawerToggle.setDrawerArrowDrawable(hamburger);
		drawerLayout.addDrawerListener(drawerToggle);
	}

	/* Swipe sideways on the Watching / Finished / History tab strip to circle
	 * through the sections. Detected here because the tabs consume their own
	 * touches, so a listener on the TabLayout itself would never fire.
	 * Purely observational: the event stream always continues to the views. */
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				tabArmed = false;
				if (tabStrip != null && tabStrip.getVisibility() == View.VISIBLE) {
					int[] loc = new int[2];
					tabStrip.getLocationOnScreen(loc);
					float x = event.getRawX(), y = event.getRawY();
					// keep the screen's left edge for the navigation drawer
					if (x >= loc[0] + swipeActionWidthPx / 4 && x <= loc[0] + tabStrip.getWidth()
							&& y >= loc[1] && y <= loc[1] + tabStrip.getHeight()) {
						tabDownX = x;
						tabDownY = y;
						tabArmed = true;
					}
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (tabArmed) {
					float dx = event.getRawX() - tabDownX;
					float dy = event.getRawY() - tabDownY;
					if (Math.abs(dx) > swipeTouchSlop && Math.abs(dx) > Math.abs(dy) * 2) {
						tabArmed = false;
						cycleTab(dx < 0 ? 1 : -1);
					}
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				tabArmed = false;
				break;
		}
		return super.dispatchTouchEvent(event);
	}

	/* Persist uncaught exceptions so the next launch can show what crashed. */
	private static final String CRASH_FILE = "tvmovie-crash.txt";

	private void installCrashReporter() {
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				try {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					FileWriter w = new FileWriter(new File(getExternalFilesDir(null), CRASH_FILE));
					w.write(Build.MODEL + " / Android " + Build.VERSION.RELEASE + "\n" + sw.toString());
					w.close();
				} catch (Exception ignored) {}
				if (previous != null) previous.uncaughtException(t, e);
			}
		});
	}

	private void showLastCrashIfAny() {
		try {
			final File f = new File(getExternalFilesDir(null), CRASH_FILE);
			if (!f.exists()) return;
			StringBuilder sb = new StringBuilder();
			BufferedReader r = new BufferedReader(new FileReader(f));
			String line;
			while ((line = r.readLine()) != null) sb.append(line).append('\n');
			r.close();
			f.delete();
			final String trace = sb.toString();
			main.post(new Runnable() {
				public void run() {
					TextView tv = new TextView(DroidShows.this);
					tv.setText(trace);
					tv.setTextIsSelectable(true);
					int pad = (int) (16 * getResources().getDisplayMetrics().density);
					tv.setPadding(pad, pad, pad, pad);
					ScrollView sv = new ScrollView(DroidShows.this);
					sv.addView(tv);
					new MaterialAlertDialogBuilder(DroidShows.this)
						.setTitle(R.string.crash_title)
						.setView(sv)
						.setPositiveButton(android.R.string.ok, null)
						.setNeutralButton(R.string.share, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								Intent share = new Intent(Intent.ACTION_SEND);
								share.setType("text/plain");
								share.putExtra(Intent.EXTRA_TEXT, trace);
								startActivity(Intent.createChooser(share, getString(R.string.share)));
							}
						})
						.show();
				}
			});
		} catch (Exception ignored) {}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (drawerToggle != null)
			drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (drawerToggle != null)
			drawerToggle.onConfigurationChanged(newConfig);
	}

	/*
	 * Bulk-migrate the existing library from TheTVDB ids to TVMaze ids right
	 * after the database upgrade that added the tvmazeId column: for every TV
	 * show row without a tvmazeId, resolve the old TheTVDB id via TVMaze,
	 * fetch the full show and update the database. Runs in a background
	 * thread with a horizontal Material progress dialog. Failures are collected and
	 * reported via errorNotify at the end; anything left unmigrated is
	 * resolved lazily on the next manual update (see updateSerie).
	 */
	private void migrateLibraryToTVMaze() {
		Cursor c = db.Query("SELECT id, serieName FROM series WHERE mediaType=0 AND (tvmazeId IS NULL OR tvmazeId='')");
		final List<String[]> toMigrate = new ArrayList<String[]>();
		try {
			if (c != null) {
				c.moveToFirst();
				if (c.isFirst()) {
					do {
						toMigrate.add(new String[] {c.getString(0), c.getString(1)});
					} while (c.moveToNext());
				}
				c.close();
			}
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "Error collecting shows to migrate", e);
		}
		if (toMigrate.isEmpty())
			return;
		if (!utils.isNetworkAvailable(DroidShows.this)) {
			Toast.makeText(getApplicationContext(), R.string.messages_no_internet, Toast.LENGTH_LONG).show();
			return;
		}
		View migView = View.inflate(DroidShows.this, R.layout.progress_dialog, null);
		final TextView migMsg = (TextView) migView.findViewById(R.id.progress_msg);
		final LinearProgressIndicator migBar =
			(LinearProgressIndicator) migView.findViewById(R.id.progress_bar);
		migMsg.setText(getString(R.string.msg_migrating_wait));
		migBar.setMax(toMigrate.size());
		migBar.setProgress(0);
		final AlertDialog migDlg = new MaterialAlertDialogBuilder(DroidShows.this)
			.setTitle(R.string.msg_migrating).setView(migView).setCancelable(false).create();
		migDlg.show();
		final ExecutorService migExec = Executors.newFixedThreadPool(3);
		final AtomicInteger migProgress = new AtomicInteger(0);
		final ConcurrentLinkedQueue<String> migFailures = new ConcurrentLinkedQueue<String>();
		for (int i = 0; i < toMigrate.size(); i++) {
			final String tvdbId = toMigrate.get(i)[0];
			final String name = toMigrate.get(i)[1];
			migExec.submit(new Runnable() {
				public void run() {
					TVMaze tvMaze = new TVMaze();
					try {
						String tvmazeId = resolveTvmazeId(tvMaze, tvdbId);
						Serie show = (tvmazeId == null || tvmazeId.isEmpty() ? null : getTVMazeShow(tvMaze, tvmazeId));
						if (show == null) {
							migFailures.add(name);
						} else {
							show.setId(tvdbId);	// keep the existing DB row; TVMaze id goes to tvmazeId
							show.setTvmazeId(tvmazeId);
							db.updateSerie(show, false);
							updatePosterThumb(tvdbId, show);
						}
					} catch (Exception e) {
						Log.e(SQLiteStore.TAG, "Migration failed for "+ name, e);
						migFailures.add(name);
					}
					final int progress = migProgress.incrementAndGet();
					runOnUiThread(new Runnable() {
						public void run() {migBar.setProgress(progress);}
					});
				}
			});
		}
		migExec.shutdown();
		Thread migWaiter = new Thread(new Runnable() {
			public void run() {
				try {
					migExec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				} catch (InterruptedException e) {
				}
				StringBuilder failedSb = new StringBuilder();
				for (String f : migFailures)
					failedSb.append(f).append(' ');
				final String failedResult = failedSb.toString();
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing())
							return;	// rotated away mid-migration: don't touch dead windows
						migDlg.dismiss();
						getSeries();
						if (failedResult.length() > 0)
							errorNotify(failedResult);
						else
							Toast.makeText(getApplicationContext(), R.string.msg_migrated, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
		migWaiter.start();
	}

	/*
	 * Resolve an old TheTVDB id to a TVMaze id (caching it in the database),
	 * retrying once after 10s when TVMaze answers HTTP 429. Call from a
	 * background thread.
	 */
	private String resolveTvmazeId(TVMaze tvMaze, String tvdbId) {
		String tvmazeId = db.getTvmazeId(tvdbId);
		if (tvmazeId == null || tvmazeId.isEmpty()) {
			try {
				tvmazeId = tvMaze.resolveTVDBId(tvdbId);
			} catch (JsonFetcher.RateLimitException e) {
				Log.d(SQLiteStore.TAG, "TVMaze rate limited, retrying in 10s");
				sleepQuietly(10000);
				try {
					tvmazeId = tvMaze.resolveTVDBId(tvdbId);
				} catch (JsonFetcher.RateLimitException e2) {
					Log.e(SQLiteStore.TAG, "TVMaze still rate limited for "+ tvdbId);
				}
			}
			if (tvmazeId != null && !tvmazeId.isEmpty())
				db.setTvmazeId(tvdbId, tvmazeId);
		}
		return tvmazeId;
	}

	/*
	 * Fetch a full TVMaze show, retrying once after 10s on HTTP 429.
	 * Returns null on failure. Call from a background thread.
	 */
	private Serie getTVMazeShow(TVMaze tvMaze, String tvmazeId) {
		try {
			return tvMaze.getShow(tvmazeId);
		} catch (JsonFetcher.RateLimitException e) {
			Log.d(SQLiteStore.TAG, "TVMaze rate limited, retrying in 10s");
			sleepQuietly(10000);
			try {
				return tvMaze.getShow(tvmazeId);
			} catch (JsonFetcher.RateLimitException e2) {
				Log.e(SQLiteStore.TAG, "TVMaze still rate limited for show "+ tvmazeId);
				return null;
			}
		}
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	private void setFastScroll() {
		listView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
		listView.setVerticalScrollBarEnabled(!excludeSeen || logMode);
/*		listView.setFastScrollEnabled(!excludeSeen || logMode);
		if (!excludeSeen || logMode) {
			if (seriesAdapter.getCount() > 20) {
				try {	// https://stackoverflow.com/a/26447004
					java.lang.reflect.Field fieldFastScroller = AbsListView.class.getDeclaredField(
						Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "mFastScroll" : "mFastScroller");
					fieldFastScroller.setAccessible(true);
					Object thisFastScroller = fieldFastScroller.get(listView);
					Drawable thumb = getResources().getDrawable(R.drawable.thumb);
					java.lang.reflect.Field i;

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
						i = fieldFastScroller.getType().getDeclaredField("mThumbImage");
						i.setAccessible(true);
						ImageView iv = (ImageView) i.get(thisFastScroller);
						iv.setImageDrawable(thumb);

						i = fieldFastScroller.getType().getDeclaredField("mThumbWidth");
						i.setAccessible(true);
						i.setInt(thisFastScroller, thumb.getIntrinsicWidth());

						i = fieldFastScroller.getType().getDeclaredField("mTrackImage");
						i.setAccessible(true);
						i.set(thisFastScroller, null);
					} else {
						i = fieldFastScroller.getType().getDeclaredField("mThumbDrawable");
						i.setAccessible(true);
						i.set(thisFastScroller, thumb);

						i = fieldFastScroller.getType().getDeclaredField("mThumbW");
						i.setAccessible(true);
						i.setInt(thisFastScroller, thumb.getIntrinsicWidth());

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							i = fieldFastScroller.getType().getDeclaredField("mTrackDrawable");
							i.setAccessible(true);
							i.set(thisFastScroller, null);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
*/	}

	/* Options Menu */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Only the search action lives in the toolbar now; everything else moved to the + button popup.
		menu.add(0, SEARCH_MENU_ITEM, 0, getString(R.string.menu_search)).setIcon(menuIcon(R.drawable.ic_menu_search))
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	/* Everything that used to live in the ⋮ overflow now lives in the + button popup. */
	private void populatePlusMenu(Menu menu) {
		menu.add(0, ADD_SERIE_MENU_ITEM, 0, "").setIcon(menuIcon(R.drawable.ic_menu_add));
		menu.add(0, DISCOVER_MENU_ITEM, 1, getString(R.string.discover)).setIcon(menuIcon(R.drawable.ic_discover_compass));
		menu.add(0, SEARCH_MENU_ITEM, 2, getString(R.string.menu_search)).setIcon(menuIcon(R.drawable.ic_menu_search));
		menu.add(0, UPDATEALL_MENU_ITEM, 3, "").setIcon(menuIcon(R.drawable.ic_menu_sync));
		menu.add(0, FILTER_MENU_ITEM, 4, "").setIcon(menuIcon(R.drawable.ic_menu_filter_list));
		menu.add(0, SORT_MENU_ITEM, 5, "").setIcon(menuIcon(R.drawable.ic_menu_sort));
		menu.add(0, SEEN_MENU_ITEM, 6, "").setIcon(menuIcon(R.drawable.ic_menu_visibility));
		menu.add(0, UNDO_MENU_ITEM, 7, getString(R.string.menu_undo)).setIcon(menuIcon(R.drawable.ic_menu_undo));
		menu.add(0, OPTIONS_MENU_ITEM, 8, getString(R.string.menu_about)).setIcon(menuIcon(R.drawable.ic_menu_settings));
		menu.add(0, BACKUP_NOW_MENU_ITEM, 9, getString(R.string.menu_backup_now)).setIcon(menuIcon(R.drawable.ic_menu_backup));
		menu.add(0, EXIT_MENU_ITEM, 10, getString(R.string.menu_exit)).setIcon(menuIcon(R.drawable.ic_menu_exit_to_app));
	}

	private void preparePlusMenu(Menu menu) {
		menu.findItem(UNDO_MENU_ITEM)
			.setVisible(undo.size() > 0);
		menu.findItem(UPDATEALL_MENU_ITEM)
			.setEnabled(!logMode)
			.setTitle(mediaType == 1 ? R.string.menu_update_movies : R.string.menu_update);
		menu.findItem(ADD_SERIE_MENU_ITEM)
			.setTitle(mediaType == 1 ? R.string.menu_add_movie : R.string.menu_add_serie);
		menu.findItem(FILTER_MENU_ITEM)
			.setEnabled(!logMode && !searching())
			.setTitle(mediaType == 1 ? R.string.menu_filter_movies : R.string.menu_filter);
		menu.findItem(SEEN_MENU_ITEM)
			.setEnabled(!logMode && !searching());
		menu.findItem(SORT_MENU_ITEM)
			.setEnabled(!logMode);

		menu.findItem(SEEN_MENU_ITEM)
			.setIcon(menuIcon(excludeSeen ? R.drawable.ic_menu_visibility_off : R.drawable.ic_menu_visibility))
			.setTitle(excludeSeen ? R.string.menu_include_seen : R.string.menu_exclude_seen);
		if (sortOption == SORT_BY_UNSEEN) {
			menu.findItem(SORT_MENU_ITEM)
				.setIcon(menuIcon(R.drawable.ic_menu_sort_by_alpha))
				.setTitle(R.string.menu_sort_by_name);
		} else {
			menu.findItem(SORT_MENU_ITEM)
				.setIcon(menuIcon(R.drawable.ic_menu_sort))
				.setTitle(R.string.menu_sort_by_unseen);
		}
	}

	public void showPlusMenu(View v) {
		androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
		populatePlusMenu(popup.getMenu());
		preparePlusMenu(popup.getMenu());
		try {
			java.lang.reflect.Field f = popup.getClass().getDeclaredField("mPopup");
			f.setAccessible(true);
			Object helper = f.get(popup);
			helper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(helper, true);
		} catch (Exception e) {
			Log.w("DroidShows", "Could not enable popup menu icons", e);
		}
		popup.setOnMenuItemClickListener(new androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return onOptionsItemSelected(item);
			}
		});
		popup.show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item))
			return true;
		switch (item.getItemId()) {
			case ADD_SERIE_MENU_ITEM :
				searchForShow(null);	// AddSerie or AddMovie, depending on the current section
				break;
			case DISCOVER_MENU_ITEM :
				startActivity(new Intent(DroidShows.this, nl.asymmetrics.droidshows.ui.DiscoverActivity.class));
				break;
			case SEARCH_MENU_ITEM :
				onSearchRequested();
				break;
			case TOGGLE_ARCHIVE_MENU_ITEM :
				toggleArchive();
				break;
			case SEEN_MENU_ITEM :
				toggleSeen();
				break;
			case SORT_MENU_ITEM :
				toggleSort();
				break;
			case FILTER_MENU_ITEM :
				filterDialog();
				break;
			case UPDATEALL_MENU_ITEM :
				updateAllSeriesDialog();
				break;
			case OPTIONS_MENU_ITEM :
				aboutDialog();
				break;
			case BACKUP_NOW_MENU_ITEM :
				safBackup();
				break;
			case UNDO_MENU_ITEM :
				markLastEpUnseen();
				break;
			case LOG_MODE_ITEM :
				toggleLogMode();
				break;
			case EXIT_MENU_ITEM :
				saveOptions();		// persist preferences
				maybeAutoBackup();	// back up database
				db.close();
				this.finish();
				System.exit(0);	// kill process
		}
		return super.onOptionsItemSelected(item);
	}

	private void toggleArchive() {
		showArchive = (showArchive + 1) % 2;
		getSeries();
		listView.setSelection(0);
	}

	private void toggleSeen() {
		excludeSeen ^= true;
		listView.post(updateListView);
	}

	private void toggleSort() {
		sortOption ^= 1;
		listView.post(updateListView);
	}

	private void toggleLogMode() {
		logMode ^= true;
		getSeries();
		removeEpisodeFromLog = "";
		listView.setSelection(0);
	}

	private void filterDialog() {
		if (m_AlertDlg != null) {
			m_AlertDlg.dismiss();
		}
		final View filterV = View.inflate(this, R.layout.alert_filter, null);
		((CheckBox) filterV.findViewById(R.id.exclude_seen)).setChecked(excludeSeen);
		List<String> allNetworks = db.getNetworks();
		final LinearLayout networksFilterV = (LinearLayout) filterV.findViewById(R.id.networks_filter);
		for (String network : allNetworks) {
			CheckBox networkCheckBox = new CheckBox(this);
			networkCheckBox.setText(network);
			if (!networks.isEmpty())
				networkCheckBox.setChecked(networks.contains(network));
			networksFilterV.addView(networkCheckBox);
		}
		ToggleButton networksFilter = (ToggleButton) filterV.findViewById(R.id.toggle_networks_filter);
		networksFilter.setChecked(filterNetworks);
		toggleNetworksFilter(networksFilter);
		if (mediaType == 1) {
			// networks are a TV concept: hide the networks filter section for movies
			networksFilterV.setVisibility(View.GONE);
			networksFilter.setVisibility(View.GONE);
			ViewGroup container = (ViewGroup) networksFilterV.getParent();
			for (int i = 0; i < container.getChildCount(); i++) {
				View child = container.getChildAt(i);
				if (child instanceof TextView && !(child instanceof CheckBox) && !(child instanceof ToggleButton)
					&& ((TextView) child).getText().toString().equals(getString(R.string.dialog_networks)))
					child.setVisibility(View.GONE);
			}
		}
		m_AlertDlg = new MaterialAlertDialogBuilder(this)
			.setView(filterV)
			.setTitle(R.string.menu_filter)
			.setIcon(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ? R.drawable.icon : 0)
			.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					applyFilters((ScrollView) filterV, networksFilterV);
				}
			})
			.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					m_AlertDlg.dismiss();
				}
			})
			.show();
	}

	public void toggleNetworksFilter(View v) {
		boolean enabled = (((ToggleButton) v).isChecked());
		LinearLayout networksFilterV = (LinearLayout) ((View) v.getParent().getParent()).findViewById(R.id.networks_filter);
		for (int i = 0; i < networksFilterV.getChildCount(); i++) {
			networksFilterV.getChildAt(i).setEnabled(enabled);
		}
	}

	private void applyFilters(ScrollView filterV, LinearLayout networksFilterV) {
		excludeSeen = (((CheckBox) filterV.findViewById(R.id.exclude_seen)).isChecked() ? true : false);
		filterNetworks = (((ToggleButton) filterV.findViewById(R.id.toggle_networks_filter)).isChecked() == true);
		for (int i = 0; i < networksFilterV.getChildCount(); i++) {
			CheckBox networkCheckBox = (CheckBox) networksFilterV.getChildAt(i);
			String network = (String) networkCheckBox.getText();
			if (networkCheckBox.isChecked()) {
				if (!networks.contains(network))
					networks.add(network);
			} else {
				if (networks.contains(network))
					networks.remove(network);
			}
		}
		getSeries();
	}

	private void aboutDialog() {
		if (m_AlertDlg != null) {
			m_AlertDlg.dismiss();
		}
		View about = View.inflate(this, R.layout.alert_about, null);
		TextView changelog = (TextView) about.findViewById(R.id.copyright);
		try {
			changelog.setText(getString(R.string.copyright)
				.replace("{v}", getPackageManager().getPackageInfo(getPackageName(), 0).versionName)
				.replace("{y}", Calendar.getInstance().get(Calendar.YEAR) +""));
			changelog.setTextColor(changelog.getTextColors().getDefaultColor());
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		((TextView) about.findViewById(R.id.change_language)).setText(getString(R.string.dialog_change_language) +" ("+ langCode +")");
		int themeMode = getSharedPreferences(PREF_NAME, 0).getInt(ThemeHelper.THEME_PREF_NAME, ThemeHelper.THEME_AUTOMATIC);
		((Button) about.findViewById(R.id.theme_option)).setText(getString(R.string.settings_theme) +": "+ themeName(themeMode));
		((Button) about.findViewById(R.id.poster_cache_size)).setText(getString(R.string.dialog_poster_cache_size) +": "+ (Utils.getPosterCacheMaxBytes(this) / (1024*1024)) +" MB");
		((CheckBox) about.findViewById(R.id.auto_backup)).setChecked(autoBackup);
		((CheckBox) about.findViewById(R.id.backup_versioning)).setChecked(backupVersioning);
		((CheckBox) about.findViewById(R.id.latest_season)).setChecked(latestSeasonOption == UPDATE_LATEST_SEASON_ONLY);
		((CheckBox) about.findViewById(R.id.include_specials)).setChecked(includeSpecialsOption);
		((CheckBox) about.findViewById(R.id.full_line_check)).setChecked(fullLineCheckOption);
		((CheckBox) about.findViewById(R.id.large_posters)).setChecked(largePostersOption);
		((CheckBox) about.findViewById(R.id.switch_swipe_direction)).setChecked(switchSwipeDirection);
		((CheckBox) about.findViewById(R.id.show_next_airing)).setChecked(showNextAiring);
		((CheckBox) about.findViewById(R.id.mark_from_last_watched)).setChecked(markFromLastWatched);
		final EditText tmdbKeyV = (EditText) about.findViewById(R.id.tmdb_api_key);
		tmdbKeyV.setText(sharedPrefs.getString(TMDB_API_KEY_NAME, ""));
		m_AlertDlg = new MaterialAlertDialogBuilder(this)
			.setView(about)
			.setTitle(R.string.menu_about)
			.setIcon(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ? R.drawable.icon : 0)
			.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					sharedPrefs.edit().putString(TMDB_API_KEY_NAME, tmdbKeyV.getText().toString().trim()).apply();
					m_AlertDlg.dismiss();
				}
			})
			.show();
	}

	private String themeName(int mode) {
		switch (mode) {
			case ThemeHelper.THEME_LIGHT: return getString(R.string.theme_light);
			case ThemeHelper.THEME_DARK: return getString(R.string.theme_dark);
			case ThemeHelper.THEME_AMOLED: return getString(R.string.theme_amoled);
			default: return getString(R.string.theme_automatic);
		}
	}

	public void dialogOptions(View v) {
		switch(v.getId()) {
			case R.id.backup:
				m_AlertDlg.dismiss();
				safBackup();
				break;
			case R.id.restore:
				m_AlertDlg.dismiss();
				safRestore();
				break;
			case R.id.clear_poster_cache:
				new Thread(new Runnable() {
					public void run() {
						Utils.clearDiscoverCache(getApplicationContext());
						toastOnUi(R.string.poster_cache_cleared);
					}
				}).start();
				break;
			case R.id.poster_cache_size: {
				final long[] sizes = {50, 100, 200, 500};
				final String[] labels = {"50 MB", "100 MB", "200 MB", "500 MB"};
				long curMB = Utils.getPosterCacheMaxBytes(this) / (1024*1024);
				int checked = 0;
				for (int i = 0; i < sizes.length; i++) if (sizes[i] == curMB) checked = i;
				new MaterialAlertDialogBuilder(this)
					.setTitle(R.string.dialog_poster_cache_size)
					.setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Utils.setPosterCacheMaxMB(getApplicationContext(), sizes[which]);
							((Button) m_AlertDlg.findViewById(R.id.poster_cache_size))
									.setText(getString(R.string.dialog_poster_cache_size) +": "+ sizes[which] +" MB");
							new Thread(new Runnable() {
								public void run() { Utils.prunePosterCache(getApplicationContext()); }
							}).start();
							dialog.dismiss();
						}
					}).show();
				break;
			}
			case R.id.auto_backup:
				autoBackup ^= true;
				break;
			case R.id.backup_versioning:
				backupVersioning ^= true;
				break;
			case R.id.latest_season:
				latestSeasonOption ^= 1;
				break;
			case R.id.include_specials:
				includeSpecialsOption ^= true;
				updateShowStats();
				break;
			case R.id.full_line_check:
				fullLineCheckOption ^= true;
				break;
			case R.id.large_posters:
				largePostersOption ^= true;
				seriesAdapter.clear();
				getSeries();
				break;
			case R.id.switch_swipe_direction:
				switchSwipeDirection ^= true;
				break;
			case R.id.show_next_airing:
				showNextAiring ^= true;
				updateShowStats();
				break;
			case R.id.mark_from_last_watched:
				markFromLastWatched ^= true;
				updateShowStats();
				break;
			case R.id.theme_option:
				int themeMode = getSharedPreferences(PREF_NAME, 0).getInt(ThemeHelper.THEME_PREF_NAME, ThemeHelper.THEME_AUTOMATIC);
				new MaterialAlertDialogBuilder(this)
					.setTitle(R.string.settings_theme)
					.setSingleChoiceItems(new String[]{ getString(R.string.theme_automatic), getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_amoled)}, themeMode, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							sharedPrefs.edit().putInt(ThemeHelper.THEME_PREF_NAME, which).apply();
							dialog.dismiss();
							recreate();
						}
					}).show();
				break;
			case R.id.change_language:
				AlertDialog.Builder changeLang = new MaterialAlertDialogBuilder(this);
				changeLang.setTitle(R.string.dialog_change_language)
					.setItems(R.array.languages, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							langCode = getResources().getStringArray(R.array.langcodes)[item];
							TextView changeLangB = (TextView) m_AlertDlg.findViewById(R.id.change_language);
							changeLangB.setText(getString(R.string.dialog_change_language) +" ("+ langCode +")");
						}
					})
					.show();
			break;
		}
	}

	private void updateShowStats() {
		Runnable updateShowStats = new Runnable() {
			public void run() {
				db.updateShowStats();
				listView.post(new Runnable() {
					public void run() {getSeries((searching() ? 2 : showArchive));}
				});
			}
		};
		Thread updateShowStatsTh = new Thread(updateShowStats);
		updateShowStatsTh.start();
	}

	/* Serialise DB file copies (backup/restore) against in-flight updates: wait
	 * for update threads to finish before the DB is closed for the copy, so a
	 * racing query can't hit a closed database. Joins are bounded — a stuck
	 * network read must not wedge the backup forever. */
	private volatile boolean backupRunning = false;

	private void waitForDbIdle() {
		if (asyncInfo != null)
			asyncInfo.cancel(true);
		try { if (updateShowTh != null) updateShowTh.join(8000); } catch (InterruptedException e) {}
		try { if (updateAllShowsTh != null) updateAllShowsTh.join(15000); } catch (InterruptedException e) {}
	}

	private void toastOnUi(final int resId) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (!isFinishing())
					Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
			}
		});
	}

	/* Fast folder prep shared by the async backup() and the blocking
	 * pre-migration safety backup: rotation + mkdir only, no I/O copy. */
	private File prepareBackupDestination(String backupFolder) {
		File destination = new File(backupFolder, "TVMovie Tracker.db");
		if (backupVersioning && destination.exists()) {
			File previous0 = new File(backupFolder, "TVMovie Tracker.db0");
			if (previous0.exists()) {
				File previous1 = new File(backupFolder, "TVMovie Tracker.db1");
				if (previous1.exists() && !previous1.delete())
					Log.w(TAG, "Could not delete old backup " + previous1.getAbsolutePath());
				if (!previous0.renameTo(previous1))
					Log.w(TAG, "Could not rotate backup " + previous0.getAbsolutePath());
			}
			if (!destination.renameTo(previous0))
				Log.w(TAG, "Could not rotate backup " + destination.getAbsolutePath());
		} else if (destination.exists() && !destination.delete())
			Log.w(TAG, "Could not delete old backup " + destination.getAbsolutePath());
		File folder = new File(backupFolder);
		if (!folder.isDirectory() && !folder.mkdir())
			Log.w(TAG, "Could not create backup folder " + backupFolder);
		return destination;
	}

	/* Synchronous safety backup, used only before a schema migration: the
	 * copy MUST be on disk before the migration starts, so this one blocks.
	 * It only runs when the schema actually needs an update (app upgrades). */
	private void backupBlocking(String backupFolder) {
		File source = new File(getApplicationInfo().dataDir +"/databases/DroidShows.db");
		File destination = prepareBackupDestination(backupFolder);
		try {
			copy(source, destination);
		} catch (IOException e) {
			Log.e(SQLiteStore.TAG, "Pre-migration backup failed", e);
		}
	}

	private void backup(boolean auto, final String backupFolder) {
		if (backupRunning)
			return;
		final File source = new File(getApplicationInfo().dataDir +"/databases/DroidShows.db");
		final File earlyCheck = new File(backupFolder, "TVMovie Tracker.db");
		if (auto && (!autoBackup ||
				new SimpleDateFormat("yyyy-MM-dd")
					.format(earlyCheck.lastModified()).equals(lastStatsUpdateCurrent) ||
				source.lastModified() == earlyCheck.lastModified()))
			return;
		final File destination = prepareBackupDestination(backupFolder);
		// The actual copy is I/O-heavy: run it on a worker thread so a big
		// database can't freeze the UI (ANR). UI feedback goes back to the
		// main thread when the copy finishes.
		backupRunning = true;
		final boolean isAuto = auto;
		new Thread(new Runnable() {
			public void run() {
				int toastTxt = R.string.dialog_backup_done;
				try {
					copy(source, destination);
				} catch (IOException e) {
					toastTxt = R.string.dialog_backup_failed;
					e.printStackTrace();
				} finally {
					backupRunning = false;
				}
				final int result = toastTxt;
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing())
							return;
						if (!isAuto && result == R.string.dialog_backup_done && !backupFolder.equals(DroidShows.backupFolder)) {
							final CharSequence[] backupFolders = {backupFolder, DroidShows.backupFolder};
							new MaterialAlertDialogBuilder(DroidShows.this)
								.setTitle(result)
								.setSingleChoiceItems(backupFolders, 1, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										DroidShows.backupFolder = backupFolders[which].toString();
									}
								})
								.setPositiveButton(R.string.dialog_backup_usefolder, null)
								.show();
						}
						if (!isAuto && listView != null) {
							Toast.makeText(getApplicationContext(), getString(result) + " ("+ backupFolder +")", Toast.LENGTH_LONG).show();
							asyncInfo = new AsyncInfo();
							asyncInfo.execute();
						}
					}
				});
			}
		}).start();
	}

	private void copy(File source, File destination) throws IOException {
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			// Don't close the database under a running update's feet.
			waitForDbIdle();
			db.close();
			try {
				FileChannel sourceCh = null, destinationCh = null;
				try {
					sourceCh = new FileInputStream(source).getChannel();
					if (destination.exists()) destination.delete();
					destination.createNewFile();
					destinationCh = new FileOutputStream(destination).getChannel();
					destinationCh.transferFrom(sourceCh, 0, sourceCh.size());
					destination.setLastModified(source.lastModified());
				} finally {
					if (sourceCh != null) {
						sourceCh.close();
					}
					if (destinationCh != null) {
						destinationCh.close();
					}
				}
			} finally {
				// Never leave the database closed: a failed copy used to break
				// every later query ("attempt to re-open an already-closed object").
				try { db.openDataBase(); } catch (Exception e2) { Log.e(SQLiteStore.TAG, "Could not re-open database after backup", e2); }
			}
		}
	}

	/* Storage Access Framework backup/restore (API 19+): pick any document as
	 * the backup source/destination instead of the legacy /TVMovie Tracker folder.
	 * Direct file I/O below (backup(auto, folder), copy) now serves only the
	 * auto-backup and the pre-database-update safety backup. */
	private void safRestore() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			Toast.makeText(getApplicationContext(), R.string.saf_not_supported, Toast.LENGTH_LONG).show();
			return;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
			startActivityForResult(intent, REQ_RESTORE_BACKUP);
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), R.string.saf_not_supported, Toast.LENGTH_LONG).show();
		}
	}

	private void safBackup() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			backup(false, backupFolder);	// legacy folder-based backup on old Android versions
			return;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("application/octet-stream");
			intent.putExtra(Intent.EXTRA_TITLE, "TVMovie Tracker.db");
			startActivityForResult(intent, REQ_BACKUP_NOW);
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), R.string.saf_not_supported, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK || data == null || data.getData() == null)
			return;
		Uri uri = data.getData();
		if (requestCode == REQ_RESTORE_BACKUP)
			confirmSafRestore(uri);
		else if (requestCode == REQ_BACKUP_NOW)
			backupToUri(uri);
	}

	private void confirmSafRestore(final Uri uri) {
		AlertDialog.Builder adb = new MaterialAlertDialogBuilder(this);
		adb.setTitle(R.string.dialog_restore);
		adb.setMessage(R.string.dialog_restore_now);
		adb.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				restoreFromUri(uri);
			}
		});
		adb.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		adb.show();
	}

	private void restoreFromUri(final Uri uri) {
		if (backupRunning)
			return;
		backupRunning = true;
		// File I/O and DB work run on a worker thread; the list reload and
		// the migration dialog go back to the UI thread at the end.
		new Thread(new Runnable() {
			public void run() {
				File tmp = new File(getCacheDir(), "restore_tmp.db");
				try {
					InputStream in = getContentResolver().openInputStream(uri);
					if (in == null)
						throw new IOException("Cannot open backup");
					FileOutputStream out = new FileOutputStream(tmp);
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0)
						out.write(buf, 0, n);
					out.close();
					in.close();
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Error reading backup", e);
					tmp.delete();
					backupRunning = false;
					toastOnUi(R.string.dialog_restore_failed);
					return;
				}
				if (!isValidDroidShowsDb(tmp)) {
					tmp.delete();
					backupRunning = false;
					toastOnUi(R.string.dialog_restore_invalid);
					return;
				}
				boolean migrationFailed = false;
				try {
					waitForDbIdle();
					db.close();
					try {
						File databasesDir = new File(getApplicationInfo().dataDir +"/databases");
						File destination = new File(databasesDir, "DroidShows.db");
						FileInputStream in = new FileInputStream(tmp);
						FileOutputStream out = new FileOutputStream(destination);
						byte[] buf = new byte[8192];
						int n;
						while ((n = in.read(buf)) > 0)
							out.write(buf, 0, n);
						out.close();
						in.close();
						// drop WAL/journal sidecars of the replaced database
						File[] files = databasesDir.listFiles();
						if (files != null)
							for (File file : files)
								if (!file.getName().equalsIgnoreCase("DroidShows.db"))
									file.delete();
					} finally {
						tmp.delete();
						db.openDataBase();
					}
					// migrate the restored (possibly ancient) schema to the current one;
					// old rows come out as TV shows (mediaType=0) with an empty tvmazeId
					if (updateDS.needsUpdate()) {
						if (updateDS.updateDroidShows())
							db.updateShowStats();
						else
							migrationFailed = true;
					}
					// posters cached for another install are stale
					Utils.clearPosterCache(getApplicationContext());
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Error restoring backup", e);
					try { db.openDataBase(); } catch (Exception e2) {}
					backupRunning = false;
					toastOnUi(R.string.dialog_restore_failed);
					return;
				}
				backupRunning = false;
				final boolean showMigrationError = migrationFailed;
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing())
							return;
						if (showMigrationError)
							Toast.makeText(getApplicationContext(), R.string.messages_error_dbupdate, Toast.LENGTH_LONG).show();
						undo.clear();
						getSeries();
						migrateLibraryToTVMaze();
						Toast.makeText(getApplicationContext(), R.string.dialog_restore_done, Toast.LENGTH_LONG).show();
					}
				});
			}
		}).start();
	}

	private boolean isValidDroidShowsDb(File dbFile) {
		SQLiteDatabase check = null;
		try {
			check = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
			Cursor c = check.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('droidseries', 'series', 'episodes')", null);
			boolean valid = (c != null && c.getCount() == 3);
			if (c != null)
				c.close();
			return valid;
		} catch (Exception e) {
			return false;
		} finally {
			if (check != null)
				check.close();
		}
	}

	private void backupToUri(final Uri uri) {
		if (backupRunning)
			return;
		backupRunning = true;
		new Thread(new Runnable() {
			public void run() {
				boolean ok = true;
				try {
					waitForDbIdle();
					db.close();
					try {
						File source = new File(getApplicationInfo().dataDir +"/databases", "DroidShows.db");
						InputStream in = new FileInputStream(source);
						OutputStream out = getContentResolver().openOutputStream(uri);
						if (out == null)
							throw new IOException("Cannot open destination");
						byte[] buf = new byte[8192];
						int n;
						while ((n = in.read(buf)) > 0)
							out.write(buf, 0, n);
						out.close();
						in.close();
					} finally {
						// Never leave the database closed.
						db.openDataBase();
					}
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Error writing backup", e);
					try { db.openDataBase(); } catch (Exception e2) {}
					ok = false;
				}
				backupRunning = false;
				toastOnUi(ok ? R.string.dialog_backup_done : R.string.dialog_backup_failed);
			}
		}).start();
	}

	/* context menu */
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		TVShowItem serie = seriesAdapter.getItem(info.position);
		boolean isMovie = serie.getMediaType() == 1;
		menu.setHeaderTitle(serie.getName());
		if (logMode && !isMovie)
			menu.add(0, VIEW_SEASONS_CONTEXT, VIEW_SEASONS_CONTEXT, getString(R.string.messages_seasons));
		menu.add(0, VIEW_SERIEDETAILS_CONTEXT, VIEW_SERIEDETAILS_CONTEXT, getString(isMovie ? R.string.menu_context_movie_details : R.string.menu_context_view_serie_details));
		if (!logMode && !isMovie && serie.getUnwatched() > 0)
			menu.add(0, VIEW_EPISODEDETAILS_CONTEXT, VIEW_EPISODEDETAILS_CONTEXT, getString(R.string.messsages_view_ep_details));
		menu.add(0, EXT_RESOURCES_CONTEXT, EXT_RESOURCES_CONTEXT, getString(R.string.menu_context_ext_resources));
		if (!logMode && canMarkNextEpSeen(serie))
			menu.add(0, MARK_NEXT_EPISODE_AS_SEEN_CONTEXT, MARK_NEXT_EPISODE_AS_SEEN_CONTEXT, getString(isMovie
				? (serie.getUnwatched() > 0 ? R.string.menu_context_mark_movie_seen : R.string.menu_context_mark_movie_unseen)
				: R.string.menu_context_mark_next_episode_as_seen));
		if (!logMode) {
			menu.add(0, TOGGLE_ARCHIVED_CONTEXT, TOGGLE_ARCHIVED_CONTEXT, getString(R.string.menu_archive));
			menu.add(0, PIN_CONTEXT, PIN_CONTEXT, getString(R.string.menu_context_pin));
			menu.add(0, DELETE_CONTEXT, DELETE_CONTEXT, getString(isMovie ? R.string.menu_context_delete_movie : R.string.menu_context_delete));
			menu.add(0, UPDATE_CONTEXT, UPDATE_CONTEXT, getString(isMovie ? R.string.menu_context_update_movie : R.string.menu_context_update));
		    if (serie.getPassiveStatus())
		    	menu.findItem(TOGGLE_ARCHIVED_CONTEXT).setTitle(R.string.menu_unarchive);
		    if (pinnedShows.contains(serie.getSerieId()))
		    	menu.findItem(PIN_CONTEXT).setTitle(R.string.menu_context_unpin);
		}
		menu.setHeaderTitle(!logMode ? serie.getName() : serie.getEpisodeName());
	}

	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final TVShowItem serie = seriesAdapter.getItem(info.position);
		final String serieId = serie.getSerieId();
		switch(item.getItemId()) {
			case MARK_NEXT_EPISODE_AS_SEEN_CONTEXT :
				if (serie.getMediaType() == 1)
					toggleMovieWatched(serie);
				else
					markNextEpSeen(info.position);
				return true;
			case VIEW_SEASONS_CONTEXT :
				serieSeasons(info.position);
				return true;
			case VIEW_SERIEDETAILS_CONTEXT :
				showDetails(serieId);
				return true;
			case VIEW_EPISODEDETAILS_CONTEXT :
				episodeDetails(info.position);
				return true;
			case EXT_RESOURCES_CONTEXT :
				extResources(serie.getExtResources(), info.position);
				return true;
			case UPDATE_CONTEXT :
				updateSerie(serie, info.position);
				return true;
			case TOGGLE_ARCHIVED_CONTEXT :
				asyncInfo.cancel(true);
				boolean passiveStatus = serie.getPassiveStatus();
				db.updateSerieStatus(serieId, (passiveStatus ? 0 : 1));
				if (!passiveStatus && pinnedShows.contains(serieId))
					pinnedShows.remove(serieId);
				String message = serie.getName() +" "+
					(passiveStatus ? getString(R.string.messages_context_unarchived) : getString(R.string.messages_context_archived));
				Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				if (!searching())
					series.remove(serie);
				else
					serie.setPassiveStatus(!passiveStatus);
				listView.post(updateListView);
				asyncInfo = new AsyncInfo();
				asyncInfo.execute();
				return true;
			case PIN_CONTEXT :
				if (pinnedShows.contains(serieId))
					pinnedShows.remove(serieId);
				else
					pinnedShows.add(serieId);
				listView.post(updateListView);
				return true;
			case DELETE_CONTEXT :
				confirmDeleteShow(info.position);
				return true;
			default :
				return super.onContextItemSelected(item);
		}
	}

	/* Delete confirmation shared by the long-press context menu and the swipe-reveal delete button. */
	private void confirmDeleteShow(final int position) {
		asyncInfo.cancel(true);
		final TVShowItem serie = seriesAdapter.getItem(position);
		if (serie == null)
			return;
		final String serieId = serie.getSerieId();
		final Runnable deleteserie = new Runnable() {
			public void run() {
				TVShowItem serie = seriesAdapter.getItem(position);
				String sname = serie.getName();
				String toastMsg = getString(R.string.messages_deleted);
				if (!db.deleteSerie(serieId))
					toastMsg = serie.getMediaType() == 1 ? getString(R.string.messages_error_dbdelete_movie) : "Database error while deleting show";
				series.remove(series.indexOf(serie));
				listView.post(updateListView);
				final String toastText = sname +" "+ toastMsg;
				new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
					public void run() {
						Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
					}
				});
				asyncInfo = new AsyncInfo();
				asyncInfo.execute();
			}
		};
		AlertDialog.Builder alertDialog = new MaterialAlertDialogBuilder(this)
			.setTitle(serie.getMediaType() == 1 ? R.string.dialog_title_delete_movie : R.string.dialog_title_delete)
			.setMessage(String.format(getString(R.string.dialog_delete), serie.getName()))
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setCancelable(false)
			.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					deleteTh = new Thread(deleteserie);
					deleteTh.start();
					return;
				}
			})
			.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					return;
				}
			});
		alertDialog.show();
	}

	@SuppressLint("NewApi")
	public void openContext(View v) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			listView.showContextMenuForChild(v, v.getX(), v.getY());
		else
			openContextMenu(v);
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
		keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
		if (!logMode) {
			serieSeasons(position);
		} else {
			episodeDetails(position);
		}
	}

	private boolean canMarkNextEpSeen(TVShowItem serie) {
		return (serie.getUnwatchedAired() > 0
			|| serie.getNextAir() != null && !serie.getNextAir().after(Calendar.getInstance().getTime()));
	}

	private void markNextEpSeen(int position) {
		TVShowItem serie = seriesAdapter.getItem(position);
		if (serie.getMediaType() == 1) {	// movies have a single pseudo-episode: toggle it
			toggleMovieWatched(serie);
			return;
		}
		String serieId = serie.getSerieId();
		String nextEpisode = db.getNextEpisodeId(serieId, true);
		if (nextEpisode != null && !nextEpisode.equals("-1")) {
			String episodeMarked = db.updateUnwatchedEpisode(serieId, nextEpisode);
			Toast.makeText(getApplicationContext(), serie.getName() +" "+ episodeMarked +" "+ getString(R.string.messages_marked_seen), Toast.LENGTH_SHORT).show();
			undo.add(new String[] {serieId, nextEpisode, serie.getName()});
			updateShowView(serie);
		}
	}

	/* Movies have a single pseudo-episode: toggle its seen state directly */
	private void toggleMovieWatched(TVShowItem movie) {
		String serieId = movie.getSerieId();
		boolean markingSeen = movie.getUnwatched() > 0;
		String episodeId = markingSeen ? db.getNextEpisodeId(serieId) : db.getFirstEpisodeId(serieId);
		if (episodeId == null || episodeId.equals("-1"))
			return;
		db.updateUnwatchedEpisode(serieId, episodeId);
		Toast.makeText(getApplicationContext(), movie.getName() +" "+ getString(markingSeen ? R.string.messages_marked_seen : R.string.messages_marked_unseen), Toast.LENGTH_SHORT).show();
		if (markingSeen)
			undo.add(new String[] {serieId, episodeId, movie.getName()});
		updateShowView(movie);
	}

	/* Wire swipe-reveal actions for one row (beta 33). Called on every bind. */
	private void bindRowSwipe(final ViewHolder holder, final TVShowItem serie) {
		if (holder.fg == null)
			return;
		if (openSwipeRow == holder.fg)
			openSwipeRow = null;	// recycled while parked open
		holder.fg.setTranslationX(0);
		if (holder.rowActions != null)
			holder.rowActions.setVisibility(View.GONE);	// hidden until a swipe starts
		/* The behind-layer measures 0-high inside a wrap_content row: sync it to the card height once laid out. */
		holder.fg.post(new Runnable() {
			public void run() {
				int h = holder.fg.getHeight();
				if (h > 0 && holder.rowActions != null) {
					ViewGroup.LayoutParams lp = holder.rowActions.getLayoutParams();
					if (lp.height != h) {
						lp.height = h;
						holder.rowActions.setLayoutParams(lp);
					}
				}
			}
		});
		final boolean canWatch = canMarkNextEpSeen(serie);
		if (holder.actionWatched != null)
			holder.actionWatched.setVisibility(canWatch ? View.VISIBLE : View.INVISIBLE);
		/* No touch listener on the row itself: swipe detection lives on the
		 * ListView (listSwipeListener), since a row-level listener never
		 * receives MOVEs once the ListView intercepts the stream. */
		if (holder.actionWatched != null) {
			holder.actionWatched.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					int pos = listView.getPositionForView(holder.fg);
					closeOpenSwipeRow();
					if (pos != ListView.INVALID_POSITION) {
						vib.vibrate(50);
						markNextEpSeen(pos);
					}
				}
			});
		}
		if (holder.actionFinished != null) {
			/* Archive lives on the left: revealed by swiping right. Hidden in
			 * History, mirroring the long-press menu. */
			holder.actionFinished.setVisibility(logMode ? View.INVISIBLE : View.VISIBLE);
			holder.actionFinished.setContentDescription(getString(
					serie.getPassiveStatus() ? R.string.menu_unarchive : R.string.menu_archive));
			holder.actionFinished.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					closeOpenSwipeRow();
					vib.vibrate(50);
					toggleArchived(serie);
				}
			});
		}
	}

	/* Swipe-button twin of the long-press "Move to Finished" menu item. */
	private void toggleArchived(TVShowItem serie) {
		asyncInfo.cancel(true);
		boolean passiveStatus = serie.getPassiveStatus();
		db.updateSerieStatus(serie.getSerieId(), (passiveStatus ? 0 : 1));
		if (!passiveStatus && pinnedShows.contains(serie.getSerieId()))
			pinnedShows.remove(serie.getSerieId());
		String message = serie.getName() + " " +
				(passiveStatus ? getString(R.string.messages_context_unarchived) : getString(R.string.messages_context_archived));
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
		if (!searching())
			series.remove(serie);
		else
			serie.setPassiveStatus(!passiveStatus);
		listView.post(updateListView);
		asyncInfo = new AsyncInfo();
		asyncInfo.execute();
	}

	private void parkOpenRow(View fg, int translationX) {
		openSwipeRow = fg;
		fg.animate().translationX(translationX).setDuration(180).start();
	}

	/* Spring the card back to its resting position, with a little bounce. */
	private void snapRowClosed(final View fg) {
		if (openSwipeRow == fg)
			openSwipeRow = null;
		fg.animate().translationX(0).setDuration(200)
				.setInterpolator(new OvershootInterpolator(1.0f))
				.withEndAction(new Runnable() {
					public void run() {
						View actions = ((View) fg.getParent()).findViewById(R.id.row_actions);
						if (actions != null)
							actions.setVisibility(View.GONE);
					}
				}).start();
	}

	private void closeOpenSwipeRow() {
		if (openSwipeRow != null)
			snapRowClosed(openSwipeRow);
	}

	/* Shared row-swipe driver (beta 34): swipe detection lives at ListView
	 * level, because a row-level listener never receives MOVEs — when no
	 * child consumes the DOWN, the ListView intercepts the rest of the
	 * stream for itself. The poster icon consumes its own DOWN, so the icon
	 * path drives the same methods. Only deltas are used, so the event's
	 * coordinate space does not matter. */
	private View swipeRowFg = null;
	private boolean swipeRowCanWatch = false;
	private float swipeDownX, swipeDownY, swipeStartTx;
	private boolean swipeDragging = false;
	private int tabSwitchDistancePx;
	private boolean tabSwitchedThisGesture = false;

	private void beginRowSwipe(View fg, boolean canWatch, float downX, float downY) {
		if (openSwipeRow != null && openSwipeRow != fg)
			closeOpenSwipeRow();
		swipeRowFg = fg;
		swipeRowCanWatch = canWatch;
		swipeDownX = downX;
		swipeDownY = downY;
		swipeStartTx = fg.getTranslationX();
		swipeDragging = false;
	}

	/* Returns true when the swipe is driving the row (caller should consume). */
	private boolean moveRowSwipe(float x, float y) {
		if (swipeRowFg == null)
			return false;
		float dx = x - swipeDownX, dy = y - swipeDownY;
		if (!swipeDragging && Math.abs(dx) > swipeTouchSlop && Math.abs(dx) > Math.abs(dy) * 2) {
			swipeDragging = true;
			View actions = ((View) swipeRowFg.getParent()).findViewById(R.id.row_actions);
			if (actions != null)
				actions.setVisibility(View.VISIBLE);
			listView.requestDisallowInterceptTouchEvent(true);
			/* The ListView never saw the MOVE (we consumed it), so its pending
			 * long-press would still fire mid-swipe and pop the context menu.
			 * Cancel its touch stream now that this is a swipe, not a press. */
			MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
			listView.onTouchEvent(cancel);
			cancel.recycle();
		}
		if (swipeDragging) {
			/* The card always follows the finger (up to the tab-switch
			 * distance); it only parks where that side has an action. */
			float tx = swipeStartTx + dx;
			if (tx > tabSwitchDistancePx)
				tx = tabSwitchDistancePx;
			else if (tx < -tabSwitchDistancePx)
				tx = -tabSwitchDistancePx;
			swipeRowFg.setTranslationX(tx);
			if (Math.abs(tx) >= tabSwitchDistancePx) {
				/* Kept swiping past the actions: switch tab instead of parking.
				 * Same direction as the tab strip: left goes to the next tab. */
				int dir = tx < 0 ? 1 : -1;
				View fg = swipeRowFg;
				swipeRowFg = null;
				swipeDragging = false;
				tabSwitchedThisGesture = true;
				listView.requestDisallowInterceptTouchEvent(false);
				fg.setTranslationX(0);
				if (openSwipeRow == fg)
					openSwipeRow = null;
				View actions = ((View) fg.getParent()).findViewById(R.id.row_actions);
				if (actions != null)
					actions.setVisibility(View.GONE);
				cycleTab(dir);
			}
			return true;
		}
		return false;
	}

	/* Returns true when the stream should be consumed (was dragging, or a
	 * tap that only closed a parked-open row). */
	private boolean endRowSwipe() {
		if (swipeRowFg == null)
			return false;
		View fg = swipeRowFg;
		swipeRowFg = null;
		listView.requestDisallowInterceptTouchEvent(false);
		if (swipeDragging) {
			swipeDragging = false;
			float tx = fg.getTranslationX();
			/* Park only where that side has an action: finished lives on the
			 * right (not in History), watched on the left (when available). */
			boolean parkRight = tx > swipeActionWidthPx / 2 && !logMode;
			boolean parkLeft = tx < -swipeActionWidthPx / 2 && swipeRowCanWatch;
			if (parkRight)
				parkOpenRow(fg, swipeActionWidthPx);
			else if (parkLeft)
				parkOpenRow(fg, -swipeActionWidthPx);
			else
				snapRowClosed(fg);	// released early, or no action on that side
			return true;
		}
		if (openSwipeRow == fg) {
			closeOpenSwipeRow();
			return true;
		}
		return false;
	}

	private void cancelRowSwipe() {
		if (swipeRowFg != null && swipeDragging) {
			final View fg = swipeRowFg;
			fg.animate().translationX(swipeStartTx).setDuration(150).withEndAction(new Runnable() {
				public void run() {
					if (fg.getTranslationX() == 0) {
						View actions = ((View) fg.getParent()).findViewById(R.id.row_actions);
						if (actions != null)
							actions.setVisibility(View.GONE);
					}
				}
			}).start();
		}
		swipeRowFg = null;
		swipeDragging = false;
		listView.requestDisallowInterceptTouchEvent(false);
	}

	/* Row swipes are detected on the ListView itself (see driver above). */
	private final View.OnTouchListener listSwipeListener = new View.OnTouchListener() {
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN: {
					tabSwitchedThisGesture = false;
					int pos = listView.pointToPosition((int) event.getX(), (int) event.getY());
					if (pos != ListView.INVALID_POSITION) {
						View row = listView.getChildAt(pos - listView.getFirstVisiblePosition());
						View fg = row != null ? row.findViewById(R.id.row_foreground) : null;
						if (fg != null) {
							TVShowItem item = seriesAdapter.getItem(pos);
							beginRowSwipe(fg, item != null && canMarkNextEpSeen(item), event.getX(), event.getY());
						} else {
							cancelRowSwipe();
						}
					} else {
						cancelRowSwipe();
						closeOpenSwipeRow();
					}
					break;
				}
				case MotionEvent.ACTION_MOVE:
					if (moveRowSwipe(event.getX(), event.getY()))
						return true;
					break;
				case MotionEvent.ACTION_UP: {
					boolean consume = tabSwitchedThisGesture || endRowSwipe();
					tabSwitchedThisGesture = false;
					if (consume)
						return true;
					break;
				}
				case MotionEvent.ACTION_CANCEL:
					tabSwitchedThisGesture = false;
					cancelRowSwipe();
					break;
			}
			return false;
		}
	};

	/* Circle through Watching -> Finished -> History -> Watching. */
	private void cycleTab(int direction) {
		TabLayout modeTabs = (TabLayout) findViewById(R.id.mode_tabs);
		if (modeTabs == null)
			return;
		int current = logMode ? 2 : showArchive;
		int next = (current + direction + 3) % 3;
		TabLayout.Tab tab = modeTabs.getTabAt(next);
		if (tab != null)
			tab.select();
	}

	private void markLastEpUnseen() {
		String[] episodeInfo = undo.get(undo.size()-1);
		String serieId = episodeInfo[0];
		String episodeId = episodeInfo[1];
		String serieName = episodeInfo[2];
		String episodeMarked = db.updateUnwatchedEpisode(serieId, episodeId);
		undo.remove(undo.size()-1);
		Toast.makeText(getApplicationContext(), serieName +" "+ episodeMarked +" "+ getString(R.string.messages_marked_unseen), Toast.LENGTH_SHORT).show();
		listView.post(updateShowView(serieId));
	}

	private void serieSeasons(int position) {
		TVShowItem item = seriesAdapter.getItem(position);
		if (item.getMediaType() == 1) {	// movies skip the seasons screen
			showDetails(item.getSerieId());
			return;
		}
		backFromSeasonSerieId = item.getSerieId();
		Intent serieSeasons = new Intent(DroidShows.this, SerieSeasons.class);
		serieSeasons.putExtra("serieId", backFromSeasonSerieId);
		serieSeasons.putExtra("nextEpisode", item.getUnwatched() > 0);
		startActivity(serieSeasons);
	}

	private Runnable updateShowView(final String serieId) {
		Runnable updateView = new Runnable(){
			public void run() {
				for (TVShowItem serie : series) {
					if (serie.getSerieId().equals(serieId)) {
						updateShowView(serie);
						break;
					}
				}
			}
		};
		return updateView;
	}

	private void updateShowView(final TVShowItem serie) {
		final int position = seriesAdapter.getPosition(serie);
		final TVShowItem newSerie = db.createTVShowItem(serie.getSerieId());
		lastSerie = newSerie;
		series.set(series.indexOf(serie), newSerie);
		listView.post(updateListView);
		listView.post(new Runnable() {
			public void run() {
				int newPosition = seriesAdapter.getPosition(newSerie);
				if (newPosition != position) {
					listView.setSelection(newPosition);
					if (listView.getLastVisiblePosition() > newPosition)
						listView.smoothScrollBy(-padding, 400);
				}
			}
		});
	}

	private void showDetails(String serieId) {
		Intent viewSerie = new Intent(DroidShows.this, ViewSerie.class);
		viewSerie.putExtra("serieId", serieId);
		startActivity(viewSerie);
	}

	private void episodeDetails(int position) {
		String serieId = seriesAdapter.getItem(position).getSerieId();
		String episodeId = "-1";
		if (!logMode)
			episodeId = db.getNextEpisodeId(serieId);
		else
			episodeId = seriesAdapter.getItem(position).getEpisodeId();
		if (episodeId != null && !episodeId.equals("-1")) {
			backFromSeasonSerieId = serieId;
			Intent viewEpisode = new Intent(DroidShows.this, ViewEpisode.class);
			viewEpisode.putExtra("serieName", seriesAdapter.getItem(position).getName());
			viewEpisode.putExtra("serieId", serieId);
			viewEpisode.putExtra("episodeId", episodeId);
			startActivity(viewEpisode);
		}
	}

	private void Search(String url, String serieName) {
		serieName = serieName.replaceAll(" \\(....\\)", "");
		Intent rt = new Intent(Intent.ACTION_VIEW, Uri.parse(url + Uri.encode(serieName)));
		rt.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(rt);
	}

	private void WikiDetails(String serieName, boolean isMovie) {
		serieName = serieName.replaceAll(" \\(....\\)", "");
		Intent wiki;
		String wikiApp = null;
	    if (getApplicationContext().getPackageManager().getLaunchIntentForPackage("org.wikipedia") != null)
	    	wikiApp = "org.wikipedia";
	    else if (getApplicationContext().getPackageManager().getLaunchIntentForPackage("org.wikipedia.beta") != null)
	    	wikiApp = "org.wikipedia.beta";
	    if (wikiApp == null) {
	    	String uri = "https://"+ (langCode.equals("all") ? "" : langCode +".") +"m.wikipedia.org/wiki/index.php?search="
	    		+ Uri.encode(serieName + (langCode.equals("en") && !isMovie ? " (TV series)" : ""));
	    	wiki = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
	    } else {
	    	wiki = new Intent(Intent.ACTION_SEND)
	    		.putExtra(Intent.EXTRA_TEXT, serieName)
	    		.setType("text/plain")
	    		.setPackage(wikiApp);
	    }
	    wiki.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    startActivity(wiki);
	}

	private void IMDbDetails(String serieId, String serieName, String episode) {
		String query;
		if (episode != null)
			query = "SELECT imdbId, episodeName FROM episodes WHERE id = '"+ episode +"' AND serieId='"+ serieId +"'";
		else
			query = "SELECT imdbId, serieName FROM series WHERE id = '" + serieId + "'";
		Cursor c = db.Query(query);
		if (c != null && c.moveToFirst()) {
			String imdbId = c.getString(0);
			if (imdbId == null)
				imdbId = "";
			if (episode != null && imdbId.equals(db.getSerieIMDbId(serieId)))	// Sometimes the given episode's IMDb id is that of the show's
				imdbId = "";	// So we want to search for the episode instead of go to the show's page
			String name = c.getString(1);
			c.close();
			String uri = "imdb:///";
			Intent testForApp = new Intent(Intent.ACTION_VIEW, Uri.parse("imdb:///find"));
			if (getApplicationContext().getPackageManager().resolveActivity(testForApp, 0) == null)
				uri = "https://m.imdb.com/";
			if (imdbId.startsWith("tt"))
				uri += "title/"+ imdbId + (episode != null ? "/fullcredits/cast" : "");
			else
				uri += "find?q="+ Uri.encode((episode != null ? serieName.replaceAll(" \\(....\\)", "") +" " : "") + name);
			Intent imdb = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
			imdb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(imdb);
		}
	}

	private void extResources(String extResourcesString, final int position) {
		if (extResourcesString.length() > 0) {
			String[] tmpResources = extResourcesString.trim().split("\\n");
			extResourcesString = "";
			for (int i = 0; i < tmpResources.length; i++) {
				String url = tmpResources[i].trim();
				if (url.length() > 0)
					extResourcesString += url +"\n";
			}
		}
		final TVShowItem serie = seriesAdapter.getItem(position);
		final boolean isMovie = serie.getMediaType() == 1;
		final String viewImdb = getString(R.string.menu_context_view_imdb);
		final String viewEpImdb = getString(R.string.menu_context_view_ep_imdb);
		final String searchOn = getString(R.string.menu_context_search_on);
		final String[] extResources = (
				viewImdb +"\n"+
				(isMovie ? "" : viewEpImdb +"\n")+
				searchOn +" FANDOM (Wikia)\n"+
				searchOn +" Rotten Tomatoes\n"+
				searchOn +" Wikipedia\n"+
				extResourcesString
				+"\u2026").split("\\n");
		final EditText input = new EditText(this);
		final String extResourcesInput = extResourcesString;
		input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_VARIATION_URI);
		new MaterialAlertDialogBuilder(this)
			.setTitle(serie.getName())
			.setItems(extResources, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					String clicked = extResources[item];
					if (item == extResources.length-1) {
						input.setText(extResourcesInput);
						new MaterialAlertDialogBuilder(DroidShows.this)
							.setTitle(serie.getName())
							.setView(input)
							.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
									String resources = input.getText().toString().trim();
									serie.setExtResources(resources);
									db.updateExtResources(serie.getSerieId(), resources);
									return;
								}
							})
							.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
									return;
								}
							})
							.show();
						if (extResourcesInput.length() == 0) {
							input.setText("Examples:\ntvshow.wikia.com\n*tvshow.blogspot.com\nLong-press show poster to directly open the starred url");
							input.selectAll();
						}
						input.requestFocus();
						keyboard.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_NOT_ALWAYS);
					} else if (clicked.equals(viewImdb)) {
						IMDbDetails(serie.getSerieId(), serie.getName(), null);
					} else if (clicked.equals(viewEpImdb)) {
						IMDbDetails(serie.getSerieId(), serie.getName(), logMode ? serie.getEpisodeId() : db.getNextEpisodeId(serie.getSerieId()));
					} else if (clicked.equals(searchOn +" FANDOM (Wikia)")) {
						Search("https://www.fandom.com/?s=", serie.getName());
					} else if (clicked.equals(searchOn +" Rotten Tomatoes")) {
						Search("https://www.rottentomatoes.com/search/?search=", serie.getName());
					} else if (clicked.equals(searchOn +" Wikipedia")) {
						WikiDetails(serie.getName(), isMovie);
					} else {
						browseExtResource(clicked);
					}
				}
			})
			.show();
	}

	private void browseExtResource(String url) {
		url = url.trim();
		if (url.startsWith("*"))
			url = url.substring(1).trim();
		if (!url.startsWith("http"))
			url = "https://"+ url;
		Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		browse.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(browse);
	}

	private void updateSerie(final TVShowItem serie, int position) {
		updateSerie(serie, null, position);
	}

	private void updateSerie(TVShowItem serie, final String langCode, int position) {
		if (!utils.isNetworkAvailable(DroidShows.this)) {
			Toast.makeText(getApplicationContext(), R.string.messages_no_internet, Toast.LENGTH_LONG).show();
		} else {
			final String serieId = serie.getSerieId();
			final String serieName = serie.getName();
			final boolean isMovie = serie.getMediaType() == 1;
			final String apiKey = sharedPrefs.getString(TMDB_API_KEY_NAME, "");
			Runnable updateserierun = new Runnable() {
				public void run() {
					Serie sToUpdate;
					String tvmazeId = null;
					if (isMovie) {
						if (apiKey == null || apiKey.isEmpty()) {
							errorNotify(serieName);
							hideTopProgress();
							return;
						}
						sToUpdate = new TMDB(apiKey).getMovie(serieId);
					} else {
						TVMaze tvMaze = new TVMaze();
						tvmazeId = resolveTvmazeId(tvMaze, serieId);
						if (tvmazeId == null || tvmazeId.isEmpty()) {
							errorNotify(serieName);
							hideTopProgress();
							return;
						}
						sToUpdate = getTVMazeShow(tvMaze, tvmazeId);
					}
					if (sToUpdate == null) {
						errorNotify(serieName);
						hideTopProgress();
					} else {
						if (!isMovie) {	// keep the existing DB row; TVMaze id goes to tvmazeId
							sToUpdate.setId(serieId);
							sToUpdate.setTvmazeId(tvmazeId == null ? "" : tvmazeId);
						}
						String toastMsg = getString(R.string.menu_context_updated);
						boolean lastSeasonOnly = !isMovie && langCode == null && latestSeasonOption == UPDATE_LATEST_SEASON_ONLY;
						if (!db.updateSerie(sToUpdate, lastSeasonOnly))
							toastMsg = isMovie ? getString(R.string.messages_error_dbupdate_movie) : "Database error while updating show";
						updatePosterThumb(serieId, sToUpdate);
						hideTopProgress();
						final String toastText = sToUpdate.getSerieName() +" "+ toastMsg;
						new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
							public void run() {
								Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();
							}
						});
						listView.post(updateShowView(serieId));
					}
				}
			};
			showTopProgress(true, 0);
			updateShowTh = new Thread(updateserierun);
			updateShowTh.start();
		}
	}

	@SuppressWarnings("deprecation")
	public void updatePosterThumb(String serieId, Serie sToUpdate) {
		Cursor c = DroidShows.db.Query("SELECT posterInCache, poster, posterThumb FROM series WHERE id='"+ serieId +"'");
		if (c == null || !c.moveToFirst()) {
			if (c != null) c.close();
			return;
		}
		String posterInCache = c.getString(0);
		String poster = c.getString(1);
		String posterThumbPath = c.getString(2);
		c.close();
		URL posterURL = null;
		boolean thumbCached = "true".equals(posterInCache) && posterThumbPath != null && new File(posterThumbPath).exists();
		if (!thumbCached) {
			poster = sToUpdate.getPoster();
			if (poster == null)
				return;
			try {
				posterURL = new URL(poster);
				if (posterThumbPath != null)
					new File(posterThumbPath).delete();
				posterThumbPath = Utils.libraryPosterFile(getApplicationContext(), posterURL).getAbsolutePath();
				} catch (MalformedURLException e) {
					Log.e(SQLiteStore.TAG, sToUpdate.getSerieName() +" doesn't have a poster URL");
					e.printStackTrace();
					return;
				}
				File posterThumbFile = null;
				try {
					posterThumbFile = new File(posterThumbPath);
					Utils.downloadPosterThumb(getApplicationContext(), posterURL, posterThumbFile);
				} catch (IOException e) {
					Log.e(SQLiteStore.TAG, "Could not download poster: "+ posterURL);
					e.printStackTrace();
					return;
				}
				Bitmap posterThumb = BitmapFactory.decodeFile(posterThumbPath);
				if (posterThumb == null) {
					Log.e(SQLiteStore.TAG, "Corrupt or unknown poster file type: "+ posterThumbPath);
					return;
				}
				int width = getWindowManager().getDefaultDisplay().getWidth();
				int height = getWindowManager().getDefaultDisplay().getHeight();
				int newHeight = (int) ((height > width ? height : width) * 0.265);
				int newWidth = (int) (1.0 * posterThumb.getWidth() / posterThumb.getHeight() * newHeight);
				Bitmap resizedBitmap = Bitmap.createScaledBitmap(posterThumb, newWidth, newHeight, true);
				OutputStream fOut = null;
				try {
					fOut = new FileOutputStream(posterThumbFile, false);
					resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
					fOut.flush();
					fOut.close();
					db.execQuery("UPDATE series SET posterInCache='true', poster='"+ poster
						+"', posterThumb='"+ posterThumbPath +"' WHERE id='"+ serieId +"'");
					Log.d(SQLiteStore.TAG, "Updated poster thumb for "+ sToUpdate.getSerieName());
				} catch (FileNotFoundException e) {
					Log.e(SQLiteStore.TAG, "File not found:"+ posterThumbFile);
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				posterThumb.recycle();
				resizedBitmap.recycle();
				System.gc();
				posterThumb = null;
				resizedBitmap = null;
			}
	}

	/** Non-intrusive progress: a thin bar at the top of the list; the list and app stay usable.
	 *  Driven through the app-wide SyncProgress so the bar stays visible (and keeps
	 *  moving) on the seasons/episodes/detail screens too, until the work finishes. */
	private void showTopProgress(final boolean indeterminate, final int max) {
		DroidShowsApp.beginOperation(indeterminate, max);
	}

	private void hideTopProgress() {
		DroidShowsApp.endOperation();
	}

	private void setTopProgress(final int progress) {
		SyncProgress.set(progress);
	}

	public void clearFilter(View v) {
		main.setVisibility(View.INVISIBLE);
		keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
		searchV.setText("");
		findViewById(R.id.search).setVisibility(View.GONE);
		getSeries();
	}

	public void searchForShow(View v) {
		keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
		Intent startSearch = new Intent(DroidShows.this, mediaType == 1 ? AddMovie.class : AddSerie.class);
		startSearch.putExtra(SearchManager.QUERY, searchV.getText().toString());
		startSearch.setAction(Intent.ACTION_SEARCH);
		startActivity(startSearch);
	}

	public void updateAllSeriesDialog() {
		boolean isMovie = mediaType == 1;
		String updateMessageAD = getString(isMovie ? R.string.dialog_update_movies : R.string.dialog_update_series)
			+ (!isMovie && latestSeasonOption == UPDATE_ALL_SEASONS ? getString(R.string.dialog_update_speedup) : "");
		AlertDialog.Builder alertDialog = new MaterialAlertDialogBuilder(this)
			.setTitle(isMovie ? R.string.messages_title_updating_movies : R.string.messages_title_update_series)
			.setMessage(updateMessageAD)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setCancelable(false)
			.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					updateAllSeries(showArchive);
					return;
				}
			})
			.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					return;
				}
			});
		alertDialog.show();
	}

	public void updateAllSeries(final int showArchive) {
		if (!utils.isNetworkAvailable(DroidShows.this)) {
			Toast.makeText(getApplicationContext(), R.string.messages_no_internet, Toast.LENGTH_LONG).show();
			if (swipeTriggered)
				swipeTriggered = false;
		} else if (updatingAll) {
			// an update is already running (swipe, bounce or menu) — don't stack another one
			if (swipeTriggered)
				swipeTriggered = false;
		} else {
			// Capture UI-thread state before leaving it; the DB queries below
			// used to run here and froze the app on large libraries.
			final boolean wasSearching = searching();
			final int archiveToUpdate = showArchive;
			final int mediaToUpdate = mediaType;
			final String apiKey = sharedPrefs.getString(TMDB_API_KEY_NAME, "");
			final boolean wasSwipe = swipeTriggered;
			final Runnable updateallseries = new Runnable() {
				public void run() {
					final List<TVShowItem> seriesToUpdate = new ArrayList<TVShowItem>();
					List<String> ids = db.getSeries(wasSearching ? 2 : archiveToUpdate, false, null, mediaToUpdate);
					for (String id : ids)
						seriesToUpdate.add(db.createTVShowItem(id));
					if (!wasSwipe) {
						updateAllDone = 0;
						runOnUiThread(new Runnable() {
							public void run() { showTopProgress(false, seriesToUpdate.size()); }
						});
					}
					String updatesFailed = "";
					TVMaze tvMaze = new TVMaze();
					TMDB tmdb = new TMDB(apiKey);
					for (int i = 0; i < seriesToUpdate.size(); i++) {
						TVShowItem item = seriesToUpdate.get(i);
						boolean isMovie = item.getMediaType() == 1;
						Log.d(SQLiteStore.TAG, "Getting updated info from "+ (isMovie ? "TMDB" : "TVMaze")
							+" for "+ (isMovie ? "movie " : "TV show ") + item.getName() +" ["+ (i+1) +"/"+ (seriesToUpdate.size()) +"]");
						dialogMsg = item.getName() + "\u2026";
						if (!swipeTriggered) {
							final int done = i + 1;
							runOnUiThread(new Runnable() {
								public void run() {
									updateAllDone = done;
									setTopProgress(done);
								}
							});
						}
						Serie sToUpdate = null;
						if (isMovie) {
							if (apiKey != null && !apiKey.isEmpty())
								sToUpdate = tmdb.getMovie(item.getSerieId());
						} else {
							String tvmazeId = resolveTvmazeId(tvMaze, item.getSerieId());
							if (tvmazeId != null && !tvmazeId.isEmpty()) {
								sToUpdate = getTVMazeShow(tvMaze, tvmazeId);
								if (sToUpdate != null) {	// keep the existing DB row; TVMaze id goes to tvmazeId
									sToUpdate.setId(item.getSerieId());
									sToUpdate.setTvmazeId(tvmazeId);
								}
							}
						}
						if (sToUpdate == null) {
							updatesFailed += dialogMsg +" ";
						} else {
							try {
								boolean lastSeasonOnly = !isMovie && latestSeasonOption == UPDATE_LATEST_SEASON_ONLY;
								if (!db.updateSerie(sToUpdate, lastSeasonOnly)) {
									final String error = getString(R.string.messages_error_dbupdate) +" "+ sToUpdate.getSerieName();
									Log.e(SQLiteStore.TAG, error);
									new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
										public void run() {
											Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
										}
									});
									// A failed DB write poisons the run: stop the loop so the
									// epilogue below still runs (hides the progress bar via
									// SyncProgress.hide(), clears updatingAll) instead of wedging
									// all future syncs like Looper.loop() did.
									updatesFailed += dialogMsg +" ";
									break;
								}
								updatePosterThumb(item.getSerieId(), sToUpdate);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						sleepQuietly(600);
					}
					if (updatesFailed.length() > 0) {
						final String updatesFailedResult = updatesFailed;
						runOnUiThread(new Runnable() {
							public void run() {errorNotify(updatesFailedResult);}
						});
					}
					updateShowStats();
					if (swipeTriggered) {
						swipeTriggered = false;
					} else {
						hideTopProgress();
					}
					updatingAll = false;
				}
			};
			updatingAll = true;
			updateAllShowsTh = new Thread(updateallseries);
			updateAllShowsTh.start();
		}
	}

	private static final String NOTIFY_CHANNEL_ID = "tvmovie_tracker_errors";

	private void ensureNotifyChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm.getNotificationChannel(NOTIFY_CHANNEL_ID) == null) {
				NotificationChannel channel = new NotificationChannel(
					NOTIFY_CHANNEL_ID, getString(R.string.layout_app_name),
					NotificationManager.IMPORTANCE_DEFAULT);
				nm.createNotificationChannel(channel);
			}
		}
	}

	private static final AtomicInteger notifyIdSeq = new AtomicInteger(1);

	@SuppressLint("NewApi")
	private void errorNotify(String error) {
		ensureNotifyChannel();
		PendingIntent appIntent = PendingIntent.getActivity(DroidShows.this, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
		// NotificationCompat renders with the platform's normal notification
		// styling instead of the app theme, so no purple accent anywhere.
		androidx.core.app.NotificationCompat.Builder builder =
			new androidx.core.app.NotificationCompat.Builder(getApplicationContext(), NOTIFY_CHANNEL_ID)
				.setContentIntent(appIntent)
				.setSmallIcon(R.drawable.ic_stat_tv)
				.setContentTitle(getString(R.string.messages_thetvdb_con_error))
				.setContentText(error)
				.setAutoCancel(true);
		androidx.core.app.NotificationManagerCompat.from(this)
			.notify(notifyIdSeq.getAndIncrement(), builder.build());
	}

	private void getSeries() {
		getSeries(showArchive, filterNetworks);
	}

	private void getSeries(int showArchive) {
		getSeries(showArchive, filterNetworks);
	}

	private void getSeries(int showArchive, boolean filterNetworks) {
		main.setVisibility(View.INVISIBLE);
		if (asyncInfo != null)
			asyncInfo.cancel(true);
		try {
			if (!logMode) {
				List<String> ids = db.getSeries(showArchive, filterNetworks, networks, mediaType);
				series.clear();
				seriesAdapter.notifyDataSetChanged();
				for (int i = 0; i < ids.size(); i++)
					series.add(db.createTVShowItem(ids.get(i)));
			} else {
				List<TVShowItem> episodes = db.getLog(0, mediaType);
				series.clear();
				seriesAdapter.notifyDataSetChanged();
				for (int i = 0; i < episodes.size(); i++)
					series.add(episodes.get(i));
			}
			String mediaTitle = (mediaType == 1 ? getString(R.string.media_movies) : getString(R.string.media_tv_shows));
			String modeTitle = (!logMode ? (showArchive == 1 ? " - "+ getString(R.string.mode_finished) : "") :
					" - "+ getString(R.string.menu_log));
			setTitle(getString(R.string.layout_app_name) +" - "+ mediaTitle + modeTitle);
			runOnUiThread(updateListView);
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "Error populating TVShowItems or no shows added yet");
			e.printStackTrace();
		}
		setFastScroll();
		boolean isMovie = mediaType == 1;
		Button addButton = (Button) findViewById(R.id.add_show);
		addButton.setText(isMovie ? R.string.menu_add_movie : R.string.menu_context_add_serie);
		addButton.setVisibility(!logMode ? View.VISIBLE : View.GONE);
		TextView emptyText = (TextView) findViewById(R.id.empty_text);
		if (emptyText != null)
			emptyText.setText(isMovie ? R.string.layout_main_no_movies : R.string.layout_main_no_items);
		if (navView != null)
			navView.setCheckedItem(mediaType == 1 ? R.id.nav_movies : R.id.nav_tv);
		main.setVisibility(View.VISIBLE);
		asyncInfo = new AsyncInfo();
		asyncInfo.execute();
	}

	public void getNextLogged() {
		List<TVShowItem> episodes = db.getLog(series.size(), mediaType);
		for (int i = 0; i < episodes.size(); i++)
			series.add(episodes.get(i));
		seriesAdapter.notifyDataSetChanged();
		listView.gettingNextLogged = false;
	}

	public static Runnable updateListView = new Runnable() {
		public void run() {
			seriesAdapter.notifyDataSetChanged();
			if (!logMode) seriesAdapter.sort(showsComperator);
			if (seriesAdapter.isFiltered)
				seriesAdapter.getFilter().filter(searchV.getText());
		}
	};

	private static Comparator<TVShowItem> showsComperator = new Comparator<TVShowItem>() {
		public int compare(TVShowItem object1, TVShowItem object2) {
			if (pinnedShows.contains(object1.getSerieId()) && !pinnedShows.contains(object2.getSerieId()))
				return -1;
			else if (pinnedShows.contains(object2.getSerieId()) && !pinnedShows.contains(object1.getSerieId()))
				return 1;

			if (sortOption == SORT_BY_UNSEEN) {
				int unwatchedAired1 = object1.getUnwatchedAired();
				int unwatchedAired2 = object2.getUnwatchedAired();
				if (unwatchedAired1 == unwatchedAired2) {
					Date nextAir1 = object1.getNextAir();
					Date nextAir2 = object2.getNextAir();
					if (nextAir1 == null && nextAir2 == null)
						return object1.getName().compareToIgnoreCase(object2.getName());
					if (nextAir1 == null)
						return 1;
					if (nextAir2 == null)
						return -1;
					return nextAir1.compareTo(nextAir2);
				}
				if (unwatchedAired1 == 0)
					return 1;
				if (unwatchedAired2 == 0)
					return -1;
				return ((Integer) unwatchedAired2).compareTo(unwatchedAired1);
			} else {
				return object1.getName().compareToIgnoreCase(object2.getName());
			}
		}
	};

	@Override
	public void onPause() {
		super.onPause();
		saveOptions();
	}

	/** Persist preferences. Called from onPause() and from the Exit menu item;
	 * never invoke the lifecycle method itself to get here. */
	private void saveOptions() {
		SharedPreferences.Editor ed = sharedPrefs.edit();
		ed.putBoolean(AUTO_BACKUP_PREF_NAME, autoBackup);
		ed.putString(BACKUP_FOLDER_PREF_NAME, backupFolder);
		ed.putBoolean(BACKUP_VERSIONING_PREF_NAME, backupVersioning);
		ed.putInt(SORT_PREF_NAME, sortOption);
		ed.putBoolean(EXCLUDE_SEEN_PREF_NAME, excludeSeen);
		ed.putInt(LATEST_SEASON_PREF_NAME, latestSeasonOption);
		ed.putBoolean(INCLUDE_SPECIALS_NAME, includeSpecialsOption);
		ed.putBoolean(FULL_LINE_CHECK_NAME, fullLineCheckOption);
		ed.putBoolean(LARGE_POSTERS_NAME, largePostersOption);
		ed.putBoolean(SWITCH_SWIPE_DIRECTION, switchSwipeDirection);
		ed.putString(LAST_STATS_UPDATE_NAME, lastStatsUpdateCurrent);
		ed.putString(LAST_STATS_UPDATE_ARCHIVE_NAME, lastStatsUpdateArchive);
		ed.putString(LANGUAGE_CODE_NAME, langCode);
		ed.putBoolean(SHOW_NEXT_AIRING, showNextAiring);
		ed.putBoolean(MARK_FROM_LAST_WATCHED, markFromLastWatched);
		ed.putString(PINNED_SHOWS_NAME, pinnedShows.toString());
		ed.putBoolean(FILTER_NETWORKS_NAME, filterNetworks);
		ed.putString(NETWORKS_NAME, networks.toString());
		ed.commit();
	}

	@Override
	protected void onStop() {
		maybeAutoBackup();
		super.onStop();
	}

	/** Trigger the auto-backup when the app goes quiet. Called from onStop()
	 * and from the Exit menu item; never invoke the lifecycle method itself. */
	private void maybeAutoBackup() {
		boolean updating = (updateShowTh != null && updateShowTh.isAlive())
			|| (updateAllShowsTh != null && updateAllShowsTh.isAlive());
		if (autoBackup && !updating && asyncInfo.getStatus() != AsyncTask.Status.RUNNING)	// not updating
			backup(true, backupFolder);
	}

	@Override
	protected void onDestroy() {
		// A dialog held in a field survives rotation and leaks its old
		// activity's window: dismiss it before the activity is torn down.
		if (m_AlertDlg != null) {
			try { m_AlertDlg.dismiss(); } catch (Exception e) {}
			m_AlertDlg = null;
		}
		super.onDestroy();
	}

	@Override
	public void onRestart() {
		super.onRestart();
		if (!logMode) {
			listView.post(updateShowView(backFromSeasonSerieId));
			backFromSeasonSerieId = null;
		} else {
			if (!removeEpisodeFromLog.isEmpty()) {
				for (int i = 0; i < series.size(); i++)
					if (series.get(i).getEpisodeId().equals(removeEpisodeFromLog)) {
						series.remove(i);
						listView.post(updateListView);
					}
				removeEpisodeFromLog = "";
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (searchV.getText().length() > 0) {
			findViewById(R.id.search).setVisibility(View.VISIBLE);
			listView.requestFocus();
		}
		if (!logMode && (asyncInfo == null || asyncInfo.getStatus() != AsyncTask.Status.RUNNING)) {
			asyncInfo = new AsyncInfo();
			asyncInfo.execute();
		}
	}

	private static class AsyncInfo extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
//			Log.d(SQLiteStore.TAG, "AsyncInfo Initializing");
			try {
				int showArchiveTmp = showArchive;
				String newToday = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());	// thread needs own SimpleDateFormat to prevent collisions in formatting of other dates
				String lastStatsUpdate = (showArchiveTmp == 0 ? lastStatsUpdateCurrent : lastStatsUpdateArchive);
				if (!lastStatsUpdate.equals(newToday)) {
					db.updateToday(newToday);
//					Log.d(SQLiteStore.TAG, "AsyncInfo RUNNING | Today = "+ newToday);
					// Iterate a snapshot: the UI thread can clear/rebuild `series`
					// (tab switch, rotation, filter) while this background thread runs.
					List<TVShowItem> snapshot;
					synchronized (series) { snapshot = new ArrayList<TVShowItem>(series); }
					for (int i = 0; i < snapshot.size(); i++) {
						TVShowItem serie = snapshot.get(i);
						if (isCancelled()) return null;
						String serieId = serie.getSerieId();
						int unwatched = db.getEpsUnwatched(serieId);
						int unwatchedAired = db.getEpsUnwatchedAired(serieId);
						if (unwatched != serie.getUnwatched() || unwatchedAired != serie.getUnwatchedAired()) {
							if (isCancelled()) return null;
							serie.setUnwatched(unwatched);
							serie.setUnwatchedAired(unwatchedAired);
							if (showNextAiring && unwatchedAired > 0) {
								NextEpisode nextEpisode = db.getNextEpisode(serieId);
								String nextEpisodeString = db.getNextEpisodeString(nextEpisode, true);
								serie.setNextEpisode(nextEpisodeString);
								if (isCancelled()) return null;
								db.execQuery("UPDATE series SET unwatched="+ unwatched +", unwatchedAired="+ unwatchedAired +", nextEpisode='"+ nextEpisodeString +"' WHERE id="+ serieId);
							} else {
								if (isCancelled()) return null;
								db.execQuery("UPDATE series SET unwatched="+ unwatched +", unwatchedAired="+ unwatchedAired +" WHERE id="+ serieId);
							}
						}
					}
					if (isCancelled()) return null;
					listView.post(updateListView);
					if (showArchiveTmp == 0 || showArchiveTmp == 2)
						lastStatsUpdateCurrent = newToday;
					if (showArchiveTmp > 0)
						lastStatsUpdateArchive = newToday;
//				Log.d(SQLiteStore.TAG, "Updated show stats for "+ (showArchiveTmp == 0 ? "current" : "archive") +" on "+ newToday);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	@Override
	public boolean onSearchRequested() {
		if (logMode)
			return false;
		if (findViewById(R.id.search).getVisibility() != View.VISIBLE) {
			findViewById(R.id.search).setVisibility(View.VISIBLE);
			getSeries(2, false);	// 2 = archive and current shows, false = don't filter networks
		}
		searchV.requestFocus();
		searchV.selectAll();
		keyboard.showSoftInput(searchV, 0);
//		keyboard.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_NOT_ALWAYS);
		return true;
	}

	@Override
	public void onBackPressed() {
		if (drawerLayout != null && navView != null && drawerLayout.isDrawerOpen(navView)) {
			drawerLayout.closeDrawer(navView);
			return;
		}
		if (searching())
			clearFilter(null);
		else {
			if (logMode)
				toggleLogMode();
			else if (showArchive == 1)
				toggleArchive();
			else
				super.onBackPressed();
			TabLayout modeTabs = (TabLayout) findViewById(R.id.mode_tabs);
			if (modeTabs != null) { TabLayout.Tab t = modeTabs.getTabAt(showArchive); if (t != null) t.select(); }
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("searching", searching());
		outState.putInt("showArchive", showArchive);
		outState.putInt("mediaType", mediaType);
		super.onSaveInstanceState(outState);
	}

	public String translateStatus(String statusValue) {
		if (statusValue.equalsIgnoreCase("Continuing")) {
			return getString(R.string.showstatus_continuing);
		} else if (statusValue.equalsIgnoreCase("Ended")) {
			return getString(R.string.showstatus_ended);
		} else {
			return statusValue.toLowerCase();
		}
	}

	private boolean searching() {
		return (seriesAdapter.isFiltered || findViewById(R.id.search).getVisibility() == View.VISIBLE);
	}

	public class SeriesAdapter extends ArrayAdapter<TVShowItem> {
		private List<TVShowItem> items;
		private ShowsFilter filter;
		private boolean isFiltered;
		private LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		private int iconListPosition;
		private ColorStateList textViewColors = new TextView(getContext()).getTextColors();

		private final String strEpAired = getString(R.string.messages_ep_aired);
		private final String strNewEp = getString(R.string.messages_new_episode);
		private final String strNewEps = getString(R.string.messages_new_episodes);
		private final String strNextAiring = getString(R.string.messages_next_airing);
		private final String strNextEp = getString(R.string.messages_next_episode);
		private final String strNoNewEps = getString(R.string.messages_no_new_eps);
		private final String strOf = getString(R.string.messages_of);
		private final String strOn = getString(R.string.messages_on);
		private final String strSeason = getString(R.string.messages_season);
		private final String strSeasons = getString(R.string.messages_seasons);
		private final String strToBeAired = getString(R.string.messages_to_be_aired);
		private final String strToBeAiredPl = getString(R.string.messages_to_be_aired_pl);

		public SeriesAdapter(Context context, int textViewResourceId, List<TVShowItem> series) {
			super(context, textViewResourceId, series);
			items = series;
			isFiltered = false;
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Filter getFilter() {
			if (filter == null)
				filter = new ShowsFilter();
			return filter;
		}

		@Override
		public TVShowItem getItem(int position) {
			return items.get(position);
		}

		public void setItem(int location, TVShowItem serie) {
			items.set(location, serie);
			notifyDataSetChanged();
		}

		private class ShowsFilter extends Filter {
			@SuppressLint("DefaultLocale")
			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				FilterResults results = new FilterResults();
				if (constraint == null || constraint.length() == 0) {
					results.count = series.size();
					results.values = series;
					isFiltered = false;
				} else {
					constraint = constraint.toString().toLowerCase();
					ArrayList<TVShowItem> filteredSeries = new ArrayList<TVShowItem>();
					// Snapshot: the filter worker thread must not iterate `series`
					// while the UI thread rebuilds it.
					List<TVShowItem> snapshot;
					synchronized (series) { snapshot = new ArrayList<TVShowItem>(series); }
					for (TVShowItem serie : snapshot) {
						if (serie.getName().toLowerCase().contains(constraint))
							filteredSeries.add(serie);
					}
					results.count = filteredSeries.size();
					results.values = filteredSeries;
					isFiltered = true;
				}
				return results;
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				items = (List<TVShowItem>) results.values;
				notifyDataSetChanged();
			}
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			TVShowItem serie = items.get(position);
			ViewHolder holder;
			if (!logMode &&  excludeSeen && !isFiltered && serie != lastSerie && serie.getUnwatchedAired() == 0 && (serie.getNextAir() == null || serie.getNextAir().after(Calendar.getInstance().getTime()))) {
				if (convertView == null || convertView.isEnabled()) {
					convertView = vi.inflate(R.layout.row_excluded, parent, false);
					convertView.setEnabled(false);
				}
				return convertView;
			} else if (convertView == null || !convertView.isEnabled()) {
				convertView = vi.inflate(R.layout.row, parent, false);
				holder = new ViewHolder();
				holder.sn = (TextView) convertView.findViewById(R.id.seriename);
				holder.si = (TextView) convertView.findViewById(R.id.serieinfo);
				holder.sne = (TextView) convertView.findViewById(R.id.serienextepisode);
				holder.icon = (IconView) convertView.findViewById(R.id.serieicon);
				holder.context = (ImageView) convertView.findViewById(R.id.seriecontext);
				holder.watched = (CheckBox) convertView.findViewById(R.id.watched_check);
				holder.textCol = (LinearLayout) convertView.findViewById(R.id.serie);
				holder.fg = convertView.findViewById(R.id.row_foreground);
				holder.rowActions = convertView.findViewById(R.id.row_actions);
				holder.actionWatched = convertView.findViewById(R.id.row_action_watched);
				holder.actionFinished = convertView.findViewById(R.id.row_action_finished);
				holder.icon.getLayoutParams().height = largePostersOption ? LARGE_POSTERS_HEIGHT : ViewGroup.LayoutParams.FILL_PARENT;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
					holder.context.setImageResource(R.drawable.context_material);
				convertView.setEnabled(true);
				convertView.setTag(holder);
				holder.icon.setOnTouchListener(iconTouchListener);
			} else {
				holder = (ViewHolder) convertView.getTag();
				holder.icon.setOnClickListener(null);
			}
			if (!logMode) {
				int nunwatched = serie.getUnwatched();
				int nunwatchedAired = serie.getUnwatchedAired();
				boolean isMovie = serie.getMediaType() == 1;
				String ended = (!isMovie && serie.getShowStatus().equalsIgnoreCase("Ended") ? " \u2020" : "");
				setTextColMargin(holder, isMovie);
				if (holder.sn != null) {
					holder.sn.setText((pinnedShows.contains(serie.getSerieId()) ? "\u2022 " : "") + serie.getName() + ended);
					holder.sn.setEnabled(!searching() || !serie.getPassiveStatus());
				}
				if (holder.si != null) {
					if (isMovie) {
						holder.si.setText(movieInfoText(serie));
						holder.si.setEnabled(nunwatched > 0);
					} else {
						String siText = "";
						int sNumber = serie.getSNumber();
						if (sNumber == 1) {
							siText = sNumber +" "+ strSeason;
						} else {
							siText = sNumber +" "+ strSeasons;
						}
						String unwatched = "";
						if (nunwatched == 0) {
							unwatched = strNoNewEps;
							if (!serie.getShowStatus().equalsIgnoreCase("null"))
								unwatched += " ("+ translateStatus(serie.getShowStatus()) +")";
							holder.si.setEnabled(false);
						} else {
							unwatched = nunwatched +" "+ (nunwatched > 1 ? strNewEps : strNewEp) +" ";
							if (nunwatchedAired > 0) {
								unwatched = (nunwatchedAired == nunwatched ? "" : nunwatchedAired +" "+ strOf +" ") + unwatched + strEpAired + (nunwatchedAired == nunwatched && ended.isEmpty() ? " \u00b7" : "");
								holder.si.setEnabled(true);
							} else {
								unwatched += (nunwatched > 1 ? strToBeAiredPl : strToBeAired);
								holder.si.setEnabled(false);
							}
						}
						holder.si.setText(siText +" | "+ unwatched);
					}
				}
				if (holder.sne != null) {
					if (isMovie) {
						holder.sne.setText("");
					} else if (nunwatched > 0 && serie.getNextEpisode() != null && !serie.getNextEpisode().isEmpty()) {
						holder.sne.setText(serie.getNextEpisode() == null ? "" : serie.getNextEpisode()
							.replace("[ne]", strNextEp)
							.replace("[na]", strNextAiring)
							.replace("[on]", strOn));
						holder.sne.setEnabled(serie.getNextAir() != null && serie.getNextAir().compareTo(Calendar.getInstance().getTime()) <= 0);
					} else {
						holder.sne.setText("");
					}
				}
				if (holder.watched != null) {
					if (isMovie) {
						holder.watched.setVisibility(View.VISIBLE);
						holder.watched.setChecked(nunwatched == 0);
						holder.watched.setTag(serie);
						holder.watched.setOnClickListener(movieWatchedListener);
					} else {
						holder.watched.setVisibility(View.GONE);
						holder.watched.setOnClickListener(null);
						holder.watched.setTag(null);
					}
				}
				if (holder.icon != null) {
					Drawable icon = serie.getDIcon();
					if (icon == null && !serie.getIcon().equals(""))
						icon = Drawable.createFromPath(serie.getIcon());
					if (icon == null) {
						holder.icon.setImageResource(R.drawable.noposter);
					} else {
						holder.icon.setImageDrawable(icon);
						serie.setDIcon(icon);
					}
				}
			} else {
				setTextColMargin(holder, false);
				if (holder.watched != null) {
					holder.watched.setVisibility(View.GONE);
					holder.watched.setOnClickListener(null);
					holder.watched.setTag(null);
				}
				if (holder.sn != null) {
					holder.sn.setText(serie.getName());
					holder.sn.setTextColor(textViewColors);
				}
				if (holder.si != null) {
					holder.si.setEnabled(true);
					holder.si.setText(serie.getEpisodeName());
				}
				if (holder.sne != null) {
					holder.sne.setEnabled(true);
					holder.sne.setText(serie.getEpisodeSeen());
				}
				if (holder.icon != null) {
					Drawable icon = serie.getDIcon();
					if (icon == null && !serie.getIcon().equals(""))
						icon = Drawable.createFromPath(serie.getIcon());
					if (icon == null) {
						holder.icon.setImageResource(R.drawable.noposter);
					} else {
						holder.icon.setImageDrawable(icon);
						serie.setDIcon(icon);
					}
				}
			}
			bindRowSwipe(holder, serie);
			return convertView;
		}

		/* "2026 · 124 min · Watched" — no seasons/episodes talk for movies */
		private String movieInfoText(TVShowItem movie) {
			StringBuilder sb = new StringBuilder();
			String firstAired = movie.getFirstAired();
			if (firstAired != null && firstAired.length() >= 4 && !firstAired.equalsIgnoreCase("null"))
				sb.append(firstAired.substring(0, 4));
			String runtime = movie.getRuntime();
			if (runtime != null && !runtime.isEmpty() && !runtime.equalsIgnoreCase("null")) {
				if (sb.length() > 0)
					sb.append(" \u00b7 ");
				sb.append(runtime).append(" ").append(getString(R.string.movie_minutes_short));
			}
			if (sb.length() > 0)
				sb.append(" \u00b7 ");
			sb.append(getString(movie.getUnwatched() == 0 ? R.string.movie_watched : R.string.movie_not_watched));
			return sb.toString();
		}

		/* Leave room for the watched checkbox on movie rows */
		private void setTextColMargin(ViewHolder holder, boolean isMovie) {
			if (holder.textCol != null) {
				ViewGroup.MarginLayoutParams tlp = (ViewGroup.MarginLayoutParams) holder.textCol.getLayoutParams();
				tlp.setMarginEnd(isMovie ? padding * 70 / 6 : padding * 22 / 6);
			}
		}

		private View.OnClickListener movieWatchedListener = new View.OnClickListener() {
			public void onClick(View v) {
				Object tag = v.getTag();
				if (tag instanceof TVShowItem)
					toggleMovieWatched((TVShowItem) tag);
			}
		};

		private OnTouchListener iconTouchListener = new OnTouchListener() {
			/* The poster icon consumes every touch, so a swipe starting on it
			 * would never reach the ListView's swipe detection. Drive the
			 * shared row-swipe methods here instead (only deltas are used,
			 * so the event's coordinate space does not matter). */
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getActionMasked();
				boolean swipeConsumed = false;
				if (action == MotionEvent.ACTION_DOWN) {
					tabSwitchedThisGesture = false;
					View fg = findRowForeground(v);
					if (fg != null) {
						int pos = listView.getPositionForView(v);
						TVShowItem item = pos != ListView.INVALID_POSITION ? seriesAdapter.getItem(pos) : null;
						beginRowSwipe(fg, item != null && canMarkNextEpSeen(item), event.getX(), event.getY());
					} else {
						cancelRowSwipe();
					}
				} else if (action == MotionEvent.ACTION_MOVE) {
					swipeConsumed = moveRowSwipe(event.getX(), event.getY());
				} else if (action == MotionEvent.ACTION_UP) {
					swipeConsumed = tabSwitchedThisGesture || endRowSwipe();
					tabSwitchedThisGesture = false;
				} else if (action == MotionEvent.ACTION_CANCEL) {
					tabSwitchedThisGesture = false;
					cancelRowSwipe();
				}
				if (!swipeConsumed) {
					iconListPosition = listView.getPositionForView(v);
					iconGestureDetector.onTouchEvent(event);
				}
				return true;
			}
		};

		/* Walk up from a row child (e.g. the poster icon) to the sliding card. */
		private View findRowForeground(View v) {
			ViewParent p = v.getParent();
			while (p instanceof View) {
				if (((View) p).getId() == R.id.row_foreground)
					return (View) p;
				p = p.getParent();
			}
			return null;
		}

		private final SimpleOnGestureListener iconGestureListener = new SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
				if (!logMode)
					episodeDetails(iconListPosition);
				else
					serieSeasons(iconListPosition);
				return true;
			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
				showDetails(seriesAdapter.getItem(iconListPosition).getSerieId());
				return true;
			}

			@Override
			public void onLongPress(MotionEvent e) {
				keyboard.hideSoftInputFromWindow(searchV.getWindowToken(), 0);
				String[] extResources = seriesAdapter.getItem(iconListPosition).getExtResources().trim().split("\\n");
				boolean foundResources = false;
				for (int i = 0; i < extResources.length; i++) {
					if (extResources[i].startsWith("*")) {
						browseExtResource(extResources[i]);
						foundResources = true;
					}
				}
				if (!foundResources)
					extResources(seriesAdapter.getItem(iconListPosition).getExtResources(), iconListPosition);
			}
		};

		private GestureDetector iconGestureDetector = new GestureDetector(getApplicationContext(), iconGestureListener);
	}

	static class ViewHolder
	{
		TextView sn;
		TextView si;
		TextView sne;
		IconView icon;
		ImageView context;
		CheckBox watched;
		LinearLayout textCol;
		View fg;			// sliding foreground card (swipe-reveal)
		View rowActions;	// behind-layer holding the action buttons
		View actionWatched;
		View actionFinished;
	}
}
