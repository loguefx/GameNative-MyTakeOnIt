# Step-by-step: Debugging the app in Android Studio

This guide covers how to debug the GameNative app in Android Studio and how to track down the two known issues: **friend list crash** and **Wine prefix / launch blocked** popup.

---

## 1. Open the project and run the app

1. Open **Android Studio**.
2. **File → Open** and select the project folder: `GameNative-MyTakeOnIt`.
3. Wait for Gradle sync to finish (bottom status bar).
4. Connect an Android device (USB debugging enabled) or start an emulator.
5. In the toolbar, choose your **run configuration** (e.g. “app”) and click **Run** (green play) or press **Shift+F10**.

The app should install and launch on the device/emulator.

---

## 2. Capture crash logs (Logcat)

When the app crashes, the stack trace appears in **Logcat**.

1. In Android Studio, open **View → Tool Windows → Logcat** (or the **Logcat** tab at the bottom).
2. In the Logcat toolbar:
   - **Device**: select your device/emulator.
   - **App**: choose **“app.gamenative”** (or your app’s package name) so only your app’s logs are shown.
   - **Log level**: set to **Verbose** or **Debug** to see more detail; for crashes, **Error** is enough.
3. Reproduce the crash (e.g. tap a friend in the friends list).
4. In Logcat, look for:
   - **FATAL EXCEPTION** – the line that starts the crash.
   - The **stack trace** right below it (lines with `at app.gamenative...` or `at androidx...`).

The top lines of the stack trace are the most important: they show the exact class and line where the crash happened. Use that to set breakpoints (see below).

---

## 3. Debug the “friend list crash” (tap on a friend)

### 3.1 Where the tap is handled

- **Friends list UI**: `app/src/main/java/app/gamenative/ui/screen/friends/FriendsScreen.kt`  
  - Search for `onFriendClick`; the tap is `onFriendClick(friend.steamId)`.
- **Navigation**: `app/src/main/java/app/gamenative/ui/PluviaMain.kt`  
  - Friend tap goes to **profile**: `PluviaScreen.PlayerProfile.route(steamId)` (search for `onChat` / `PlayerProfile`).
- **Profile / chat screen**: `app/src/main/java/app/gamenative/ui/screen/chat/FriendDetailScreen.kt`  
  - Used for both `profile/{id}` and `chat/{id}`; it takes `steamId`, `isProfileOnly`, etc.

### 3.2 Set breakpoints

1. In the **Project** view, open **FriendsScreen.kt** and find the line with `onFriendClick(friend.steamId)` (in the click handler of a friend item).
2. Click in the **gutter** (left of the line number) to set a **breakpoint** (red dot).
3. Open **PluviaMain.kt**, find the composable for `PluviaScreen.PlayerProfile` (search for `PlayerProfile.route`). Set a breakpoint on the line that gets `id` from arguments:  
   `val id = backStackEntry.arguments?.getString(PluviaScreen.PlayerProfile.ARG_ID)?.toLongOrNull() ?: 0L`
4. Open **FriendDetailScreen.kt**. Set a breakpoint on the first line of the composable (the `FriendDetailScreen(` function).

### 3.3 Run in debug mode

1. Click **Debug** (bug icon) instead of Run, or press **Shift+F9**.
2. When the app is running, go to the **friends list** and **tap a friend**.
3. Execution should stop at your first breakpoint. Use **F8** (Step Over) and **F7** (Step Into) to move through the code.
4. In the **Variables** view, check:
   - In FriendsScreen: `friend.steamId` (is it a valid long?).
   - In PluviaMain: `backStackEntry.arguments`, `id` (is `id` correct or 0?).
   - In FriendDetailScreen: `steamId`, `isProfileOnly` (is `steamId` 0 when it shouldn’t be?).

If the app crashes before hitting a breakpoint, look at the **Logcat** stack trace: the top “at app.gamenative...” line is where to put your next breakpoint.

### 3.4 Common causes for this crash

- **Nav argument missing or wrong**: `profile/{id}` must have argument name `"id"` (see `PluviaScreen.kt`: `ARG_ID = "id"`). If the route is built incorrectly or the argument is not passed, `id` can be 0 and later code may assume a valid steam ID.
- **Null or invalid data**: In `FriendDetailScreen`, `friend` can be null until loaded; the UI should handle that. A crash might happen if something (e.g. ViewModel or a composable) assumes `friend` or `steamId` is always valid.
- **ViewModel / Hilt**: If the crash is in Hilt or ViewModel creation, the stack trace will point to that; then check `FriendDetailViewModel` and how it’s scoped to the navigation.

Use the stack trace from Logcat to see the exact line and object that caused the crash, then add breakpoints there and inspect variables.

---

## 4. Debug the “Launch blocked – Wine prefix missing or invalid” popup

### 4.1 Where the popup comes from

- The message is defined in:  
  **`app/src/main/java/app/gamenative/preflight/PreflightRunner.kt`**
- Method: **`checkWinePrefix`** (around lines 49–67).
- Preflight is run from: **`app/src/main/java/app/gamenative/launch/LaunchOrchestrator.kt`** (`runPreflight`), which is called from **PluviaMain.kt** when you start a game (before the game process is launched).

So when you “install a game and try to load it”, the app runs preflight checks; if the **wine prefix directory** is considered missing or invalid, it shows that popup and does not start the game.

### 4.2 What the check does (and the bug)

- **Intended behavior**: The check should verify the **container’s** Wine prefix (the directory that actually will be used when running the game).
- **Bug**: The code was using **`ImageFs.find(context).wineprefix`**, which is the **default** imagefs path (e.g. `context.getFilesDir()/imagefs/home/xuser/.wine`), and **ignoring** the `prefixPath` argument.
- When you launch a game, the app passes **`prefixPath = container.rootDir.absolutePath`** (the container’s root). The real Wine prefix for that container is **inside** that root (e.g. `prefixPath + "/home/xuser/.wine"`). If the app uses a different container or path, the default path may not exist or may be empty, so the check fails and you see “Wine prefix is missing or invalid… Re-create the container or run container setup.”

The fix is to make **`checkWinePrefix`** use the **`prefixPath`** passed by the caller (the container root) and check the wine prefix directory **under that path** (e.g. `prefixPath + "/home/xuser/.wine"`). That fix is applied in the codebase; see `PreflightRunner.kt`.

### 4.2 Capturing the debug log (path check)

When debugging the wine prefix path, the app logs one JSON line to **logcat** with tag `DEBUG_6f24d2`. You don’t need `run-as` or a debuggable build.

1. Reproduce the issue (launch a game until “launch blocked” appears).
2. In a terminal where `adb` is available, run:
   ```bash
   adb logcat -d -s DEBUG_6f24d2
   ```
   **If `adb` is not recognized:** Your SDK path is in **`local.properties`** as `sdk.dir`. Use the full path to `adb` (PowerShell):
   ```powershell
   & "C:\Users\logue\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -d -s DEBUG_6f24d2
   ```
   If you see **"more than one device/emulator"**, add `-d` (physical device) or `-e` (emulator): put `-d` or `-e` right after `adb.exe"`, e.g. `...adb.exe" -d logcat -d -s DEBUG_6f24d2`. Or run `adb devices` and use `-s SERIAL` to pick one.
   If your SDK is elsewhere, replace the path with your `sdk.dir` from `local.properties` (use single backslashes), e.g. `& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -s DEBUG_6f24d2`.
   Or to save to a file, add `> debug_logcat.txt` at the end.
3. Copy the line that looks like `D DEBUG_6f24d2: {"sessionId":"6f24d2",...}` and share it.

If you use **run-as** (debuggable build only), use the full path:
```bash
adb shell run-as app.gamenative cat /data/data/app.gamenative/files/debug_6f24d2.log
```
If you see “Package is not debuggable”, use the logcat command above.

### 4.3 How to confirm in the debugger

1. Set a breakpoint in **PreflightRunner.kt** in **`checkWinePrefix`** on the line that creates `winePrefixDir` (or the first line of the method).
2. Start a game from the app (so preflight runs).
3. When the breakpoint hits, in the **Variables** view check:
   - **`prefixPath`**: should be the container root (e.g. a path under your app files that corresponds to the selected container).
   - **`winePrefixDir`**: should be the directory that is actually used as WINEPREFIX for that container (e.g. `prefixPath + "/home/xuser/.wine"`).
4. Step over and see whether `winePrefixDir.isDirectory` is true or false. If the code now uses `prefixPath` to build `winePrefixDir`, it should be true when the container is set up correctly.

After the fix, the popup should only appear when the **actual** container prefix directory is missing or invalid (e.g. container not set up yet). In that case, the user should run **container setup** or create/recreate the container as indicated in the message.

### 4.4 "Proton/Wine runtime not found" (imagefs/opt missing)

If the block message says the runtime is not found at `.../imagefs/opt/wine` and logs show **`optDirExists: false`**, the **system image (including Wine/Proton) has not been installed**. The `imagefs/opt` directory does not exist, so there is no compatibility layer to run.

**How to fix:** Install the system image before launching a game.

1. Open the app and go to the **game** (e.g. open the game’s detail screen from the library).
2. Use the game’s **container / component settings** (e.g. menu or “Edit container”).
3. When the app prompts to **install system files** or **download/install imagefs**, confirm (e.g. **Proceed**). Wait for the install to finish.
4. Then launch the game again.

Alternatively, open the **XServer / Component** screen for the container used by the game; that flow also runs the system image install if needed. After the install completes, `imagefs/opt` will exist and the Wine runtime check should pass.

---

## 5. Game launch crash (download worked, then crash on start)

When the game looks like it’s about to start then crashes (e.g. after downloading Proton 10), logs are in **Logcat**.

### 5.1 Open and filter Logcat

1. **View → Tool Windows → Logcat** (or the **Logcat** tab at the bottom).
2. In the Logcat toolbar:
   - **Device**: your device/emulator.
   - **App**: **“No Filters”** or **“app.gamenative”** — try both (see below).
   - **Log level**: **Verbose** or **Debug** to see everything; **Error** for crash-only.
3. **Reproduce**: Download/select Proton, save, launch the game so it crashes again.
4. Use the **search** box in Logcat to search for: `FATAL`, `Exception`, `crash`, `AndroidRuntime`, `Timber`, or your app package.

### 5.2 What to look for

- **FATAL EXCEPTION** — Java/Kotlin crash in the app process. The next lines are the stack trace; the **top** `at app.gamenative...` (or `at com.winlator...`) line is usually where to start.
- **Timber** — App logs often use `Timber`; search for `Timber` or tags like `XServerDisplayActivity`, `LaunchOrchestrator`, `PreflightRunner`, `WineInfo`, `ContentsManager`.
- If the crash is in a **child process** (game/Wine), that process may have a different name. Leave **App** as **“No Filters”** and search for the game name or `Wine`/`proton` in the log, or look for a second **FATAL EXCEPTION** block.

### 5.3 Save logs to a file

- In Logcat, use the **Export** (disk) icon or **right‑click → Export** to save the current buffer to a file.
- Or from a terminal (with device connected):
  ```bash
  adb logcat -d > logcat_full.txt
  ```
  Then search the file for `FATAL`, `Exception`, or your app package. If `adb` isn’t in PATH, use the full path from `local.properties` (e.g. `& "C:\Users\...\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -d > logcat_full.txt` in PowerShell).

### 5.4 Clear and recapture (optional)

To avoid old logs: in Logcat click **Clear logcat** (trash icon), then reproduce the crash and export or copy the stack trace so you only have the relevant crash.

### 5.5 Filter by **GameLaunch** tag (recommended)

The app logs the entire launch flow under a single tag so you can see why a launch failed.

1. In Logcat, in the **search/filter** box, type: **`GameLaunch`** (or use the tag filter if your Logcat supports filtering by tag).
2. **Reproduce**: Tap Play on the game so it crashes (or blocks).
3. You should see, in order (earliest first):
   - **Play tapped (Library), appId=…** — user tapped Play from the library game detail (this path calls preLaunchApp directly).
   - Or **Emitting ExternalGameLaunch appId=…** → **AndroidEvent.ExternalGameLaunch received** → **Received ExternalGameLaunch UI event** — alternative path (e.g. custom game or intent launch).
   - **preLaunchApp called appId=…** — preLaunchApp was entered (main thread); if you see this but nothing after, the failure is before the IO coroutine runs or during gameId extraction.
   - **preLaunchApp started appId=…** — launch coroutine began (runs on background thread).
   - **runPreflight appId=…** — preflight started (prefix path, game binary path).
   - Either **Preflight passed** or **Preflight blocked: &lt;reason&gt;** — if blocked, the dialog message matches this reason.
   - **launchApp started appId=…** — after preflight, container/Steam API setup.
   - **getOrCreateContainer done wineVersion=…** — container and Wine version in use.
   - **launchApp emitting LaunchApp** — app is about to navigate to XServer/game screen.
   - **---- Launching Container ----** and **Guest Executable: …** — XServer is starting the game process.
   - **Starting game environment** — environment started.
   - If the **game/Wine process** exits with an error: **Guest program terminated with exit status: &lt;code&gt;** — this is the crash; the exit code may indicate signal (e.g. 139 = SIGSEGV) or application exit code.

If you see **preLaunchApp failed** or **launchApp failed** with a stack trace in the same tag, that’s a Java/Kotlin exception in the app (e.g. missing file, NPE). Use that stack trace to set breakpoints. If you see **Guest program terminated with exit status**, the crash is in the **native** game/Wine process; the app cannot log a Java stack trace for that—check the exit code and any native crash dumps (e.g. tombstones) if needed.

**If you see no GameLaunch lines at all** when you tap Play, the launch path is not being hit. Ensure you (1) built and installed the app that includes the GameLaunch logging, (2) are tapping Play from the **game’s detail screen** in the Library (not from the main library list), and (3) have Logcat set to show your app (e.g. filter by **app.gamenative**) or **No Filters** and search for `GameLaunch` after reproducing. If still nothing, the tap may be going to a different code path (e.g. a different store or screen); check that you’re on the Library tab and have opened a game then tapped Play.

### 5.6 `libproot.so` "No such file or directory" (error=2)

If Logcat shows **`execInContainerWithOutput failed: Cannot run program ".../lib/arm64/libproot.so": error=2, No such file or directory`**, or a **GameLaunch** log **`libproot.so missing or not executable at ...`**, the **proot** native binary is missing from the app. The game runs inside a proot sandbox; without it, launch fails immediately.

**Cause:** The app’s native build (CMake) was disabled or the proot subdirectory was not included, so `libproot.so` and `libproot-loader.so` were never built or packaged.

**Fix:** Rebuild the app with proot enabled:

1. In **`app/src/main/cpp/CMakeLists.txt`**, ensure **`add_subdirectory(proot)`** is present (not commented out).
2. In **`app/build.gradle.kts`**, ensure **`externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") ... } }`** is present (not commented out).
3. **Build → Rebuild Project** (or **Build → Make Project**), then run the app on the device again.

After a full rebuild, the APK will include `libproot.so` and `libproot-loader.so` in `lib/arm64-v8a/` (and `lib/armeabi-v7a/` if built). If you still see the error, confirm in Logcat that **`nativeLibraryDir` contains: [...]** includes `libproot.so` and `libproot-loader.so`.

### 5.7 Native build fails on Windows (ProcessException / ninja "Entering directory 'D'")

If the build fails with **`:app:buildCMakeDebug[arm64-v8a]`** and **`ProcessException: ninja: Entering directory 'D'`** (or similar), the CMake/Ninja native build is failing on Windows. Often the working directory or path is truncated (e.g. only the drive letter `D` is used) due to path length or backslash handling.

**Try in order:**

1. **Clean and rebuild**
   - **Build → Clean Project**, then **Build → Rebuild Project**.
   - If the project was in a bad state, this can fix it.

2. **Shorter project path**
   - Long paths (e.g. `D:\Gitlab\GameNative-MyTakeOnIt\app\.cxx\Debug\...`) can hit Windows limits or get mangled.
   - **Option A:** Move or clone the repo to a short path, e.g. `D:\GN` or `C:\dev\gamenative`, then open that folder in Android Studio and rebuild.
   - **Option B:** Use a virtual drive: in **Command Prompt** (as admin if needed) run:
     ```bat
     subst Z: D:\Gitlab\GameNative-MyTakeOnIt
     ```
     Then in Android Studio **File → Open** and open **Z:\**. Build from there. (Remove with `subst Z: /D` when done.)

3. **Invalidate caches**
   - **File → Invalidate Caches… → Invalidate and Restart**. After restart, **Build → Rebuild Project**.

4. **SDK/NDK**
   - In **File → Project Structure → SDK Location**, confirm **Android SDK** and **NDK** paths are valid and contain no spaces or odd characters if possible.
   - In **Tools → SDK Manager**, ensure **CMake** (e.g. 3.22.1) and **NDK** are installed and match `app/build.gradle.kts` (`ndkVersion`).

If the error persists, copy the **full** Build Output (including the first few lines of the stack trace under **Caused by**) so the exact command and path can be checked.

---

## 6. Summary

| Goal | Where to look | What to do |
|------|----------------|------------|
| **See any crash** | Logcat (filter by app, look for FATAL EXCEPTION) | Reproduce crash → copy stack trace → set breakpoint at top line |
| **Friend tap crash** | FriendsScreen → PluviaMain (profile route) → FriendDetailScreen | Breakpoints on friend click, nav argument `id`, and FriendDetailScreen entry; inspect `steamId` and `id` |
| **Wine prefix popup** | PreflightRunner.checkWinePrefix, LaunchOrchestrator, PluviaMain (launch flow) | Fix: preflight must use `prefixPath` (container root) to check the wine prefix directory; then debug with breakpoint in `checkWinePrefix` to confirm path |
| **Game launch crash** | Logcat: filter by tag **GameLaunch** or search FATAL/Exception | Use **GameLaunch** tag to see full launch flow and preLaunchApp/launchApp/guest termination errors; for Java crashes use stack trace; for native (Wine/game) use “Guest program terminated with exit status” |

After applying the PreflightRunner fix, run the app again, install/select a game, and try to launch it. If the container is set up and the prefix exists under the container root, the “Wine prefix is missing or invalid” popup should no longer appear. If it still does, use the breakpoint in `checkWinePrefix` to see the exact `prefixPath` and `winePrefixDir` values and whether the directory exists on disk.
