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

## v2.0: Google Play release

Publishing guide with every Play Console value, Data safety answers and declarations: **`docs/PLAY_STORE.md`**.

- **Updates:** "Update available" banner on Today and a blocking **"Time to update"** screen when this version is below
  the minimum set in the website admin (App versions). Both use Google Play in-app updates (install without leaving the
  app); phones that didn't install from Play get the Play listing. The API answers 426 to versions below the minimum.
- **Report a circle** (search results and circle screens), reviewed in the admin (Play UGC policy).
- **Smaller:** fonts subset to English + Hindi characters (−330 KB), only `en`/`hi` resources, debug logging stripped,
  preview tooling removed, Play bundle split by density and language.
- Version names are now `2.0.<build>`; the versionCode is still the GitHub build number.

## v1.5: Move tab, competitions, redesigned Circles

**Bottom tabs:** Today · **Move** · Circles · Insights · Profile. The health wheel moved to its card on Today.

- **Move** (daily): this week's distance against a weekly goal (tap to set 5–50 km), activities, time, all-time
  distance and Mon–Sun active days; one-tap **Start walk / run / ride** (location and GPS checks, 3-2-1, live
  map); tracking settings behind the ⚙ button; **personal bests** (longest walk/run/ride, best pace over 1 km,
  biggest week); history grouped by This week / Last week / month.
- **Activity detail** rebuilt: loads defensively (Loading / Deleted / Error, never a blank screen); routes shorter
  than ~50 m show "Too short to draw a route" instead of an empty, over-zoomed map; distance, time, pace, speeds,
  elevation, energy, splits, Share, GPX, rename, delete.
- **Circles:** competitions first (live ones in yellow, your rank, join), then your global rank, your requests,
  your circles ("You own 1 of 3"), Start (disabled at the limit) / Join with code / Find. Creating a circle asks
  **Private** (invite only) or **Listed** (anyone can find it and ask to join); change it any time in settings.
- **Competition screen:** status and days left, metric, dates, prize, your place, Join / Leave, leaderboard.
  Saved for offline like every circle.
- **Full names** in the global ranking and competitions (names only, as the website returns them).

## Global ranking, Find a circle, website sign-in, offline circles (v1.4)

All on the real server now (website v12; `docs/COMMUNITY_API.md`). No demo data.

- **Global ranking:** Circles → Global card. Everyone sees the top 100 (week or today, masked names); you get a rank
  when you share steps; your own rank shows even outside the top 100. One point per day at your own step goal;
  only phone/watch-measured steps count. "Show me in the global ranking" switch.
- **Find a circle:** search open circles, ask to join with a note, cancel; owners approve/decline in **Join
  requests** and switch **Show in search** on with a one-line description. Invite codes still join instantly.
- **Website sign-in:** Profile → Website → **Sign in on the website**. The website shows a code and a QR; scanning
  the QR opens this screen with the code (App Link `https://<site>/link/CODE/`, or `bas1minute://link/CODE`).
  The app shows the browser ("Chrome on Windows · 49.36.x.x") and you Approve or Deny. Signed-in browsers are
  listed with Sign out / Sign out of every browser. No passwords anywhere.
- **Offline circles (circle clone):** every time Circles opens online, and every background sync (~3 h), the app
  saves the circles list, **every circle's leaderboard**, the global board (week + today), your requests and, for
  circles you own, their join requests (`Repo.syncCommunity()`, SQLite `cache`). Offline, every screen shows the
  last copy with "Offline · saved copy from 10:42". Shabash/Chalo made offline are queued and sent later; joining,
  approving and search need the internet and say so.

## Phone check: battery, data, background and permissions (v1.2)

Insights → **Phone check**. One health score (0–100) for the last 24 hours, plain-language findings with a
one-tap fix, and four lenses. Everything is worked out and stored on this phone; nothing is sent.

| Lens | What you see | How it's measured |
| --- | --- | --- |
| **Battery** | Level chart (green = charging), % used, drain per hour with screen **on** vs **off**, hours left at today's pace, temperature, condition, charge cycles (Android 14+), current draw | `BatteryWorker` saves the level every ~15 min (`battery_samples`, DB v6, kept 8 days); screen on/off from usage events |
| **Battery by app** (estimate) | Each app's share of today's drain, plus "Phone idle" | Screen time × 10%/h, background services × 3%/h, mobile 0.012%/MB, Wi-Fi 0.004%/MB, idle 0.6%/h — then **scaled to the real drop this phone measured** (0.4–2.5×). Labelled "estimate" on screen |
| **Data** | Mobile / Wi-Fi per app, split **while open** vs **in background** | `NetworkStatsManager` (Usage access; per-app mobile needs Android 10+) |
| **Background** | Apps that ran background tasks and for how long, flagged when you didn't open them; apps with **no battery limits** | Foreground-service events (Android 10+) minus on-screen time; `PowerManager.isIgnoringBatteryOptimizations` |
| **Permissions** | Who can use location (incl. all the time), camera, mic, contacts, SMS, call logs, photos…, oldest-used first; **powerful access** (accessibility, notification reading, device admin); apps installed outside an app store | `PackageManager` granted flags, `AccessibilityManager`, enabled notification listeners, `DevicePolicyManager`, install source |

**Findings** (`phone/PhoneCheck.kt`, unit tested in `PhoneCheckTest.kt`): screen-off drain ≥ 1.5%/h; battery ≥ 42°C;
≥ 50 MB mobile data in the background (High if ≥ 200 MB or the app wasn't opened); background tasks ≥ 3 h
(High if ≥ 8 h and not opened); "Unrestricted" apps busy in the background; accessibility / notification /
device-admin access (High if sideloaded); sensitive permissions on apps not opened in 30+ days; location all
the time; sideloaded apps with SMS / contacts / call-log access; 6+ unrestricted apps; 3+ charges a day.
Each links to the app's Android settings page (battery, mobile data, permissions, uninstall).

**Evening alert:** at most once a day after 7 pm, only High findings, never the same one twice (toggle on the screen).

**Honest limits, shown in the app:** Android gives exact per-app battery only to system apps
(`BATTERY_STATS`), so per-app battery is an estimate. Apps are visible if they have a home-screen icon (no
`QUERY_ALL_PACKAGES`, which Play restricts); data from hidden apps shows as "Other apps". Background
times and per-app mobile data need Android 10+. Network numbers update every hour or two.

## GPS walk / run (v1.1)

Today → **Walk or run with GPS** (or the card's Start). Pick Walk / Run / Ride, tap Start: precise location is
asked once, Android's "Turn on location?" dialog appears if GPS is off, then 3-2-1 and it records.

| Part | What it does | Files |
| --- | --- | --- |
| Live map | Your route draws on an OpenStreetMap map as you move, with a green start dot and your live position. Drag to look around; the locate button follows you again. GPS strength chip (±m). | `track/RouteMap.kt`, `ui/TrackScreen.kt` |
| Live stats | Distance (km), moving time, pace now, average pace (rides show km/h), last km split, elevation | `ui/TrackScreen.kt` |
| Screen off | Foreground service (type `location`) keeps recording; notification shows km · time · pace with **Pause / Finish** | `track/TrackingService.kt` |
| Smart GPS | Drops inaccurate fixes (> 30 m), Kalman smoothing, teleport filter, auto-pause at signals, minimum step against drift. Tested within ~1–2% on simulated routes with corners | `track/TrackMath.kt`, `app/src/test/.../TrackMathTest.kt` |
| Voice | Optional: "2 kilometres. Time 12 minutes 25 seconds…" every km (Android text-to-speech) | `TrackingService.kt` |
| Summary | Map with start (green) and finish (red) markers, or one marker + **Round trip** badge when you end where you began. Distance, moving and total time, avg pace, avg/max speed, elevation gain, energy estimate, per-km split bars, longest-ever badge, rename, delete | `ui/TrackScreen.kt` |
| Share picture | Map or Route-only style, Post 4:5 or Story 9:16, name on/off, **Hide exact start and finish** (on by default: drops first/last 200 m). Frame the map yourself; Share to WhatsApp/Instagram or **Save to phone** (Gallery → Pictures → Bas 1 Minute) | `track/RouteCard.kt` |
| GPX download | Standard GPS file of the whole route (segments, time, elevation): opens in Strava, Google Earth, Komoot, Garmin | `TrackMath.kt` (`Gpx`) |
| History | All activities with route thumbnails, this week's km on Today | `track/TrackStore.kt` |
| Crash-safe | Every GPS point is saved as it arrives. If Android closes the app mid-run, Today offers **Resume / Save / Discard** | `TrackingService.kt` |

**Privacy:** location is used only while recording, with the notification visible (no background location
permission). Routes are in SQLite (`activities`, `activity_points`, DB v5) and never synced. Map tiles come from
OpenStreetMap; the phone's position is never sent. "Delete everything" removes activities too.

**Map tiles:** osmdroid + OpenStreetMap (no API key). OSM's free tile servers are fine for a pilot, but their
usage policy doesn't allow heavy app traffic. Before a big Play Store launch, point `TileSourceFactory.MAPNIK` in
`RouteMap.kt` at a hosted tile provider (MapTiler, Stadia, Thunderforest) or swap the file for Google Maps
Compose with an API key; nothing else changes.

**Tests:** `./gradlew test` runs the GPS engine checks (distance, corners, auto-pause, teleports, pause/resume,
crash restore, loops, polyline, GPX).

## v1.0: love and retention

- **Home-screen widget** (`StepsWidget.kt`, `res/layout/widget_steps.xml`): steps, goal bar, distance, streak;
  refreshed after every phone read and every 30 minutes; tap opens the app.
- **Week in review** (`WeekReview.kt`, `ui/WeekReviewStory.kt`): 5-page swipeable story (steps vs last week, best
  day, goal days + streak, screen time vs last week, share). Card on Home on Sunday/Monday until opened; also in Insights.
- **Circle podium** (`ui/Podium.kt`): top 3 sharers, animated.
- **Transitions:** fade-through between tabs and full screens (`ui/MainShell.kt`).

## Hindi and dark mode (v0.11)

- **Language:** Profile → Appearance → English / हिन्दी. `ui/I18n.kt`: wrap a screen string as `t("English")` and add
  its Hindi to the map; anything untranslated falls back to English. Tabs, Home, 21 days, setup, lock and Profile
  sections are translated so far.
- **Theme:** System (default) / Light / Dark. Surfaces read theme tokens (`Paper`, `CardBg`, `TextMain`, `Muted`,
  `PaperLine` in `ui/Theme.kt`); the dark hero, brand yellow and chart colours are the same in both.

## Share achievements (v0.7)

"Share my day" (Today), "Share my streak" (21 days) and "Share my rank" (circle) open a sheet: pick Steps /
21-day streak / Circle rank, Post (4:5) or Story (9:16), name on or off, preview, share. The image is drawn on
the phone (`ShareCard.kt`), saved in the app's private cache and handed to WhatsApp / Instagram / Facebook via
Android's share sheet (FileProvider, `res/xml/share_paths.xml`).

## Motivation push notifications (v0.6)

The admin writes templates (Django admin → Push notifications → Campaigns → "Send now"). Firebase delivers them
as data messages; `PushService` fills `{name} {steps} {goal} {steps_left} {km} {screen} {limit} {rank} {circle}`
from this phone's own data and checks the audience on the phone (`Personalizer.kt`). Profile → Motivation
notifications switches it off.

**Setup:** create a Firebase project → add Android app `com.bas1minute.sync` → download `google-services.json`
→ GitHub secret `GOOGLE_SERVICES_JSON_BASE64` (`base64 -w0 google-services.json`). Without it the app builds
without pushes.

## API security (v0.5)

Every request is POST and signed: `X-B1M-Time`, `X-B1M-Nonce`, `X-B1M-Sign` = HMAC-SHA256(device secret,
method\npath\ntime\nnonce\nsha256(body)). The secret comes once from `auth/device/` (or `auth/upgrade/` for older
installs) and is encrypted with Android Keystore (`api/SecretStore.kt`). Release builds are obfuscated (R8).

## Tabs and invite links

Bottom tabs: Today · Insights · Circles · **Spin** (health wheel) · Profile. Calm opens from the Calm tile
on Today. **Invite on WhatsApp** sends `https://<site>/join/CODE/`: tapping it opens the app and joins
automatically (also right after first setup). For links to open the app directly, set
`ANDROID_CERT_SHA256` on the server (`/.well-known/assetlinks.json`); otherwise the link opens a page with
"Open in the app and join".

## Privacy first (v0.4)

- **No login.** First launch asks only for a **name and a 4-digit PIN**. Optional App lock uses the PIN;
  "Forgot PIN" sets a new one (a convenience lock, not security).
- **Everything stays on the phone** (SQLite): steps, screen time, sleep, water, weight, mood, bursts, calm.
- **Sharing is off by default.** Profile → Your data → *Share my steps and screen time* sends only those two
  numbers (circles + backup). Turning it off can delete what was shared.
- **Circles work for everyone** via an anonymous name-only account (`POST /api/v1/auth/device/`). Non-sharing
  members show as **Private**. Points: 1 per day at your own step goal.
- **Delete everything** wipes the phone and the circle account.

## Distance and 21 days

- **Distance** = steps × step length. Default 76 cm (≈ 1,315 steps per km), editable in Profile → Step length.
  Shown in the home ring, updating with steps, and in the 21-day totals (`Distance.kt`).
- **Your 21 days** (Today, right below Bursts and Calm): habit grid (one dot per day, gold = step goal reached),
  current streak, total steps, distance, average, goal days, and a 21-day chart for steps or screen time.
  Read from the phone's database, so it works offline. The database keeps up to 400 days (a few KB); the
  home screen shows the last 21. 21 days is a challenge length, not a scientific habit threshold
  (research suggests about 66 days on average).
- **Screen time on the home screen** counts social, messaging and browser apps (`ScreenTimeReader.SOCIAL`:
  Instagram, YouTube, Facebook, WhatsApp, Telegram, Chrome and other browsers…) plus anything Android labels
  as social. Total phone time is still recorded and synced as before.

## Daily motivation

- **Today's thought:** 30 original health quotes (`Wellbeing.kt`), one per day, the same for everyone all day,
  cycling through all 30 before repeating. Share sends it to WhatsApp.
- **Spin the health wheel:** 8 segments (Sleep, Food, Move, Water, Mind, Phone, Morning, Habit) and 30 tips.
  3 spins a day (`Wellbeing.SPINS_PER_DAY`); prefers tips not seen yet; "X of 30 tips discovered" and a list
  of found tips. Landing maths in `WheelMath` (tested: 20,000 simulated spins all land on the chosen segment).
  Tips are general wellness habits worded as "can help", not medical advice.

## Feature parity

See `FEATURES.md`: every website member feature has an in-app home. **Profile → Sync with server** uploads
everything waiting (phone days and offline actions) and downloads the last 90 days, so a new phone or a
reinstall gets your history back. Logging in on a new phone does this automatically.

## Native only

The app never opens the Bas 1 Minute website. It talks to the server only through `/api/v1/`.
Our idea, How we're different, Free for everyone and Privacy are built-in screens (`ui/AboutScreens.kt`);
`PrivacyActivity` (also opened by Health Connect) shows the same privacy text. Circle invites share a
**code** ("Open the app → Circles → Join with code"), sent straight to WhatsApp. The only outside link is
Google Play, to install Health Connect. Set your support email in `app/build.gradle.kts` (`SUPPORT_EMAIL`).

For the Play Store you still need a public privacy policy URL; the website's `/privacy/` page can serve
that without the app linking to it.

## Samsung Health

Samsung Health data (steps, sleep, weight, water, incl. Galaxy Watch) arrives through **Health Connect**,
no Samsung partnership needed. Samsung Health only shares once the user switches it on, so the app checks
whether Samsung Health is installed and whether any of its steps from the last 7 days are in Health
Connect (`SamsungHealth.kt`, using Health Connect's data origins). If not, a "Connect Samsung Health" card
explains the steps and opens Samsung Health (Today, dismissible for a week; Profile; setup).

## Sharing the app for free (GitHub Releases)

Every signed build on `main` publishes a GitHub Release automatically. Friends download from:

```
https://github.com/YOUR-USERNAME/bas1minute-android/releases/latest/download/Bas1Minute.apk
```

That link always gives the newest version. Release downloads need a **public** repository (your signing
key stays private in Secrets). Put the same link in the website's `ANDROID_APK_URL` for the `/download/` page.

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

## Usage access on sideloaded installs (Android 13+)

Android blocks Usage access for apps installed from an APK (WhatsApp, Files): "App was denied access…
restricted setting". Testers allow it once:

1. Try to switch on usage access for Bas 1 Minute (the denial is expected; it unlocks the next step).
2. Settings → Apps → Bas 1 Minute → ⋮ → **Allow restricted settings** → confirm with PIN or fingerprint.
   (Some Samsung One UI versions show it at the bottom of App info.)
3. Switch on usage access again. It works.

The app detects a sideloaded install (`InstallSource.kt`) and shows these steps with buttons, during setup
and in Profile. Installs from the Play Store don't need this.

**Phone-specific help** (`PhoneHelp.kt`, Profile → About → Phone-specific help): detects brand, model,
Android version and software (One UI, OxygenOS, HyperOS, ColorOS, realme UI, Funtouch OS…), shows the usual
menu path for that brand for usage access and battery, and offers **Search Google for my phone** (e.g.
"OnePlus CPH2581 Android 15 OxygenOS allow restricted settings usage access") and **Copy question** (a full
description of the phone and error to paste into Google, Gemini or ChatGPT). Menu paths are "usually"
guidance; the search covers whatever a phone update changed.

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
