[![CI](https://github.com/EricCai00/streambee/actions/workflows/flow.yml/badge.svg)](https://github.com/EricCai00/streambee/actions/workflows/flow.yml)
[![License: GPL v3](https://img.shields.io/github/license/EricCai00/streambee.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/EricCai00/streambee)](https://github.com/EricCai00/streambee/releases)

<p align="center">
  <a href="https://github.com/EricCai00/streambee">
    <img src="logo.png" alt="StreamBee logo" width="88" height="88" />
  </a>
</p>

<h1 align="center">StreamBee</h1>

<p align="center">
  A modern Android companion for MusicBee: stream your MusicBee library directly to your phone.
  <br />
  <a href="https://github.com/EricCai00/streambee/releases"><strong>Download the Android app</strong></a>
  ·
  <a href="https://github.com/EricCai00/streambee-plugin"><strong>Get the MusicBee plugin</strong></a>
  ·
  <a href="https://github.com/EricCai00/streambee/issues">Report an issue</a>
</p>

StreamBee is a community fork of
[MusicBee Remote](https://github.com/musicbeeremote/mbrc). It streams audio from your MusicBee
library to your phone, with richer library navigation, detailed track information, on-device
playback history and lyrics, and a paired
[StreamBee plugin](https://github.com/EricCai00/streambee-plugin) for MusicBee on Windows.

## Features

- Stream MusicBee library and playlist files to your phone with seeking, local queues, and phone
  playback controls.
- Browse artists, albums, tracks, playlists, genres, and MusicBee Genre Categories.
- See MusicBee artist pictures, album year/genre links, and read-only loved-track markers while browsing.
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

## StreamBee 2.3.0 highlights

- Add a track to an existing MusicBee playlist from library, playlist, queue, or player menus.
- Toggle the player heart to synchronize the exact track's loved state with MusicBee.
- Read the bundled offline Help guide and reopen What's New from Help.

## Requirements

- Android 6.0 (API 23) or newer.
- MusicBee 3.1 or newer on Windows.
- The [StreamBee MusicBee plugin](https://github.com/EricCai00/streambee-plugin).
- The phone and computer must be on the same trusted local network without client/AP isolation.

## Installation

### Android app

Download `StreamBee_X.Y.Z.apk` from
[GitHub Releases](https://github.com/EricCai00/streambee/releases) and open it on the Android device.

Official StreamBee APKs use the package name `com.ericcai.streambee` and a dedicated release signing key.
Development builds can retain this package for in-place updates with a `-devN` version suffix.
Use the `.dev` package only for an explicitly requested side-by-side installation. Builds using the previous `com.kelsos.mbrc` package are treated as a
separate app and can be removed after any data or settings that are still needed have been migrated.

### MusicBee plugin

Install the matching plugin from the
[StreamBee plugin repository](https://github.com/EricCai00/streambee-plugin), restart MusicBee, and verify
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
git clone https://github.com/EricCai00/streambee.git
cd mbrc
```

The project uses Kotlin, Jetpack Compose, Coroutines/Flow, Koin, Room, Paging 3, Media3, Coil,
DataStore, and Glance.

### Build variants

| Variant | Package | Purpose |
| --- | --- | --- |
| `githubDebug` | `com.ericcai.streambee.dev` | Explicit side-by-side debugging without Firebase |
| `playDebug` | `com.ericcai.streambee.dev` | USB development with Google services |
| `githubRelease` | `com.ericcai.streambee` | Signed GitHub APK |
| `playRelease` | `com.ericcai.streambee` | Signed Play build |

Use signed `githubRelease` development builds for in-place USB updates. Release builds must be signed with the same private
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
./gradlew :app:assembleGithubRelease -x :app:uploadCrashlyticsMappingFileGithubRelease

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
