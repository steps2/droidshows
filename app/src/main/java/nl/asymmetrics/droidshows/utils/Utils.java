package nl.asymmetrics.droidshows.utils;

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

	/* Poster storage: everything lives under the app cache dir so posters
	 * count as cache (clearable, reclaimable by the OS) instead of permanent
	 * app data. The cache is capped at POSTER_CACHE_MAX_BYTES with
	 * oldest-first eviction; a pruned poster simply re-downloads on demand. */
	public static final long POSTER_CACHE_MAX_BYTES = 50L * 1024L * 1024L;

	public static File posterDir(Context ctx) {
		return new File(ctx.getCacheDir(), "thumbs");
	}

	/** Cache file for the poster at url, mirroring its path under /thumbs. */
	public static File posterFile(Context ctx, URL url) {
		return new File(posterDir(ctx), url.getFile());
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

	public static void clearPosterCache(Context ctx) {
		deleteTree(posterDir(ctx));
	}

	/** Delete oldest poster files until the cache is under the cap. */
	public static void prunePosterCache(Context ctx) {
		File dir = posterDir(ctx);
		if (!dir.exists()) return;
		List<File> files = new ArrayList<File>();
		collectFiles(dir, files);
		long total = 0;
		for (File f : files) total += f.length();
		if (total <= POSTER_CACHE_MAX_BYTES) return;
		Collections.sort(files, new Comparator<File>() {
			public int compare(File a, File b) {
				long d = a.lastModified() - b.lastModified();
				return d < 0 ? -1 : (d > 0 ? 1 : 0);
			}
		});
		for (File f : files) {
			if (total <= POSTER_CACHE_MAX_BYTES) break;
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