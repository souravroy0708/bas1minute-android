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

**v2.3: Sync Goals, Diary and Digital detox to the website** — Profile → "Sync goals, diary and detox," off by
default: turns on a one-way upload (phone → website) of every daily goal and check-off, every life goal and its
milestones and photo, every diary entry and its photo, and every digital detox session, to a new read-only
dashboard on bas1minute.pythonanywhere.com (Goals, Diary, Detox pages). A manual "Sync now" button; no background
sync yet. Stable client UUIDs were added to the local goals/life_goals/life_goal_milestones/diary_entries tables
(DB v11) so re-syncing is always a safe upsert, keyed to the server's apps.journal models. Photos travel as base64
over the app's existing signed API, and are served back only to their own owner, never a public URL. See
sync/JournalSync.kt.

**v2.2: Circles moved, Goals is now a tab** — bottom bar is Today, Move, **Goals**, Detox, Profile. Goals opens to
Goals (daily + life goals) with Diary one tap away, same pattern as Detox/Insights. Circles (Bas 1 Minute India,
private circles, competitions) moved to Profile, one tap away, exactly where it was before just relocated.

**v1.9.1: Block only what you choose to block, not everything** — Detox flipped from a strict allow-list (block
everything except a few picked apps) to a block-list the member curates. Two defaults, both on: Social media &
short video (Instagram, Facebook, YouTube, X, Snapchat, TikTok-style apps...) and Web browsers (Chrome, Firefox,
every browser installed) — either can be turned off entirely, or one app exempted out of it by name (e.g. keep
LinkedIn while still blocking Instagram). Everything else — calls, messages, WhatsApp, music apps, UPI and
payment apps, maps, office/work apps, games — stays usable by default without being named, because the model
now fails open, not closed. "+ Block another app" lets a member add any other app they personally find
distracting, with a small "Usually left open: Music / UPI & payments / Maps / Work" tag when known. See
detox/AppRules.kt and DetoxLogic.isPackageBlocked (pure, unit-tested).

**v1.3.1: Stop over-counting steps when a watch is also connected** — Health Connect's own daily step total was
being trusted as-is, but Health Connect does not reliably deduplicate steps when more than one app writes them
(a watch's companion app, Google Fit, Samsung Health and, on Android 14+, the phone itself can all write for the
same walking) — it often just adds every contributing app together for the overlapping time range, which is
exactly the "more steps than I actually walked" bug reported with a watch in use. Fixed by reading Health Connect's
raw per-app records instead of its aggregate, grouping by which app wrote each one, and keeping only the single
largest app's total for the day — never a sum across apps — the same "take the larger, never the total" rule
this app already used one level up, between Health Connect and the phone's own sensor. See StepsReader.kt.

**v2.1.3: Real fix #2 — stop missing app switches entirely on some phones** — the previous fix (wake lock) was
correct but incomplete: detection used an advancing cursor over Android's usage-event stream, and on phones that
deliver those events a few seconds late (OnePlus/OxygenOS confirmed, likely other heavily customized OEM builds
too), a delayed event's timestamp could fall behind the cursor before it became queryable — permanently losing
that app switch, every time, no matter how reliably the service's own loop ran. UsageReader.currentForegroundApp
now re-reads the last ~20s fresh on every poll instead of advancing a cursor, and cross-checks against aggregate
usage stats (lastTimeUsed) as a fallback when the event stream itself comes back empty. See detox/UsageReader.kt.

**v2.1.2: Real fix for live blocking, and a 5-minute option** — found the actual cause behind "detox never blocks
anything, but still marks itself guilty afterward": the service's own polling loop held no wake lock, so several
OEM skins (OnePlus/OxygenOS confirmed) throttle it to a near-stop while the foreground notification and countdown
keep working fine (those are drawn by the system, not this loop) — guilty detection worked because that's a
separate, one-time check against Android's usage history at session end, regardless of whether the loop ran.
Now holds a partial wake lock for the session, the loop can't be silently killed by one bad tick, and Start a
detox explains the OnePlus/Xiaomi/Oppo/Vivo battery-optimization + autostart settings this depends on, with a
direct button to them. Minimum detox length is now 5 minutes (was 15), useful for exactly this kind of testing.

**v2.1.1: Detox diagnostics** — when the pause screen can't be shown (most often: "Display over other apps" was
never actually granted, or Android's restricted-settings block for apps installed outside Play Store is silently
preventing it), Bas 1 Minute now says so on screen instead of quietly letting the blocked app through. A "Preview
the pause screen" button in Start a detox lets you confirm the permission actually works before relying on it.

**v2.1: Diary** — Today → Diary: a slam book of your own life (first school, a daughter's birth, the first job,
a college memory — any milestone, dated whenever it happened, with a free-text story, a category and an optional
photo) plus a one-a-day reflection — how hard you worked toward your goals and how satisfied you feel about today,
on two 1-5 scales, with a note. "On this day" resurfaces a milestone from a past year automatically. A reflection
streak, and an honest effort-vs-satisfaction read-out over time. All phone only, never synced, even with sharing on.

**v2.3.5: The Today hour grid now shows the whole day, and flags overnight phone use in red** — two fixes
from a member screenshot. First, "Used" was a light coral that read as too close to "Clean" green at a glance;
it's now the same strong red (Danger) the rest of the app already uses for warnings, with white text for
contrast — matching what was asked for directly. Second, the grid only ever showed 6am–11pm; the underlying
hour-cell logic already supported a full 24-hour day (it just wasn't being asked for one), so this was a
one-line fix at the call site, not a rewrite — it now shows all 24 hours, midnight to 11pm, so the early-morning
hours aren't invisible. New: any hour between 11pm and 6am that shows as "Used" gets an extra gold border on
top of the same red fill — the one span of the day the app explicitly recommends no phone use at all, now
visually called out as more than just an ordinary busy hour, with a line explaining the gold border beneath the
legend. One new test (hourCells_fromMidnight...) confirms the full-day range and that an overnight use shows up
correctly; 124 tests total still pass. See detox/DetoxController.kt and ui/DetoxScreen.kt (HourBox, SLEEP_HOURS).

**v2.3.4: Fixed a real layout bug in every full-screen story/dialog** — the Week in Review story (and five
other full-screen dialogs: celebrations, the walk/run summary, the detox block-choice picker, the share sheet,
Phone Check) showed a gap at the very top and bottom of the screen where the screen underneath bled through,
with "Close"/"Next" sometimes appearing to render twice. Root cause: a Compose `Dialog` opens its own separate
Android window, which does NOT inherit the main Activity's own edge-to-edge configuration — even with
`usePlatformDefaultWidth = false`. Added one shared `EdgeToEdgeDialogWindow()` call (Components.kt) that every
affected dialog now calls as its first line, explicitly disabling that window's own auto-fit-to-system-bars
behavior so it draws truly edge-to-edge like the rest of the app, with its own existing `statusBarsPadding()`/
`navigationBarsPadding()` modifiers doing all the positioning, as intended. 124 tests unaffected (UI-only fix).

**v2.3.3: Detox stops polling at full speed while the screen is off** — the live block-screen check ran every
0.6 seconds unconditionally, even with the screen off, when nobody can freshly open a blocked app anyway.
This mattered little for a quick manual detox, but Schedules (just built) makes long overnight sessions
routine — a 9-hour bedtime window where the phone sits untouched, screen off, for most of it. Now the check
backs off to once every 30 seconds while the screen is off, and a screen-on broadcast receiver cancels that
wait immediately the moment the screen turns back on — resuming full 0.6s-speed checking right away, rather
than waiting out whatever's left of a slow interval, since the screen turning on is exactly the moment someone
could open a blocked app. The wake lock itself is unchanged (the OEM throttling it guards against isn't only a
screen-off phenomenon) — only the poll interval adapts. Compiles clean with zero errors against the real
Android SDK (this file depends only on core framework APIs, no missing third-party jars to account for). See
detox/DetoxService.kt.

**v2.3.2: One background wake-up instead of up to four** — a battery audit found that Session Guard, Schedules
and App Timers had each registered their own independent 15-minute periodic job, on top of the battery/phone-
health logger that was ALREADY running on that same cadence — up to four separate OS wake-ups every 15
minutes for a member using all three opt-in features, three of which were pure duplication. All three now
ride on that single already-scheduled tick instead of registering their own: SessionGuardWorker, ScheduleWorker
and AppTimerWorker are gone as WorkManager jobs entirely, replaced by plain SessionGuardChecker/
ScheduleChecker/AppTimerChecker objects the shared tick calls directly — same behavior, same tests, one wake-
up instead of up to four. The foreground app is now looked up once per tick and passed to both checks that
need it, rather than each separately querying UsageStatsManager. Also fixed in the process: Schedules'
periodic job was being unconditionally registered at every app start even for members who had never created
a single schedule — it now costs nothing beyond the tick that was already happening for battery logging.
124 unit tests unaffected; this is purely how the checks are triggered, not what they do. See
phone/BatteryLog.kt.

**v2.3.1: App Timers — a real daily limit per app, not just a nudge** — closes the gap Session Guard always
had: it could only ever nudge someone using Instagram's own app past 15 minutes, never actually stop them the
way Site Guard already stops facebook.com in a browser. Detox → "App Timers": a short, member-chosen list of
apps, each with the same kind of daily time budget (15–180 min, default 30) Site Guard already gives a
website — spend it, and the app is paused until midnight. No new permission: Android's own UsageStatsManager
already tracks exact foreground time per app, so unlike Site Guard this needed no incremental accumulation
logic at all, just a direct daily-total query. Shares its budget math with Site Guard (SiteGuardLogic) rather
than duplicating it — one definition of "over budget" used by both. Checked every 15 minutes, the same
battery-respecting cadence as Session Guard and Schedules, with the same honest trade-off: blocking can lag a
few minutes behind the exact limit, which a persistent always-on service could avoid but only at the cost of
its own permanent notification. See detox/AppTimerLogic.kt, detox/AppTimerWorker.kt, detox/AppTimerOverlay.kt.

**v2.3.0: Schedules — Detox that starts and ends itself** — the single clearest gap against every well-known paid
focus app (Freedom's Scheduled Sessions, Opal's Schedules, Digital Wellbeing's own Bedtime mode): until now,
Detox only ever ran if a member remembered to start it. A new "Schedules" section in Detox lets a member set
recurring windows — any days, any start/end time, including overnight ones that cross midnight (tested
specifically, since that's the easiest place to get an off-by-one wrong) — that auto-start and auto-end a
detox using exactly the block choices already set up in Detox itself: no separate category list to configure
twice, no new permission, no new risky subsystem — it's the same DetoxController, BlockOverlay and block-list
every existing Detox session already uses, just started by a 15-minute periodic check instead of a tap. Never
touches a detox a member started by hand. See schedule/ScheduleLogic.kt, schedule/ScheduleStore.kt,
schedule/ScheduleWorker.kt.

**v2.2.1: Sync is now a real backup, with restore after reinstall** — the one-way "mirror to the website" sync
became a genuine backup: if a phone is recognised as one that synced before (same device, same way auth/device/
already reconnects circles and ranking after a reinstall) and local Goals/Diary/Detox storage is genuinely
empty, the app now offers to bring that history straight back — life goals, milestones, diary entries and
their photos, habit goals and check-ins, detox sessions, all of it, keyed by the SAME ids they had before so a
normal sync afterward never creates duplicates. Deliberately scope-limited rather than a general two-way sync:
restore only ever runs when local storage is confirmed empty first, so there's no merge logic to get wrong —
if anything is already there, it says so and changes nothing. A manual "Restore from backup" button in Profile
covers it too, for whenever the automatic prompt was dismissed or didn't fire. See sync/JournalRestore.kt.

**v2.2.0: Block specific time-wasting websites, inside any browser** — Profile → "Block distracting websites":
a small daily time budget per site (15 min default, adjustable) for a member's own list (facebook.com,
instagram.com, youtube.com, x.com, tiktok.com, reddit.com... editable), enforced in any browser — Chrome,
Firefox, Samsung Internet and the rest. Once a site's budget for today is spent, it's paused until midnight;
every OTHER site in the same browser — work, email, banking, search — is never touched. This needed
Accessibility access to read the browser's own address bar (the only way to see which WEBSITE is open, as
opposed to which app), so it's a deliberate two-step turn-on: a full in-app disclosure screen explains exactly
what it reads and doesn't, before Android's own Accessibility settings ever open — the app can never grant
this to itself. The accessibility service's own config file restricts it, at the OS level, to receiving events
from browser apps only: WhatsApp, banking apps and everything else cannot send it anything, not by policy but
because Android itself won't deliver those events. Phone-only, like every guard before it. See
siteguard/SiteGuardLogic.kt, siteguard/SiteGuardAccessibilityService.kt, siteguard/SiteGuardOverlay.kt.

**v2.1.5: Catches a long, unbroken scroll before it eats the day** — Profile → "Long-session nudges," on by
default: checked every 15 minutes (Android's own minimum for this kind of background work, which happens to
match exactly what was asked for), it looks at whether the person is, right now, in the middle of an unbroken
stretch on one distracting app — Instagram, YouTube, Facebook, any browser (reuses AppRules.isDistracting, the
same definition Detox's clean hours already use). A notification glance or quick app-switch-and-back under 90
seconds doesn't reset the clock — real sessions have micro-interruptions. Three nudges per continuous session,
escalating — 1st gentle and reflective, 2nd a direct offer to start a detox, 3rd the last one — then silence for
the rest of that session rather than repeating forever; tested explicitly for never using shaming language.
Its own notification channel and its own toggle, independent of the four scheduled daily nudges, so a member can
turn either off without the other. Phone-only, same as every nudge before it: nothing to sync to the website.
See nudge/SessionGuard.kt and nudge/SessionGuardWorker.kt.

**v2.1.4: A fourth check-in for hard mornings, plus the occasional surprise** — a new 10 AM check (Nudges.isSlowStart)
looks at steps so far against a generous floor (not the daily goal — nobody reasonably hits that by mid-morning)
and, only on a genuinely slow start, sends one supportive nudge ("hard days happen" framing, never shaming
language — tested for it). Silent on an ordinary morning, same restraint as the rest of the system. Separately,
a new MotivationWorker fires once a day at a random time (11:00-20:00, picked fresh each day) with a short
encouraging line from a rotating pool that never repeats itself back-to-back — unlike the four fixed check-ins,
this one is deliberately unpredictable. Both respect the same rules as the existing nudges: silent during a
detox, silent without notification permission, share the "Daily nudges" toggle rather than adding a new setting.
Phone-only, like every nudge before it — nothing to sync to the website, which has its own, separate,
server-authored push-campaign system for admin-sent messages. See nudge/Nudges.kt and nudge/MotivationWorker.kt.

**v2.0.5: Finished the loading-state audit** — Circles had the same bug as Goals/Diary/Life goals, and a more
visible version of it: "Your circles" treated `data == null` (not loaded yet — this one loads over the network,
so it can take a real moment) the same as "genuinely has zero circles," so the "start a circle" prompt could sit
there wrongly for however long a slow connection took. Fixed with the same Skeleton pattern. Checked Move and the
other two Circles sections (Global ranking card, your pending requests, Competitions) too: all three already
handled loading correctly — Move just used plain "Loading…" text where Skeleton is now the house style, so that
one line was swapped for visual consistency, not because it was wrong.

**v2.0.4: No more flash of the wrong empty state** — Goals, Life goals and Diary's timeline each start with an
empty list by default, then fill in once SQLite has actually loaded — the problem is that's the exact same shape
as the real empty-state, so for a frame on every single open (even for someone with years of data saved), the
"add your first goal" / "your story starts here" card flashed before the real content replaced it. All three now
track loading separately from empty, and show the existing `Skeleton()` placeholder in between — previously
only used in one screen in the whole app. Phone-only change, nothing to sync to the website: this is purely how
the phone looks while its own local data loads.

**v2.0.3: Diary gets the same streak badges as Detox** — a daily-reflection streak (already shown as a number)
now earns the same 3/7/21/50/100-day badges and one-time confetti celebration as Digital detox's focus streak,
reusing the same StreakLogic thresholds rather than inventing a second set. The website's Diary page shows the
same badges now too, with identical wording, so the app and the website never disagree about what counts as a
badge.

**v2.0.2: Celebration, where it was missing** — the app already had a well-built confetti-and-haptic
celebration for the daily step goal; it just wasn't used anywhere else. Extended to the two moments that
arguably deserve it more: finishing a life goal (shows their own "why" read back to them, not just a plain
confirm dialog) and crossing a new detox focus-streak badge (3/7/21/50/100 days) the first time it happens,
never again for the same badge. Same shared confetti component everywhere now, not three different
implementations. See ui/Celebration.kt.

**v2.0.1: The first-run tour, reanimated** — swipeable pages (spring-flung, not just tap), a parallax carousel
feel (the page you're leaving shrinks and fades), a top progress bar that fills smoothly and tracks the swipe
itself mid-gesture, a light haptic tick on every page change, and a hero per page that pulses, sweeps in a
progress ring, springs into place with a little bounce, and floats gently rather than sitting frozen — plus a
small spark burst on the very last page. Content refreshed to match the current app: Detox's block-choice model,
Goals+Diary combined, Circles now under Profile, sync off by default. All hand-built with Canvas and Compose's
own animation APIs — no new dependency. See ui/TourScreen.kt.

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
