[![CI](https://github.com/EricCai00/mbrc/actions/workflows/flow.yml/badge.svg)](https://github.com/EricCai00/mbrc/actions/workflows/flow.yml)
[![License: GPL v3](https://img.shields.io/github/license/EricCai00/mbrc.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/EricCai00/mbrc)](https://github.com/EricCai00/mbrc/releases)

<p align="center">
  <a href="https://github.com/EricCai00/mbrc">
    <img src="logo.png" alt="StreamBee logo" width="88" height="88" />
  </a>
</p>

<h1 align="center">StreamBee</h1>

<p align="center">
  A modern Android companion for MusicBee: control playback on your computer or stream your
  MusicBee library directly to your phone.
  <br />
  <a href="https://github.com/EricCai00/mbrc/releases"><strong>Download the Android app</strong></a>
  ·
  <a href="https://github.com/EricCai00/mbrc-plugin"><strong>Get the MusicBee plugin</strong></a>
  ·
  <a href="https://github.com/EricCai00/mbrc/issues">Report an issue</a>
</p>

StreamBee is a community fork of
[MusicBee Remote](https://github.com/musicbeeremote/mbrc). It keeps the full remote-control
experience and adds a phone-first playback path, richer library navigation, and a paired
[StreamBee plugin](https://github.com/EricCai00/mbrc-plugin) for MusicBee on Windows.

## Features

- Control MusicBee playback, volume, output, ratings, shuffle, repeat, and the now-playing queue.
- Browse artists, albums, tracks, playlists, genres, and MusicBee Genre Categories.
- Drag an indexed scrollbar through large libraries by letter or year.
- Stream files from the MusicBee library and playlists to Android with seeking and Media3 playback.
- Start an album, artist, playlist, or the full library from any selected track.
- Restore large local queues, the paused track, playback position, shuffle, and repeat after restart.
- Display plain or synchronized LRC lyrics, follow the active line, and tap a line to seek.
- Fetch lyrics exposed by MusicBee and supported MusicBee lyrics-provider plugins.
- Keep an on-device playback history and report completed plays to MusicBee, with optional Last.fm
  scrobbling through MusicBee's configured account.
- Load artwork progressively for the library, player, media session, and Android notification.
- Use a privacy-focused GitHub build without Firebase, Crashlytics, or analytics.

## StreamBee 2.1.0 highlights

- Adds on-device playback history in place of the old Radio drawer entry.
- Adds MusicBee Genre Category browsing and indexed fast scrolling for large libraries.
- Adds synchronized lyric parsing, active-line following, and tap-to-seek.
- Makes local playback queues scalable and safely restorable from an atomic on-disk format.
- Records qualifying phone playback in MusicBee and optionally scrobbles it to Last.fm.
- Streams files referenced only by MusicBee playlists, not just files already in the library.
- Improves local-player queue actions, notification artwork, playlist playback, and stream format
  compatibility.

## Requirements

- Android 6.0 (API 23) or newer.
- MusicBee 3.1 or newer on Windows.
- The [StreamBee MusicBee plugin](https://github.com/EricCai00/mbrc-plugin).
- The phone and computer must be on the same trusted local network without client/AP isolation.

## Installation

### Android app

Download `StreamBee-vX.Y.Z.apk` from
[GitHub Releases](https://github.com/EricCai00/mbrc/releases) and open it on the Android device.

Official StreamBee APKs use the package name `com.kelsos.mbrc` and a dedicated release signing key.
Development builds use `com.kelsos.mbrc.dev`, so a release and a development build can be installed
side by side with separate data. An old debug-signed build that used `com.kelsos.mbrc` must be
uninstalled before the first official StreamBee release can be installed.

### MusicBee plugin

Install the matching plugin from the
[StreamBee plugin repository](https://github.com/EricCai00/mbrc-plugin), restart MusicBee, and verify
that it appears under **Edit > Preferences > Plugins**.

Create or select a connection in StreamBee using the computer's LAN address and the command port
configured by the plugin. Network discovery can also find compatible plugin instances automatically.

## On-device playback

Open a track's overflow menu and choose **Play on this device**. StreamBee queues the surrounding
album, artist, playlist, or library context and asks the plugin to serve the original file over HTTP.
The command service uses the configured MusicBee Remote port; the audio service uses the next port
(for example, command port `3000` and audio port `3001`). Both ports must be reachable from the
phone.

The audio endpoint supports `GET`, `HEAD`, and single byte-range requests for seeking. It only
exposes paths found in the current MusicBee library or one of its playlists and applies the plugin's
client-address filter. The endpoint is intended for a trusted LAN and is not encrypted or separately
authenticated: **do not port-forward either StreamBee port to the internet**.

## Development

Clone this fork and open it in Android Studio:

```bash
git clone https://github.com/EricCai00/mbrc.git
cd mbrc
```

The project uses Kotlin, Jetpack Compose, Coroutines/Flow, Koin, Room, Paging 3, Media3, Coil,
DataStore, and Glance.

### Build variants

| Variant | Package | Purpose |
| --- | --- | --- |
| `githubDebug` | `com.kelsos.mbrc.dev` | Normal USB development without Firebase |
| `playDebug` | `com.kelsos.mbrc.dev` | USB development with Google services |
| `githubRelease` | `com.kelsos.mbrc` | Signed GitHub APK |
| `playRelease` | `com.kelsos.mbrc` | Signed Play build |

Use `githubDebug` for day-to-day USB debugging. Release builds must be signed with the same private
release key as earlier StreamBee releases; never commit signing credentials to Git.

### Common commands

```bash
# Build and test
./gradlew build
./gradlew test

# Static analysis and local verification
./gradlew staticAnalysis
./gradlew verifyLocal

# Build the privacy-focused APK
./gradlew :app:assembleGithubRelease

# Formatting
./gradlew formatKotlin
./gradlew lintKotlin
```

### Screenshot tests

```bash
./gradlew updateGithubDebugScreenshotTest
./gradlew validateGithubDebugScreenshotTest
```

## Contributing

Issues and pull requests are welcome. Please keep changes focused, add tests for behavior changes,
and run the relevant Gradle checks before submitting. See [CONTRIBUTING.md](CONTRIBUTING.md) and
[CHANGELOG.md](CHANGELOG.md) for more information.

## License and upstream

StreamBee remains licensed under [GPLv3](LICENSE). This fork builds on the long-running MusicBee
Remote project created by Konstantinos Paparas and its contributors. Original copyright and license
notices are retained in the source tree.

MusicBee is a separate product and is not affiliated with this repository.
