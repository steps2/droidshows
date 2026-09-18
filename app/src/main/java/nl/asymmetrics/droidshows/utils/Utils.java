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
}