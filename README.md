# Run Together

A minimal, dark-first Android running accountability app built for two runners running at the same time from different cities. No accounts, no login, no social feed — just live speed, live distance, dual OpenStreetMap routes, and a shareable end-of-run card.

---

## Features (v1)

- **Code-Based Rooms (No Accounts):** Pick a display name on first launch, create a room to get a 6-character code & deep link (`runtogether://join/<CODE>`), and share it via WhatsApp/SMS.
- **Flexible Start + Optional 3-2-1 Sync:** Start running immediately even before your partner joins, or tap **Start together (3-2-1)** when both runners are ready for a server-synced countdown.
- **Glanceable Live Screen:** Huge tabular **speed** and **distance** readouts plus a live elapsed **time** for both runners (orange for you, sky-blue for your partner), clear **PAUSED** states, **"Waiting for GPS"** detection, and per-runner **Pause / Resume / End** controls.
- **Live Dual-Route Map:** Dark-styled **OpenStreetMap (`osmdroid`)** directly under the metrics drawing both runners' live polylines with one-tap **Me / Partner / Both** camera focus chips.
- **Screen-Off GPS Tracking:** High-accuracy `FusedLocationProviderClient` inside an Android `location` foreground service with rolling time-window speed smoothing (`SpeedSmoother`).
- **Dual-Map Share Card & History:** Renders a `1080 × 1350` PNG card via Android Canvas with two side-by-side OpenStreetMap route panels (fitted to each runner's city) plus distance, time, avg pace, and top speed. Auto-saves to the gallery and shares to WhatsApp/Instagram.
- **Per-Phone Units:** Default **km** (`km/h`, `km`, `min/km`) with a Settings toggle to **miles** (`mph`, `mi`, `min/mi`). Each phone converts both runners' metrics into its own unit.

---

## Tech Stack

- **UI:** Kotlin, Jetpack Compose, Material 3, single-activity Navigation Compose
- **Maps:** OpenStreetMap via `osmdroid` (no API key required)
- **Location:** Google Play Services `FusedLocationProviderClient` + Foreground Service (`minSdk 26`)
- **Live Sync:** Firebase Realtime Database (free Spark plan — no Auth, no Firestore, no Cloud Functions)
- **Local Storage:** Jetpack DataStore Preferences + local run history/card cache

---

## Local Setup & Building the APK

`app/google-services.json` is intentionally gitignored so you never commit your Firebase config to a public repository. A template is provided at `app/google-services.json.example`.

The app supports one or two runners in the same room. A finished runner can start a later run while the room remains active. Each completed run is stored as a separate history entry.

### 1. Clone the repository
```bash
git clone <your-repo-url>
cd run-together
```

### 2. Configure Firebase Realtime Database (Free Spark Plan)
1. Create a free Spark project in the [Firebase Console](https://console.firebase.google.com).
2. Enable **Build → Realtime Database** and create a database.
3. Go to **Project Settings → Your apps → Add Android app** with exact package name:
   ```
   com.aistudio.runtogether.kxrmzp
   ```
4. Download **`google-services.json`** and place it at:
   ```
   app/google-services.json
   ```
   *(Verify `"firebase_url"` is present under `"project_info"`. The app also derives the database URL from the project ID as a fallback, but the downloaded config is the preferred source.)*
5. Open **Realtime Database → Rules**, paste the contents of **`database.rules.json`** from the repo root, and click **Publish**.

For detailed instructions, see [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md).

### 3. Build the Debug APK
```bash
./gradlew assembleDebug
```
The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```
Install it on both Android phones, create a room on Phone A, share the 6-character code to Phone B, and start running.

*(Note: If you build without `app/google-services.json`, the app still compiles and runs in local single-device mode so you can test GPS tracking, maps, and share cards offline.)*

---

## Architecture Notes (Phase 2 Ready)

- `model/` — Canonical SI-unit models (`RunModels.kt`, `UnitFormatter.kt`, `RoomCodeGenerator.kt`). Runners are stored in a map keyed by `runnerId` (`maxRunners = 2` in v1) so multi-runner rooms and Phase 2 extensions (voice chat room IDs, Spotify Jam links, nudges, streaks) fit cleanly without schema breaks.
- `data/` — `RoomSyncRepository` interface with `FirebaseRoomSyncRepository` (compact live scalar writes + batched path uploads) and `LocalRoomSyncRepository` fallback.
- `location/` — `RunTrackingService` (foreground service), `SpeedSmoother`, and `RunTrackerStateHolder`.
- `map/` — `DualRouteMapView` (`osmdroid` with dark athletic tile filter).
- `share/` — `ShareCardGenerator` (Canvas dual-map PNG renderer) and `ImageGallerySaver` (MediaStore + FileProvider).
