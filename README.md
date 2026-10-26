# Bas 1 Minute (Android)

**v0.3 adds:** social media time with a 1 / 3 / 10-year forecast (Insights tab), first phone use after
waking and last use before sleep, sleep (Health Connect, or estimated from the longest overnight break from
the phone), water (Health Connect or "+1 glass"), and optional private weight. Collected automatically
wherever the phone can measure it; see the table below.

| Metric | Automatic source | Fallback |
| --- | --- | --- |
| Social media time | Usage events: known social apps + Android's "social" category (messaging not counted) | — |
| Last use at night / first use in the morning | Longest gap in phone use, 6 pm–1 pm, at least 3 hours | — |
| Sleep | Health Connect sleep sessions (watch, Samsung Health, Fit) | That overnight gap, labelled "estimated" |
| Water | Health Connect hydration (water apps) | "+1 glass" (250 ml) or Log a day |
| Weight (off by default) | Health Connect weight (smart scale) | Log a day |

Only daily totals and the two times are synced. The per-app breakdown stays on the phone (`app_usage` table).

**v0.2: the full Bas 1 Minute app.** Everything on the website's member area is in the app, and it works
offline. Log in with your mobile number (same account as the website); no sync key needed any more.

| Tab | What it does |
| --- | --- |
| **Today** (+ Log a day) | 7K + 4 ring (steps + screen time), steps left, bursts (+ "I did one"), calm, private mood check-in with Tele-MANAS support, cheers from your circles, 7-day chart, sync status |
| **Insights** | Social media today and 7-day average, 1 / 3 / 10-year forecast with equivalents, top social apps (phone only), phone & sleep habits, water, weight (if on) |
| **Circles** | Your circles, start one, join by code; leaderboard, team goal, Shabash! / Chalo!, invite on WhatsApp or share, screen-time sharing, owner tools (rename, new link, remove, delete), leave |
| **Calm** | Guided 1-minute breathing (in 4, out 6). Counts a calm minute |
| **Profile** | Name, city, goals; phone permissions; auto sync; links to Our idea, How we’re different, Free for everyone, Privacy; log out; delete account |

**Offline:** screens show the last saved data. Logging, bursts, mood, calm and cheers made offline are
queued on the phone (SQLite `outbox`) and sent in order on the next sync. Creating/joining circles and
changing goals need the internet and say so.

**Needs website v7** (adds the `/api/v1/` endpoints). Run `python manage.py migrate` after deploying it.

---

## Original sync notes

A tiny native Android app with one job: read **daily steps** and **daily screen time** on the phone and
send the totals to the Bas 1 Minute website. Kotlin + Jetpack Compose, one screen, no third-party
networking or database libraries.

## What it reads

| Data | Where from | Permission |
| --- | --- | --- |
| Steps (main) | **Health Connect**: Google Fit, Samsung Health, watches, and the phone itself on Android 14+ | Health Connect "Steps" (+ "read in background" where available) |
| Steps (backup) | **Recording API** in Google Play services: counts with the phone's step sensor, keeps 10 days | Physical activity |
| Screen time | Android **usage statistics**, added up on the phone (same idea as Digital Wellbeing) | Usage access (Settings) |

If both step sources have a number, the app sends the larger one (they count the same walking).
Only daily totals leave the phone. App names, location and everything else stay on the device.

## Works offline (local database)

The app keeps its own copy of every day in Android's built-in **SQLite** (`LocalDb.kt`, no extra
libraries). It works with no internet and when the website is down:

- Every time the app opens (and on each background run) it reads the last 10 days from the phone and
  saves them. Days beyond what the phone remembers stay saved, for up to 400 days.
- The home screen and week chart are drawn from this database, never from the network.
- The same table is the **sync outbox**: a day stays "waiting" until the website confirms it. Sync sends
  all waiting days, oldest first, in batches of 15 days (the website accepts up to 31 days back).
- If the website is down, the app says how many days are waiting and WorkManager retries with backoff.
- A missing reading never overwrites a saved one, and a day that changes while being sent stays waiting.
- Uninstalling the app deletes this database. Days already synced stay on the website.

## How it syncs

`POST {website}/api/v1/activity/` with `Authorization: Bearer <sync key>`, sending yesterday and today:

```json
{"days": [
  {"date": "2026-10-25", "steps": 5240, "source": "health_connect"},
  {"date": "2026-10-25", "screen_minutes": 192, "source": "usage_stats"}
]}
```

- **Sync now** button, plus WorkManager every ~3 hours when online (Android picks the exact time).
- Sending yesterday each time means a late-night walk still lands on the right day.
- Needs the website update that accepts the `phone_sensor` source (v5 of the Django project).

## Build it

**Option A: Android Studio (recommended)**
1. Install Android Studio (latest stable). Open this folder.
2. Let it sync Gradle. If it suggests updating the Android Gradle Plugin or libraries, accept.
3. Plug in your phone with USB debugging on, press **Run**. Or **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.

**Option B: GitHub builds it for you (no Android Studio)**
1. Push this folder to its own GitHub repository.
2. Open the **Actions** tab. The *Build APK* workflow runs on every push to `main`.
3. Download the `bas1minute-sync-debug` artifact, unzip, copy the `.apk` to your phone and install
   (allow "install unknown apps" for your file manager).

## Updating from a new zip

Always update with the script, so files removed in a newer version are deleted too (otherwise the build
fails with "Redeclaration" errors):

```bash
./update-from-zip.sh ~/Downloads/bas1minute-android.zip
git commit -m "Update Android app" && git push
```

## Pilot testing by sharing the APK

### One-time setup (10 minutes)
1. **Create your signing key** on your laptop (keep the passwords somewhere safe):
   ```bash
   keytool -genkeypair -v -keystore bas1minute-release.jks -alias bas1minute \
     -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 bas1minute-release.jks > keystore.b64
   ```
2. **Back up `bas1minute-release.jks`** (Google Drive, password manager). If you lose it, testers must
   uninstall and reinstall. Never commit it to Git.
3. **Create a private GitHub repo** and push this folder:
   ```bash
   git init && git add . && git commit -m "Bas 1 Minute Sync"
   git branch -M main
   git remote add origin git@github.com:YOUR-USERNAME/bas1minute-android.git
   git push -u origin main
   ```
4. In the repo: **Settings > Secrets and variables > Actions > New repository secret**. Add four:
   | Name | Value |
   | --- | --- |
   | `B1M_KEYSTORE_BASE64` | the whole contents of `keystore.b64` |
   | `B1M_KEYSTORE_PASSWORD` | the keystore password you chose |
   | `B1M_KEY_ALIAS` | `bas1minute` |
   | `B1M_KEY_PASSWORD` | the key password (same as keystore password if you pressed Enter) |
5. **Actions > Build pilot APK > Run workflow** (or push any change).

### Every release
1. Push to `main`. When the build is green, open the run and download the `Bas1Minute-v0.1.N` artifact.
2. Unzip it and send `Bas1Minute-v0.1.N.apk` in your testers' WhatsApp group (as a document).
3. Testers tap it and choose **Update**. Their settings and sync key stay.

Each build gets a higher version (`0.1.N`), and the same key signs every build, so updates install in place.

**Later, for the Play Store:** when setting up Play App Signing, choose to use this same key. Then pilot
users can move to the Play Store version without uninstalling.

## First run on the phone

1. Website: **Profile > Connect your phone > Create sync key**. Copy it.
2. App: paste the key, **Save**.
3. **Allow steps from Health Connect** (and the phone backup if offered).
4. **Open usage access settings**, find Bas 1 Minute, switch it on, come back.
5. **Sync now**. Your website dashboard should show "From Health Connect" or "From phone step sensor".

**Testing against your laptop:** debug builds allow `http://`. Run Django with
`python manage.py runserver 0.0.0.0:8000`, add your laptop's Wi-Fi IP to `ALLOWED_HOSTS`, then in the
app tap **Change website address** and enter `http://192.168.x.x:8000`.

## Design

Same look as the website: indigo and haldi, Anek Latin headlines and Mukta body text (bundled in
`res/font`, SIL Open Font License).

- **Onboarding (first launch):** dark welcome screen with the 7K + 4 ring, then three short steps with a
  progress bar: link account, allow steps, allow screen time. Each step can be skipped.
- **Home:** dark hero with the date, a sync status chip and a two-ring dial (steps outside, screen time
  inside; turns coral when over the limit), plus "Steps left" and "Screen time" tiles. Below, white cards:
  a nudge when you're close to the goal or over your screen limit, a 7-day chart (Steps / Screen) read
  straight from the phone, Sync (button + auto toggle), and Connections (fix any missing permission
  with one tap). **Pull down anywhere to sync.**

## Project layout

```
app/src/main/java/com/bas1minute/sync/
  MainActivity.kt      permissions, links, sharing; routes login → permissions → tabs
  AppController.kt     what every screen shows; actions; offline fallbacks
  api/Api.kt           HTTPS JSON client (no libraries)
  api/Models.kt        parsing for today, circles, leaderboard
  api/Repo.kt          caching, offline queue, all account/circle actions
  Snapshot.kt          phone readings → local database → screens
  ui/AuthScreens.kt    welcome, log in, create account
  ui/PermissionsScreen.kt  steps + screen-time permission steps
  ui/MainShell.kt      bottom navigation
  ui/TodayTab.kt, LogTab.kt, CirclesTab.kt, CalmTab.kt, ProfileTab.kt
  ui/Theme.kt, ui/Components.kt  design system
  StepsReader.kt       Health Connect + Recording API
  ScreenTimeReader.kt  usage events added up per day
  LocalDb.kt           SQLite: saved days, action outbox, screen cache
  Recorder.kt          copies the phone's recent days into the database
  Sync.kt              sends waiting days, handles server replies
  SyncClient.kt        one HTTPS POST (HttpURLConnection)
  SyncWorker.kt        background sync every ~3 hours
  PrivacyActivity.kt   "what we read and send" (also opened by Health Connect)
  Prefs.kt             login token, profile, goals, website address, last result
```

## Before the Play Store

- **Health Connect declaration** in Play Console: explain you read Steps to show the user's daily goal.
- **Usage access**: Play's policy allows it for core features; state clearly that only totals are sent.
- **Release signing**: create an upload key in Android Studio (*Build > Generate Signed Bundle*). Never commit it.
- **Battery savers** on Xiaomi, Oppo, Vivo, Realme can delay background sync. Opening the app always syncs fresh numbers when you tap Sync now.

## Not verified yet

This project was written without compiling (no Android SDK in the environment it was written in).
The first Gradle sync may ask for small version bumps; if a build error mentions a specific API, paste it
and it can be fixed quickly.
