# Run Together — Google AI Studio build prompt (Android app)

Paste this whole prompt into Google AI Studio and ask it to build the Android
app. It is written to be self-contained: everything the builder needs is here.

---

Build an Android app called **Run Together**. It is a minimal Strava-like app
for exactly two people — me and my brother — who run at the same time from
different cities and want to see each other live while running, to keep each
other accountable. It is NOT a social network and NOT a full Strava clone.
Keep it small, fast, and readable mid-run.

## Who it is for

- Two runners in different cities (one in the US, one in India), each on an
  Android phone. They run at the same time but in different places.
- v1 is strictly 2 people per room. Design the data model so more people per
  room is possible later, but do not build it in v1.

## Core features (v1 — build all of these)

### 1. Rooms — no accounts, join by code

- Home screen has two buttons: **Create room** and **Join room**.
- Create room generates a short room code (6 characters, easy to read aloud)
  and a shareable link. The creator can share the code/link (WhatsApp, SMS,
  any share target) so the other person can join.
- Join room takes the code (or opens the shared link) and joins. No login,
  no sign-up, no accounts anywhere in the app.
- Each person picks a display name on first launch (stored on the phone).
  That name is what the other person sees.

### 2. Flexible start — no forced countdown

- A run does NOT require both people to start at the same time.
- One person can create the room, start running, and share the code; the
  other joins later and starts running whenever they are ready. Each person
  has their own start time, distance, and timer.
- If both people ARE in the room and ready at the same time, offer an
  optional **Start together** button that runs a 3-2-1 countdown and starts
  both timers at once. This is optional, never required.

### 3. Live screen — speeds and distances, nothing else

- While running, the screen shows, for EACH person, exactly two numbers:
  current speed and distance so far. Big, high-contrast numbers readable at a
  glance while running. My numbers on top, the other person's below (or side
  by side — pick the layout that reads best on a phone held in one hand).
- Below the numbers, a live map shows BOTH runners' paths as two colored
  polylines (one color per person), updating live as they run. Same screen —
  no tab switching mid-run.
- **Pause**: a pause button pauses MY run (timer and distance stop). When one
  person pauses, the other person sees a clear "Paused" state on that
  person's card. Resume continues the same run.
- **End run**: an end button finishes the run for me. The other person can
  keep running; ending is per-person, not per-room.
- GPS must keep tracking with the screen off: use a foreground service with
  high-accuracy location. Handle the standard Android location permissions
  (foreground + background location) with clear rationale text.
- Speed smoothing: raw GPS speed is jumpy. Smooth it (rolling average over
  the last few seconds) so the number does not flicker wildly.
- If GPS signal is lost, show "Waiting for GPS" instead of a stale number.

### 4. Live sync — Firebase Realtime Database (free tier)

- Use **Firebase Realtime Database on the free Spark plan** for all live
  sync. No other backend. The free tier (100 simultaneous connections,
  1 GB stored, 10 GB/month downloads) is far more than two runners need.
- Each runner's phone writes their own live state a few times per second:
  latitude, longitude, current speed, distance so far, elapsed time, status
  (running / paused / finished). Each phone reads the other runner's state.
- Write only small state updates, not the full path, on every tick. Sync the
  full path points less often (every few seconds, or in small batches) so
  bandwidth stays tiny.
- The app must include a `google-services.json` placeholder and clear setup
  instructions (create a Firebase project, add the Android app, download
  `google-services.json`, paste it in) — the builder cannot create my
  Firebase project for me.
- Firebase security rules: anyone with a room code can read/write that room
  only. Do not leave the database world-readable.

### 5. End-of-run share image

- When a run ends, generate a **shareable image** (one image file) that shows:
  - TWO map panels side by side (one per runner — they ran in different
    cities, so one map cannot show both routes). Each panel shows that
    person's route drawn on a map.
  - Under each panel, that person's stats: distance, time, average pace,
    top speed.
  - A header with the date and both names, e.g. "Sivaganesh & Shukirthan —
    22 Nov 2026".
- Render it with Android Canvas/Bitmap (draw the map snapshot + stats into
  one bitmap). Share it through the standard Android share sheet (WhatsApp,
  Instagram, etc.). Also save a copy to the phone's gallery.
- Keep the design clean and good-looking: dark background, big numbers, one
  accent color per person matching their route color.

### 6. Run history (simple)

- Save finished runs (both runners' routes + stats + the share image) in
  Firebase under the room, and show a simple history list: date, both names,
  both distances. Tapping one re-opens its share image.
- No social feed, no likes, no comments, no leaderboards, no segments.

### 7. Units

- Default unit is **kilometers** (speed in km/h, distance in km, pace in
  min/km). A settings toggle switches to **miles** (mph, miles, min/mile).
  The setting is per-phone: I can use km while he uses miles, and each of us
  sees BOTH people's numbers in OUR OWN unit.

## Tech

- Kotlin, Jetpack Compose, single-activity, Material 3.
- Maps: OpenStreetMap via **osmdroid** (fully free, no API key needed).
  Do NOT use Google Maps (needs an API key and billing setup).
- Location: Google Play Services Location API (FusedLocationProviderClient),
  foreground service while running.
- Backend: Firebase Realtime Database only (Spark/free plan). No Cloud
  Functions, no Firestore, no Auth (anonymous local identity only — generate
  a random runner ID per phone, stored locally).
- Min SDK 26 (Android 8.0), target the latest stable SDK.
- The app must build with `./gradlew assembleDebug` and produce a working
  debug APK.

## Design

- Minimal and athletic: dark theme first, huge readable numbers, one accent
  color per runner (e.g. orange for me, blue for him — consistent everywhere:
  live screen, map polylines, share image).
- The live screen is the whole product: optimize it for glancing at while
  running. Nothing decorative on it.

## Explicitly do NOT build in v1

- No accounts, no login, no social feed, no leaderboards, no segments,
  no challenges, no heart-rate sensors, no watch app, no voice chat,
  no music sync, no more than 2 people per room, no streaks.

## Phase 2 ideas (do NOT build now — just structure the code so these fit later)

- **Voice chat during a run** (WebRTC — e.g. LiveKit or Agora free tier).
- **Live music sync** (hard to sync perfectly in-app; realistic route is a
  Spotify Jam deep link so both listen to the same queue).
- **Nudge button**: tap to send the other runner a quick "pick it up!"
  buzz + message mid-run.
- **Streaks**: weeks in a row where both ran at least once.
- Rooms with more than 2 people.

## Acceptance checks (the build is done when all of these pass)

1. Two phones: phone A creates a room, phone B joins with the code.
2. A starts running; B joins 2 minutes later and starts; A sees B's live
   speed and distance appear, and B sees A's.
3. A pauses; B's screen shows A as Paused. A resumes; it clears.
4. Both end their runs; each phone generates the share image with two map
   panels and both people's stats, and it shares correctly to WhatsApp.
5. A run with the screen off keeps tracking (foreground service works).
6. Units: set A to km and B to miles; each sees both runners in their own unit.
7. `./gradlew assembleDebug` succeeds with no manual fixes.

---

End of prompt. After AI Studio generates the code, I will build the APK and
test it with my brother on two real phones.
