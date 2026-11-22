# Run Together — Firebase Realtime Database Setup Guide (Free Spark Plan)

Run Together uses **Firebase Realtime Database** on the free **Spark plan** (no billing card required, no Cloud Functions, no Firestore, no user accounts).

Because the app includes a placeholder `app/google-services.json`, you will need to connect it to your own free Firebase project before two phones can sync live across cities.

---

## Step 1: Create a Firebase Project (Free Spark Plan)
1. Go to [https://console.firebase.google.com](https://console.firebase.google.com).
2. Click **Add project**, name it `Run Together`, and disable Google Analytics (optional).
3. Keep the project on the default free **Spark** plan.

## Step 2: Create the Realtime Database
1. In the left sidebar, open **Build → Realtime Database**.
2. Click **Create Database**.
3. Pick a region (e.g. `us-central1` or `asia-southeast1`).
4. Start in **Locked mode** (we will apply room-scoped rules in Step 4).

## Step 3: Register the Android App & Download `google-services.json`
1. In Firebase Console, open **Project Settings** (gear icon) → **Your apps** → click the **Android** icon.
2. Enter the exact **Android package name**:
   ```
   com.aistudio.runtogether.kxrmzp
   ```
3. Give it a nickname like `Run Together Android` and click **Register app**.
4. Click **Download `google-services.json`**.
5. Save the downloaded file to:
   ```
   app/google-services.json
   ```
   *(See `app/google-services.json.example` for the expected structure. `app/google-services.json` is listed in `.gitignore` so your Firebase config won't be committed to GitHub.)*
   *(Make sure the downloaded `google-services.json` includes `"firebase_url"` inside `"project_info"`. If you created the Realtime Database after registering the Android app, re-download `google-services.json` so `"firebase_url"` is included.)*

## Step 4: Apply Room-Scoped Security Rules
1. In Firebase Console, open **Realtime Database → Rules**.
2. Copy the contents of `database.rules.json` from the root of this project and paste them into the Rules editor.
3. Click **Publish**.

These rules ensure:
- The database root and `/rooms` list are **never** world-readable (nobody can list active rooms).
- Anyone who knows a valid 6-character room code (e.g. `K7M4P9`) can read and write **only** inside `/rooms/K7M4P9`.
- Runner status, names, GPS coordinates, and room metadata are schema-validated.

## Step 5: Build & Install on Both Phones
```bash
./gradlew assembleDebug
```
Install `app/build/outputs/apk/debug/app-debug.apk` on both Android phones (e.g. via WhatsApp or USB).
Create a room on Phone A, share the 6-character code or link to Phone B, and run together!
