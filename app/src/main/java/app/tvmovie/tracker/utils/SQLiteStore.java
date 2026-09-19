package app.tvmovie.tracker.utils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import app.tvmovie.tracker.DroidShows;
import app.tvmovie.tracker.thetvdb.model.Episode;
import app.tvmovie.tracker.thetvdb.model.Serie;
import app.tvmovie.tracker.thetvdb.model.TVShowItem;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.text.TextUtils;
import android.util.Log;

public class SQLiteStore extends SQLiteOpenHelper
{
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	public static final SimpleDateFormat dateFormatSeen = new SimpleDateFormat("yyyyMMdd");
	public static final String TAG = "DroidShows";
	private static SQLiteStore instance = null;
	private static String DB_PATH = "";
	private static String DB_NAME = "DroidShows.db";
	private SQLiteDatabase db;
	private static volatile String today = dateFormat.format(Calendar.getInstance().getTime());	// Get today's date;

	public static synchronized SQLiteStore getInstance(Context context) {
		if (instance == null)
			instance = new SQLiteStore(context.getApplicationContext());
		return instance;
	}
	
	private SQLiteStore(Context context) {
		super(context, DB_NAME, null, 1);
		DB_PATH = context.getApplicationInfo().dataDir +"/databases/";
		try {
			openDataBase();
		} catch (SQLException sqle) {
			try {
				createDataBase();
				close();
				try {
					openDataBase();
				} catch (SQLException sqle2) {
					Log.e(TAG, sqle2.getMessage());
				}
			} catch (IOException e) {
				Log.e(TAG, "Unable to create database");
			}
		}
	}

	public void createDataBase() throws IOException {
		boolean dbExist = checkDataBase();
		if (!dbExist) {
			this.getWritableDatabase();
		}
	}

	private boolean checkDataBase() {
		SQLiteDatabase checkDB = null;
		try {
			String myPath = DB_PATH + DB_NAME;
			checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
		} catch (SQLiteException e) {
			Log.d(TAG, "Database does't exist yet.");
		}
		if (checkDB != null) {
			checkDB.close();
		}
		return checkDB != null ? true : false;
	}

	@SuppressLint("NewApi")
	public void openDataBase() throws SQLException {
		// Open the database
		String myPath = DB_PATH + DB_NAME;
		db = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			db.disableWriteAheadLogging();
	}

	/* Insert Methods */
	private synchronized void ensureOpen() {
		if (db == null || !db.isOpen()) {
			try { openDataBase(); } catch (Exception e) { Log.e(TAG, "Could not re-open database", e); }
		}
	}

	public boolean execQuery(String query) {
		ensureOpen();
		try {
			db.execSQL(query);
		} catch (SQLiteException e) {
			e.printStackTrace();
			return false;
		} catch (IllegalStateException e) {
			// database closed mid-query (e.g. backup racing an update)
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/* Same as execQuery, but throws instead of swallowing the error: use this
	 * for statements inside a transaction, so a failed INSERT aborts the whole
	 * transaction instead of committing a half-written show. */
	private void execQueryOrThrow(String query) {
		ensureOpen();
		db.execSQL(query);
	}

	public Cursor Query(String query) {
		Cursor c = null;
		ensureOpen();
		try {
			c = db.rawQuery(query, null);
		} catch (SQLiteException e) {
			return null;
		} catch (IllegalStateException e) {
			// database closed mid-query (e.g. backup racing an update)
			return null;
		}
		return c;
	}

	/* Get Methods */
	public TVShowItem createTVShowItem(String serieId) {
		String name = "", language = "", tmpPoster = "", showStatus = "", tmpNextEpisode = "", nextEpisode = "", tmpNextAir = "", extResources = "", runtime = "", firstAired = "";
		int tmpStatus = 0, seasonCount = 0, unwatched = 0, unwatchedAired = 0, mediaType = 0;
		Date nextAir = null;
		Cursor c = Query("SELECT serieName, language, posterThumb, status, passiveStatus, seasonCount, unwatchedAired, unwatched, nextEpisode, nextAir, extResources, mediaType, runtime, firstAired FROM series WHERE id = '" + serieId + "'");
		try {
			if (c != null && c.moveToFirst()) {
				name = c.getString(c.getColumnIndex("serieName"));
				language = c.getString(c.getColumnIndex("language"));
				tmpPoster = c.getString(c.getColumnIndex("posterThumb"));
				showStatus = c.getString(c.getColumnIndex("status"));
				tmpStatus = c.getInt(c.getColumnIndex("passiveStatus"));
				seasonCount = c.getInt(c.getColumnIndex("seasonCount"));
				unwatchedAired = c.getInt(c.getColumnIndex("unwatchedAired"));
				unwatched = c.getInt(c.getColumnIndex("unwatched"));
				tmpNextEpisode = c.getString(c.getColumnIndex("nextEpisode"));
				tmpNextAir = c.getString(c.getColumnIndex("nextAir"));
				extResources = c.getString(c.getColumnIndex("extResources"));
				int mediaTypeCol = c.getColumnIndex("mediaType");
				if (mediaTypeCol != -1) {
					mediaType = c.getInt(mediaTypeCol);
				}
				int runtimeCol = c.getColumnIndex("runtime");
				if (runtimeCol != -1) {
					runtime = c.getString(runtimeCol);
				}
				int firstAiredCol = c.getColumnIndex("firstAired");
				if (firstAiredCol != -1) {
					firstAired = c.getString(firstAiredCol);
				}
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		if (!"-1".equals(tmpNextEpisode))
			nextEpisode = tmpNextEpisode;
		if (!TextUtils.isEmpty(tmpNextAir) && !tmpNextAir.equals("null")) {
			try {
				// dateFormat is shared across threads: parse under its lock.
				synchronized (SQLiteStore.dateFormat) { nextAir = SQLiteStore.dateFormat.parse(tmpNextAir); }
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		boolean status = (tmpStatus == 1);
		TVShowItem tvsi = new TVShowItem(serieId, language, tmpPoster, null, name, seasonCount, nextEpisode, nextAir, unwatchedAired, unwatched, status, showStatus, extResources);
		tvsi.setMediaType(mediaType);
		tvsi.setRuntime(runtime);
		tvsi.setFirstAired(firstAired);
		return tvsi;
	}

	
	public List<Episode> getEpisodes(String serieId) {
		List<Episode> episodes = null;
		episodes = new ArrayList<Episode>();
		Cursor c = Query("SELECT * FROM episodes WHERE serieId = '"+ serieId +"'");
		try {
			if (c != null) {
				int idCol = c.getColumnIndex("id");
				int combinedEpisodeNumberCol = c.getColumnIndex("combinedEpisodeNumber");
				int combinedSeasonCol = c.getColumnIndex("combinedSeason");
				int dvdChapterCol = c.getColumnIndex("dvdChapter");
				int dvdDiscIdCol = c.getColumnIndex("dvdDiscId");
				int dvdEpisodeNumberCol = c.getColumnIndex("dvdEpisodeNumber");
				int dvdSeasonCol = c.getColumnIndex("dvdSeason");
				int epImgFlagCol = c.getColumnIndex("epImgFlag");
				int episodeNameCol = c.getColumnIndex("episodeName");
				int episodeNumberCol = c.getColumnIndex("episodeNumber");
				int firstAiredCol = c.getColumnIndex("firstAired");
				int imdbIdCol = c.getColumnIndex("imdbId");
				int languageCol = c.getColumnIndex("language");
				int overviewCol = c.getColumnIndex("overview");
				int productionCodeCol = c.getColumnIndex("productionCode");
				int ratingCol = c.getColumnIndex("rating");
				int seasonNumberCol = c.getColumnIndex("seasonNumber");
				int absoluteNumberCol = c.getColumnIndex("absoluteNumber");
				int filenameCol = c.getColumnIndex("filename");
				int lastUpdatedCol = c.getColumnIndex("lastUpdated");
				int seriesIdCol = c.getColumnIndex("serieId");
				int seasonIdCol = c.getColumnIndex("seasonId");
				int seenCol = c.getColumnIndex("seen");
				c.moveToFirst();
				if (c.isFirst()) {
					do {
						Episode eTmp = new Episode();
						List<String> directors = new ArrayList<String>();
						Cursor cdirectors = Query("SELECT director FROM directors WHERE serieId='"+ serieId
							+"' AND episodeId='"+ c.getString(idCol) +"'");
						if (cdirectors != null) {
							int directorCol = cdirectors.getColumnIndex("director");
							if (cdirectors.moveToFirst()) {
								do {
									directors.add(cdirectors.getString(directorCol));
								} while (cdirectors.moveToNext());
							}
							cdirectors.close();
						}
						List<String> guestStars = new ArrayList<String>();
						Cursor cguestStars = Query("SELECT guestStar FROM guestStars WHERE serieId='"+ serieId
							+"' AND episodeId='"+ c.getString(idCol) +"'");
						if (cguestStars != null) {
							int guestStarCol = cguestStars.getColumnIndex("guestStar");
							if (cguestStars.moveToFirst()) {
								do {
									guestStars.add(cguestStars.getString(guestStarCol));
								} while (cguestStars.moveToNext());
							}
							cguestStars.close();
						}
						List<String> writers = new ArrayList<String>();
						Cursor cwriters = Query("SELECT writer FROM writers WHERE serieId='"+ serieId
							+"' AND episodeId='"+ c.getString(idCol) +"'");
						if (cwriters != null) {
							int writersCol = cwriters.getColumnIndex("writer");
							if (cwriters.moveToFirst()) {
								do {
									writers.add(cwriters.getString(writersCol));
								} while (cwriters.moveToNext());
							}
							cwriters.close();
						}
						eTmp.setDirectors(directors);
						eTmp.setGuestStars(guestStars);
						eTmp.setWriters(writers);
						eTmp.setId(c.getString(idCol));
						eTmp.setCombinedEpisodeNumber(c.getString(combinedEpisodeNumberCol));
						eTmp.setCombinedSeason(c.getString(combinedSeasonCol));
						eTmp.setDvdChapter(c.getString(dvdChapterCol));
						eTmp.setDvdDiscId(c.getString(dvdDiscIdCol));
						eTmp.setDvdEpisodeNumber(c.getString(dvdEpisodeNumberCol));
						eTmp.setDvdSeason(c.getString(dvdSeasonCol));
						eTmp.setEpImgFlag(c.getString(epImgFlagCol));
						eTmp.setEpisodeName(c.getString(episodeNameCol));
						eTmp.setEpisodeNumber(c.getInt(episodeNumberCol));
						eTmp.setFirstAired(c.getString(firstAiredCol));
						eTmp.setImdbId(c.getString(imdbIdCol));
						eTmp.setLanguage(c.getString(languageCol));
						eTmp.setOverview(c.getString(overviewCol));
						eTmp.setProductionCode(c.getString(productionCodeCol));
						eTmp.setRating(c.getString(ratingCol));
						eTmp.setSeasonNumber(c.getInt(seasonNumberCol));
						eTmp.setAbsoluteNumber(c.getString(absoluteNumberCol));
						eTmp.setFilename(c.getString(filenameCol));
						eTmp.setLastUpdated(c.getString(lastUpdatedCol));
						eTmp.setSeriesId(c.getString(seriesIdCol));
						eTmp.setSeasonId(c.getString(seasonIdCol));
						if (c.getInt(seenCol) == 0) {
							eTmp.setSeen(false);
						} else {
							eTmp.setSeen(true);
						}
						episodes.add(eTmp);
					} while (c.moveToNext());
				}
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return episodes;
	}

	public List<String> getSeries(int showArchive, boolean filterNetworks, List<String> showNetworks) {
		return getSeries(showArchive, filterNetworks, showNetworks, 2);
	}

	public List<String> getSeries(int showArchive, boolean filterNetworks, List<String> showNetworks, int mediaType) {
		String networks = null;
		if (filterNetworks && showNetworks != null && !showNetworks.isEmpty()) {
			networks = "(";
			for (String network : showNetworks) {
				networks += "'"+ network +"', ";
			}
			networks = networks.substring(0, networks.length()-2) +")";
			Log.d(TAG, "showNetworksStr = "+ networks);
		}
		List<String> series = new ArrayList<String>();
		String showArchiveString = (showArchive < 2 ? " WHERE (passiveStatus"
			+ (showArchive == 0 ? "=0 OR passiveStatus IS NULL)" : ">=1)") : "");	// Solves issue with former bug when adding show directly after restoring backup
		boolean hasWhere = !showArchiveString.isEmpty();
		String showNetworksString = (networks != null ? (hasWhere ? " AND " : " WHERE ")
			+"network IN "+ networks : "");
		hasWhere = hasWhere || !showNetworksString.isEmpty();
		String mediaTypeString = (mediaType < 2 ? (hasWhere ? " AND " : " WHERE ")
			+"mediaType="+ mediaType : "");
//		Log.d(TAG, "SELECT id FROM series"+ showArchiveString + showNetworksString + mediaTypeString);
		Cursor cseries = Query("SELECT id FROM series"+ showArchiveString + showNetworksString + mediaTypeString);
		try {
			if (cseries != null && cseries.moveToFirst()) {
				do {
					series.add(cseries.getString(0));
				} while (cseries.moveToNext());
			}
			cseries.close();
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			cseries.close();
		}
		return series;
	}
	
	public List<String> getNetworks() {
		List<String> networks = new ArrayList<String>();
		Cursor c = Query("SELECT DISTINCT network FROM series");
		try {
			if (c != null && c.moveToFirst()) {
				do {
					String network = c.getString(0);
					if (network != null) {
						networks.add(network);
					}
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		Collections.sort(networks, String.CASE_INSENSITIVE_ORDER);
		return networks;
	}

	public List<TVShowItem> getLog() {
		return getLog(0);
	}

	public List<TVShowItem> getLog(int offset) {
		return getLog(offset, 2);
	}

	public List<TVShowItem> getLog(int offset, int mediaType) {
		List<TVShowItem> episodes = new ArrayList<TVShowItem>();
		String serieId = "", episodeId = "", episodeName = "";
		long seen;
		int seasonNumber = -1, episodeNumber = -1;
		if (offset < 0) offset = 0;	// a negative OFFSET is a SQL error, not an empty page
		Cursor c = Query("SELECT episodes.serieId, episodes.id, episodes.seasonNumber, episodes.episodeNumber, episodes.episodeName, episodes.seen"
								+" FROM episodes JOIN series ON episodes.serieId = series.id WHERE episodes.seen>1"
								+ (mediaType < 2 ? " AND series.mediaType="+ mediaType : "")
								+" ORDER BY episodes.seen DESC, episodes.serieId DESC, episodes.episodeNumber"
								+" DESC LIMIT 25 OFFSET "+ offset);
		if (c != null && c.moveToFirst()) {
			do {
				serieId = c.getString(c.getColumnIndex("serieId"));
				episodeId = c.getString(c.getColumnIndex("id"));
				seasonNumber = c.getInt(c.getColumnIndex("seasonNumber"));
				episodeNumber = c.getInt(c.getColumnIndex("episodeNumber"));
				episodeName = c.getString(c.getColumnIndex("episodeName"));
				seen = c.getLong(c.getColumnIndex("seen"));
		
				TVShowItem episode = createTVShowItem(serieId);

				episode.setEpisodeId(episodeId);
				if (episode.getMediaType() == 1)
					episode.setEpisodeName("");
				else
					episode.setEpisodeName(seasonNumber + (episodeNumber < 10 ? "x0" : "x") + episodeNumber +" "+ episodeName);
				Date epSeen = new Date(seen * 1000);
				episode.setEpisodeSeen(SimpleDateFormat.getDateTimeInstance().format(epSeen));
				
				episodes.add(episode);
			} while (c.moveToNext());
		}
		if (c != null) c.close();
		return episodes;
	}

	public EpisodeRow getEpisodeRow(String serieId, int seasonNumber, String episodeId) {
		List<EpisodeRow> rows = getEpisodeRows(serieId, seasonNumber, episodeId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public List<EpisodeRow> getEpisodeRows(String serieId, int seasonNumber) {
		return getEpisodeRows(serieId, seasonNumber, "");
	}
	
	private List<EpisodeRow> getEpisodeRows(String serieId, int seasonNumber, String episodeId) {
		List<EpisodeRow> episodes = new ArrayList<EpisodeRow>();
		Cursor c = Query("SELECT id, episodeName, episodeNumber, seen, firstAired FROM episodes WHERE "
			+ (episodeId.isEmpty() ? "" : "id="+ episodeId +" AND ")
			+ "serieId='"+ serieId +"' AND seasonNumber="+ seasonNumber
			+" ORDER BY episodeNumber ASC");
		try {
			if (c != null && c.moveToFirst()) {
				do {
					String id = c.getString(c.getColumnIndex("id"));
					String name = c.getInt(c.getColumnIndex("episodeNumber")) +". "
							+ c.getString(c.getColumnIndex("episodeName"));
					String aired = c.getString(c.getColumnIndex("firstAired"));
					Date airedDate = null;
					if (!TextUtils.isEmpty(aired) && !aired.equals("null")) {
							try { 
								// dateFormat is shared across threads: parse under its lock.
								synchronized (SQLiteStore.dateFormat) { airedDate = dateFormat.parse(aired); }
								aired = SimpleDateFormat.getDateInstance().format(airedDate);
							} catch (ParseException e) { e.printStackTrace(); }
					} else
						aired = "";
					long seen = c.getLong(c.getColumnIndex("seen"));
					
					episodes.add(new EpisodeRow(id, name, aired, airedDate, seen));
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return episodes;
	}

	private List<EpisodeSeen> getSeen(String serieId, int max_season) {
		List<EpisodeSeen> episodesSeen = new ArrayList<EpisodeSeen>();
		Cursor c = Query("SELECT seasonNumber, episodeNumber, seen, notified FROM episodes WHERE serieId='"+ serieId +"'"
			+ (max_season != -1 ? " AND (seasonNumber="+ max_season +" OR seasonNumber=0)": "")
			+" AND (seen>0 OR notified<>0)");
		try {
			if (c != null && c.moveToFirst()) {
				do {
					episodesSeen.add(new EpisodeSeen(c.getInt(0) +"x"+ c.getInt(1), c.getLong(2), c.getInt(3)));
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return episodesSeen;
	}

	public boolean convertSeenTimestamps() {
		boolean result = true;
		int converted = 0;
		List<EpisodeSeen> episodesSeen = new ArrayList<EpisodeSeen>();
		Cursor c = Query("SELECT id, seen FROM episodes WHERE seen>1");
		try {
			db.beginTransaction();
			if (c != null && c.moveToFirst()) {
				do {
					episodesSeen.add(new EpisodeSeen(c.getString(0), c.getLong(1), 0));
				} while (c.moveToNext());
			}
			Date seenTimestamp;
			for (EpisodeSeen ep : episodesSeen) {
				if (ep.seen > 1) {
					try {
						// dateFormatSeen is shared across threads: parse under its lock.
						synchronized (SQLiteStore.dateFormatSeen) { seenTimestamp = SQLiteStore.dateFormatSeen.parse(""+ ep.seen); }
						long seen = seenTimestamp.getTime() / 1000;
//						Log.d(TAG, "Converting seen from\n"+ ep.seen +" to\n"+ SQLiteStore.dateFormatSeen.format(new Date(seen * 1000))  +"(db-value ="+ seen +")");
						execQuery("UPDATE episodes SET seen ="+ seen +" WHERE id='"+ ep.episode +"'");
						converted++;
					} catch (ParseException e) {
						e.printStackTrace();
						return false;
					}
				}
			}
			db.setTransactionSuccessful();
			Log.d(TAG, "Converted seen dates (done) = "+ converted);
		} catch (SQLiteException e) {
			Log.d(TAG, "Converted seen dates (error!) = "+ converted);
			Log.e(TAG, e.getMessage());
			result = false;
		} finally {
			db.endTransaction();
		}
		if (c != null) c.close();
		return result;
	}

	public String getSerieIMDbId(String serieId) {
		String imdbId = "";
		Cursor c = Query("SELECT imdbId, serieName FROM series WHERE id = '" + serieId + "'");
		try {
			if (c != null && c.moveToFirst()) {
				imdbId = c.getString(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			imdbId = null;
		}
		if (c != null) c.close();
		return imdbId;
	}

	public String getSerieName(String serieId) {
		String sname = "";
		Cursor c = Query("SELECT serieName FROM series WHERE id='"+ serieId +"'");
		try {
			if (c != null && c.moveToFirst()) {
				sname = c.getString(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			sname = null;
		}
		if (c != null) c.close();
		return sname;
	}
	
	public int getEpsWatched(String serieId) {
		int watched = 0;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId
			+"' AND seen>0"
			+ (DroidShows.includeSpecialsOption ? "" : " AND seasonNumber <> 0"));
		try {
			if (c != null && c.moveToFirst()) {
				watched = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
//		Log.d(TAG, "serie="+serieId+" getEpsWatched="+ watched);
		return watched;
	}

	public int getEpsUnwatchedAired(String serieId) {
		int unwatchedAired = 0;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId
			+"' AND seen=0 AND firstAired < '"+ today +"' AND firstAired <> ''"
			+ (DroidShows.includeSpecialsOption ? "" : " AND seasonNumber <> 0"));
		try {
			if (c != null && c.moveToFirst()) {
				unwatchedAired = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
//		Log.d(TAG, "serie="+serieId+" getEpsUnwatchedAired"+" today="+today+" | unwatchedAired="+unwatchedAired);
		return unwatchedAired;
	}

	public int getEpsUnwatchedAired(String serieId, int snumber) {
		int unwatched = -1;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId +"' AND seasonNumber="+ snumber
				+" AND seen=0 AND firstAired < '"+ today +"' AND firstAired <> ''");
		try {
			if (c != null && c.moveToFirst()) {
				unwatched = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
//		Log.d(TAG, "serie="+serieId+" getSeasonEpsUnwatchedAired"+" today="+today+" | season ="+snumber+" | unwatchedAired="+unwatched);
		return unwatched;
	}

	public int getEpsUnwatched(String serieId) {
		int unwatched = -1;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId
			+"' AND seen=0 "+ (DroidShows.includeSpecialsOption ? "" : "AND seasonNumber <> 0"));
		try {
			if (c != null && c.moveToFirst()) {
				unwatched = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return unwatched;
	}

	public int getEpsUnwatched(String serieId, int snumber) {
		int unwatched = 0;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId
			+"' AND seasonNumber="+ snumber +" AND seen=0");
		try {
			if (c != null && c.moveToFirst()) {
				unwatched = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return unwatched;
	}

	public String getNextEpisodeId(String serieId) {
		return getNextEpisodeId(serieId, false);
	}

	/* First episode row id, regardless of seen state (used to toggle a movie back to unwatched) */
	public String getFirstEpisodeId(String serieId) {
		String id = null;
		Cursor c = null;
		try {
			c = Query("SELECT id FROM episodes WHERE serieId='"+ serieId +"' ORDER BY seasonNumber, episodeNumber ASC LIMIT 1");
			if (c != null && c.moveToFirst()) {
				id = c.getString(c.getColumnIndex("id"));
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return id;
	}

	public String getNextEpisodeId(String serieId, boolean noFutureEp) {
		int[] lastWatched = (DroidShows.markFromLastWatched ? getLastWatchedEpisode(serieId) : null);
		int id = -1;
		Cursor c = null;
		try {
				c = Query("SELECT id, seasonNumber, episodeNumber FROM episodes WHERE serieId='"+ serieId +"' AND seen=0"
						+ (lastWatched != null ? " AND ((seasonNumber = "+ lastWatched[0] +" AND episodeNumber > "+ lastWatched[1] +") OR seasonNumber > "+ lastWatched[0] +")" : "")
						+ (DroidShows.includeSpecialsOption ? "" : " AND seasonNumber <> 0")
						+ (noFutureEp ? " AND firstAired <= '"+ today +"' AND firstAired <> ''" : "")
						+" ORDER BY seasonNumber, episodeNumber ASC LIMIT 1");
			if (c != null && c.moveToFirst()) {
				int index = c.getColumnIndex("id");
				id = c.getInt(index);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return ""+ id;
	}

	private int[] getLastWatchedEpisode(String serieId) {
		int[] lastSeen = new int[2];
		Cursor c = Query("SELECT seasonNumber, episodeNumber FROM episodes WHERE serieId='"+ serieId
				+"' AND seen>=1 ORDER BY seen DESC, seasonNumber DESC, episodeNumber DESC LIMIT 1");
			try {
				if (c != null && c.moveToFirst()) {
					lastSeen[0] = c.getInt(0);
					lastSeen[1] = c.getInt(1);
				} else lastSeen = null;
			} catch (SQLiteException e) {
				lastSeen = null;
				Log.e(TAG, e.getMessage());
			}
			if (c != null) c.close();
			return lastSeen;
	}

	public NextEpisode getNextEpisode(String serieId) {
		return getNextEpisode(serieId, -1, false);
	}
		
	public NextEpisode getNextEpisode(String serieId, boolean showNextAiring) {
		return getNextEpisode(serieId, -1, showNextAiring);
	}

	public NextEpisode getNextEpisode(String serieId, int snumber) {
		return getNextEpisode(serieId, snumber, false);
	}

	private NextEpisode getNextEpisode(String serieId, int snumber, boolean showNextAiring) {
		int[] lastWatched = (!showNextAiring && DroidShows.markFromLastWatched ? getLastWatchedEpisode(serieId) : null);
		NextEpisode nextEpisode = null;
		Cursor c = null;
		try {
			if (snumber == -1) {
				c = Query("SELECT seasonNumber, episodeNumber, firstAired FROM episodes WHERE serieId='"+ serieId +"' AND seen=0"
					+ (lastWatched != null ? " AND ((seasonNumber = "+ lastWatched[0] +" AND episodeNumber > "+ lastWatched[1] +") OR seasonNumber > "+ lastWatched[0] +")" : "")
					+ (DroidShows.includeSpecialsOption ? "" : " AND seasonNumber <> 0")
					+ (showNextAiring ? " AND firstAired >= '"+ today +"'" : "")
					+" ORDER BY seasonNumber, episodeNumber ASC LIMIT 1");
			} else {
				c = Query("SELECT seasonNumber, episodeNumber, firstAired FROM episodes WHERE serieId='"+ serieId
					+"' AND seasonNumber="+ snumber +" AND seen=0"
					+" ORDER BY episodeNumber ASC LIMIT 1");
			}
			if (c != null && c.moveToFirst())
				nextEpisode = new NextEpisode(serieId, c.getInt(0), c.getInt(1), c.getString(2));
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		if (showNextAiring && (nextEpisode == null || "null".equals(nextEpisode.firstAired)))
			return null;
		if (nextEpisode == null)
			return new NextEpisode(serieId, -1, -1, "");
		return nextEpisode;
	}
	
	public String getNextEpisodeString(NextEpisode nextEpisode) {
		return getNextEpisodeString(nextEpisode, false);
	}

	public String getNextEpisodeString(NextEpisode nextEpisode, boolean showNextAiring) {
		if (nextEpisode.episode == -1)
			return "";
		
		String nextEpisodeString = nextEpisode.season
			+ (nextEpisode.episode < 10 ? "x0" : "x") + nextEpisode.episode;

		if (showNextAiring) {
			NextEpisode nextEpisodeAiring = getNextEpisode(nextEpisode.serieId, true);
			if (nextEpisodeAiring != null)
				return nextEpisodeString + " | [na] "+ nextEpisodeAiring.season
					+ (nextEpisodeAiring.episode < 10 ? "x0" : "x") + nextEpisodeAiring.episode
					+ (nextEpisode.firstAiredDate != null ? " [on] "
						+ SimpleDateFormat.getDateInstance().format(nextEpisodeAiring.firstAiredDate) : "");
		} 
		
		return "[ne] "+ nextEpisodeString
				+ (nextEpisode.firstAiredDate != null ? " [on] "
					+ SimpleDateFormat.getDateInstance().format(nextEpisode.firstAiredDate) : "");
	}

	public int getSeasonCount(String serieId) {
		int count = 0;
		Cursor c = Query("SELECT COUNT(season) FROM serie_seasons WHERE serieId = '"+ serieId +"' AND season <> 0");
		try {
			if (c != null && c.moveToFirst()) {
				count = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return count;
	}

	public int getSeasonEpisodeCount(String serieId, int sNumber) {
		int count = -1;
		Cursor c = Query("SELECT COUNT(id) FROM episodes WHERE serieId='"+ serieId +"' AND seasonNumber="+ sNumber);
		try {
			if (c != null && c.moveToFirst()) {
				count = c.getInt(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return count;
	}
	
	/* Update Methods */
	public void updateUnwatchedSeason(String serieId, int nseason) {
		try {
			long seen = System.currentTimeMillis() / 1000;
			db.execSQL("UPDATE episodes SET seen="+ seen +" WHERE serieId='"+ serieId +"' AND seasonNumber="+ nseason
			+" AND firstAired < '"+ today +"' AND firstAired <> '' AND seen < 1");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		updateShowStats(serieId);
	}

	public void updateWatchedSeason(String serieId, int nseason) {
		try {
			db.execSQL("UPDATE episodes SET seen=0 WHERE serieId='"+ serieId +"' AND seasonNumber="
				+ nseason);
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		updateShowStats(serieId);
	}

	public String updateUnwatchedEpisode(String serieId, String episodeId) {
		return updateUnwatchedEpisode(serieId, episodeId, -1);
	}

	public String updateUnwatchedEpisode(String serieId, String episodeId, long newSeen) {
		Cursor c = null;
		String episodeMarked = "";
		try {
			c = Query("SELECT seen, seasonNumber, episodeNumber FROM episodes WHERE serieId='"+ serieId+"' AND id='"+ episodeId +"'");
			if (c != null && c.moveToFirst()) {
				long seen = c.getLong(0);
				int season = c.getInt(1);
				int episode = c.getInt(2);
				episodeMarked =  season + (episode < 10 ? "x0" : "x") + episode;
				if (newSeen > -1) {
					seen = newSeen;
				} else {
					if (seen > 0)
						seen = 0;
					else
						seen = System.currentTimeMillis() / 1000;
				}
				db.execSQL("UPDATE episodes SET seen="+ seen +" WHERE serieId='"+ serieId +"' AND id='"+ episodeId +"'");
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		updateShowStats(serieId);
		return episodeMarked;
	}

	public void updateSerieStatus(String serieId, int passiveStatus) {
		try {
			db.execSQL("UPDATE series SET passiveStatus="+ passiveStatus +" WHERE id='"+ serieId +"'");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}
	
	public void updateExtResources(String serieId, String extResources) {
		try {
			db.execSQL("UPDATE series SET extResources='"+ extResources +"' WHERE id='"+ serieId +"'");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	public String getTvmazeId(String serieId) {
		String tvmazeId = "";
		Cursor c = Query("SELECT tvmazeId FROM series WHERE id='"+ serieId +"'");
		try {
			if (c != null && c.moveToFirst()) {
				tvmazeId = c.getString(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			tvmazeId = "";
		}
		if (c != null) c.close();
		return tvmazeId == null ? "" : tvmazeId;
	}

	public void setTvmazeId(String serieId, String tvmazeId) {
		try {
			db.execSQL("UPDATE series SET tvmazeId='"+ tvmazeId +"' WHERE id='"+ serieId +"'");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}
	/* Personal rating, 0-10 scale (0 = not rated). */
	public double getUserRating(String serieId) {
		double rating = 0;
		Cursor c = Query("SELECT userRating FROM series WHERE id='"+ serieId +"'");
		try {
			if (c != null && c.moveToFirst()) {
				rating = c.getDouble(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return rating;
	}

	public void setUserRating(String serieId, double rating) {
		try {
			db.execSQL("UPDATE series SET userRating="+ rating +" WHERE id='"+ serieId +"'");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/* Cached TMDB id for TV shows ("", unknown / not resolved yet). */
	public String getTmdbId(String serieId) {
		String tmdbId = "";
		Cursor c = Query("SELECT tmdbId FROM series WHERE id='"+ serieId +"'");
		try {
			if (c != null && c.moveToFirst()) {
				tmdbId = c.getString(0);
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			tmdbId = "";
		}
		if (c != null) c.close();
		return tmdbId == null ? "" : tmdbId;
	}

	public void setTmdbId(String serieId, String tmdbId) {
		try {
			db.execSQL("UPDATE series SET tmdbId='"+ tmdbId +"' WHERE id='"+ serieId +"'");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/* Episodes that have aired but were never notified about (for the
	 * new-episode check). Each row: {rowId, serieId, serieName, seasonNumber,
	 * episodeNumber, episodeName, firstAired}. TV shows on the Watching list only. */
	public List<String[]> getUnnotifiedAiredEpisodes(String today) {
		List<String[]> rows = new ArrayList<String[]>();
		Cursor c = Query("SELECT e.id, e.serieId, s.serieName, e.seasonNumber, e.episodeNumber, "
			+ "e.episodeName, e.firstAired FROM episodes e JOIN series s ON s.id=e.serieId "
			+ "WHERE s.mediaType=0 AND (s.passiveStatus=0 OR s.passiveStatus IS NULL) "
			+ "AND e.seen=0 AND e.notified=0 AND e.seasonNumber<>0 "
			+ "AND e.firstAired<>'' AND e.firstAired<='"+ today +"' "
			+ "ORDER BY s.serieName COLLATE NOCASE, e.seasonNumber, e.episodeNumber");
		try {
			if (c != null && c.moveToFirst()) {
				do {
					rows.add(new String[] { c.getString(0), c.getString(1), c.getString(2),
						c.getString(3), c.getString(4), c.getString(5), c.getString(6) });
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
		if (c != null) c.close();
		return rows;
	}

	public void markEpisodesNotified(List<String> episodeRowIds) {
		if (episodeRowIds == null || episodeRowIds.isEmpty()) return;
		try {
			StringBuilder in = new StringBuilder();
			for (String id : episodeRowIds) {
				if (in.length() > 0) in.append(",");
				in.append("'").append(id.replace("'", "''")).append("'");
			}
			db.execSQL("UPDATE episodes SET notified=1 WHERE id IN ("+ in +")");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	public boolean updateSerie(Serie s, boolean last_season) {
		if (s == null) {
			Log.e(TAG, "Error: Serie is null");
			return false;
		}
		String tmpSOverview = "";
		if (s.getOverview() != null && !TextUtils.isEmpty(s.getOverview())) {
			tmpSOverview = s.getOverview();
		}
		String tmpSName = "";
		if (!TextUtils.isEmpty(s.getSerieName())) {
			tmpSName = s.getSerieName();
		}
		Cursor cms = null;
		int max_season = -1;
		if (last_season) {
			try {
				cms = Query("SELECT season FROM serie_seasons WHERE serieID='"+ s.getId() +"'");
				if (cms != null && cms.moveToFirst()) {
					do {
						if (max_season < cms.getInt(0)) {
							max_season = cms.getInt(0);
						}
					} while (cms.moveToNext());
				}
				if (cms != null) cms.close();
				Log.d(TAG, "Updating only last season "+ max_season +" and specials of "+ tmpSName);
			} catch (SQLiteException e) {
				if (cms != null) {
					cms.close();
				}
				Log.e(TAG, e.getMessage());
			}
		} else {
			Log.d(TAG, "Updating all seasons of "+ tmpSName);
		}
		// Log.d(TAG, "MAX SEASON: "+ max_season);
		try {
			db.beginTransaction();
			db.execSQL("UPDATE series SET language='"+ s.getLanguage() +"', serieName="+ DatabaseUtils.sqlEscapeString(tmpSName)
				+", overview="+ DatabaseUtils.sqlEscapeString(tmpSOverview) +", "+"firstAired='"+ s.getFirstAired()
				+"', imdbId='"+ s.getImdbId() +"', zap2ItId='"+ s.getZap2ItId()
				+"', airsDayOfWeek='"+ s.getAirsDayOfWeek() +"', airsTime='"+ s.getAirsTime()
				+"', contentRating='"+ s.getContentRating() +"', "+"network="+ DatabaseUtils.sqlEscapeString(s.getNetwork())
				+", rating='"+ s.getRating() +"', runtime='"+ s.getRuntime() +"', "+"status='"
				+ s.getStatus() +"', lastUpdated='"+ s.getLastUpdated() +"' WHERE id='"+ s.getId() +"'");
			db.execSQL("DELETE FROM serie_seasons WHERE serieId='"+ s.getId() +"'");
			for (int n = 0; n < s.getNSeasons().size(); n++) {
				execQueryOrThrow("INSERT INTO serie_seasons (serieId, season) "+"VALUES ('"+ s.getId() +"', '"
					+ s.getNSeasons().get(n) +"');");
			}
			db.execSQL("DELETE FROM actors WHERE serieId='"+ s.getId() +"'");
			for (int a = 0; a < s.getActors().size(); a++) {
				execQueryOrThrow("INSERT INTO actors (serieId, actor) "+"VALUES ('"+ s.getId()
				+"',"+ DatabaseUtils.sqlEscapeString(s.getActors().get(a)) +");");
			}
			db.execSQL("DELETE FROM genres WHERE serieId='"+ s.getId() +"'");
			for (int g = 0; g < s.getGenres().size(); g++) {
				execQueryOrThrow("INSERT INTO genres (serieId, genre) "+"VALUES ('"+ s.getId()
				+"',"+ DatabaseUtils.sqlEscapeString(s.getGenres().get(g)) +");");
			}
			
			if (max_season != -1) {
				String episodes = "";
				cms = Query("SELECT id FROM episodes WHERE serieId='"+ s.getId() +"'"
				+" AND (seasonNumber="+ max_season +" OR seasonNumber=0)");
				if (cms != null && cms.moveToFirst()) {
					do {
						episodes += "'"+ cms.getString(0) +"', ";
					} while (cms.moveToNext());
				}
				if (cms != null) cms.close();
				if (!episodes.isEmpty()) {
					episodes = episodes.substring(0, episodes.length() - 2);
					db.execSQL("DELETE FROM directors WHERE serieId='"+ s.getId() +"' AND episodeId IN ("+ episodes +")");
					db.execSQL("DELETE FROM guestStars WHERE serieId='"+ s.getId() +"' AND episodeId IN ("+ episodes +")");
					db.execSQL("DELETE FROM writers WHERE serieId='"+ s.getId() +"' AND episodeId IN ("+ episodes +")");
				}
			} else {
				db.execSQL("DELETE FROM directors WHERE serieId='"+ s.getId() +"'");
				db.execSQL("DELETE FROM guestStars WHERE serieId='"+ s.getId() +"'");
				db.execSQL("DELETE FROM writers WHERE serieId='"+ s.getId() +"'");
			}

			List<EpisodeSeen> seenEpisodes = getSeen(s.getId(), max_season);
			db.execSQL("DELETE FROM episodes WHERE serieId='"+ s.getId() +"'"
					+(max_season != -1 ? " AND (seasonNumber="+ max_season +" OR seasonNumber=0)" : ""));
			
			for (int e = 0; e < s.getEpisodes().size(); e++) {
				if (max_season != -1) {
					int season = s.getEpisodes().get(e).getSeasonNumber();
					if (season < max_season && season != 0) {	// include specials in update
						continue;
					}
				}
				
				Episode ep = s.getEpisodes().get(e); 
				
				if (ep.getEpisodeNumber() == 0 && " ".equals(ep.getEpisodeName()) && ep.getOverview() == null) {
					continue;
				}
								
				for (int d = 0; d < ep.getDirectors().size(); d++) {
					execQueryOrThrow("INSERT INTO directors (serieId, episodeId, director) "+"VALUES ('"
						+ s.getId() +"', '"+ ep.getId()
						+"',"+ DatabaseUtils.sqlEscapeString(ep.getDirectors().get(d)) +");");
				}
				for (int g = 0; g < ep.getGuestStars().size(); g++) {
					execQueryOrThrow("INSERT INTO guestStars (serieId, episodeId, guestStar) "+"VALUES ('"
						+ s.getId() +"', '"+ ep.getId()
						+"',"+ DatabaseUtils.sqlEscapeString(ep.getGuestStars().get(g)) +");");
				}
				for (int w = 0; w < ep.getWriters().size(); w++) {
					execQueryOrThrow("INSERT INTO writers (serieId, episodeId, writer) "+"VALUES ('"+ s.getId()
						+"', '"+ ep.getId()
						+"',"+ DatabaseUtils.sqlEscapeString(ep.getWriters().get(w)) +");");
				}
				String tmpOverview = "";
				if (ep.getOverview() != null) {
					if (!TextUtils.isEmpty(ep.getOverview())) {
						tmpOverview = ep.getOverview();
					}
				}
				String tmpName = "";
				if (ep.getEpisodeName() != null) {
					if (!TextUtils.isEmpty(ep.getEpisodeName())) {
						tmpName = ep.getEpisodeName();
					}
				}
				
				long iseen = 0;
				int inotified = 0;
				String epCode = ep.getSeasonNumber() +"x"+ ep.getEpisodeNumber();
				for (EpisodeSeen es : seenEpisodes) {
					if (epCode.equals(es.episode)) {
						iseen = es.seen;
						inotified = es.notified;
						break;
					}
				}
								
				if (!tmpName.equals("")) {
					execQueryOrThrow("INSERT INTO episodes (serieId, id, combinedEpisodeNumber, combinedSeason, "
						+"dvdChapter, dvdDiscId, dvdEpisodeNumber, dvdSeason, epImgFlag, episodeName, "
						+"episodeNumber, firstAired, imdbId, language, overview, productionCode, rating, seasonNumber, "
						+"absoluteNumber, filename, lastUpdated, seasonId, seen, notified) VALUES ('"
						+ s.getId()
						+"', '"
						+ ep.getId()
						+"', '"
						+ ep.getCombinedEpisodeNumber()
						+"', '"
						+ ep.getCombinedSeason()
						+"', '"
						+ ep.getDvdChapter()
						+"', '"
						+ ep.getDvdDiscId()
						+"', '"
						+ ep.getDvdEpisodeNumber()
						+"', '"
						+ ep.getDvdSeason()
						+"', '"
						+ ep.getEpImgFlag()
						+"',"
						+ DatabaseUtils.sqlEscapeString(tmpName)
						+", "
						+ ep.getEpisodeNumber()
						+", '"
						+ ep.getFirstAired()
						+"', '"
						+ ep.getImdbId()
						+"', '"
						+ ep.getLanguage()
						+"',"
						+ DatabaseUtils.sqlEscapeString(tmpOverview)
						+", '"
						+ ep.getProductionCode()
						+"', '"
						+ ep.getRating()
						+"', "
						+ ep.getSeasonNumber()
						+", '"
						+ ep.getAbsoluteNumber()
						+"', '"
						+ ep.getFilename()
						+"', '"
						+ ep.getLastUpdated()
						+"', '"
						+ ep.getSeasonId() +"', "+ iseen +", "+ inotified +");");
				}
			}
			db.setTransactionSuccessful();
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			return false;
		} finally {
			// Never leave the transaction open, and never let a failure here
			// (e.g. the DB was closed mid-update) mask the real error.
			try { db.endTransaction(); } catch (Exception e) { Log.e(TAG, "Could not end transaction", e); }
		}
		updateShowStats(s.getId());
		return true;
	}

	/* Delete Methods */
	// DELETE FROM table_name WHERE some_column=some_value
	public boolean deleteSerie(String serieId) {
		boolean result = true;
		Cursor c = Query("SELECT posterThumb FROM series WHERE id='"+ serieId +"'");
		try {
			db.beginTransaction();
			db.execSQL("DELETE FROM directors WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM guestStars WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM writers WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM episodes WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM actors WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM genres WHERE serieId='"+ serieId +"'");
			db.execSQL("DELETE FROM serie_seasons WHERE serieId='"+ serieId +"'");
			if (c != null && c.moveToFirst()) {
				String thumbPath = c.getString(0);
				if (!TextUtils.isEmpty(thumbPath)) {
					File thumbImage = new File(thumbPath);
					thumbImage.delete();
				}
			}
			db.execSQL("DELETE FROM series WHERE id='"+ serieId +"'");
			db.setTransactionSuccessful();
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			result = false;
		} finally {
			db.endTransaction();
		}
		if (c != null) c.close();
		return result;
	}
	
	public boolean deleteEpisode(String serieId, String episodeId) {
		try {
			db.beginTransaction();
			db.execSQL("DELETE FROM directors WHERE serieId='"+ serieId +"' AND episodeId='"+ episodeId +"'");
			db.execSQL("DELETE FROM guestStars WHERE serieId='"+ serieId +"' AND episodeId='"+ episodeId +"'");
			db.execSQL("DELETE FROM writers WHERE serieId='"+ serieId +"' AND episodeId='"+ episodeId +"'");
			db.execSQL("DELETE FROM episodes WHERE serieId='"+ serieId +"' AND id='"+ episodeId +"'");
			db.setTransactionSuccessful();
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
			return false;
		} finally {
			db.endTransaction();
		}
		updateShowStats(serieId);
		return true;
	}

	/* *********************************************************************************** */
	@Override
	public synchronized void close() {
		if (db != null) db.close();
		super.close();
	}

	@Override
	public void onCreate(SQLiteDatabase dbase) {
		try {
			dbase.execSQL("CREATE TABLE IF NOT EXISTS droidseries (version VARCHAR);");
			dbase.execSQL("INSERT INTO droidseries (version) VALUES ('0.1.5-7G5');");
			// tabela dos directors
			dbase.execSQL("CREATE TABLE IF NOT EXISTS directors (serieId VARCHAR, episodeId VARCHAR, director VARCHAR);");
			// tabela dos guestStars
			dbase.execSQL("CREATE TABLE IF NOT EXISTS guestStars (serieId VARCHAR, episodeId VARCHAR, guestStar VARCHAR);");
			// tabela dos writers
			dbase.execSQL("CREATE TABLE IF NOT EXISTS writers (serieId VARCHAR, episodeId VARCHAR, writer VARCHAR);");
			// tabela dos episodios
			dbase.execSQL("CREATE TABLE IF NOT EXISTS episodes (serieId VARCHAR, id VARCHAR, "
				+"combinedEpisodeNumber VARCHAR, combinedSeason VARCHAR, dvdChapter VARCHAR, "
				+"dvdDiscId VARCHAR, dvdEpisodeNumber VARCHAR, dvdSeason VARCHAR, "
				+"epImgFlag VARCHAR, episodeName VARCHAR, episodeNumber INT, "
				+"firstAired VARCHAR, imdbId VARCHAR, language VARCHAR, overview TEXT, "
				+"productionCode VARCHAR, rating VARCHAR, seasonNumber INT, "
				+"absoluteNumber VARCHAR, filename VARCHAR,lastUpdated VARCHAR, "
				+"seasonId VARCHAR, seen INT, notified INTEGER DEFAULT 0);");
			// tabela dos actores
			dbase.execSQL("CREATE TABLE IF NOT EXISTS actors (serieId VARCHAR, actor VARCHAR);");
			// tabela dos genres
			dbase.execSQL("CREATE TABLE IF NOT EXISTS genres (serieId VARCHAR, genre VARCHAR);");
			// tabela das seasons
			dbase.execSQL("CREATE TABLE IF NOT EXISTS serie_seasons (serieId VARCHAR, season VARCHAR);");
			// create tables
			dbase.execSQL("CREATE TABLE IF NOT EXISTS series (id VARCHAR PRIMARY KEY, "
				+"serieId VARCHAR, language VARCHAR, serieName VARCHAR, banner VARCHAR, "
				+"overview TEXT, firstAired VARCHAR, imdbId VARCHAR, zap2ItId VARCHAR, "
				+"airsDayOfWeek VARCHAR, airsTime VARCHAR, contentRating VARCHAR, "
				+"network VARCHAR, rating VARCHAR, runtime VARCHAR, status VARCHAR, "
				+"fanart VARCHAR, lastUpdated VARCHAR, passiveStatus INTEGER DEFAULT 0, poster VARCHAR, "
				+"posterInCache VARCHAR, posterThumb VARCHAR, "
				+"seasonCount INTEGER, unwatchedAired INTEGER, unwatched INTEGER, nextEpisode VARCHAR, nextAir VARCHAR, "
				+"extResources VARCHAR NOT NULL DEFAULT '', mediaType INTEGER DEFAULT 0, tvmazeId VARCHAR DEFAULT '', userRating REAL DEFAULT 0, tmdbId VARCHAR DEFAULT '');");
		} catch (SQLiteException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Intentionally empty: this helper is always constructed with version 1,
		// so the framework never calls onUpgrade. Schema migrations are handled
		// by Update.java, which tracks the schema in the droidseries table and
		// walks every step (u0156To0157 ... u0157G3To0157G4). Do not add migration
		// logic here — it would run in addition to Update.java and corrupt data.
	}
	
	public void updateShowStats() {
		List<String> series = getSeries(2, false, null, 2);	// 2 = archive and current shows, false = don't filter networks, null = ignore networks filter, 2 = all media types
		for (int i = 0; i < series.size(); i += 1)
			updateShowStats(series.get(i));
	}
	
	public void updateShowStats(String serieId) {
		int seasonCount = getSeasonCount(serieId);
		int unwatchedAired = getEpsUnwatchedAired(serieId);
		int unwatched = getEpsUnwatched(serieId);
		NextEpisode nextEpisode = getNextEpisode(serieId);
		String nextEpisodeString = getNextEpisodeString(nextEpisode, DroidShows.showNextAiring && 0 < unwatchedAired && unwatchedAired < unwatched);
		execQuery("UPDATE series SET seasonCount="+ seasonCount +", unwatchedAired="+ unwatchedAired +", unwatched="+ unwatched +", nextEpisode='"+ nextEpisodeString +"', nextAir='"+ nextEpisode.firstAired +"' WHERE id="+ serieId);
	}
	
	public void updateToday(String newToday) {
		today = newToday;
	}
	
	private class EpisodeSeen {
		public String episode;
		public long seen;
		public int notified;
		
		public EpisodeSeen(String episode, long seen, int notified) {
			this.episode = episode;
			this.seen = seen;
			this.notified = notified;
		}
	}
	
	public class EpisodeRow {
		public String id;
		public String name;
		public String aired;
		public Date airedDate;
		public long seen;
		
		public EpisodeRow(String id, String name, String aired, Date airedDate, long seen2) {
			this.id = id;
			this.name = name;
			this.aired = aired;
			this.airedDate = airedDate;
			this.seen = seen2;
		}
	}
	
	public class NextEpisode {
		public String serieId;
		public int season;
		public int episode;
		public String firstAired;
		public Date firstAiredDate = null;
		
		public NextEpisode(String serieId, int season, int episode, String firstAired) {
			this.serieId = serieId;
			this.season = season;
			this.episode = episode;
			this.firstAired = firstAired;
			if (!TextUtils.isEmpty(firstAired) && !"null".equals(firstAired)) {
				try { this.firstAiredDate = new SimpleDateFormat("yyyy-MM-dd").parse(firstAired);	// used by seasons AsyncTask, so shouldn't use dateFormat
				} catch (ParseException e) { e.printStackTrace(); }
			}
		}
	}
}