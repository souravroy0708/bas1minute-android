# Community API: global ranking, circle search, join requests

Implemented in the website (v12): `apps/circles/services.py`, `apps/circles/scoring.py`, `apps/api/views.py`.
Every call is `POST /api/v1/<path>` with the usual signed headers and `Authorization: Bearer <token>`.
Errors: `{"error": "Human message"}` with 4xx.

## Rules (server side)

- **Global circle:** every account is a member automatically (no membership rows needed; no join or leave).
- **Who is ranked:** accounts with `share_activity = true` AND `global_visible = true`. Everyone else can view but has `me.rank = null`.
- **Points:** same as circles: one per day at the member's own step goal (only steps are shared).
  Week = Monday to Sunday (Asia/Kolkata). Ties: more steps first, then earlier account.
- **Fair play:** only `steps_source` in (`health_connect`, `phone_sensor`) counts for the global board; cap 50,000 steps/day.
  Manual / typed steps still count inside private circles.
- **Names:** full names (and city, if set) are shown; nothing else about a member. The caller's row has `is_me: true`.
- **Cache** the global board for 5 minutes per period; compute the caller's rank separately.
- **Search:** only circles with `discoverable = true`. Case-insensitive on name, city and kind. Max 30 results; empty `q` = most team points this week.
  Rate limit 30 searches/min. Block offensive names on save (shared word list).
- **Requests:** max 5 pending per user; message ≤ 140 chars; one request per circle; after a decline, no new request to that circle for 7 days.
  Approve fails if the circle is full (20). Joining by invite code still works without approval.
- **Notifications (FCM, optional):** owner on new request; requester when approved or declined.

## Endpoints

### `global/`  `{"period": "week" | "today"}`
```json
{"name": "Bas 1 Minute India", "period": "week", "members": 18930, "ranked": 12481, "days_left": 3,
 "top": [{"rank": 1, "name": "Ra*** S.", "city": "Pune", "points": 21, "steps": 98211, "is_me": false}],
 "me": {"rank": 2341, "points": 9, "steps": 41022, "shares": true, "visible": true}}
```
`top` has up to 100 rows. `me.rank` is `null` when not ranked. `city` is optional (only if the member set one).

### `me/`  `{"global_visible": false}` — existing endpoint, new field. Returns the usual `{"me": {...}}`.

### `circles/search/`  `{"q": "hyderabad"}`
```json
{"results": [{"id": 12, "name": "Hyderabad Morning Walkers", "kind": "other", "kind_label": "Other", "city": "Hyderabad",
  "size": 18, "full": false, "owner": "Ra*** R.", "about": "KBR Park, 6 am", "week_points": 41,
  "status": "none"}]}
```
`status` for the caller: `none`, `pending`, `member`, `declined`.

### `circles/<id>/request/`  `{"message": "Hi!"}` → `{"status": "pending"}`
### `circles/<id>/request/cancel/` → `{}`
### `circles/requests/mine/`
```json
{"requests": [{"circle_id": 12, "name": "Hyderabad Morning Walkers", "status": "pending", "ago": "12 min ago"}]}
```
Show approved / declined for 7 days, then drop them.

### Owner only
- `circles/<id>/requests/` → `{"requests": [{"id": 77, "name": "Pr*** S.", "message": "Hi", "ago": "3 h ago", "shares": true, "week_points": 12}]}`
  (`week_points` null when the requester doesn't share).
- `circles/<id>/requests/<request_id>/approve/` → `{}` (adds the member)
- `circles/<id>/requests/<request_id>/decline/` → `{}`
- `circles/<id>/visibility/`  `{"discoverable": true, "about": "One line, ≤ 100 chars"}` → `{}`
- `circles/<id>/` (existing detail): add `"circle": {"discoverable": true, "about": "..."}` and `"me": {"requests_count": 2}`.
- `circles/list/` (existing): add `"requests_count"` per circle for owners.

## Suggested models (Django)
- `Circle`: add `discoverable = BooleanField(default=False)`, `about = CharField(max_length=100, blank=True)`, `city` (optional), index on `name`.
- `CircleJoinRequest(circle, user, message, status[pending|approved|declined|cancelled], created_at, decided_at, decided_by)`,
  unique on (circle, user) for pending.
- `User`/profile: `global_visible = BooleanField(default=True)`.

## Website sign-in (approved from the phone)

- `web-login/check/` `{"code": "K7M2-QX9P"}` → `{"request": {"browser": "Chrome on Windows", "ip": "49.36.x.x", "ago": "just now"}}`
- `web-login/approve/` `{"code": "...", "allow": true}` → `{"status": "approved" | "denied"}`
- `web-login/sessions/` → `{"sessions": [{"id": 3, "browser": "...", "ip": "...", "since": "2 h ago", "last_seen": "just now"}]}`
- `web-login/sessions/<id>/signout/`, `web-login/sessions/signout-all/` → `{"signed_out": 1}`

Codes: 8 characters, 5 minutes, single use, stored as HMAC hashes; rate limited (bucket `weblogin`, 20 per 10 min per IP).
Only the browser that asked for the code can finish the sign-in.

## Competitions

- `competitions/` → `{"competitions": [<competition>]}` (live first, then upcoming, then ended in the last 30 days; top 3 each)
- `competitions/<id>/` → `{"competition": <competition>}` (top 100)
- `competitions/<id>/join/`, `competitions/<id>/leave/` → `{"competition": <competition>}`

`<competition>`: `{"id", "title", "description", "prize", "metric": "steps"|"goal_days", "metric_label", "starts_on", "ends_on",
"status": "live"|"upcoming"|"ended", "days_left", "days_to_start", "entrants", "scored",
"top": [{"rank", "name", "city", "score", "steps", "is_me"}], "me": {"joined", "rank", "score", "steps", "shares"}}`

Circles: `POST circles/ {"name", "kind", "discoverable", "about"}`; `circles/list/` adds `owned`, `owned_limit` (3 by default).
