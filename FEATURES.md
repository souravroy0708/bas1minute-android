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

**App only:** Insights (social media time check and 1 / 3 / 10-year forecast, phone and sleep habits,
water, weight), automatic steps / screen / social / sleep / water / weight, Samsung Health setup, offline mode.

**Website only (not member features):** landing pages, waitlist and launch countdown, staff admin.
