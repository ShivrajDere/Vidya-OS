# Vidya OS: a study-first launcher for Android 10 tablets

Native Android (Kotlin) home screen. Plain Android views and **no third-party libraries**, so the APK is small
(a few MB), starts fast and uses little RAM on a 4 GB / 32 GB tablet.

## What it does

| Area | Behaviour |
|---|---|
| **Home screen** | Clock, Fun-time ring, study time today, MHT-CET countdown, search, app grid, dock. Works in portrait and landscape. |
| **Videos** | Paste a YouTube link (or Share a video from any app to Vidya OS) and it plays inside the launcher. Channels on the Learn list never use Fun time. Everything else uses a **30 min/day** budget (parent can change it). Time counts only while the video is playing. At zero, Fun videos lock until tomorrow; Learn videos stay open. |
| **Sunday is Funday** | No video limit. Games, the real YouTube app and (optionally) other browsers can open. Each is a switch in Parent controls. |
| **Study Browser** | Opens only approved sites: MHT-CET Cell, Balbharati e-books, NCERT, Maharashtra HSC Board, Wikipedia, PhET, Desmos. YouTube links are sent to Videos so the limit applies. Parent can add sites. Time here counts as study time. |
| **Notes** | Type, handwrite (pen, marker, eraser, undo/redo, 6 colours, pen-only mode for palm rejection), plain/ruled/grid paper, bullets and to-do boxes, page grows with "+ Page", search. |
| **Clock** | Alarms (repeat days, label, rings over the lock screen, snooze), timer (keeps running when the app is closed), stopwatch with laps, world clock. Alarms survive a restart. |
| **Installing apps** | Play Store works. New apps appear on the home screen. Anything Android marks as a **game** is hidden and closed (except on Funday). Parent can mark a wrongly detected app as "not a game", or block any app. |
| **Wallpapers and themes** | 8 free wallpapers, your own photo, 7 accent colours, light / dark / match tablet. |
| **Parent controls** (PIN, starts as 1234) | Fun limit, learning-only, Funday switches, exam date, Learn channels, approved sites, games and blocked apps, last 7 days, PIN change, Android settings (opens for 10 minutes). |

## Build the APK

1. Install **Android Studio** (current stable). Open this folder. Let it sync (it downloads the Android SDK and Gradle).
2. Connect the tablet (Settings, About tablet, tap Build number 7 times, then Developer options, USB debugging) and press Run.
   Or build a file: `./gradlew assembleDebug`, then the APK is `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the tablet and open it.

## First-time setup on the tablet (about 3 minutes)

1. Install the APK and open **Vidya OS**.
2. Press the **Home** button, choose **Vidya OS**, then **Always**. If it does not ask: Parent, then Home app, then Choose.
3. **Parent** tile, enter PIN **1234**, then change it (Security, Change PIN).
4. Parent, **App guard**, Turn on. In the screen that opens, find **Vidya OS** under Accessibility, switch it on and accept.
   This is what closes Chrome, the YouTube app and games. Without it they can still be opened by other routes.
5. Parent, MHT-CET countdown, set the date once the CET Cell announces it (not announced when this was written).
6. Review the Learn channels and approved sites lists. The defaults are suggestions, not a verified syllabus list.

## Read this: what is and is not tested

- **This code has not been compiled or run.** The workspace it was written in has no Android SDK. A syntax pass of
  the Kotlin compiler found no syntax errors, but real type-checking needs the Android SDK, so expect a few small
  compile errors on the first build. They are quick to fix; paste them back and they will be sorted out.
- **YouTube playback uses YouTube's own embedded player.** Some videos forbid embedding and will show "does not allow
  it to play here". YouTube also changes embed rules from time to time; if every video fails, that is the first thing to check.
- **The guard is an accessibility service, not a locked-down device.** A determined child who knows about Safe mode,
  a factory reset, or installing a second browser with an unlisted package name can get around it. Settings is
  closed while the guard is on, but the guard can be switched off from there once the PIN has opened Settings.
  For stronger control later, Android's device-owner mode (set up with a computer, on a freshly reset tablet) can be added.
- **Game detection** uses the category Android stores for each app. Most Play Store games are marked correctly,
  a few are not. Use "Block an app" for those.
- **Clock tampering.** Funday follows the tablet's date. Changing the date needs Settings, which is closed.
- **Sites with plain http** (no https) will not load; the app does not allow unencrypted traffic.
- The Fun-time count is stored on the tablet. Clearing the app's storage resets it, and so does uninstalling.
