# DroidShows Migration Plan: TheTVDB v1 → TVMaze + Movies (TMDB)

**Status:** planning only — no code changed yet
**Date:** 2026-09-15
**Repo:** `~/workspace/droidshows` (clone of `ltGuillaume/droidshows`)

## 1. Executive summary

DroidShows is dead in the water for adding/updating shows because it talks to the
TheTVDB **v1 XML API** (`https://thetvdb.com/api/GetSeries.php?...` and
`https://thetvdb.com/api/<key>/series/<id>/all/<lang>.xml`), which TheTVDB
scheduled for shutdown in H1 2022 and which no longer responds. The fix has two parts:

1. **TV shows → TVMaze** (`https://api.tvmaze.com`): free, no API key, JSON,
   rate limit ~20 req / 10 s. Covers search, show details, full episode lists,
   cast, images. Also offers `/lookup/shows?thetvdb=<id>` to migrate existing
   DB entries that are keyed by old TVDB ids.
2. **Movies → TMDB** (`https://api.themoviedb.org/3`): TVMaze is TV-only, so
   movies need a second source. TMDB needs a (free) API key, which becomes a
   user-configurable setting. This mirrors what the proven
   `warren-bank/Android-Tiny-Television-Time-Tracker` fork did for this exact
   codebase lineage.

Plus the requested UI change: top-level **TV Shows / Movies** tabs, each with
**Watching / Finished / Log** sub-tabs (replacing Active / Archive / Log).

Movies reuse the existing `series`/`episodes` tables: a movie is stored as one
`series` row (`mediaType=1`) plus a single `episodes` row (the film itself).
This reuses the list adapter, stats, log, seen/unseen machinery with minimal
new code.

---

## 2. How TheTVDB is used today (what must be replaced)

### 2.1 The provider class

`app/src/main/java/nl/asymmetrics/droidshows/thetvdb/TheTVDB.java`

- Constructor: `TheTVDB(String apiKey, boolean useMirror)`. Base URLs:
  `https://thetvdb.com/api/` and mirror `https://thetvdb.plexapp.com/api/`
  (`useMirror` pref, now obsolete).
- Hardcoded API key `"8AC675886350B3C3"` at two call sites:
  - `ui/AddSerie.java` → `Search()` (line ~218): `theTVDB = new TheTVDB("8AC675886350B3C3", DroidShows.useMirror);`
  - `DroidShows.java` → `updateSerie(...)` (~line 1050): `theTVDB = new TheTVDB("8AC675886350B3C3", useMirror);`
- `searchSeries(String title, String language)` → `List<Serie>`
  Hits `GetSeries.php?seriesname=<q>&language=<lang>`, SAX-parses `<Series>`
  blocks. Only fields used downstream: `id` (`<id>`), `serieId` (`<SeriesID>`),
  `serieName`, `language`, `banner`, `overview`.
- `getSerie(String id, String language)` → `Serie` (full + episodes)
  Hits `<apiKey>/series/<id>/all/<lang>.xml`, parses `<Series>` + all `<Episode>`
  blocks, computes `nseasons`.
- HTTP + XML plumbing: `thetvdb/utils/XMLParser.java` (SAX over
  `HttpsURLConnection`, 5 s timeouts), `thetvdb/utils/XMLHandler.java`.

### 2.2 Callers of the provider

| Call site | File | What it does |
|---|---|---|
| `Search()` → `searchSeries()` | `ui/AddSerie.java` (~218) | background thread, fills search results list |
| `AsyncAddSerie.doInBackground()` → `getSerie()` | `ui/AddSerie.java` (~280) | fetches full show + episodes, downloads poster thumb, `saveToDB()` |
| `updateSerie()` → `getSerie()` | `DroidShows.java` (~1050) | refresh one show, preserves seen dates |
| `updateAllSeries()` → `getSerie()` per show | `DroidShows.java` (~1300+) | refresh all shows in current/archive scope |

### 2.3 Data the app actually needs

From `thetvdb/model/Serie.java` + `thetvdb/model/Episode.java` and the
`series`/`episodes` tables (see §5), the fields that matter downstream:

**Series:** `id` (PK), `serieName`, `overview`, `firstAired`, `poster`
(+ locally cached thumb), `banner`, `fanart`, `network`, `genres[]`,
`actors[]`, `rating`, `runtime`, `status`, `language`, `airsDayOfWeek`,
`airsTime`, `imdbId`, `lastUpdated`, `passiveStatus` (0 = watching, 1 = finished).
Viewed in `ui/ViewSerie.java` (network, contentRating, name, poster, genre,
rating, firstAired, airtime, runtime, overview, actors).

**Episodes:** `id`, `seasonNumber`, `episodeNumber`, `episodeName`,
`firstAired`, `overview`, `seen` flag (+ seen timestamp), `filename`
(episode still image). Derived stats in `utils/SQLiteStore.java`:
`seasonCount`, `unwatched`, `unwatchedAired`, `nextEpisode`, `nextAir`.
Specials convention: `seasonNumber = 0` = specials; `includeSpecialsOption`
toggles `AND seasonNumber <> 0` in ~7 queries (`SQLiteStore.java` lines
~395–867).

**List rows:** `thetvdb/model/TVShowItem.java` (serieId, name, poster thumb,
season count, next-episode string, next air date, unwatched counts,
passiveStatus, show status, extResources; log mode reuses it with
episodeId/episodeName/episodeSeen).

---

## 3. TVMaze API mapping (TV shows)

Base `https://api.tvmaze.com`, JSON, **no auth**, rate limit **≥ 20 requests /
10 s per IP** (HTTP 429 → back off), edge cache ~60 min. Set a descriptive
`User-Agent`. Data is **CC BY-SA** — credit TVmaze (link via `show.url`) in
the About screen.

| Need | Endpoint |
|---|---|
| Search shows | `GET /search/shows?q={query}` → `[{score, show}]` |
| Show details + cast | `GET /shows/{id}?embed=cast` → show + `_embedded.cast[]` |
| Episode list | `GET /shows/{id}/episodes?specials=1` → `[...]` |
| Migrate old TVDB ids | `GET /lookup/shows?thetvdb={tvdbId}` → show |

### 3.1 Show field mapping (TVMaze → `Serie`)

| TVMaze | Serie field | Notes |
|---|---|---|
| `show.id` (int) | `id`, `serieId` | `String.valueOf()`; both set (code uses `id` as canonical key) |
| `show.name` | `serieName` | |
| `show.summary` | `overview` | **HTML** — strip tags (`Html.fromHtml(...).toString()` or regex) |
| `show.premiered` | `firstAired` | `YYYY-MM-DD` already |
| `show.externals.imdb` | `imdbId` | may be null |
| `show.network.name` else `show.webChannel.name` | `network` | null-safe |
| `show.status` | `status` | `Running` / `Ended` / `To Be Determined` |
| `show.rating.average` | `rating` | number → String, null-safe |
| `show.runtime` | `runtime` | int → String, null-safe |
| `show.genres[]` | `genres` | direct |
| `show.image.original` (fallback `medium`) | `poster` | |
| `show.image.original` | `banner`, `fanart` | TVMaze has no separate banner/fanart — reuse poster or leave empty |
| `show.language` | `language` | e.g. `"English"` |
| `show.schedule.days[]` | `airsDayOfWeek` | join with `, ` |
| `show.schedule.time` | `airsTime` | may be empty |
| `show.updated` | `lastUpdated` | unix timestamp (int) → String |
| `_embedded.cast[].person.name` | `actors` | |
| — | `zap2ItId`, `contentRating` | **no equivalent** — leave empty |
| `show.url` | — | keep for attribution link |

### 3.2 Episode field mapping (TVMaze → `Episode`)

| TVMaze | Episode field | Notes |
|---|---|---|
| `ep.id` | `id` | `String.valueOf()` |
| `ep.name` | `episodeName` | |
| `ep.season` | `seasonNumber` | int |
| `ep.number` | `episodeNumber` | int |
| `ep.airdate` | `firstAired` | `YYYY-MM-DD`, may be null |
| `ep.summary` | `overview` | HTML — strip |
| `ep.image.medium` | `filename` | still image URL |
| `ep.runtime` | — | model has no field; skip |
| — | `imdbId`, DVD fields, `guestStars`, `writers`, `directors`, `absoluteNumber` | **no equivalent** in list endpoint; leave empty. (Guest cast/crew exist at `/episodes/{id}/guestcast`, `/episodes/{id}/guestcrew` — optional lazy-load later, out of scope.) |

### 3.3 Gaps, quirks, decisions

- **Specials:** TVMaze omits specials from `/shows/{id}/episodes` unless
  `?specials=1`. The app treats `seasonNumber=0` as specials. **Verify during
  implementation** how TVMaze numbers specials when included and map them to
  `seasonNumber=0` so `includeSpecialsOption` keeps working.
- **Language:** TVMaze search/details are not localised. The "change synopsis
  language" button in `AddSerie` becomes a no-op — remove it (or keep the
  stored `language` = `show.language`). The global `langCode` pref can stay for
  Wikipedia/IMDb links.
- **`useMirror` pref:** obsolete (was a TVDB mirror). Remove the checkbox from
  `alert_about.xml` and the pref (keep DB column out; it's in-memory only).
- **Episode-number stability:** the update flow saves seen dates, wipes and
  recreates episodes, then re-applies seen by season/episode number (existing
  pattern). TVMaze numbering is generally stable, but absolute/dvd-order
  differences vs TVDB can orphan seen flags on some shows — acceptable,
  document in release notes.
- **Update-all rate limiting:** `updateAllSeries()` loops over shows; insert a
  ~600 ms delay between shows and handle HTTP 429 with backoff + retry.
- **Existing DB rows** keep their TVDB numeric `id`; add `tvmazeId` column
  (see §5). On update, if `tvmazeId` is empty, resolve once via
  `/lookup/shows?thetvdb=<id>` and backfill. New shows store the TVMaze id
  directly in `id`.

---

## 4. Movies via TMDB

### 4.1 Decision: TMDB

- **TMDB** (`https://api.themoviedb.org/3`) — free API key after account
  signup, generous quotas, complete movie metadata + posters/backdrops, JSON.
  Proven for this codebase lineage by the warren-bank fork.
- Rejected: **OMDb** (free tier only 1 000 req/day, thinner data);
  **TVMaze** (TV-only, no movies).

### 4.2 Endpoints

| Need | Endpoint |
|---|---|
| Search movies | `GET /3/search/movie?api_key={k}&query={q}` → `results[]` |
| Movie details + cast | `GET /3/movie/{id}?api_key={k}&append_to_response=credits` |
| Images | `https://image.tmdb.org/t/p/w500{poster_path}`, `/w780` or `/original` for fanart/backdrop |

### 4.3 Movie field mapping (TMDB → `Serie` + single `Episode`)

| TMDB | Stored as | Notes |
|---|---|---|
| `movie.id` | `Serie.id`, `Serie.serieId` | `String.valueOf()` |
| `movie.title` | `serieName` | |
| `movie.overview` | `overview` | plain text already |
| `movie.release_date` | `firstAired` | `YYYY-MM-DD` |
| `poster_path` → `https://image.tmdb.org/t/p/w500/...` | `poster` | |
| `backdrop_path` → `.../w780/...` | `fanart`, `banner` | |
| `vote_average` | `rating` | number → String |
| `runtime` | `runtime` | int → String |
| `genres[].name` | `genres` | |
| `credits.cast[].name` (top ~10) | `actors` | |
| `imdb_id` | `imdbId` | |
| `status` (`Released`) | `status` | |
| `tagline` | — | could append to overview; optional |
| `mediaType=1` | `series.mediaType` | marks row as movie |

The **single episode row** for a movie: `seasonNumber=1`, `episodeNumber=1`,
`episodeName = movie title`, `firstAired = release_date`, `overview` = overview,
`seen` = watched flag. This makes "mark as watched", unwatched counts
(0/1), the Log, and `nextEpisode` ("1x1") all work unmodified.

### 4.4 API key handling

- New `SharedPreferences` key `tmdb_api_key` (+ `TMDB_API_KEY_NAME` const in
  `DroidShows.java`).
- Settings UI: add an `EditText` row to `res/layout/alert_about.xml`
  (handled in `dialogOptions()`), plus a first-run prompt when the user opens
  Add Movie without a key (dialog → link to https://www.themoviedb.org/settings/api).
- Never hardcode the key. If absent, AddMovie shows an explanatory empty state.

---

## 5. Database changes

Current schema: `utils/SQLiteStore.java` `onCreate()` (~lines 1040–1075);
migrations: `utils/Update.java`, current version `0.1.5-7G3`.

### 5.1 New version `0.1.5-7G4`

In `Update.java`:
- `currentVersion = "0.1.5-7G4"`, chain `u0157G3To0157G4()` after `u0157GTo0157G3()`.
- Migration SQL:
  ```sql
  ALTER TABLE series ADD COLUMN mediaType INTEGER DEFAULT 0;
  ALTER TABLE series ADD COLUMN tvmazeId VARCHAR DEFAULT '';
  UPDATE droidseries SET version='0.1.5-7G4';
  ```
- `onCreate()`: add `mediaType INTEGER DEFAULT 0, tvmazeId VARCHAR DEFAULT ''`
  to the `series` CREATE TABLE and bump the inserted version string to
  `'0.1.5-7G4'`.
- Existing rows: `mediaType=0` (TV), `tvmazeId=''` → lazily backfilled (§3.3).

No changes to `episodes`, `actors`, `genres`, `serie_seasons`, `directors`,
`writers`, `guestStars` tables.

### 5.2 Query changes (`SQLiteStore.java`)

- `getSeries(int showArchive, boolean filterNetworks, List<String> showNetworks)`
  (~line 264) → add `int mediaType` param; append `AND mediaType=<mediaType>`
  to the WHERE clause (careful: `showArchiveString` may be null when
  `showArchive==2` — searching; keep the existing null-handling pattern).
- `getLog()` / `getLog(int offset)` (~line 314): log rows come from episodes;
  join/filter by parent `series.mediaType`.
- `createTVShowItem(String serieId)`: read `mediaType` into the item (see §6.3).
- `getNetworks()`: TV-only concept; leave as-is (filter UI hidden for movies).

---

## 6. UI restructuring: TV Shows / Movies × Watching / Finished / Log

### 6.1 Current navigation (to replace)

`DroidShows.java` `arrangeActionBar()` (~line 340): on API ≥ 11 the ActionBar
gets a **single** `Spinner` custom view with 3 entries
(`R.string.layout_app_name`="DroidShows", `R.string.archive`="Archive",
`R.string.menu_log`="Log"). Selection drives:
- `position 0` → `showArchive=0`, `logMode=false`  (Active)
- `position 1` → `showArchive=1`, `logMode=false`  (Archive)
- `position 2` → `logMode=true`                     (Log)

Pre-Honeycomb fallback uses menu items `TOGGLE_ARCHIVE_MENU_ITEM` /
`LOG_MODE_ITEM` (already `setVisible(false)` on HC+).

### 6.2 Proposed navigation (minimal-disruption)

Replace the single spinner with a **horizontal `LinearLayout` holding two
spinners** as the ActionBar custom view:

- **Media spinner:** [`TV Shows`, `Movies`] → new `public static int mediaType`
  (`0` = TV, `1` = movies). Changing it calls `getSeries()` and updates the
  empty-state add button label.
- **Mode spinner:** [`Watching`, `Finished`, `Log`] → existing `showArchive`
  (`0`/`1`) + `logMode` (bool). `Log` sets `logMode=true` (keeps `showArchive`
  untouched, as today).

Rename user-visible strings (keep the string **keys** to avoid touching all 6
locale files; change English `values/strings.xml` text, other locales fall
back or can be updated later):
- `archive` "Archive" → "Finished"; `menu_show_archive`, `menu_archive`,
  `messages_context_archived/unarchived`, `menu_unarchive` ("Recover") text
  likewise. New strings: `media_tv_shows`, `media_movies`, `mode_watching`,
  `mode_finished`, `menu_add_movie`, TMDB-key hints.

`onBackPressed()` / `onSaveInstanceState()`: persist + restore `mediaType`
alongside `showArchive`.

### 6.3 Plumbing `mediaType` through the list

- `DroidShows.series` is `List<TVShowItem>` — add `mediaType` field +
  getter/setter to `TVShowItem.java`; populate in `createTVShowItem()`.
- `getSeries()` / `getSeries(int)` / `getSeries(int, boolean)` (~line 1518):
  pass `mediaType` to `db.getSeries(...)`; title becomes e.g.
  `"DroidShows – Movies - Finished"`.
- `getLog()` path: `db.getLog()` filtered by media type.
- Every `getSeries(...)` call site (~15 in `DroidShows.java`, plus
  `AddSerie.java` duplicate-check `db.getSeries(2, false, null)`) must scope
  to the current media type. `AsyncInfo` stats refresh is per-row, media-agnostic.
- `serieSeasons(int position)` (~line 1055): **if the item is a movie, skip the
  seasons screen** and open `ViewSerie` (details) directly — a movie has one
  pseudo-episode, a seasons list would be noise.
- Context menu (`onCreateContextMenu`, ~line 880): for movies, drop
  TV-specific items if desired (keep Archive→Finished toggle, Delete, Update,
  Details; `SYNOPSIS_LANGUAGE` per-show update is meaningless for TMDB —
  hide for movies).
- `onSearchRequested()` / `ADD_SERIE_MENU_ITEM`: route to AddMovie when
  `mediaType==1` (see §6.4). `onSearchRequested()` currently returns `false`
  in logMode — keep.
- `updateAllSeries()`: update shows of the **currently visible** media type
  (matches today's current/archive scoping). TV updates via TVMaze (with
  429 backoff), movies via TMDB.
- Networks filter dialog (`filterDialog()`): TV-only; disable the networks
  section when `mediaType==1`.
- `excludeSeen`, sort, pin, swipe-to-mark-seen, undo: media-agnostic, no change.

### 6.4 Add Movie flow

- New `ui/AddMovie.java`: clone of `ui/AddSerie.java`, wired to `TMDB`
  provider instead of `TheTVDB`. Same `AsyncTask` add pattern; creates the
  single-episode movie model (§4.3); downloads poster thumb the same way
  (`addPosterThumb()`).
- Manifest: register `.ui.AddMovie` with `ACTION_SEARCH` + its own searchable
  config (`res/layout/add_movie_search.xml`, clone of `add_serie_search.xml`).
  `DroidShows` keeps `android.app.default_searchable → .ui.AddSerie`; in
  `AddSerie.getSearchResults()`, **if `mediaType==1`, forward the intent to
  `AddMovie` and `finish()`** (router pattern — avoids fragile
  `default_searchable` switching).
- Layouts: `add_movie.xml` / `row_search_movies.xml` can clone the serie ones;
  adjust titles/hints.
- TMDB key missing → `AddMovie` shows empty state prompting for key (deep-link
  to settings), `Search()` short-circuits with a toast.

### 6.5 `AddSerie` (TV) changes

- Replace `TheTVDB` with new `TVMaze` provider; drop the `language` param from
  search (TVMaze has no locales) — remove the "change language" button
  (`R.id.change_language`) or make it informational.
- Search results show `serieName` + year (`premiered`) to disambiguate.

---

## 7. New / modified files checklist

### New files
- `thetvdb/` → new package `nl.asymmetrics.droidshows.provider` (keep old
  package until cutover, then delete):
  - `provider/JsonFetcher.java` — `HttpsURLConnection` GET → `String`/`JSONObject`
    (same timeout pattern as `XMLParser`), sets `User-Agent: DroidShows/<version>`,
    throws on non-200 (surface 429 distinctly).
  - `provider/TVMaze.java` — `searchShows(q)`, `getShow(tvmazeId)`,
    `resolveTVDBId(tvdbId)`; JSON → `Serie`/`Episode` mapping per §3.1–3.2.
  - `provider/TMDB.java` — `searchMovies(q, apiKey)`, `getMovie(tmdbId, apiKey)`;
    image URL builder.
  - `provider/HtmlUtil.java` — strip HTML from TVMaze summaries (or use
    `android.text.Html.fromHtml`).
- `ui/AddMovie.java` — TMDB-backed clone of `AddSerie`.
- `res/layout/add_movie.xml`, `res/layout/add_movie_search.xml`,
  `res/layout/row_search_movies.xml` (clones, retitled).

### Modified files
- `utils/Update.java` — version `0.1.5-7G4`, `u0157G3To0157G4()`.
- `utils/SQLiteStore.java` — `onCreate` columns; `getSeries(..., mediaType)`;
  `getLog()` media filter; `createTVShowItem()` populates `TVShowItem.mediaType`.
- `thetvdb/model/TVShowItem.java` — add `mediaType` field/getter/setter.
- `thetvdb/model/Serie.java` — add transient `mediaType` (+ `tvmazeId`) for the
  add/update flows; `saveToDB()` writes them.
- `DroidShows.java` — dual-spinner action bar; `mediaType` state; all
  `getSeries`/`getLog` call sites; search routing; `updateSerie()` /
  `updateAllSeries()` provider swap + 429 backoff; `serieSeasons()` movie
  bypass; context-menu gating; `onSaveInstanceState`/`onBackPressed`;
  `TMDB_API_KEY_NAME` pref const; remove `useMirror` (keep field removal
  minimal: stop reading/writing the pref, remove checkbox).
- `ui/AddSerie.java` — TVMaze provider; remove language/mirror UI.
- `ui/SerieSeasons.java`, `ui/ViewSerie.java`, `ui/ViewEpisode.java` — audit
  for TV-only assumptions; hide season chrome when `mediaType==1`
  (small, mostly no-ops since a movie has exactly one S01E01).
- `res/values/strings.xml` — renames + new strings (§6.2).
- `res/layout/alert_about.xml` — TMDB key `EditText`; remove mirror checkbox;
  add data-source credits (TVMaze CC BY-SA, TMDB).
- `AndroidManifest.xml` — register `AddMovie` (+ searchable meta).
- Delete after cutover: `thetvdb/TheTVDB.java`, `thetvdb/utils/XMLParser.java`,
  `thetvdb/utils/XMLHandler.java`.

### Notes for implementers
- `org.json` is built-in on Android (no new dependency). Keep `minSdk 14`.
- `Serie.saveToDB()` / `Episode.saveToDB()` build raw SQL with string
  concatenation — follow the existing (ugly but working) pattern; use
  `DatabaseUtils.sqlEscapeString()` for text as the current code does.
- The update flow's seen-date preservation (save → wipe → recreate →
  re-apply by S/E number) must be kept; verify it in `DroidShows.updateSerie()`
  (~line 1050–1180) before swapping providers.
- `db.getSeries(2, ...)` = "both scopes" is used for searching/dupe checks —
  keep the `2` convention, add mediaType alongside.

---

## 8. Attribution & legal notes

- **TVMaze** data is CC BY-SA: credit "TV data by TVmaze (tvmaze.com)" with a
  link (use `show.url`) — add to About screen.
- **TMDB**: "This product uses the TMDB API but is not endorsed or certified
  by TMDB" — required wording; add to About screen alongside their logo
  guidelines if bundling artwork.

---

## 9. Suggested implementation phases

1. **Phase 1 — TV data layer:** `JsonFetcher`, `TVMaze`, field mapping,
   DB migration (`mediaType`, `tvmazeId`), swap provider in
   `AddSerie`/`updateSerie`/`updateAllSeries`. Manual test: search, add,
   update, seen-state preservation, backup/restore.
2. **Phase 2 — Movies:** `TMDB`, `AddMovie` + layouts + manifest, TMDB-key
   settings UI, single-episode movie model, add/update/delete movie manually.
3. **Phase 3 — Tabs:** dual-spinner action bar, string renames, media
   filtering in `getSeries`/`getLog`/dupe checks, search routing,
   `SerieSeasons` bypass for movies, context-menu gating.
4. **Phase 4 — Polish:** remove mirror/language remnants, About credits,
   429 backoff tuning, `./gradlew assembleDebug` build, on-device smoke test
   (needs Android SDK + device/emulator).

## 10. Open questions for O.J

1. TMDB API key: you register a free key at themoviedb.org and paste it into
   Settings — acceptable?
2. "Finished" replaces "Archive" everywhere (menus, toasts) — confirmed?
3. Existing library migration is lazy (old shows resolve TVDB→TVMaze id on
   first update, needs network once per show) — OK, or prefer a one-time
   bulk migration with progress UI?
