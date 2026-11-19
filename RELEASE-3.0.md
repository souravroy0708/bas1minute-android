# Bas 1 Minute 3.0 — release checklist

## What's in 3.0
- **Home**: priority layout — Circles and Goals as big cards, 8 everyday features, Water/Log as quick buttons;
  "New" markers and press-and-hold help for the first two weeks.
- **Setup**: 4 guided steps (steps → screen time → notifications → what's on). Everything is ON by default
  (Circles, step sharing, India ranking and monthly challenges, nudges, App Timers, team messages); members
  opt out with one switch there or in Profile. "Start counting" on Home + up to 3 reminders if steps are skipped.
- **History**: every day kept forever on the phone (compact), any week / month / year / all time / custom range,
  personal records (best day, best week, streaks, all-time steps) and 12 step badges with a "New badge" notification.
- **First two weeks**: one evening tip a day about a feature not yet used (max 10, stops after 14 days).
- **Detox**: normal mode needs only Usage access; optional **Strict mode** brings back the countdown pause screen.
- **Voice typing** in Diary and Goals, with guided fixes when the phone's speech service is off.
- **Privacy**: private-screen shield (Goals/Diary), no cloud backup/device transfer, no analytics, Firebase only for pushes.
- **Offline-first**: everything except Circles/ranking/backup works without internet.
- Static website copy in `docs/` for GitHub Pages.

## Play Console — update before submitting
1. **Permissions declaration**
   - `FOREGROUND_SERVICE_SPECIAL_USE` → "User-started digital detox in Strict mode; covers apps the user chose to
     pause with a countdown until the time they chose." Attach a short screen recording.
   - `SYSTEM_ALERT_WINDOW` → same feature (Strict mode only, opt-in).
   - `RECORD_AUDIO` → voice typing in Diary/Goals; only while the Listening window is open.
2. **Health Connect declaration**: Steps, plus the optional **READ_HEALTH_DATA_HISTORY** ("import older steps").
3. **Data safety**
   - Collected & shared with other users: name, daily step total, daily social-media minutes (Circles/ranking).
     Optional for the user (can turn off in Profile). Encrypted in transit. Deletion: in-app + website.
   - Audio: **not collected**. App activity (which apps): **not collected** (stays on device).
   - No analytics, no ads, no advertising ID.
4. **Privacy policy URL**: the live site, with the GitHub Pages copy as backup.
5. **What's new** (paste):
   > A new Home with everything one tap away, your full step history with records and badges, voice typing in your
   > diary, and an optional Strict mode for detox. Your diary and goals stay private on your phone.

## Test before release (fresh install on 2 phones, one Android 14+)
- [ ] Setup: allow steps, usage access, notifications → Home shows steps; "Start" leaves all switches on.
- [ ] Fresh install, skip steps → Home shows **Start counting**; reminder arrives next day 10:00–20:00.
- [ ] Circles: new member appears in India ranking and the monthly challenge after first sync.
- [ ] Profile: turning off sharing / Online features works and stays off after restart.
- [ ] History: Week / Month / Year / All / Custom; tap bars; badges show; records match the 21-day card.
- [ ] Detox normal: reminder notification when a paused app is open; promise judged correctly at the end.
- [ ] Detox Strict: allow overlay → open Instagram → countdown screen within ~1 s; screen off/on still blocks.
- [ ] Voice typing: allow mic → speak in Diary; deny with "Don't ask again" → settings guide appears.
- [ ] Diary screenshot is blocked (Private screens on); App lock → blank card in Recents.
- [ ] Airplane mode: Home, History, Goals, Diary, Detox all work; no error banners.
- [ ] Light and dark theme; Hindi; large font size.

## After release
Freeze features for 2–4 weeks. Watch Play Console → Android vitals (crashes, ANRs) and fix only bugs.
