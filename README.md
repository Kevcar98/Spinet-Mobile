# Spinet for Android

Spinet is a music player for your own music. It plays the files on your phone,
the library on your own server, and online sources you add yourself. There are
no ads, no accounts and nothing to pay for.

It is written in Kotlin Multiplatform and Compose Multiplatform, and shares its
screens and logic with the PC app.

| Repo | What it is |
|---|---|
| Spinet-Mobile (this one) | Spinet for Android |
| [Spinet-Desktop](https://github.com/Kevcar98/Spinet-Desktop) | Spinet for Windows (macOS and Linux from source) |
| [Spinet-Server](https://github.com/Kevcar98/Spinet-Server) | The optional server you host yourself, which keeps your devices in sync |

## What it does

Spinet plays music from three places and shows them as one library:

1. Music on your phone. Pick a folder and play it offline.
2. Your own server. A library you host yourself, which you can upload to,
   browse and download from on every device you use.
3. Online sources. These come from extensions you install.

When you play a song, Spinet uses the copy closest to you: your phone first,
then your server, then online. Saving a song works the same way, so it is taken
from your server if it is already there. A song you already have on your phone
or your server is skipped instead of being saved twice.

Playlists, likes and play counts are kept on your server, so the phone and the
PC show the same thing.

## Features

- Search every source you have switched on at once, in one list.
- Filter any list by title or artist, and search your whole server library
  instead of one folder at a time.
- Keeps playing in the background, with controls on the lock screen and in the
  notification (Media3 `MediaSessionService`).
- Play a folder of your own music offline.
- Browse your server's folders as playlists, and upload, download or delete
  songs on it.
- Saved songs keep their title, artist and cover, so they show up properly in
  other players too.
- Covers and song details come from iTunes. Karaoke and tribute versions are
  ignored when looking for a match, and you can look a song up again if the
  first match was wrong.
- Playlists: create them, import them from a CSV file, drag songs to reorder
  them and pick a different version of a song.
- Shuffle, repeat all, repeat one, and a marker on the song that is playing.
- Built-in themes, and your own themes from hex colours.
- Updates from inside the app. It checks the GitHub releases, checks the
  download against its published SHA-256, and asks before installing.
- Picks up where you left off: the queue, the song, the position, shuffle and
  repeat are all kept when you close the app.

### Saving songs

Spinet can save songs from your own server, and from an extension when that
extension allows it. Each source decides whether its songs can be saved, and
the save button is hidden when they can't.

Downloading copyrighted material may break a provider's terms of service or the
law where you live. You decide what you save and keep, and that responsibility
is yours.

## Sources

Spinet comes with no online sources of its own. A search goes to every source
you have switched on and puts the results in one list:

1. Your own server, for searching, playing and browsing your library.
2. A folder on your phone, chosen in Settings.
3. Extensions you install yourself from a file or a repository you choose,
   under Settings > Sources > Extensions. None are included and none are
   suggested.

## Built with

- Kotlin Multiplatform and Compose Multiplatform (screens shared with the PC app)
- Ktor Client and kotlinx.serialization
- AndroidX Media3 (ExoPlayer and MediaSession) for playback
- Multiplatform Settings for saved settings
- A shared `ViewModel` with `StateFlow`; the Compose screens hold no logic

## Build and run

You need JDK 17 or newer (21 recommended) and the Android SDK (compileSdk 36).

```bash
# tell Gradle where your SDK is: local.properties -> sdk.dir=/path/to/Android/Sdk
./gradlew :composeApp:installDebug
```

Or open the project in Android Studio and press Run. `local.properties` is
ignored by git, so SDK paths and keys are never committed.

### Release builds

```bash
./gradlew prepareRelease
```

This builds the APK, names it after the version and writes `SHA256SUMS` next to
it in `composeApp/build/release/`. Those are the files to upload to a GitHub
release, which is where the in-app updater looks. Release signing is read from
`keystore.properties`, which git ignores (see `keystore.properties.example`).
Without it the release build stops, instead of signing with the debug key.

## Connect it to your server

1. Set up the server by following [Spinet-Server](https://github.com/Kevcar98/Spinet-Server).
   It runs with Docker Compose, and the guide covers hosting it for free on
   Oracle Cloud.
2. In the app, open Settings > My Servers and press Add a server. Enter your server's address, choose
   HTTPS or HTTP (HTTP also needs a port), add your username and password if
   you turned sign-in on, and press Save.
3. To add online sources, open Settings > Sources and install an extension.

## Project layout

```
composeApp/
  src/commonMain/   screens, view model, models, networking, settings, library
  src/androidMain/  the Android entry point, Media3 service, saving, local music
```

`commonMain` is the same code the PC app runs. Only the parts that talk to the
operating system are different.

## License

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Spinet is Free Software. You can use, study, share and change it under the
terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl-3.0.html),
version 3 or (at your option) any later version. The full text is in
[LICENSE](LICENSE).

Its libraries (Compose, Ktor, kotlinx, Coil, OkHttp and Media3) are under
Apache-2.0 or MIT, which work with GPLv3.
