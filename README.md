<p align="center">
  <img src="app/src/main/res/drawable-xxhdpi/icon.png" alt="TV Movie Tracker icon" width="128" />
</p>

<h1 align="center">TV Movie Tracker</h1>

<p align="center">
  Track every TV show and movie you watch — episodes, air dates, ratings and stats, all in one clean Android app.
</p>

<p align="center">
  <a href="https://github.com/steps2/droidshows/releases/latest"><img src="https://img.shields.io/github/v/release/steps2/droidshows?label=latest%20release" alt="Latest release" /></a>
  <a href="https://github.com/steps2/droidshows/actions/workflows/build-apk.yml"><img src="https://github.com/steps2/droidshows/actions/workflows/build-apk.yml/badge.svg" alt="CI build status" /></a>
  <img src="https://img.shields.io/github/license/steps2/droidshows" alt="License: GPL-3.0" />
</p>

---

## What it is

TV Movie Tracker is an Android app for keeping track of the TV shows and movies you watch: what's next, what aired, what you thought of it. It uses [TVMaze](https://www.tvmaze.com) for TV shows (no account or API key needed) and [TMDB](https://www.themoviedb.org) for movies. Everything you enter lives on your device — no account, no cloud.

**Requirements:** Android 5.0 (API 21) or newer. Internet access is needed to sync show info, posters and air dates.

## Features

**📚 Your library**
- TV Shows and Movies tabs, each with **Watching / Finished / History** sections
- Episodes view with watched tracking, per-season overviews and a personal episode log
- **Personal ratings** — 5 stars in half steps, shown next to the online score
- Pin favourites, quick search, filters and sorting

**🧭 Discover**
- Trending, popular and on-the-air TV shows and movies with posters — one tap adds a show to your library
- Posters load fast and re-download automatically if one goes missing

**🔔 Stay up to date**
- **New-episode notifications** — daily check plus after every sync; one quiet alert per show, tap to open it
- **"Up next" home-screen widget** — next unwatched episode per show and your unwatched film count
- **Calendar** — month view with air-date dots; tap a day to see what's on
- **Statistics** — episodes and hours watched, current and longest streaks, library counts, average personal rating

**🎬 Details that matter**
- **Where to watch** on the details screen (streaming providers in your region, via TMDB)
- Backdrops and full-size posters, IMDb quick-links
- Full **backup & restore** of your database (auto-backup included)

**✨ Made to feel right**
- Material You dynamic colours on Android 12+ (Automatic / Light / Dark), plus a pure-black AMOLED theme
- Thin, non-blocking progress bar — the app stays usable during syncs and updates, on every screen
- Swipe rows: swipe right to archive, swipe left to mark the next episode watched; swipe the tab strip to switch sections
- Library posters are stored in persistent app storage, so "Clear cache" can never wipe them
- Available in English, German, Spanish, French, Italian, Dutch and Russian

## Screenshots

| TV Shows · Watching | Discover | Statistics | Calendar |
|---|---|---|---|
| <img src="screenshots/watching-shows.png" width="180"> | <img src="screenshots/discover.png" width="180"> | <img src="screenshots/statistics.png" width="180"> | <img src="screenshots/calendar.png" width="180"> |
| Movies · Watching | Finished | History | Settings |
| <img src="screenshots/watching-movies.png" width="180"> | <img src="screenshots/finished.png" width="180"> | <img src="screenshots/history.png" width="180"> | <img src="screenshots/settings.png" width="180"> |

## Installation

Download the APK from the **[latest release](https://github.com/steps2/droidshows/releases/latest)** — it installs over any previous 1.x version.

> **Coming from the old beta builds?** The app now ships under the new package name `app.tvmovie.tracker`, so it installs *alongside* the old beta rather than over it. To move your data: in the old app use **Back up now**, install the new version, then **Restore** — everything carries over.

## Getting started

1. Install the APK and open the app.
2. To add movies and use Discover for movies, get a free API key at [themoviedb.org](https://www.themoviedb.org) and enter it under **Settings → TMDB API key**. TV shows work with no key at all.
3. Tap **+** → **Add show / Add movie**, or browse **Discover** and tap anything to add it.
4. Tap **+** → **Sync** to update air dates and episode info; the **calendar** and **notifications** take it from there.

## Privacy

- No account, no analytics, no ads.
- Your library, ratings and history stay on your device (and in your own backups).
- Your TMDB API key is stored only on your device and is sent only to TMDB's API.

## Credits

Forked from [DroidShows](https://github.com/ltGuillaume/DroidShows) by ltGuillaume, which is a reboot of [DroidSeries](https://github.com/asymmetrics/droidshows) (Carlos Limpinho / Paulo Cabido). This fork replaces the data backends (TVMaze for TV, TMDB for movies), adds a movies library, and ships a full redesign with notifications, widget, calendar, statistics and personal ratings.

## License

[GNU General Public License v3.0](LICENSE.md)
