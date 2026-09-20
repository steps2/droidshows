<p align="center">
  <img src="screenshots/app-icon.png" width="120" alt="TV Movie Tracker icon">
</p>

<h1 align="center">TV Movie Tracker</h1>

<p align="center">Track every TV show and movie you watch — episodes, films, ratings and what's on next.</p>

<p align="center">
  <a href="https://github.com/steps2/tvmovietracker/releases/latest"><img src="https://img.shields.io/github/v/release/steps2/tvmovietracker" alt="Latest release"></a>
  <img src="https://github.com/steps2/tvmovietracker/actions/workflows/build-apk.yml/badge.svg" alt="Build status">
  <img src="https://img.shields.io/github/license/steps2/tvmovietracker" alt="GPL-3.0 licence">
</p>

## Screenshots

| Watching · TV shows | Discover | Statistics | Calendar |
|---|---|---|---|
| <img src="screenshots/watching-shows.png" width="180" alt="Watching tab"> | <img src="screenshots/discover.png" width="180" alt="Discover"> | <img src="screenshots/statistics.png" width="180" alt="Statistics"> | <img src="screenshots/calendar.png" width="180" alt="Calendar"> |
| Watching · Movies | Finished | History | Settings |
| <img src="screenshots/watching-movies.png" width="180" alt="Movies watching tab"> | <img src="screenshots/finished.png" width="180" alt="Finished tab"> | <img src="screenshots/history.png" width="180" alt="History tab"> | <img src="screenshots/settings.png" width="180" alt="Settings"> |

## Features

- **TV shows and movies, side by side** — each with Watching, Finished and History tabs
- **Episode tracking** — mark episodes watched, see what's next and when it airs
- **Discover** — browse trending and popular shows and films, add them with a tap
- **Search** — find anything in your library, in every tab
- **New-episode notifications** — get told when a show you follow airs a new episode
- **Up Next widget** — next unwatched episode per show, right on your home screen
- **Where to watch** — streaming and purchase options for films (via TMDB)
- **Statistics** — episodes and hours watched, watch streaks, library counts, average personal rating
- **Calendar** — month view with air-date dots; tap a day to see what's on
- **Personal ratings** — rate shows and films in half stars, next to the online score
- **Swipe actions** — swipe right to archive, swipe left to mark the next episode watched
- **Sync** — refresh your whole library from TVMaze and TMDB with one tap
- **Thin progress bar** — syncs and searches never block the app; it stays usable throughout
- **Backup and restore** — your whole library, including posters, in one file
- **Posters that survive** — library posters live in persistent storage, so "Clear cache" never wipes them
- **Themes** — Automatic, Light and Dark with Material You dynamic colours, plus pure-black AMOLED
- **Seven languages** — English, German, Spanish, French, Italian, Dutch and Russian

## Installation

Requires Android 5.0 (API 21) or newer.

Download the APK from the **[latest release](https://github.com/steps2/tvmovietracker/releases/latest)** — it installs over any previous 1.x version.

> **Coming from the old beta builds?** The app now ships under the new package name `app.tvmovie.tracker`, so it installs *alongside* the old beta rather than over it. To move your data: in the old app use **Back up now**, install the new version, then **Restore** — everything carries over.

## Getting started

- **TV shows** work out of the box — episode data comes from TVMaze, no key needed.
- **Movies** need your own TMDB API key: paste it into **Settings** once and the Movies section, Discover films and where-to-watch all light up.

## Privacy

No account, no analytics, no ads. Your library lives on your device and in your own backup file. The only network traffic is episode and film data from TVMaze and TMDB.

## Credits

- Episode data: [TVMaze](https://www.tvmaze.com)
- Film data and artwork: [TMDB](https://www.themoviedb.org) — this product uses the TMDB API but is not endorsed or certified by TMDB
- Originally based on [DroidShows](https://github.com/ItzNotABug/droidshows)

## Licence

GNU General Public Licence v3.0 — see [LICENSE.md](LICENSE.md).
