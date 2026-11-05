# Publishing Bas 1 Minute on Google Play

Everything to fill in, in the order Play Console asks. Values in **bold** are ready to paste.

## 1. Create app (the form you're on)

| Field | Value |
| --- | --- |
| App name (max 30) | **Bas 1 Minute: Steps & Habits** (28). Changeable later. |
| Package name | **com.bas1minute.sync** — permanent once created. It's what the app, Firebase (push), the website's App Links (`ANDROID_PACKAGE`) and Health Connect already use, so keep it. |
| Default language | **English (India) – en-IN** (add Hindi – hi-IN as a second listing later) |
| App or game | **App** |
| Free or paid | **Free** (can't become paid after publishing; the app is free by design) |
| Developer Program Policies | Read and tick (the app is built to meet them; see section 4) |
| Play App Signing | Tick. Google keeps the app signing key; your GitHub keystore becomes the **upload key** |
| US export laws | Tick. The app only uses standard HTTPS/HMAC encryption (exempt) |

## 2. First release

1. GitHub → Actions → latest run on `main` → download **Bas1Minute-play-bundle-v2.0.N** (`.aab`). It is only built when the
   `B1M_KEYSTORE_*` secrets are set. Keep that keystore and its passwords safe: it's your upload key.
2. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → release name `2.0.N` →
   notes: *First Play release: walks and runs with GPS, competitions, global ranking, website sign-in.*
3. Add yourself and testers (email list), install from the opt-in link, test, then **Promote release → Production**
   (new personal developer accounts must first run a **closed test with 12+ testers for 14 days**).
4. After Play App Signing is set up: Play Console → Setup → **App signing** → copy the **SHA-256 of the app signing key**
   and set it on the website as `ANDROID_CERT_SHA256` (comma-separate it with your upload key's SHA-256). Without this,
   invite links and website sign-in QR codes open the browser instead of the app.
5. Firebase console → Project settings → Android app → **Add fingerprint**: the same app-signing SHA-256 (push keeps working).

## 3. Store listing

- **Short description (max 80):** Walk more, scroll less. One-minute habits, circles and GPS walks. Free, private.
- **Full description:**

  > Fit hone ke liye ghante nahi, minute chahiye.
  >
  > Bas 1 Minute turns tiny habits into a daily routine: one-minute movement bursts, a step goal that fits your life,
  > and a gentle check on screen time. Built in India, free for everyone.
  >
  > • Today: steps from your phone or Health Connect, screen-time ring, streaks and a 21-day habit grid
  > • Move: track walks, runs and rides with GPS; see your route, distance, pace and kilometre splits; share a picture; export GPX
  > • Circles: move together with family, friends or your office. Personal goals, so everyone can win
  > • Competitions and a ranking for all of India (only phone-measured steps count)
  > • Phone check: which apps drain your battery and data, and which can use your camera, microphone and location
  > • Insights: where your time goes and what 15 minutes a day adds up to
  > • Calm minutes, mood, water and sleep — kept only on your phone
  >
  > Private by design: everything stays on your phone unless you turn on sharing, and then only daily steps and screen
  > time are sent. No ads. No selling data. Delete your account any time.

- App icon 512×512 PNG, feature graphic 1024×500, 4–8 phone screenshots (Today, Move, an activity, Circles, Global ranking, Phone check).
- Category **Health & Fitness**; contact email **tosourav6@gmail.com**; website **https://bas1minute.pythonanywhere.com**
- **Privacy policy URL:** https://bas1minute.pythonanywhere.com/privacy/

## 4. App content (Policy → App content)

| Item | Answer |
| --- | --- |
| Privacy policy | URL above |
| Ads | **No ads** |
| App access | **All functionality available without special access** (no login: the app creates an anonymous account with a name) |
| Content rating | Questionnaire: category *Utility/Productivity/Communication*; users can interact (circles) — **yes**; shares location — **no** (GPS stays on the phone); UGC: circle names and join notes, moderated with **in-app Report** and admin review |
| Target audience | **18 and over** (circles with strangers and a public ranking; avoids the Families policy) |
| News app | No |
| Health apps | Declare: fitness/activity tracking, wellbeing; **not** a medical device |
| Health Connect | Declare read access: Steps, Sleep, Hydration, Weight and **background read** — justification: "Shows today's steps, sleep, water and weight on the home screen and keeps the step ring current when the app is closed. Read only, never written, kept on the phone unless the user turns on sharing (steps only)." |
| Foreground service (location) | Declare **location** type: "Records a walk or run the user starts, with the screen off, until they stop it. A notification is always shown." Attach a 30-second screen recording: Move → Start → lock screen → notification → Finish. |
| Usage access (screen time, Phone check) | Not a declared permission, but explain in the listing; the app shows a full-screen disclosure before asking |
| Account deletion | **App:** Profile → Your data → Delete. **Web URL:** https://bas1minute.pythonanywhere.com/delete-account/ |
| Government app / financial features | No |

### Data safety form

Data "collected" = sent off the phone. Encrypted in transit: **Yes**. Users can request deletion: **Yes**.

| Data type | Collected? | Shared with third parties? | Optional? | Purpose |
| --- | --- | --- | --- | --- |
| Name | Yes | No | Required (can be any name) | App functionality, account |
| Health and fitness → Fitness info (daily steps) | Yes | No | **Optional** (sharing switch, off by default) | App functionality (circles, ranking, competitions) |
| App activity → Other (daily screen-time minutes) | Yes | No | **Optional** | App functionality |
| Device or other IDs (push token; a SHA-256 of the app's Android ID, so a reinstall on the same phone keeps the same account) | Yes | No | Required | App functionality, account management, fraud prevention (one person, one ranking entry) |
| Location | **No** (GPS routes never leave the phone) | — | — | — |
| Photos, contacts, messages, files, audio, financial | No | — | — | — |

Everything else (mood, calm, sleep, water, weight, routes, Phone check, app-by-app usage) is processed **only on the
device** and is not "collected".

## 5. Every later release, and forcing an update

1. Merge to `main` → GitHub builds `2.0.N+1` → upload the `.aab` to a Play track → roll out.
2. Members get **"Update available"** on Today as soon as Play has it (in-app update, installs without leaving the app).
3. To make it **mandatory**: when the rollout reaches 100% and the version is live, open the website admin →
   **App versions** → set *Minimum version code* to the new build number (and *Latest*). Every older app then shows a
   full-screen **"Time to update"** screen, and the API answers 426 to it, until it updates. Data on the phone stays.
   (Alternatively give a release in-app update priority 4–5 with the Play Developer API.)
4. Never raise the minimum above a version that isn't fully rolled out on Play yet, or some people can't update.

## 6. Size

Release builds are shrunk and obfuscated (R8), keep only English and Hindi resources, strip debug logging, and ship
fonts subset to the characters the app uses (~330 KB smaller). As an `.aab`, each phone downloads only its own screen
density and language. Expect roughly a 7–9 MB download.
