# Website → app feature map

Every member feature of the Django website, and where it lives in the app. The app never opens the
website; it uses the server only through `/api/v1/`.

| Website | App | API used |
| --- | --- | --- |
| Sign up, log in, log out | Welcome → Create account / Log in; Profile → Log out | `auth/signup`, `auth/login`, `auth/logout` |
| Dashboard: 7K + 4 ring, steps, screen time | Today: ring, Steps left, Screen time | phone + `today` |
| Bursts | Today: Bursts tile → **Start 1 min** (timer) or **+ I already did one** | `day` |
| "Try a 1-minute burst" timer (home page) | Today → Bursts → Start 1 min (squats / stairs / fast march) | `day` |
| Calm (1-minute breathing) | Calm tab | `calm` |
| Mood check-in + Tele-MANAS support | Today: How are you feeling? | `mood` |
| Cheers received | Today: From your circles today | `today` |
| 7-day charts and averages | Today: This week (Steps / Screen) | phone |
| Log a day (last 7 days) | Today → Log a day (+ sleep, water, weight) | `day` |
| Circles list, create, join by code | Circles tab | `circles`, `circles/join` |
| Leaderboard, team goal, Shabash! / Chalo!, feed | Circles → a circle | `circles/<id>`, `cheer` |
| Invite (link + WhatsApp) | Circles → a circle → Invite on WhatsApp / Share invite (code) | `circles/<id>` |
| Share screen time per circle | Circles → a circle → What you share here | `settings` |
| Owner: rename, change type, new code, remove, delete | Circles → a circle → Manage circle | `rename`, `invite`, `remove`, `delete` |
| Leave circle | Circles → a circle → Leave this circle | `leave` |
| Profile: name, city, goals | Profile → Your details and goals | `me` |
| Delete account and data | Profile → Delete my account and data | `me/delete` |
| Connect your phone (sync key) | Not needed: logging in connects this phone | — |
| Our idea, How we're different, Free for everyone | Profile → About (native screens) | — |
| Privacy | Profile → About → Privacy (native; also Health Connect's link) | — |
| Backup to server | Profile → **Sync with server** (two-way: upload + restore 90 days) | `activity`, outbox, `history` |

**App only:** distance from steps, the 21-day habit grid and streak, Today's thought (daily quote) and the Spin the health wheel game, Insights (social media time check and 1 / 3 / 10-year forecast, phone and sleep habits,
water, weight), automatic steps / screen / social / sleep / water / weight, Samsung Health setup, offline mode.

**Website only (not member features):** landing pages, waitlist and launch countdown, staff admin.

**App only (v1.1): GPS walk / run** — Today → Walk or run with GPS: live map route, km, pace, splits, auto-pause, voice per km, round-trip detection, share picture (map or route, post or story, hide start/finish), save to Gallery, GPX download. Phone only, never synced.

**App only (v1.2): Phone check** — Insights → Phone check: health score, battery level history and screen-on/off drain, estimated battery by app, mobile/Wi-Fi data per app (foreground vs background), apps running in the background, battery exemptions, permissions and powerful access, sideloaded apps, evening alert. Phone only, never synced.

**v1.6: Always the latest version** — every release published on Google Play is required: once Play can deliver it
to a phone, "Time to update" replaces the app and Play's update opens by itself (waits for a running walk or detox).
Server *Minimum version code* stays as a kill switch; *Latest* forces non-Play installs. See docs/PLAY_STORE.md §5.

**v2.0: Mandatory first-run tour** — right after setup, before the tabs appear, a short page-per-feature tour
(Today, Move, Circles, Detox, Goals, privacy) that can't be skipped, only moved through. Shown once per account;
members who already existed when this shipped are not retroactively forced through it. See ui/TourScreen.kt.

**v1.9: Life goals — short, mid and long term, with a why and a photo** — Today → Your goals → Life goals:
three honest horizons (1–3 months, 1–3 years, 5–10 years), up to 3 goals each. Each goal has a free-text plan,
a "why it matters" shown with a photo of who or what it's for (a daughter, a mother, a grandfather — whatever
the reason is), a named obstacle and an "if X then I will Y" plan for it (implementation intentions), and a
milestone checklist with a time-vs-progress pace indicator (ahead / on track / behind). Pausing a goal shows the
why and progress first, then keeps everything if the person changes their mind later. Photos are stored only on
this phone; everything is phone-only, never synced.

**v1.8: Goals — the fourth pillar** — Today → Your goals: up to 5 at once, from presets (step goal, 7+ hours
sleep, stay under your screen limit, a 2-hour kept detox) or write-your-own (study, read, no phone after 10, anything
the phone can't measure, checked off by hand each day). Each goal gets its own streak and "X/7 this week" from data
the app already has — steps, sleep, screen time, detox — so Goals never introduces new tracking, only gives the
existing numbers a reason. Phone only, never synced.

**v1.7: Real blocking on Android 14+, searchable allowed-apps picker** — the block screen is now a system
overlay window (TYPE_APPLICATION_OVERLAY), not a launched Activity: Android 14 tightened background-activity-start
rules so holding "Display over other apps" alone no longer let a plain Activity launch from the detox service
succeed on many phones, which meant blocked apps could silently stay reachable. An overlay window sidesteps this
entirely. Picking the 3 always-allowed apps now has a search box.

**v1.5: Everyone in the global circle, daily nudges, Detox tab**
- **Bas 1 Minute India** (global circle): new members are in from setup (stated on the name screen); earlier members who
  weren't sharing get one "Count me in" invite. Periods: Today, Week, Month, All time. Every published challenge
  includes everyone in the global circle: no Join button, just Leave and Rejoin. Leave the whole ranking (and rejoin)
  any time from Circles → Bas 1 Minute India.
- **Daily nudges** (Profile → Daily nudges, on by default): ~8:30, 13:30 and 19:30, written on the phone from today's
  steps, screen time and detox: a walk when behind, a detox challenge when screen time is over the limit, a cheer when
  the goal is done. Silent during a detox; one notification replaces the last. NudgeWorker + Nudges.kt (unit tested).
- **Detox tab** replaces Insights in the bottom bar: *Focus* (Digital detox, clean hours, promises) by default and
  *Insights* (screen time, forecast, Phone check) beside it. A Digital detox card on Today starts one in a tap.

**App only (v1.4): Move without GPS** — Move → **Map with GPS** on or off. Off (or if location or GPS is refused at
Start), walks and runs still record: distance from the step counter × your step length (longer steps when running
faster), moving time, pace, speed, km splits, steps and cadence, with auto-pause when you stop. Rides, or phones
without Physical activity / a step counter, record time only. After finishing, the distance can be added or corrected;
for a walk, that can also set your step length. No map, route or GPX for these; the share picture shows steps and
time instead. Phone only, never synced.

**App only (v1.3): Digital detox** — Insights → Digital detox: promise 30 min to 4 h (or custom, up to 12 h) with only
calls, messages, contacts, camera and alarms; WhatsApp, browsers, social media and every other app show a pause screen.
Up to 3 extra apps can be allowed (Maps, UPI). Ending early (after a 10-second "Keep my promise" dialog), turning off a
needed permission, or really using a paused app (checked against Android's usage history) is recorded as **Guilty**.
Clean hours: each full hour from 6 AM with no social media or browser use; today's hour grid, 7-day chart, promises kept
vs guilty, and a streak. Needs Usage access and "Display over other apps". Phone only, never synced.
