package app.tvmovie.tracker.utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//import android.util.Log;
public class Utils
{
	public boolean isNetworkAvailable(Activity mActivity) {
		Context context = mActivity.getApplicationContext();
		ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity == null) {
			// Log.d(TAG," connectivity is null");
			return false;
		} else {
			// Log.d(TAG," connectivity is not null");
			NetworkInfo[] info = connectivity.getAllNetworkInfo();
			if (info != null) {
				// Log.d(TAG," info is not null");
				for (int i = 0; i < info.length; i++) {
					if (info[i].getState() == NetworkInfo.State.CONNECTED) {
						return true;
					} else {
						// Log.d(TAG," info["+i+"] is not connected");
					}
				}
			} else {
				// Log.d(TAG," info is null");
			}
		}
		return false;
	}

	/* Download a URL to a file with connect/read timeouts. The old code used
	 * commons-io's copyURLToFile, which waits forever on a stalled host and
	 * can hang a poster download indefinitely (and, on the serial AsyncTask
	 * executor, everything queued behind it). */
	private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15000;
	private static final int DOWNLOAD_READ_TIMEOUT_MS = 20000;

	public static void downloadToFile(URL url, File file) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
		conn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
		conn.setInstanceFollowRedirects(true);
		try {
			int code = conn.getResponseCode();
			if (code < 200 || code >= 300)
				throw new IOException("HTTP "+ code +" downloading "+ url);
			InputStream in = conn.getInputStream();
			try {
				File parent = file.getParentFile();
				if (parent != null)
					parent.mkdirs();
				FileOutputStream out = new FileOutputStream(file);
				try {
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0)
						out.write(buf, 0, n);
				} finally {
					out.close();
				}
			} finally {
				in.close();
			}
		} finally {
			conn.disconnect();
		}
	}

	/* Poster storage: library posters (shows/movies the user added) live under
	 * getFilesDir()/posters so Android "Clear cache" and low-storage wipes can
	 * never delete them; they are never pruned by the app either. Discover
	 * posters are disposable: they live under getCacheDir()/thumbs/discover,
	 * capped at the user-chosen size (minimum 50MB) with oldest-first
	 * eviction; a pruned poster simply re-downloads on demand. */
	public static final String PREFS_NAME = "DroidShowsPref";
	public static final String POSTER_CACHE_SIZE_KEY = "poster_cache_size_mb";
	public static final long POSTER_CACHE_MIN_MB = 50;

	public static long getPosterCacheMaxBytes(Context ctx) {
		long mb = POSTER_CACHE_MIN_MB;
		try {
			mb = ctx.getSharedPreferences(PREFS_NAME, 0).getLong(POSTER_CACHE_SIZE_KEY, POSTER_CACHE_MIN_MB);
		} catch (Exception ignored) {}
		if (mb < POSTER_CACHE_MIN_MB) mb = POSTER_CACHE_MIN_MB;
		return mb * 1024L * 1024L;
	}

	public static void setPosterCacheMaxMB(Context ctx, long mb) {
		if (mb < POSTER_CACHE_MIN_MB) mb = POSTER_CACHE_MIN_MB;
		try {
			ctx.getSharedPreferences(PREFS_NAME, 0).edit().putLong(POSTER_CACHE_SIZE_KEY, mb).apply();
		} catch (Exception ignored) {}
	}

	public static File posterDir(Context ctx) {
		return new File(ctx.getCacheDir(), "thumbs");
	}

	public static File libraryPosterDir(Context ctx) {
		return new File(posterDir(ctx), "library");
	}

	public static File discoverPosterDir(Context ctx) {
		return new File(posterDir(ctx), "discover");
	}

	/** Persistent home for library posters. This is under getFilesDir(), NOT
	 *  the cache dir: Android Settings "Clear cache" and low-storage wipes
	 *  must never eat the user's show/movie posters. Discover posters stay
	 *  in the cache dir (disposable by design). */
	public static File libraryPosterDir(Context ctx) {
		return new File(ctx.getFilesDir(), "posters");
	}

	/** Cache file for a library poster at url. */
	public static File libraryPosterFile(Context ctx, URL url) {
		return new File(libraryPosterDir(ctx), url.getFile());
	}

	/** Cache file for a Discover poster at url. */
	public static File discoverPosterFile(Context ctx, URL url) {
		return new File(discoverPosterDir(ctx), url.getFile());
	}

	public static void downloadPosterThumb(Context ctx, URL url, File file) throws IOException {
		downloadToFile(url, file);
		prunePosterCache(ctx);
	}

	/** One-time move of posters from the old files-dir location to the cache
	 *  dir. Idempotent: once the old dir is gone there is nothing to do. */
	public static void migratePosterCache(Context ctx) {
		File oldDir = new File(ctx.getFilesDir(), "thumbs");
		if (!oldDir.exists()) return;
		File newDir = posterDir(ctx);
		if (newDir.exists()) {
			deleteTree(oldDir);
			return;
		}
		if (!oldDir.renameTo(newDir))
			deleteTree(oldDir);
	}

	/** Split cache/thumbs into library/ (DB-referenced posters, never pruned)
	 *  and discover/ (everything else, pruned). Idempotent. */
	public static void organizePosterCache(Context ctx, SQLiteStore db) {
		File base = posterDir(ctx);
		if (!base.exists()) return;
		File libDir = libraryPosterDir(ctx);
		File discDir = discoverPosterDir(ctx);
		libDir.mkdirs();
		discDir.mkdirs();
		String basePath;
		try { basePath = base.getCanonicalPath(); }
		catch (IOException e) { basePath = base.getAbsolutePath(); }
		String libPrefix = "library" + File.separator;
		String discPrefix = "discover" + File.separator;
		// 1. every DB-referenced poster -> library/
		try {
			Cursor c = db.Query("SELECT DISTINCT posterThumb FROM series WHERE posterThumb IS NOT NULL AND posterThumb != ''");
			if (c != null) {
				while (c.moveToNext()) {
					String p = c.getString(0);
					if (p == null || p.isEmpty()) continue;
					String fp;
					try { fp = new File(p).getCanonicalPath(); }
					catch (IOException e) { continue; }
					if (!fp.startsWith(basePath + File.separator)) continue;
					String rel = fp.substring(basePath.length() + 1);
					if (rel.startsWith(libPrefix) || rel.startsWith(discPrefix)) continue;
					File dest = new File(libDir, rel);
					if (moveFile(new File(fp), dest)) {
						String newPath = dest.getAbsolutePath();
						if (!newPath.equals(p))
							db.execQuery("UPDATE series SET posterThumb='" + sqlEsc(newPath) + "' WHERE posterThumb='" + sqlEsc(p) + "'");
					}
				}
				c.close();
			}
		} catch (Exception e) {
			Log.e("DroidShows", "organizePosterCache db pass failed", e);
		}
		// 2. anything else directly under thumbs/ -> discover/
		File[] kids = base.listFiles();
		if (kids != null) {
			for (File k : kids) {
				if (k.getAbsolutePath().equals(libDir.getAbsolutePath())
						|| k.getAbsolutePath().equals(discDir.getAbsolutePath())) continue;
				moveFile(k, new File(discDir, k.getName()));
			}
		}
	}

	private static String sqlEsc(String s) {
		return s.replace("'", "''");
	}

	private static boolean moveFile(File src, File dest) {
		if (src.getAbsolutePath().equals(dest.getAbsolutePath())) return true;
		if (!src.exists() || dest.exists()) return dest.exists();
		File parent = dest.getParentFile();
		if (parent != null) parent.mkdirs();
		return src.renameTo(dest);
	}

	/** One-time move of library posters from the old cache-dir location
	 *  (thumbs/library) to the persistent files-dir location (posters/).
	 *  Rewrites series.posterThumb to the new absolute paths. Idempotent:
	 *  once thumbs/library is gone there is nothing to do. */
	public static void migrateLibraryPostersToFilesDir(Context ctx, SQLiteStore db) {
		File oldLib;
		String oldPrefix;
		try {
			oldLib = new File(posterDir(ctx), "library");
			oldPrefix = oldLib.getCanonicalPath();
		} catch (IOException e) {
			return;
		}
		if (!oldLib.exists()) return;
		File newLib = libraryPosterDir(ctx);
		newLib.mkdirs();
		String newPrefix;
		try {
			newPrefix = newLib.getCanonicalPath();
		} catch (IOException e) {
			newPrefix = newLib.getAbsolutePath();
		}
		List<File> files = new ArrayList<File>();
		collectFiles(oldLib, files);
		boolean movedAny = false;
		for (File f : files) {
			String fp;
			try {
				fp = f.getCanonicalPath();
			} catch (IOException e) {
				continue;
			}
			if (!fp.startsWith(oldPrefix + File.separator)) continue;
			String rel = fp.substring(oldPrefix.length() + 1);
			if (moveFile(f, new File(newLib, rel))) movedAny = true;
		}
		deleteTree(oldLib);
		if (movedAny) {
			try {
				db.execQuery("UPDATE series SET posterThumb = REPLACE(posterThumb, '"
						+ sqlEsc(oldPrefix + File.separator) + "', '"
						+ sqlEsc(newPrefix + File.separator) + "')");
			} catch (Exception e) {
				Log.e("DroidShows", "migrateLibraryPostersToFilesDir db pass failed", e);
			}
		}
	}

	/** Whole poster cache (used after a restore: everything is stale).
	 *  Covers both the cache dir (Discover) and the persistent files-dir
	 *  library posters. */
	public static void clearPosterCache(Context ctx) {
		deleteTree(posterDir(ctx));
		deleteTree(libraryPosterDir(ctx));
	}

	/** Only the disposable Discover posters; library posters are kept. */
	public static void clearDiscoverCache(Context ctx) {
		deleteTree(discoverPosterDir(ctx));
	}

	/** Delete oldest Discover posters until the cache is under the cap.
	 *  Library posters are never touched. */
	public static void prunePosterCache(Context ctx) {
		pruneDirTo(discoverPosterDir(ctx), getPosterCacheMaxBytes(ctx));
	}

	private static void pruneDirTo(File dir, long maxBytes) {
		if (!dir.exists()) return;
		List<File> files = new ArrayList<File>();
		collectFiles(dir, files);
		long total = 0;
		for (File f : files) total += f.length();
		if (total <= maxBytes) return;
		Collections.sort(files, new Comparator<File>() {
			public int compare(File a, File b) {
				long d = a.lastModified() - b.lastModified();
				return d < 0 ? -1 : (d > 0 ? 1 : 0);
			}
		});
		for (File f : files) {
			if (total <= maxBytes) break;
			long len = f.length();
			if (f.delete()) total -= len;
		}
	}

	private static void collectFiles(File dir, List<File> out) {
		File[] kids = dir.listFiles();
		if (kids == null) return;
		for (File k : kids) {
			if (k.isDirectory()) collectFiles(k, out);
			else out.add(k);
		}
	}

	private static void deleteTree(File f) {
		if (f.isDirectory()) {
			File[] kids = f.listFiles();
			if (kids != null)
				for (File k : kids) deleteTree(k);
		}
		f.delete();
	}
}