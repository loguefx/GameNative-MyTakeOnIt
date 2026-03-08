# GameNative

Play games you own on **Steam**, **Epic**, and **GOG** directly on Android—with cloud saves and a dedicated gaming UI.

This fork builds on [Pluvia](https://github.com/oxters168/Pluvia) (Steam client for Android) and [GameNative](https://github.com/utkarshdalal/GameNative) with an updated look and extra features.

---

## New in this fork

- **GameNative UI** — Dark theme with a Steam-style blue accent (`#1E9FFF`), layered surfaces, and a clear design system. No generic Material purple; everything is tuned for a gaming library and in-game use.
- **Multi-store** — One app for Steam, Epic, and GOG: library, installs, downloads, and play from a single place.
- **XServer integration** — Built-in XServer support for running and displaying games with proper input and display handling.
- **Cloud saves** — Sync saves across devices for supported games (Steam Cloud, Epic, etc.).
- **Custom games** — Add non-store titles to your library; optional [SteamGridDB](https://www.steamgriddb.com/) API key for automatic cover art.
- **Release builds** — Signed release APKs are built on push to `main` and published to [Releases](https://github.com/loguefx/GameNative-MyTakeOnIt/releases/latest); the “Download the latest” link below always points to the newest build.

*Add a screenshot of your device running the app here to show off the new UI (e.g. library, in-game, or settings).*

---

## How to Use

(Note that GameNative is still in its early stages, and all games may not work, or may require tweaking to get working well)
1. Download the latest release [here](https://github.com/loguefx/GameNative-MyTakeOnIt/releases/latest/download/gamenative-release.apk)
2. Install the APK on your Android device
3. Login to your Steam account
4. Install your game
5. Hit play and enjoy!

## Support
To report issues or receive support, join the [Discord server](https://discord.gg/2hKv4VfZfE)

Do not create issues on GitHub as they will be automatically closed!

You can support GameNative on Ko-fi at https://ko-fi.com/gamenative

## Building
### IF YOU JUST WANT TO USE THE APP, PLEASE SEE THE HOW TO USE SECTION ABOVE. THIS IS ONLY NEEDED IF YOU WANT TO CONTRIBUTE FOR DEVELOPMENT.
1. **First-time setup:** Create `local.properties` in the project root with your Android SDK path (required for Gradle):
   ```
   sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
   ```
   On Windows use double backslashes. On macOS/Linux use forward slashes, e.g. `sdk.dir=/Users/you/Library/Android/sdk`.
2. Build the debug APK from the project root:
   ```
   ./gradlew assembleDebug
   ```
   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
3. I use a normal build in Android studio. Hit me up if you can't figure out how to build.
4. **SteamGridDB API Key (Optional):** To enable automatic fetching of game images for Custom Games, add your SteamGridDB API key to `local.properties`:
   ```
   STEAMGRIDDB_API_KEY=your_api_key_here
   ```
   Get your API key from: https://www.steamgriddb.com/profile/preferences
   If the API key is not configured, the app will log a message but continue to work normally without fetching images.

**GitHub Actions (APK builds):** Workflows build a signed release APK on push to `main` and publish it to [Releases](https://github.com/loguefx/GameNative-MyTakeOnIt/releases/latest). For CI, configure repository **Secrets** (keystores, POSTHOG/SUPABASE, etc.). Bundletool is downloaded automatically. You can also download the APK from the run’s **Artifacts** or the nightly.link URL in the run summary.

## Community

Join our [Discord server](https://discord.gg/2hKv4VfZfE) for support and updates.

## License
[GPL 3.0](https://github.com/loguefx/GameNative-MyTakeOnIt/blob/main/LICENSE)

## Privacy Policy
[Privacy Policy](https://github.com/loguefx/GameNative-MyTakeOnIt/blob/main/PrivacyPolicy/README.md)

**Disclaimer: This software is intended for playing games that you legally own. Do not use this software for piracy or any other illegal purposes. The maintainer of this fork assumes no
responsibility for misuse.**
