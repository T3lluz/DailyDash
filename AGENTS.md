# DailyDash — Agent Guide

## Project Identity
Single-module Android app (Kotlin + Jetpack Compose). The **app label is "DailyDash"**; the package/module name is `com.macrotracker`. Keep both names in mind — they differ intentionally.

**Ship note:** When the user asks to commit/push, follow **[Commit, push & release](#commit-push--release-mandatory-when-user-asks)** so GitHub Releases + in-app What's New stay accurate (message quality, no manual version bumps, no `[skip ci]` on real changes).

## Build & API Keys
```bash
./gradlew assembleDebug      # standard debug build
./gradlew installDebug       # build + deploy to connected device
```
**Looking at a change:** on the phone, over wireless debugging (`adb mdns services`, `adb connect`, `./gradlew :app:assembleDebug`, `adb install -r`, `adb exec-out screencap -p`), and build on the laptop (t3lluz) when it is on: the home server has 14 GB, runs the house, and a Gradle build there took it to 92°. `tools/preview.sh` is the fallback for when neither the laptop nor the phone can be used: it runs the app on a headless emulator (an AVD named `dash`, KVM) with the dashboard's name pinned so cards have real data. `tools/preview.sh shot usage out.png` builds, installs, opens a screen and screenshots it; `open`, `tap`, `swipe`, `scroll`, `ui` (text nodes with their tap points) and `snap` drive it after that; `down` stops it. On the server, stop the emulator (`down`) and the Gradle daemon (`./gradlew --stop`) as soon as a look is over, and never leave both idling. The emulator has no Health Connect, location or calendar data.

API keys go in `local.properties` (never committed):
```
GEMINI_API_KEY=...
OPENAI_API_KEY=...
YOUTUBE_API_KEY=...
TWITCH_CLIENT_ID=...
TWITCH_CLIENT_SECRET=...   # optional locally; required for confidential Twitch apps / CI search
GITHUB_CLIENT_ID=...       # GitHub OAuth App Client ID for the home GitHub card (Device Flow; no secret in the APK)
GITHUB_TOKEN=...           # optional PAT fallback if OAuth Client ID is not set
```
**GitHub Releases:** `.github/workflows/build-apk.yml` writes the same keys from repo Actions secrets into `local.properties` before `assembleRelease`. Required for Twitch in published APKs: `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`. Required for GitHub Connect in published APKs: Actions secret `GH_OAUTH_CLIENT_ID` (OAuth App Client ID — **not** a PAT and **not** the automatic Actions `GITHUB_TOKEN`; GitHub forbids secrets named `GITHUB_*`). CI writes it as `GITHUB_CLIENT_ID` in `local.properties`. Optional mirrors of local keys: `GEMINI_API_KEY`, `OPENAI_API_KEY`, `YOUTUBE_API_KEY`. YouTube **Connect Google** does not use BuildConfig keys — it needs Google Cloud Console (YouTube Data API v3 + Android OAuth client for package `com.macrotracker` + `tester.jks` SHA-1); CI already signs releases with `app/tester.jks`.

At runtime, Settings → AI lets the user pick **Claude**, **Cursor**, **Hermes**, **Gemini** or **OpenAI** (`AiProvider`, in that order: plans first). Every picker marks how the phone is let in (`AiAccess`, `AiAccessBadge`): **Account** (Claude signed in with Claude Code's public OAuth, Pro / Max / Team / Enterprise, usage from the plan; Cursor, which has no sign-in for apps, so the dashboard runs Cursor's official CLI on the plan and the phone asks `<dashboard>/_api/v1/chat/completions` with a `cursor:` model per call, never touching Hermes' global model), **API key** (Gemini, OpenAI, or Claude without a session) or **Dashboard** (Hermes, the dashboard's own model). OpenRouter is gone (2.0 beta 1.6; a stored `openrouter` provider falls back to Gemini and its key is wiped). Each provider keeps its own model (`SettingsRepository.modelFor` / `setModel`; Claude's in `anthropicModelId`, Hermes' is the dashboard's); `AiModels` holds the suggestions and reads the provider's own list (Gemini's and OpenAI's with the key, Cursor's from `/_api/ai/status`), kept 12 h. `AiCredentialResolver.resolve()` returns the whole route (`ResolvedAiAuth`: provider, secret, access, model, dashboard URL) and every caller goes through `AiApiClient.generate(httpClient, auth, params)`; `AiApiClient.stream(...)` is the same call streamed (Claude with its thinking summarized, OpenAI, Cursor and Hermes over SSE; Gemini and web searches in one piece), which Tilesmith uses for its live line. Calls read with a 180 s timeout (`longReads`), since the shared client's 30 s cut long answers off. One view-model, `AiSetupViewModel`, backs every picker (Settings → AI, Tilesmith's composer, the meal estimate card): `AiPill` (logo, model, a dot in the access colour; `compact` is the logo alone) opens `AiPickerSheet` (ready providers with their models, search, the rest under Set up), drawn like the AI tab's Hermes model sheet. Settings → AI (`AiSettingsScreen`, after Tilesmith for Windows' AI page): the AI in use with Test and Change, every provider as a card with its badge, and the tapped one's setup (Claude's sign-in, Cursor and Hermes through the dashboard, keys, the model list as radio rows).

## Architecture Overview
```
com.macrotracker/
  DailyDashApp.kt          ← @HiltAndroidApp; configures Coil with browser User-Agent for F1 CDN
  MainActivity.kt          ← single activity, edge-to-edge, sets DailyDashTheme + MainScreen
  data/
    local/                 ← Room DB (macro_tracker.db): MacroLogEntity, GoalsEntity, MacroDao
                              (incl. `DayTotals` batch-totals projection + `getTotalsForDates()` query),
                              MacroRepository (suspend fns; `getDailySummary`, `getDailySummariesRange`,
                              `getDailySummariesBetween` — all use 2 DB round-trips via batch query),
                              SettingsRepository (two SharedPrefs: `macro_tracker_settings` +
                              `health_connect_settings`; per-metric health toggles + master toggle
                              + `weatherEnabled`/`calendarEnabled` all as `StateFlow`)
    remote/                ← Claude/Cursor/Hermes/Gemini/OpenAI via OkHttp (`AiApiClient`, `AiModels`; NutritionAiRepository),
                              WeatherRepository (met.no forecast, and MET warnings from MetAlerts as the
                              dashboard shows them, `fetchWarnings`, 15 min) + ClothingAdvice, LocationProvider
    chat/                  ← AI tab chat: ChatRepository, AiChatClient, BotPrompts, ServerAiHandoff
                              (server dashboard → AI tab context hand-off, keyed by seed id)
    hermes/                ← HermesClient: Tech support through Hermes on the dashboard server, via the
                              bridge at `<dashboardServerUrl>/_api/ai/*` (status, threads, chat + watch SSE,
                              stop, exec for approval cards, model, window, rename/pin/clear, commands,
                              upload) and the `/_api/live` change feed. Threads live on the server, shared
                              with the web dashboard. Send a thread's own `permId` back unchanged (it may be a
                              CLI mode like `agent`). HermesCatalog ports the web's picker rules
                              (`hermes-models.js` / `hermes-core.js`): families per provider, OpenCode's billed
                              rows behind search, `pickVariant` for depth/think/fast, modes per CLI source with
                              `modeFor` mapping a saved mode by kind. HermesCatalogTest pins them.
                              Turns outside the chat: the pane reports each live turn to the singleton
                              HermesActivityTracker (label from `HermesActivityLabel.of(live)`); when it
                              stops following a running turn it `release`s it and HermesTurnService
                              (specialUse FGS) rejoins it via `/watch` and sees it through. The first
                              follower to `finish` a turn wins. HermesNotifier: silent ongoing
                              notification (Android 16 promoted chip, Stop) plus a "done / needs you /
                              failed" one with Reply (RemoteInput → the service sends it), posted only
                              while the app is in the background. HermesActivityTest pins the labels.
                              HermesLiveFeed (bound in DailyDashApp) holds the one `/_api/live` connection
                              while the app is in front. Every turn on every thread rides it (`ch: ai`), so a
                              turn started on the web shows in the island, the ongoing notification and
                              the open chat; the feed reports it as follower FEED, which never overrides PANE
                              or SERVICE, and hands its turns to the service (`handOffFeed`) when the app
                              goes to the background. Duty and staff threads are skipped, as the web skips
                              them. It also re-emits `events` (the chat pane uses it instead of its own
                              connection) and `files` (stats/history/f1/f1live changed; the server dashboard,
                              Coming up and the F1 page refresh off it)
    server/                ← server monitor: SSH probes (SshClient/ServerProbe), ServerMonitorService +
                              ServerNotifier, encrypted ServerStore, ServerAdvisories.
                              ServerProbe has three lanes: the fast script (every poll: /proc, df, hwmon
                              temps, PSI, diskstats, cpu MHz, battery, ps with RSS), the detail script (every
                              30 s, only while a `startPolling(detailed = true)` consumer holds it: docker
                              stats, ps by RSS, `ss -tln`, running units, journal errors) and the news script
                              (15 min: updates). Scripts are POSIX sh with no variables, every command guarded.
                              DashboardSettingsSync: the web's settings blob (`/_api/sync`, last write wins)
                              shares the new-chat Hermes mode (`ai.perm`), `units` and `wind` with the phone,
                              and hands down the web's calendar switches (`cals`, `calMine`) read-only as
                              `SettingsRepository.dashboardCalendars`.
                              DashboardLink: `_stats.json`, `_history.json` and the tiles in `index.html` from
                              the dashboard server; it belongs to the SSH profile whose `hostname` matches
                              `host.sys.host` (`DashboardLink.belongsTo`).
                              Live notification: ServerMonitorService (specialUse FGS) → ServerLiveNotification
                              (RemoteViews in DecoratedCustomViewStyle: native text in the Compat notification
                              text appearances, pictures from ServerLiveGraphics). Graphics are drawn in dp at
                              the screen density (capped at 2.5×, 2× before Android 12), RGB_565, light or dark
                              with the system; keep them well under the 2 MB RemoteViews warning. Nothing is
                              redrawn while the screen is off. SystemUI always opens the notification at the
                              top of the shade, and an FGS channel cannot go below LOW, so the full panel is
                              opt-in: `liveNotificationDetailed` (More / Less action, or Settings) sets the big
                              view; otherwise the opened view is the compact row plus actions. Three actions at
                              most: More/Less, then Ask (compact) or Next server (panel), then Stop. Ask opens
                              Tech support on the worst advisory via `EXTRA_ASK_ABOUT` (also on alert
                              notifications). A tapped notification opens the dashboard on its server through
                              `ServerFocus`
    phone/                 ← Phone hub: this phone on the t3lluz dashboard (its top-bar phone button) and the
                              dashboard's commands on this phone, over the bridge's `/_api/phone/...` with a token
                              from PhoneHubPrefs (the first phone to report pairs; another waits to be accepted).
                              PhoneHub (bound in DailyDashApp) holds a link while the app is in front or
                              PhoneNotificationListener is bound: `/live?phone=<token>`, whose `phone` events carry
                              the queued commands inline (`cmds`; run straight from the event, deduped by id against
                              `/phone/commands`, which still runs on `hello`). A full PhoneSnapshot report every 30 s
                              in front / 3 min behind and on battery or torch changes; a *light* report (no health,
                              calendar) after commands. Media has its own lane: track, state, queue, volume,
                              ringer, DND, headphone and ringing changes send a media-only report (`collectMedia`) 180
                              ms after the player settles, skipped when nothing the dashboard shows changed and the
                              position did not jump (>1.5 s off its course). Every report carries `sentAt` so the
                              bridge can move the track position onto its own clock. Album art is keyed on the
                              picture itself (players often send the title first and the art later), sent once per
                              `artId`, and re-sent when the bridge answers `needArt`. `caps.cmds` lists the commands
                              this build runs and `caps.hub` (PhoneSnapshot.HUB_VERSION, now 2) the protocol round: the
                              dashboard offers only those, so add a command to COMMANDS when you add one to
                              `execute`. The battery reports watts from BATTERY_PROPERTY_CURRENT_NOW × voltage.
                              PhoneHubWorker reports every 15 min otherwise.
                              PhoneSnapshot reads the cheap things every time (device, battery with W/mA/capacity,
                              network with SSID/signal, storage, RAM, system switches, sound and the output device,
                              every media session, the next alarm, today's health,
                              events, weather); PhoneExtras keeps the slow ones (week of health, sleep stages, HR by
                              half hour, workouts, vitals hourly, screen time from UsageStats when Usage access is
                              granted) and refreshes them behind the report, poking a new one when they land.
                              The listener mirrors notifications (not this app's, summaries, ongoing media/progress or
                              secret ones) with their plain action buttons, and runs dismiss / reply (the app's own
                              RemoteInput action) / action (tap a button) / media (incl. another player by `pkg`,
                              custom actions, ±15 s) / DND (`requestInterruptionFilter`). Removals flush after 60 ms.
                              Commands: ring (PhoneRinger, alarm stream at full, restored after), torch, open-url (a
                              notification when the app is not in front: Android blocks background activity starts),
                              clipboard, note, volume (media/ring/notif/alarm/call), ringer, dnd, refresh.
                              PhoneShareActivity is "Send to desk" in the share sheet. Location is the weather cache's,
                              never a fresh fix. Settings: Connections → Phone hub (notification access, usage access,
                              what to share). The food log is not shared: the hub sends health, never macros.
    update/                ← GitHub Releases in-app updater (see "In-app updates")
    links/                 ← AppLinks + OpenWithActivity: mail, calendar (incl. `content://com.android.calendar`), YouTube,
                              Twitch and GitHub links (`LinkKind.forUrl`, LinkKindTest) ask once which app opens them (a
                              sheet listing the service's apps, then browsers) and keep the pick (prefs `app_links`;
                              Settings → Connections → Open links in changes it). A picked app that can't take the exact
                              link opens on its start screen. `openUrl`, the theme's `LocalUriHandler`, widgets'
                              `openUrlAction` / `openEventAction` (through OpenWithActivity), Tilesmith links and the phone
                              hub all go through it, so never start a bare ACTION_VIEW for these
    health/                ← HealthConnectRepository (read-only; lazy client; PERMISSIONS companion set);
                              reads: Steps, HeartRate, RestingHeartRate, OxygenSaturation,
                              RespiratoryRate, Distance, FloorsClimbed, ActiveCaloriesBurned,
                              SleepSession, TotalCaloriesBurned; has throttle cache to avoid
                              hammering Health Connect IPC on rapid ViewModel refreshes.
                              **Aggregates must be permission-scoped** — Health Connect fails the
                              whole `aggregate()` call when any requested metric is not granted,
                              so build the set from `grantedAggregateMetrics()` and let
                              `aggregateResilient()` retry metric-by-metric on failure.
                              `hasAnyPermissions()` ignores READ_EXERCISE_ROUTES (it reads no data
                              on its own). `readTodayStats()` throws only when every granted
                              metric failed, so the ViewModel can tell "empty" from "broken".
                              Body & Vitals: `readBodyVitals()` reads Weight, BodyFat, Height, Vo2Max,
                              HRV (RMSSD), RestingHR, SpO2, RespiratoryRate, BodyTemperature,
                              BasalMetabolicRate, BloodPressure, LeanBodyMass, BoneMass, BodyWaterMass,
                              BloodGlucose (90 days; the rate-like vitals as one mean per day over 30),
                              Hydration per day (14) and SkinTemperature (only where Health Connect has
                              `FEATURE_SKIN_TEMPERATURE`; the sheet asks via `requestablePermissions()`),
                              each only when granted, and reports the rest in `BodyVitals.notShared`.
                              Heart rate is also read in half hours (`HrBucket`, 30 days) for what an
                              HR-only watch still tells: resting HR when nothing writes one
                              (`derivedRestingHr`, `restingHrDerived`), sleeping HR and each day's
                              range. 90 days of daily peaks and the last 60 days' outdoor runs feed
                              `estimateVo2Max` when nothing writes VO₂ max; `estimateBmr` fills resting
                              energy. Those tiles carry an "Est." badge; the birth year
                              (`SettingsRepository.birthYear`, asked in the card) sharpens both. **Garmin** (the owner's watch, checked on his phone in Nov 2026) writes steps, distance, floors, heart rate, resting HR, sleep with stages and a total burn to Health Connect, and no active energy, HRV, SpO₂, respiratory rate, weight, VO₂ max, exercise routes, speed or cadence; workouts arrive only now and then. So the tab shows **calories burned** (the whole day's, resting included) where Apple shows Move: the Calories meter and ring, the Activity chart's Calories, the bests and a calorie goal (`HealthMetric.CALORIES` is total burn). Working Move out from the total was tried and dropped: the resting burn it needs couldn't be read reliably from Garmin's totals. Garmin's total is one record per day, midnight to 23:59:59, rewritten at each sync with the day so far; Health Connect's aggregate spreads it over the whole day, so a read up to now gave about half of it at midday. `writtenDailyTotals` sums the records by day and today's and each history day's figure is the larger of the two. `readSleepNights()` groups
                              two weeks of sessions by the 18:00 → 18:00 sleep day; `readHourlySteps()`
                              is today in 24 one-hour slices. HealthVitals.kt holds the pure maths
                              (daily means, 30-day baselines, readiness, sleep consistency, BMI / blood
                              pressure / VO₂ max / glucose bands, the heart-rate derivations and
                              estimates); HealthVitalsTest pins it. VitalRange.kt: the usual range
                              Body & Vitals draws and flags against (VitalRangeTest).
    f1/                    ← F1Repository via Ktor + OpenF1 API (https://api.openf1.org/v1/);
                              15-min in-memory + SharedPrefs disk cache. F1Season.kt: the season so far from
                              Jolpica's paged results and sprints (`SeasonHistory`: winners, finishes and points
                              round by round, fastest laps; refetched only when a round has run that it lacks),
                              a weekend's sessions in order, and what can still be won (F1SeasonTest). Podiums and
                              fastest laps on a driver are counted from it; the standings feed has neither. F1Circuits: circuit outlines from
                              bacinger/f1-circuits matched by coordinates (nearest within ~25 km), folded into a
                              0–100 box exactly as the dashboard's `f1_path()` does, cached per circuit for good
                              and attached to each `RaceScheduleEntry.outline`. A cancelled load must never be
                              cached (it used to cache a season with no circuits); a cache hit fills in any
                              circuits it is missing. F1Hub.kt + F1HubRepository: the F1 page's one model, from
                              the dashboard's `_f1.json`, `_f1final.json` and `_f1live.json` (see Formula 1)
    youtube/               ← YouTubeRepository via RSS feeds + optional Google OAuth subscription
                              import (AuthorizationClient / youtube.readonly); tracked channels in SharedPrefs
    twitch/                ← TwitchRepository via Helix + Device Code OAuth (Custom Tabs →
                              twitch.tv/activate, scope `user:read:follows`);
                              imports followed channels; live streams with 60s cache + auto-refresh
    github/                ← GitHubRepository via REST (OkHttp); authenticated user dashboard
                              (issues/PRs/activity/repos across every repo the account can see), plus one
                              GraphQL query for the contribution calendar. ContributionSnake is a line-for-line
                              port of the dashboard's `snkSolve()`; ContributionSnakeTest pins it to the JS output;
                              5-min memory + SharedPrefs disk cache; GitHubAuthClient Device Code
                              OAuth (Custom Tabs → github.com/login/device, scopes `repo read:user`);
                              leftover PAT / BuildConfig.GITHUB_TOKEN is an optional fallback
    upcoming/              ← UpcomingRepository: the Coming up timeline, read from the t3lluz dashboard's
                              `_stats.json` (`rows.upcoming`: Sonarr, Radarr, Stremio, F1 sessions and your
                              Google Calendar events, `svc` gcal) plus `_f1.json` circuits. A gcal row carries
                              a `CalendarEntry` (calendar, end, all-day, description, `join` call link, kind)
                              and draws as `CalendarPoster`, the web's `gcalChip`; `DashboardCalendars.shows`
                              is the web's calendar switch rule (DashboardCalendarsTest). Server URL is
                              `SettingsRepository.dashboardServerUrl` (Settings → Connections); tailnet-only,
                              so the last good copy is kept on disk.
                              UpcomingTimeline.kt holds the strip's rules (rest card, default focus, jumps)
    dashboard/             ← DashboardRepository + DashboardModels: the dashboard server's extras. The island
                              (`/_api/island`, island.py: today.js's rules, led by "needs you" in Hermes' chats),
                              the mail (`_today.json`, actions through `/_api/mail/act`), what the agents spent
                              (`/_api/usage`, usage.py) and the schedule (`/_api/schedule`: scheduled asks, staff
                              rounds, the briefing, host timers). Each answer's last good copy is kept in
                              `filesDir/dashboard_cache`. Parsers are pure (DashboardModelsTest).
    brief/                 ← BriefRepository + DailyBrief: the dashboard's morning briefing (`/_api/brief`,
                              re-run with `POST /_api/brief/run`), written at 06:45 by Hermes' first staff
                              member on duty. `BRIEFING` on Home (BriefCard, BriefViewModel) reloads on the live
                              feed's `ch: brief`; its chat button opens the writer's thread in the AI tab. The card
                              is the lead, "Right now" (the island's own items, from the activity's IslandViewModel,
                              so it is never stale by evening) and the body's headed parts (`DailyBrief.sections`)
                              behind Morning notes.
                              Last good copy on disk (prefs `brief_cache`). DailyBriefTest pins the parse
    calendar/              ← CalendarRepository (READ_CALENDAR permission). All-day instances are stored
                              at UTC midnight — always convert with `calendarLocalDateTime(millis, allDay, zone)`
                              so they land on the right day
  di/
    AppModule.kt           ← all @Provides (DB, DAO, OkHttpClient, KtorClient);
                              @Binds abstract modules for F1, YouTube, Twitch, and GitHub interface → impl
  ui/
    screens/               ← one file per tab screen (HomeScreen, HealthScreen, AIScreen,
                             SettingsScreen) + sub-screens (HelpScreen)
                             + onboarding/ (SplashScreen overlay, WelcomeScreen, PermissionsScreen, TutorialScreen)
                             + ai/ (ChatKit.kt shared chat bubbles/header/composer + IME helpers, SysopChatPane,
                             HermesChatPane + HermesChrome.kt: the chat, after T3 Code's phone app (MIT; its
                             ideas are ported, themed to this app). The chats are a panel from the left
                             (HermesRail.kt, a `ModalNavigationDrawer` with gestures off and the pane clipped;
                             the History button or the title opens it): what is active leads as cards with a
                             word for its state (Needs input, Working in `ShimmerText`, Done), the rest are
                             slim rows; a row swipes right to pin and left to delete, a hold opens the rest.
                             The transcript folds a finished run (`HermesFeed.rows`, HermesFeedTest): everything
                             between what you said and the last answer is one line, "Worked for 31s · 10
                             steps", that opens to every step; the run still going and anything that waits on
                             you are never folded. What you said is a quiet neutral block. More from T3 Code: what you
                             send while Hermes works is a queue (`HermesUiState.queue`; the strip over the
                             composer opens `QueueSheet`: send now, which stops the running turn for it, edit,
                             remove); the composer's microphone dictates through Android's recogniser; attach
                             asks Photo or File; a row swiped left is archived on this phone
                             (`SettingsRepository.hermesArchived`; Archived is a folded shelf at the foot, and
                             a chat that wants you leaves it); the header menu copies the chat as text
                             (`HermesFeed.transcript`); the model sheet also sets the chat's mode. Then a composer that folds, with one model pill
                             (`ModelPill`: the maker's logo, the family, its depth, Fast/Thinking) that opens the
                             picker (HermesModelPicker.kt, after the web's `modelPicker`): a search over every
                             model, the providers as tabs (`PickerTab`; starred families get a tab of their own,
                             `SettingsRepository.hermesFavouriteModels`), the tab's families (the newest version of
                             each line, older ones behind a fold, OpenCode's billed rows only when searched:
                             `HermesCatalog.pickerTab`), and pinned under them what the family in use can be set to:
                             depth on a slider (`effortStops`; sent when the finger lifts), Fast and Thinking as
                             switches, the context window as a two-way control. A tapped family is picked at once
                             and the sheet stays open until Done. A switch on its way is drawn as landed
                             (`HermesUiState.switchingTo`, laid over the status with `HermesCatalog.heading`), so
                             every control reads the model it changes; `HermesViewModel.setModel` sends one
                             request at a time behind a lock, newest last, and a chat keeps the model picked in it. `AiPickerSheet` (the phone's own AI) has the same
                             search and tabs. Logos are the web's, bundled in
                             assets/models and matched by `ModelBrands` (the web's `brandOf`). There is no mode chip:
                             a new chat is always Agent (`HermesViewModel.AGENT_MODE`), and `/agent`, `/plan`, `/ask`
                             (typed with a space, or picked in the slash palette) switch the chat to that brain's
                             mode of that kind (`setModeKind`); a non-Agent chat shows a "Plan ×" tag that returns it.
                             A chat only restores its saved model when the person opens it; the model is
                             Hermes-global. Hermes opens on the chat last open on this phone (prefs `hermes_last`;
                             the newest when that one is gone). The AI tab is Tech support alone: no page title, no
                             tabs, the pane's one-row header (chats, title, usage, new chat, menu; the console is in the menu).
                             ChatDock.kt floats over the foot of the chat, after T3 Code's phone app: while Hermes
                             works, one glass pill with the scanner, the step he is on and the turn's clock (the
                             header keeps saying what the chat runs on), and a round button back to the latest
                             message once the chat is scrolled well away from it (`rememberAwayFromChatEnd`
                             turns on further out than it turns off, so the button never flickers). The composer
                             folds as T3 Code's does: open (two lines, the model pill, the tools) while it has
                             focus, files, or a new chat; otherwise one row (attach, a line, the model's logo,
                             the usage square, send). A finger on the transcript clears focus, so reading folds
                             it. One text field serves both shapes, so folding never drops the cursor. A finished
                             answer copies from the row under it (`CopyButton`); a message of yours from the row under its bubble, or when held. Estimating a
                             meal is `MealEstimateSheet` (a card over Food, see Health tab), opened by "Estimate with AI" in
                             Home's `FoodLogForm` and by the sparkle button in Health's Food. The transcript is T3 Code's phone app's (`ThreadFeed.tsx`, `thread-work-log.tsx`),
                             built by `HermesFeed.rows(items, live)` (data/hermes/HermesFeed.kt, HermesFeedTest) and drawn by
                             HermesTranscript.kt: what Hermes does is a `WorkStep`, one 32dp line with an icon (the command,
                             "Read ~/notes/Home.md", "Searched …"); steps with nothing said between them are one `FeedRow.Work`
                             group closed to a summary ("Ran 3 commands and read 2 files", T3's `summarizeToolGroup`), which
                             opens to its steps (256dp, then it scrolls) and each step to what it ran and what came back; a
                             hold copies a step. While a turn runs nothing folds and the last group is live: it names the step
                             Hermes is on, shimmering, with the newest thought's line above it, or a Thinking row when there
                             is nothing yet. A finished run folds everything before its last answer into "Worked for 1m 12s"
                             (`FeedRow.Fold`). What Hermes says is plain text with no bubble and no byline; the run's answer
                             carries copy, fork, ask again, the time and model, and "2 files changed +16 −0", which opens the
                             files in a sheet. Approvals still waiting, questions and errors are never folded; a decided
                             approval is the commands it ran. The bridge's plumbing never shows: a ```search / ```run block
                             is the request and is stripped (`stripActionFences`) once its receipt (a web item, an exec)
                             follows or while the turn runs, and a search the bridge ran is one step, not the tool event and
                             its receipt. Row keys go by place (`work-<run>-<n>`), so an open row stays open as its turn grows.
                             `HermesNarration` still splits the bridge's one `text` into what was said on the way and the answer.
                             A reply streaming in ticks the motor at most every 320 ms (T3's stream haptics), and a screen
                             reader hears how a turn ended. A step that read or changed a file opens it in the server's
                             file viewer, a page read offers "Open the page", a question can be skipped (`skipQuestion`),
                             and the newest error offers Ask again, Change model and, when it reads like a limit, Usage.
                             While Hermes works the composer grows a tab on its top left: `ComposerPanelShape`
                             (ChatDock.kt) is one outline round both, so the glass, its edge and its shadow have no
                             seam, and `WorkingStatus` in it shows the scanner, the turn's clock and the chat's own
                             live line (`liveLabel`, from the feed's last row), so the two never disagree. The jump
                             button is all that still floats.
                             What waits on you is docked over the composer, as T3 Code docks it (HermesRequests.kt):
                             `PendingQuestionCard` (the choices as rows, a line for an answer of your own, Submit, Dismiss)
                             and `PendingApprovalCard` (the command, why, Allow once and Decline). A card holding several
                             questions (`Clarify.questions`) or commands shows one at a time with arrows and dots
                             (`Pager`) and sends the answers together (`clarify.answers`). The bridge's ```ask block
                             holds up to six questions (a plain line after a question's choices starts the next), and
                             tells Hermes each answer by its question (`parse_ask`, `clarify_reply` in bridge.py).
                             The card stands where the composer does and folds to a `RequestBar`; the transcript keeps a
                             quiet `RequestRow` where it was asked, and an answered question stays as "Answered: …".
                             `HermesNarration` reads any sentence that opens on an -ing word, "I'm …ing" or "I'll …" as
                             said mid-work, so a turn the phone did not watch still keeps its narration out of the answer.
                             In a chat, `LocalMarkdownActions` (MarkdownText.kt) makes a server path written as `code` open
                             in the file viewer at its line (`ServerPath.parse`) and draws a picture alone on its line.
                             Code blocks are coloured by `CodeHighlight` (comments, strings, numbers, keywords, keys, per
                             family of languages; CodeHighlightTest), everywhere MarkdownText is used. A table fits the
                             width when it can (`fitColumns`: long columns wrap, and only a table that cannot fit scrolls);
                             `<br>`, HTML entities, backslash escapes and `<https://…>` autolinks read as a browser reads
                             them (`plainInline`, MarkdownLayoutTest).
                             Every chat follows its newest line through a `ChatPin` (`rememberChatPin`, ChatKit.kt): pinned
                             until a finger's scroll ends away from the end, pinned again by a scroll back, a send or the jump
                             button. Never decide following from where the list happens to sit: that pulled a reader back
                             down each time a row arrived.
                             Composers sit `composerBottomGap()` above the pane
                             bottom (follows the keyboard frame by frame; never switch on "ime > 0"), and
                             `FollowChatOnKeyboard` pins the list with plain scrolls, never an animation per
                             frame. Tech support is Hermes when `HermesViewModel.usesHermes`: an
                             explicit `techSupportBrain` choice, or in `auto` whenever Hermes answers, else Sysop)
                             + Usage (the `usage` sub-screen; one tap from the AI tab's header, where `UsageButton` is
                             the dashboard's square filled with the plan in use's fullest window, from Settings →
                             AI usage, and from the launcher shortcut): T3 Code's usage page as the dashboard draws
                             it, in five tabs. UsageLimits.kt: a block per provider (`ProviderLimit`), a row per
                             window with how much is used, a notch where even spending would be by now
                             (`usagePace`), ahead / on / under pace and when it resets; a tap opens the detail.
                             Cursor adds what it spent this cycle and its own warning; Claude its banked reset
                             credits. UsageHistory.kt: Cost and Tokens for one window at a time (24 h by the hour;
                             7, 30, 90 days by the day): the total against the window before, who it went to, a bar
                             per hour or day stacked by provider, agent or model (`usageSeries`; drag sideways to
                             read one), tokens or cost by type and by speed, then models (ids that share a name are
                             one row), hours or days as a table, agents and projects. UsagePane.kt holds the shell,
                             Chats, and `ComposerUsageMeter`, the square beside send: the chat's provider's fullest
                             window where it reports limits, else this chat's context fill; its sheet lists this
                             chat, the plan it runs on, the other plans, All usage. UsageSchedule.kt: scheduled
                             asks and what runs by itself. Models and maths are data/dashboard/UsageModels.kt
                             (UsageModelsTest); usage.py asks Anthropic and Cursor itself, so the phone reads
                             nothing from a provider. Colours: a provider keeps its own, anything else ranked
                             takes one of six categorical slots (UsageKit.kt).
                             Hermes' rail groups "Needs you" first, dots a chat that moved since this phone
                             opened it (`HermesUiState.seen`), and shows each chat's tokens and context fill
                             (`HermesThreadSummary.contextFraction`).
                             More from T3 Code's phone app, in the rail and chat: each row shows the model it
                             runs on (logo, family, mode when not Agent, tokens: `railModel`), with filters
                             (Active, Pinned, Snoozed, Settled), snooze (`HermesSnooze.kt`: T3 Code's presets
                             plus a time of your own), a hand-made order (Move up/down/top, "Sort by recent"
                             resets it), day groups, an undo pill after pin, archive and snooze, and a footer
                             to Usage, Console and Files. The panel closes on a tap outside it (Material ignores
                             a scrim tap while `gesturesEnabled` is false, so it is on only while open). The
                             header's second line opens `ChatInfoSheet` (T3 Code's thread settings: model, context
                             fill, tokens, cost, what Hermes did, mode, chat actions). A message: tap shows its
                             time, hold gives Copy and Edit and resend (`/ai/threads/{id}/rewind`; the newest
                             answer also has Ask again), days are divided, and a Plan answer ends with an
                             Implement card. A change card opens the file at its lines or reverts it
                             (`/ai/revert`, with a conflict prompt). `HermesFiles.kt` is the server file
                             browser and viewer over `/ai/preview` (header menu, attach sheet, or typing "@");
                             `HermesViewModel.draftFor` keeps a draft per chat. Find in chat, camera in attach,
                             pictures drawn in messages (tap for full screen), selectable answers, and a
                             hardware keyboard (Enter sends, Ctrl+N, Ctrl+K focuses the chat search, Ctrl+B, Ctrl+F, Esc). "Ask Hermes" in the share
                             sheet (`AskHermesActivity` copies the files into the cache, `HermesShareInbox`
                             hands them to the pane) starts a new chat. Settling is T3 Code's word for archiving: swipe a row left (a tick for every tenth of the way, a click at the commit
                             point, as the home pull to refresh does), or the menu. Settled also happens by itself after N idle days (the
                             list-options menu). What the phone once kept for itself about the chats is the same on the web
                             dashboard and the phone. The bridge keeps `settled`, `snoozed`, `seen` (the `updated` a chat was
                             last read at) and `draft` on each chat, and `rail` beside the list in `/ai/threads`: `order`,
                             `autoSettle`, `favs` (starred models), `followUp`, `byDay` and `draft` (the chat not started
                             yet). The phone writes them with `PATCH /ai/threads/{id}` and `PUT /ai/rail`
                             (`HermesClient.setSettled`, `setSnoozed`, `setSeen`, `setDraft`, `setRail`), and
                             `HermesViewModel.takeRail` copies every list into `SettingsRepository`, which the pane draws
                             from; a list asked for before a change made here landed is skipped. A chat is unread while
                             `seen` is behind `updated` (`HermesUiState.isUnread`), and opening it with the pane in front
                             marks it read everywhere once no turn runs in it. A draft goes up 800 ms after the typing
                             stops (`tellDraft`); one changed elsewhere lands in the box unless something was typed here
                             since (`takeDraft`, `remoteDrafts`). `hermesRailVersion` counts what this phone has handed over. A swipe to the right anywhere in the chat opens the panel. The rail's search
                             is the server's full-text one (`/_api/search?kind=chat`: excerpt under each row, matching words picked out; opening a hit
                             searches that chat for the words). Fork (`/ai/threads/{id}/fork`: copies the messages up to one, and the new chat's first
                             turn carries them to Hermes as its memory, `fork_ctx`), Name it again (`/retitle`), Share chat, a failed turn
                             (`fail` on the summary) as a red Failed row, a "Needs your answer" pill in the dock while a question or approval is
                             out of sight, a launcher "New chat" shortcut and `dailydash://ask?prompt=` (fills the box, never sends).
                             The AI tab's accent is `AiAccent` (a saturated orange, Color.kt), used by the Hermes*, Chat*, git/ files in place of the app's pastel `ServerBrand`;
                             the rest of the app keeps its own.
                             The chat panel is a card floating clear of the screen's edges, all four corners round (`ModalDrawerSheet` with margins), and the navbar slides down
                             out of the way while it is open (`ChatPanelState`, read by MainScreen's nav `AnimatedVisibility`).
                             Send (`SendControl` in HermesChrome.kt) is T3 Code's: an arrow when idle; while Hermes works with text typed it says Steer
                             (stop the turn and say it now, `HermesViewModel.steer`) or Queue (after the turn), per the follow-up setting
                             (`hermesFollowUp`, default steer, toggled in the header menu), and a hold on it offers the other; with nothing typed it is
                             Stop. The queue strip over the composer shows the first queued message with its own Steer button. There is no usage
                             meter in the composer; the header's square is the only one.
                             Git and pull requests (`ui/screens/git/`, reached from the panel's footer, the header menu or the welcome cards): `GitScreen` has
                             the repo strip, the branch card with Fetch / Pull / Push and stash, then Changes (stage, unstage, discard, commit),
                             History, Branches (switch, new, "Review vs base") and Pull requests (checks, reviews, comment, approve, request
                             changes, squash-merge, open a PR). A diff opens `ReviewScreen` (`GitDiff` parses it; a tap on a line writes a note; the
                             notes go to a new Hermes chat with file and line, `GitViewModel.reviewPrompt`). All of it is the bridge's `gitapi.py`
                             (`/_api/ai/git/*`, `gh` for pull requests; only repos found under ~/projects, ~/docker and ~ are accepted).
                             Look (since the 2.0 rooms pass, the owner asked for the AI tab to match Home and Health): the tab is rooms
                             in its own orange (ChatKit.kt: `AiRoom` for a card, a chat row, a step's detail, git's cards; `AiRoomHi` for the
                             open or picked one; `AiSheet` for the chat panel and every sheet). What you said is a warm room of its own, the
                             header is round buttons (`HeaderRound`) with the chat's name in the display face, "Worked for…" and the day are
                             pills, the empty chat leads with "What should we look at?" set large over starter rooms in Home's tones, send is
                             filled orange when it can go. No outlines, no rules between rows, no capitals-and-letterspacing labels, no
                             gradients. Git's screens use `HeaderRound` for back too.
                             The bridge side lives in `bridge.py` on twolluz (`thread_fork`, `thread_retitle`, `fork_context`). Not ported, since
                             the bridge has no equivalent: git and pull requests, worktrees, review screens, subagents.
                             + ConsoleScreen.kt: the dashboard server's shell, drawn natively (no WebView). The
                             shells are the server's (`console.py`, shared with the web console); `ConsoleClient`
                             (data/console) streams what one prints over `/_api/console/<id>/stream` and posts
                             what is typed, `ConsoleViewModel` feeds it to Termux's `TerminalEmulator`
                             (`com.termux.termux-app:terminal-view`, Apache 2.0, from JitPack) and
                             `TerminalCanvasView` draws the emulator's screen with `TerminalRenderer`, takes the
                             keyboard and scrolls back. A key row adds esc, tab, a sticky ctrl, arrows and paste. Reached from the AI tab's menu, Home's
                             Servers card, the Servers screen and settings, Usage, and the launcher shortcuts
                             (res/xml/shortcuts.xml: Console, Usage, Hermes; MainActivity `EXTRA_OPEN`)
                             + server/ (the server page's rooms: hero, history, services wall as tiles,
                             activity, compute, memory, network, storage, sensors, processes, containers,
                             system). `ServerScreen` is a `HomePage` in the servers' green with a dock of four
                             (`ServerPages`: Now, Machine, Apps, System), each holding its sections in the saved
                             order. A section is a `ServerRoom` (a `PageRoom` in the colour of what it measures)
                             led by `SectionHeader`; the hero draws CPU, memory and disk as rings inside one
                             another with the hottest sensor in the middle (`ServerRings`), and the machine rooms
                             lead with one big figure. The pencil in the bar opens the editor (order and
                             visibility, `serverSectionOrder`); rooms are not dragged on the page. Labels are
                             written in sentence case (`quietCase`), numbers in `HealthDisplayFamily`; only
                             commands, units, paths and logs stay monospace. No borders, no dividers
                             + health/ (the Health tab: body clock and rooms, see Health tab) + settings/ (category sub-screens)
                             + home/ (Home's tiles, the page shell and the simpler pages, see Home: tiles and pages)
                             + f1/ (the F1 page and live timing, see Formula 1)
    viewmodel/             ← one @HiltViewModel per screen; UI state as sealed classes via StateFlow;
                              includes OnboardingViewModel (manages onboardingCompleted + splashShown flags);
                              DashboardViewModel (per-metric Health Connect StateFlows with today/yesterday
                              comparison — used directly by HealthScreen, NOT via DashboardScreen);
                              YouTubeViewModel (YouTube feed + channel search — consumed by YoutubeCard
                              component directly via hiltViewModel(), not from a screen ViewModel);
                              TwitchViewModel (live streams + follow import — consumed by TwitchCard
                              via hiltViewModel());
                              GitHubViewModel (account dashboard — consumed by GitHubCard via
                              hiltViewModel());
                              F1UiState.kt / GitHubUiState.kt (dedicated files for sealed UI state);
                              F1HubViewModel (the dashboard's F1 season and live timing; `follow()` runs only
                              while the tile or the page is on screen)
    navigation/            ← Screen.kt (sealed class, 4 bottom-nav tabs) + OnboardingRoutes / SettingsRoutes /
                             SubScreenRoutes (const routes) + DailyDashNavHost.kt + NavigationActions.kt
                             (`navigateToTab`, `popSubScreen`, `subScreen` destination builder)
    components/            ← shared Composables (MacroCard for settings and the server screens, PillNavigationBar, DraggableWidgetColumn,
                              WidgetEditor, WidgetExpandBar, PillButton, …). What Hermes is doing
                              (HermesActivityViewModel's `navActivity`) is the island's second line; the
                              navbar is a plain pill whose selected tab is a raised key that stretches
                              between tabs as it moves (`SelectionBubble`, two springs, drawn in the draw
                              phase). That bar is `ChromeTabBar` (PillNavigationBar.kt), and every page's dock is the
                              same bar with the page's tabs in it (`PageDock` in home/HomePage.kt), so a page reads
                              like a tab screen: never draw a second kind of tab bar. Its icons act out their tab once on arrival or a tap on the current tab
                              (NavTabIcon.kt: the house squashes and its door swings up, the heart beats while
                              its line draws the pulse, the sparkle spins a quarter, the gear turns two teeth),
                              drawn with the island's `GlyphPen`, ending where they began. NavActivityTab.kt holds `NavActivity` and its tones. WorkingScanner.kt is
                              opencode's Knight Rider scanner (KnightRiderTest pins it to opencode's
                              frames). ScreenHeader.kt: tab
                              `ScreenHeader`, pushed-screen `SubScreenHeader`, `TabContentBottomPadding`,
                              `Modifier.subScreenBottomPadding()`. HomeWidgetShell.kt: what the cards share
                              under Home's tiles: `HubErrorState` (message + Retry), `HubHeaderAction`,
                              `ChannelSheetHeader`, `WidgetStateSwitch`, and `WidgetScrollBox`, which lays out
                              whole inside a page (`LocalFullPage`). The tiles and pages themselves are
                              ui/screens/home (see Home: tiles and pages). MarkdownParser.kt + MarkdownText.kt: the web's
                              `hermes-markdown.js` rules (GFM tables, nested/task lists, callouts, `run`/`search`
                              fences, diffs); `breaks`/`streaming` for chat. MarkdownParserTest pins it.
                              The weather card (WeatherCard.kt, WeatherForecast.kt, WeatherDetails.kt), after Pixel
                              Weather, Carrot and (Not Boring) Weather: the temperature big, the sky beside it, one
                              line of facts (feels, high and low, wind and where from), then a headline that says
                              what the sky is about to do (`weatherHeadline`: MET's radar nowcast when it has one,
                              else the first wet hour) and five hours three apart (`MiniHours`). On its page: the radar's
                              next ninety minutes as bars when rain is on it (`RainNowcast`), the next 24 hours as
                              one chart with chips for what it draws and a finger along it to read an hour
                              (`HourlyExplorer`), what to wear (`WearRow`), every reading as a tile with its own
                              small picture (`ConditionsGrid`: feels like, wind on a compass, rain, humidity and dew
                              point, UV on its scale, pressure, cloud, the sun's arc). The page's order is now
                              (with what to wear: two short lines; a tap says why), the radar, the hours, the
                              days, then the tiles. A day is a
                              row (day and date, its sky in a word, rain and chance, the range bar) that opens to
                              `DayDetail`: its figures in a row, then the whole day as one chart, temperature as a
                              line with its high and low written on it and rain as bars along the foot, a finger
                              along it to read an hour. Today keeps the hours already gone (`TodayHistory`, kept
                              in prefs by `HomeViewModel.withTodayWhole`: MET's feed starts at the hour in hand),
                              dimmed behind a line at now, and runs on into tomorrow until there are twelve hours
                              to look at. One tile shape, AppIcons
                              only, and every time in the phone's clock (`hourShort`). Feels-like is the widget's
                              sum (`WxConditions.feelsLikeC` through `WeatherMath`); it used to be the dew point.
                              `WeatherRepository.fetchNowcast` reads `nowcast/2.0` (Nordics, radar coverage "ok"
                              only; empty anywhere else). `LocationProvider` takes the network fix when it is
                              within a kilometre (MET's grid), asks the satellites briefly when it is not, and
                              falls back to Android's own providers where Play Services has nothing; Home
                              asks again every five minutes while it is in front. WeatherMathTest pins the sums.
                              Charts never stretch a small change to full height: `chartSpan` gives each reading a
                              least span (temperature 8°, wind 8 m/s, rain 2 mm; humidity is 0 to 100), so a
                              one-degree day draws nearly flat (WeatherChartTest), and lines are Catmull-Rom
                              splines. Under the headline, `GroundLine` warns of ice (wet at about freezing) or
                              frost in the next eighteen hours (`WeatherMath.groundRisk`). `SkyRepository`
                              (WeatherExtras.kt) adds MET's sun and moon feed (real rise and set, moon phase, how
                              the daylight moved since yesterday), MET's ocean forecast (sea temperature and waves;
                              empty inland) and NOAA's Kp forecast, shown as Moon, Sea and Aurora tiles (the last
                              from latitude 54 north, against `WeatherMath.kpNeeded`). `RainAlertWorker` reads the
                              radar every fifteen minutes for the card's last position and notifies "Rain in about
                              25 min" when a shower that lasts is ten to sixty minutes off, once in three hours,
                              never while the app is in front (`WeatherMath.rainAlert`; Settings → Connections →
                              Weather → Rain alerts).
                              CalendarStrip.kt: the calendar's glance (`CalendarGlance`: a day strip to the last
                              busy day within `CalendarRepository.WINDOW_DAYS`, the picked day's events in
                              MediaCarousel) and the page's room per day. A peek never shows a badge or words: it
                              shows who (`PeekAvatar`, the creator's picture) or when (the calendar's date column),
                              faded with `peekAlpha`; badges and words come in with `textAlpha`. MediaCarousel.kt:
                              the M3 multi-browse carousel the YouTube and Twitch tiles use (`MediaItemLook` read
                              in draw/layer blocks only; tap a peek to bring it in). F1CircuitMap.kt: the dashboard's four-pass circuit
                              (kerb, bed, marque line, car) that paints its lap on screen, with a
                              `CircuitMapWeight` per place and `CircuitMotion` STILL / PAINT_ONCE / BACKDROP
                              (replays, and keeps a dim car lapping).
                              The F1 tile is ui/screens/f1/F1Tile.kt (see Formula 1).
                              Every circuit is drawn the same way (`CircuitMapWeight.BOLD` with
                              `CircuitMotion.FILL`: a bold white track that the marque's line fills, clears and
                              fills again; no car). Times follow the phone's 12/24-hour setting (`rememberClock24`). GitHubContributionGraph.kt: the year with
                              the snake. ServerCharts.kt / ServerVitals.kt: 270° dials with an average notch,
                              mirrored area charts, the scrubbable history chart, stacked meters, uptime bars,
                              fact chips. DeviceCodePanel.kt: GitHub/Twitch device-code
                              sign-in (copyable code + equal-width Open / Cancel). Glass.kt is the chrome's glass: `Modifier.glass(hazeState)` blurs the page under a `hazeSource` smooth,
                              washes it with one flat dark tint (`GlassSpec.ChromeTint`), as iOS drew its materials before
                              Liquid Glass: no sheen, no grain, no gradients or fake glints (the user asked for clean frost). The
                              navbar and the island share `Modifier.chromeGlass(hazeState, shape)`: `chromeShadow` (two layers,
                              cast by the outline with the inside cut away, since a shadow under translucent glass shows through
                              it), the clip, `glass` and `chromeOutline`, a faint neutral 1dp edge. The AI composers use `chromeGlass` too,
                              with no opaque band behind them, so the chat runs under the glass. Keep them alike. `glass` fills
                              `GlassSpec.Backdrop` (the page colour) under the blurred copy: a chat list draws on its pane's
                              colour, not its own, so its haze source records text on clear and the sharp chat showed through. 2.0's dotted frost (a dot grid
                              perforating the glass) is gone: it read as noise on a phone. Don't bring dots back.
                              Anything that clips (`animateContentSize`, scrolling) goes after `chromeGlass`, or it cuts the shadow off.
                              TopIsland.kt: the island, drawn by MainScreen under the status bar on Home, Health and
                              Settings (IslandViewModel). A tap goes where the item is about (`IslandTarget.of`, IslandTargetTest): its Hermes chat, the phone event in the calendar app picked for events (`IslandItem.ref`), its link through AppLinks (a mail, stream, video, review), the server it is about, or else the Home card / Health section that shows its kind, scrolled into view through `CardFocus`; a calendar item's call is its own Join pill (`onJoin`, call hosts only). A pill after iOS's
                              Dynamic Island, in the navbar's frosted glass (`chromeGlass`), exactly the navbar's width (the card
                              opens `CardOverhang` wider), always two lines tall (`IslandHeight`, 56dp). Two things share it side by
                              side in cells of one width (`MAX_ON_PILL`, Hermes counted): a disc (progress as a ring around it,
                              `RingDisc`), the title (whole when it fits, else its label when `hasIslandLabel`, never a lone
                              first word like "The"), and a second line with its live number in its tone, then its detail
                              ("35m left · Olivia"). Hermes at work is a cell like any item (`HermesCell`: the scanner in his
                              disc, what he is on, "1:23 · Hermes"). One thing alone is split as iOS splits one Live
                              Activity (`SoloEntry`, `HermesSolo`): what it is on the left (title over detail; the AI's fuller
                              title and detail line, `IslandRich`, when there are some), its numbers on the right in columns of
                              one width, value over label, after Android 17's metric-style Live Updates (`islandSolo` in
                              IslandFacts.kt, IslandFactsTest: countdown, "until 18:00", "CPU 48%", "7° / 4°"), or its action
                              ("Answer"), Join, or a chip naming where a tap goes, so that side is never bare. The AI's words
                              come from `IslandShortener.enrich` (cached by `islandRichKey`: the title plus the detail's digit-free
                              parts, so nothing that ticks makes a new call; never for live-number kinds); Hermes and
                              Cursor get `AGENT_TIMEOUT_MS`, since a dashboard turn is slow. The plan is `rememberPillPlan`.
                              Nothing turns on a timer: the line-up changes only when the ranking does, and then the words roll up; a ticking countdown updates in place (`PillPlan.key`).
                              Pill and card are one surface: an `AnimatedContent` on `expanded` springs between their sizes
                              (`islandSpring`) while one fades into the other, and only then, so a row closing its gap is not
                              chased by a second size spring. The rest wait at the
                              right end in one circle (`Cluster`: the next one's disc, a "+N" badge on its edge); a tap there or a pull down on the pill opens the card
                              over a light scrim (a tap outside, the handle at its foot or a pull up folds it). The card groups
                              items (Needs you, Now, Soon, Also; Hermes in the first two) and sizes them by need (`CardSize`:
                              a hero for needs-you / on-now with a progress line and Join, slim rows for background lines and
                              things to watch); rows fade and drop in one after another as it opens (`rememberEntrance`). A row
                              swipes off sideways as Android 16 clears a notification (`SwipeRow`, `SwipeMagnet`, specs in
                              `MacroMotion.IslandSwipe`: half pace while attached with the neighbours leaning, a
                              GESTURE_THRESHOLD haptic as it detaches, their facing corners rounding, a "Hide" hint in the
                              space it leaves, fly-off with the gap starting to close `CLOSE_AFTER_MS` in), inside its group's
                              rounded clip so it never crosses the card's margin, hidden for the day with an Undo pill under the
                              island whose fill drains while it stays up (`IslandLearning.unhide`). The card is handed every item
                              plus `isCleared`, not the filtered line: a cleared row must stay composed to collapse (and to come
                              back on Undo); the view-model drops it after `CLOSE_GAP_MS`. A long press on the pill hides with
                              the same undo. Icons are drawn part by part after the web island's marks (IslandGlyphs.kt,
                              `glyphFor`: rain falls out of its cloud, an equaliser for what is playing, the download arrow
                              drops into its tray, the sun's rays turn, the moon rocks while its star twinkles, an eye blinks,
                              a flag waves, the bell's clapper swings behind it, the heart beats as its trace runs), off one
                              12 s clock whose every loop divides it, in the draw phase; `GlyphShotsTest` draws them all
                              into `glyphs.png`. Icons without parts keep IslandIconMotion's whole-icon motions. What needs
                              you also gets the web's nudge. While Hermes works he is the pill's first cell. Ranking (IslandRankingTest): tier
                              (needs you, on now, soon, the rest), then `soonness`, the server's ambient lines after the rest and
                              something to watch after those, then learned taste per part of the day (`hourFit`). Labels
                              (`islandLabel`, IslandLabelTest) and `compactIslandEnd` as before. `islandClearance` →
                              `LocalIslandClearance` is the pill's height; the card opens over the page.
                              MailCard.kt: Home's `MAIL` (MailViewModel: optimistic actions laid over the server's
                              copy until it agrees, undo for archive, "Ask Hermes" starts a chat on the server).
    theme/                 ← Color, Theme, Animation (MacroMotion object — single source for all specs),
                              AppIcons.kt — the app's only icon set (generated Lucide/Tabler ImageVectors).
                              The Material icons dependency is gone: never import
                              `androidx.compose.material.icons`. Text colours use `TextPrimary` /
                              `TextSecondary` / `TextTertiary`, never `TextSecondary.copy(alpha = …)`
    util/                  ← HapticHelper (Compose-friendly performHapticFeedback wrapper, ui/util/Haptics.kt)
                              + LastUpdatedText composable + rememberRelativeTime (ui/util/LastUpdated.kt)
  widget/                  ← Five Glance home-screen widgets plus Tilesmith's (tilesmith/, see App Widgets →
                              Tilesmith), all resizable (`SizeMode.Exact`), listed in
                              DashWidgets.kt (`DashWidgetSpec`: key, receiver, `Content`, `refresh`, `renderPreview`,
                              `viewOption`). One Glance class, DashWidget, draws every receiver's copies: each copy
                              shows what WidgetInstances says for its app-widget id (what it was placed as, until
                              switched in its settings). settings/WidgetSettingsActivity is that long-press screen.
                              Weather (this package), calendar/, f1/, server/, github/: each has a Spec + Receiver
                              (extends DashWidgetReceiver), a Widget object (its `Content`), a store that snapshots
                              its data into prefs `daily_dash_widget_<key>`, and a pure *Logic/*Model file with a
                              size planner + tests.
                              kit/: WidgetKit (WK palette, WT type, `WidgetDims` cells, frame/header/tabs/chips/tiles,
                              `openUrlAction`/`openAppAction`/`refreshAction`), WidgetCharts (bitmap charts),
                              WidgetAi (cached AI briefs), WidgetStateAction (`setStateAction` per-copy tab state),
                              WidgetData (`rememberWidgetData`: read snapshots in composition, never before
                              `provideContent`, or a live session draws stale data).
                              WidgetRefreshWorker refreshes every placed widget every 15 min (each honours its own
                              TTL), runs a fast lane at the soonest `refreshEveryMs` a placed widget asks for, a pass
                              on unlock, and refresh-button taps; WidgetStateProvider (what is placed?).
                              Renders and picker previews come from the opt-in harness in app/src/widgetShots
                              (see App Widgets)
  util/                    ← HapticUtils (raw VibrationEffect-based haptics, used outside Compose)
```

## Key Patterns

### Dependency Injection
Hilt throughout. `AppModule.kt` is the only `@Provides` module. Concrete implementations are bound to interfaces via separate abstract `@Binds` modules (`F1DataModule`, `YouTubeDataModule`, `TwitchDataModule`, `GitHubDataModule`). **Glance widgets cannot receive injected deps normally** — they use `EntryPointAccessors`, e.g. `WidgetEntryPoint`.

### UI State
Each screen's ViewModel exposes sealed-class state via `StateFlow`. Example pattern from `HomeViewModel`:
```kotlin
sealed class WeatherUiState { object Loading; data class Success(...); data class Error(val message: String) }
private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
val weatherState: StateFlow<WeatherUiState> = _weatherState
```

When a sealed interface is shared across a screen and its sub-components, it lives in its own file (e.g. `F1UiState.kt` in `ui/viewmodel/`).

Health Connect metrics use `HealthMetricUiState(value, today, yesterday, isEnabled)` from `ui/components/BodyStats.kt`. 

**Throttled loading pattern** — both `DashboardViewModel` and `HealthViewModel` skip reloads if called within 30 s of the last load:
```kotlin
fun loadDataThrottled() {
    if (lastLoadMs > 0 && System.currentTimeMillis() - lastLoadMs < 30_000L) return
    loadData()
}
```
`DashboardViewModel.loadData()` also cancels any in-flight job first (`loadJob?.cancel()`) to prevent pile-up on rapid calls.

### Navigation
`DailyDashNavHost` owns all routes. The **`SplashOverlay` is a full-window Compose overlay** placed above the `Scaffold` in `MainScreen` — it is **not a nav destination** and lives in `OnboardingViewModel.splashShown`. The rest of the onboarding flow (`WelcomeScreen`, `PermissionsScreen`, `TutorialScreen`) **are** nav destinations via `OnboardingRoutes.WELCOME/PERMISSIONS/TUTORIAL`. Transitions are defined exclusively in `MacroMotion` (`ui/theme/Animation.kt`); do not hardcode tween/spring values elsewhere.

Sub-screens (`SubScreenRoutes.HELP/WIDGETS` and the `SettingsRoutes` categories, including the `servers` dashboard) are registered with the `subScreen(route) { … }` builder from `NavigationActions.kt`, which applies the `MacroMotion.subScreenEnter/Exit/PopEnter/PopExit` transitions. Parent screens push them imperatively (e.g. `SettingsScreen` → `help`). **The look everywhere, not only on Home** (the owner asked for every panel in the app to match, Nov 2026). Shared pieces in home/HomeLook.kt: `RoomNeutral` (a card standing on the page by itself), `WellFill` (a block inside a room), `SheetColor` (a bottom sheet; YouTube, Twitch, Tilesmith and the AI tab tint theirs with their own colour), `roomFieldColors()` with `FieldShape` (every text field: a fill that brightens with focus, never an outline), `roomSwitchColors()`. The theme sets Material's shapes rounder (`AppShapes`: menus 18 dp, dialogs 32 dp) and dialog titles and button labels in the display face (`AppTypography`). Bold text is set in `HealthDisplay`; small labels are sentence case with no letter spacing (`quietCase` turns an old capitals label into one). Outlines are gone except a picked item's ring (`Modifier.border(2.dp, tone, shape)` only while selected), driver and avatar rings, and Markdown's task boxes. `HubHeaderAction` and sheet headers use round buttons; Git and the Hermes file browser use `HeaderRound`. A room that opens a page sinks a little under a finger (`PageRoom(onOpen = …)`, as `HomeTile` does), rooms rise in on a page's or the Settings tab's first screen (`pageRise`, `LocalPageEntrance`), and every tappable thing ticks.

Settings and its pages, and Widgets, are `SubPage`s (home/HomePage.kt): Home's page head (the title large in the display face, one sentence, the title pill on scroll) and glass bar over one scrolling column. Their cards are `MacroCard`, now a room (28 dp, a tint of `tone`, no edge; a `borderColor` an old caller passes tints it instead), groups are `SettingsGroup` rooms in a tone with no rules between rows, switches take `roomSwitchColors()`, `MacroButton` is a pill with no border, and Material's menus are rounded through the theme's `AppShapes`. Screens that need a fixed header (Console, Usage) keep `SubScreenHeader`, which has the same round back button. Every sub-screen closes with `navController.popSubScreen(entry)` so a double-tapped back arrow can't pop the tab underneath.

**Tab switches must go through `navigateToTab(route)`** (pop to the start destination with `saveState`, `launchSingleTop`, `restoreState`). Never `navigate()` a tab route on top of another tab or a sub-screen — that tab's saved stack then ends in the other tab and the nav pill stops responding. (The one exception is finishing onboarding, which replaces the onboarding stack with Home.) The server dashboard → AI hand-off uses `navigateToTab(Screen.AI.withSeed(id), restoreState = false)` so the seed args beat the AI tab's saved state.

Callers must not add haptics to components that already fire their own: `MacroButton`, `PillButton`, `SettingsCategoryRow`, `HubHeaderAction`, `HubErrorState`, `WidgetExpandBar`, `DeviceCodePanel`.

### Decorative motion
Two long-running animations mirror the t3lluz web dashboard and take their specs from `MacroMotion.CircuitPaint`
and `MacroMotion.Snake`. Both run off a clock read only in the draw phase (a redraw per frame, never a
recomposition), take frames only while on screen (`Modifier.trackOnScreen`) and the app is resumed
(`rememberIsResumed`), and show their finished state when animations are off (`rememberReducedMotion`).

Pull to refresh (Home and Health) is Essentials' (sameerasw/essentials), shared as `RipplePullToRefreshBox`
(components/PullRippleRefresh.kt): no spinner. The page follows the finger (`PULL_FOLLOW` of the pull) and
springs back on release; a tick every tenth of the pull, then a click and `Modifier.liquidRipple`
(components/LiquidRipple.kt, its AGSL shader ported as is; Android 13+, off with animations off) out from the
header (`rippleAnchor(origin)`, the origin handed to the box's content) the moment the pull passes the
threshold, only while a finger drives it. The ripple is the only sign a page refresh ran: cards don't show a
spinner for a background reload. Specs in `MacroMotion.LiquidRipple`.

The F1 countdown's digits roll over (`RollingText`, components/RollingText.kt): each changed character
slides up out of its own clipped box as the new one rises in from below, as a countdown ticks on iOS. Specs
in `MacroMotion.CountdownRoll`; the digit style uses tabular figures so nothing shifts.

### Home: tiles and pages
Home is the dashboard's rooms on a phone (2.0 beta; the owner asked for Home to work as the "new caddy" does and to share the Health tab's look). Every card is a **tile**: a room in its subject's colour (`HomeTile`, ui/screens/home/HomeLook.kt: a flat dark tint with 28 dp corners and no edge, the title in the display face in the subject's tone, one quiet line beside it, a chevron). A tile is the glance and **nothing unfolds on Home any more**: a tap anywhere on a tile opens the subject's own **page**, a pushed screen (`SubScreenRoutes.homePage(id)`, `HomePageHost` in home/HomePages.kt). Don't bring `WidgetExpandBar`, `WidgetExpandFooter`, `HubCardHeader` or a fixed-height pane back onto Home.

Tiles still drag: hold one and move it (`draggableWidgetItems`), or use the pencil (`WidgetEditor`, a room of rows in each tile's tone). Order and visibility are one string in SharedPrefs:
```
"WEATHER:true,CALENDAR:true,BRIEFING:true,MAIL:true,UPCOMING:true,BODY_STATS:true,FOOD:true,F1:true,GITHUB:true,YOUTUBE:true,TWITCH:true"
```
A hold that lifts a tile and lets go without moving it ends in a click, so `HomeTile` reads `LocalTileDragging` and ignores it. A card missing from a saved order (one added in an update) is slotted in after the card that precedes it by default (`parseWidgetConfig`); layouts from before 2.0 carry `PROGRESS`/`QUICK_ADD`, mapped onto `FOOD`.

**The kit** (home/HomeLook.kt). `HomeTone` holds one colour per subject (the dashboard's own: weather sky, calendar orange, briefing sun, mail coral, coming up violet, F1 red, GitHub teal, servers green, YouTube red, Twitch purple, health and food as on the Health tab). `HomeTileLoading` and `HomeTilePrompt` are a tile still being read and one that needs a permission or an account. `PageRoom` is a room inside a page; `TonePill` (tinted, or filled when it is the one thing to do), `RoundAction`, `SegRow` (the dashboard's segmented control) and `RoomLabel` are the controls. Blocks set into a room are a white wash with no edge (`WellFill`); numbers and titles are `HealthDisplay`. No borders, no hairlines between rows, no capitals-and-letterspacing labels.

**A page** (`HomePage`, home/HomePage.kt). A large title in the display face with one sentence under it leads a `LazyColumn` of rooms. The back button and the page's own actions float over it as glass (`GlassRound`, `GlassPill`, `chromeGlass` on the page's own haze state), and the title joins them in a glass pill once it has scrolled away. A subject with several sides passes `tabs` and gets a **dock** where the navbar sits on a tab screen: a glass bar with a tile in the page's colour that slides to the open tab; a tap or a sideways swipe turns the page (a `HorizontalPager`, one list per tab). Inside a page `LocalFullPage` is true, which makes `WidgetScrollBox` lay its content out whole so the page does the scrolling. A page reads the view-model its tile reads: `HomePageHost` hands every page Home's own back-stack entry as the owner, so nothing is fetched twice and a page opens on what the tile showed.

The shell's small manners, which every page gets for free. Rooms on the first screen rise into place one after another when a page opens (`Modifier.pageRise`, on `PageRoom` and the head; anything composed later just stands there). The head thins out and lags as it is scrolled off, and the title lands in the bar on a spring from below; a tap on that pill, or on the dock's open tab, scrolls back to the top. A page's own controls stay in reach: it marks the copy in its content with `Modifier.pageStickyAnchor()` and hands the same composable to `HomePage(sticky = …)`, which pins it on glass under the bar once the inline copy is behind it (YouTube's and Twitch's channel row, Mail's tabs, Coming up's sources, GitHub's repo picker). `PageRoom(onOpen = …)` makes a whole room a button with a chevron. Carousel cards are 24 dp with no outline (`MediaCarouselShape`, Coming up's `CardShape`).

**Icons.** Every app on the server page has its own: the dashboard's `_services.json` lists each app's icon, brand and container (`DashboardLinkRepository.parseServices`; the older page's tile markup is still read, the list wins). A container takes its app's icon (`DashboardLink.iconForContainer`), else the public dashboard-icons set by its name and parts (`containerIconCandidates`: "-db" is Postgres, "-tunnel" Cloudflare); `AppIcon` tries each in turn and shows the first letter until one loads. Driver faces on the F1 page fall back to the app's own OpenF1 headshots behind the dashboard's cut-outs (`F1HubFallback.withFaces`).

**What each tile opens.**
- `WEATHER` → `WeatherPage`: the place as the title and what the sky is about to do as the sentence, then rooms in the order a day is planned by (`weatherPageRooms`: now with warnings and what to wear, the radar's rain, the next 24 hours, the days, every reading).
- `CALENDAR` → `CalendarPage`: the day strip and the picked day's cards (`CalendarGlance`, the tile's own body), five weeks as a grid with a dot per event (`MonthAhead`; a tapped day lists what is on it), then a room per day.
- `BRIEFING` → `BriefPage`: the lead with Ask and Write it again, Right now, then each of the morning's parts as a room in the colour of what it is about.
- `MAIL` → `MailPage`: the tabs as a `SegRow`, every mail of the open one with its actions. The tile shows the first three that need you.
- `UPCOMING` → `UpcomingPage`: the source filter, the strip with its Today and two-week jumps, then every entry still ahead in a room per day. The tile is the strip alone (`UpcomingStrip`: a Material 3 `HorizontalCenteredHeroCarousel` with `singleAdvanceFlingBehavior`; items are masked, not resized, so anything that grows or fades with a card reads `carouselItemDrawInfo` inside `graphicsLayer`/draw blocks and never in composition).
- `YOUTUBE`, `TWITCH` → `YoutubePage`, `TwitchPage`: a dock of two (Feed or Live, and Channels). The tiles show the channel faces and a `MediaCarousel`. Both filter by channel with `ChannelAvatarRow`; the row leads with a search button and, once anything is starred, a star (`ChannelFinderState`, one per surface); a long press on a face stars it. Favourites are `SettingsRepository.youtubeFavourites` / `twitchFavourites`. Twitch rings live channels in scarlet `#eb0400`.
- `GITHUB` → `GitHubPage`: a dock of six (Overview with the year, the numbers, what moved last and the account; Issues; PRs; Inbox; Activity; Repos), the repo picker at the head of each.
- `F1` → `F1Page` (see Formula 1 below).
- `SERVERS` opens the server dashboard that already existed; `BODY_STATS` ("Health": `HealthGlanceCard`, rings, readouts and one cycling chart) opens the Health tab; `FOOD` (the day's two rings via Health's `FoodToday`, then Log a meal, the AI card and the label scanner) opens the Health tab at Food.

An island tap about a card still scrolls Home to its tile (`CardFocus`), and then opens the tile's page when it has one. `MainActivity.EXTRA_OPEN` `page:<ID>` opens a page from outside (`tools/preview.sh open page:F1`).

### Formula 1
The F1 page (ui/screens/f1) draws one model, `F1Hub` (data/f1/F1Hub.kt). `F1HubRepository` reads it from the dashboard when the phone can reach it: `_f1.json` (stats.py, enriched by f1_live.py: both tables, every session's result this weekend and the last, tyre strategies, the season round by round, driver cut-outs under `art/` and team badges under `icons/f1-teams/`), `_f1final.json` (a session's classification the moment it ends, shown as provisional until the official one arrives) and `_f1live.json` (live timing, written about once a second around a session; never kept on disk). Off the tailnet `F1HubFallback.from` builds the same model from the app's own sources (OpenF1, Jolpica), so the page draws the same way with less on it. `F1HubParser` drops a "provisional" result that was kept before its session started or holds no times: the collector has written both. F1HubTest pins the parsing, the phases and the sums.

`F1HubViewModel.follow()` keeps both fresh for as long as its caller is on screen (the tile's `F1LiveGlance`, the page): around a session the tower every two seconds, sooner when `HermesLiveFeed.files` says `f1live` changed, and the results every minute; when a session is seen to end the results are read again at once, and `f1` on the feed reloads the season. `F1Live.phase()` is the dashboard's own rule for what counts as live (a fresh file), about to start (45 minutes) or just finished (three hours).

The page (`F1Page`): a **Live** tab leads the dock around a session (`f1LiveRooms`: the session clock set large with the track's status and the weather; the tower, where a tapped car opens to its three sectors in timing colours, its best lap, speed trap, stops and stints; the circuit in the feed's own frame, in the flag's colour, with the cars on it when the feed has positions; race control; in a race the lap chart and the stints; team radio clips that play in place). **Weekend** is the next race (`F1Hero`: the round, the place in the race cut, the page's sentence, then the circuit with the clock to whatever runs next and every session in the phone's time with who topped it), results one session at a time (`ResultsRoom`: a podium for a race or sprint, the three qualifying parts, practice by best lap), and the tyre strategies. **Drivers**: the leader, points by round, standings whose rows open to wins, podiums, best finish and form. **Teams**: a room per team in its colour with its badge and both drivers. **Season**: wins per driver, then every round with its podium; a round opens to its circuit, facts and sessions.

**Pages inside the page** (f1/F1Detail.kt). Nothing unfolds in place: a tap opens a full page pushed over the tabs. `F1Page` keeps them as a stack of `F1Dest` keys (`LocalF1Open` pushes, back pops, the tabs keep their scroll underneath). A **driver** (`F1Dest.Driver`, from any row, face or podium step): the car's number huge behind the name, the season in sums (`F1HubMath.season`: wins, podiums, poles, best and average finish, races in the points), the title state with the neighbours in the table, the season as a bar per round to tap (`RaceByRace`), a head to head with the teammate (`F1HubMath.duel`), points by round against the teammate, and every session of this weekend and the last with the race's own fastest lap and stints. A **team** (`F1Dest.Team`): the same for both cars, with one-twos, double points finishes and the haul per round. A **session** (`F1Dest.Session`, from any session row or a results room's pill): practice as the three quickest, the whole classification with each gap as a bar, team pace (`F1HubMath.teamPace`) and laps run; qualifying as pole, each part on its own with who went out, and teammates set against each other; a race or sprint as the result, movers, every car's fastest lap, team points, who did not finish and the strategies; a session still to run is its clock. A **round** (`F1Dest.Round`, from the season list, the hero's circuit or a tapped bar): the circuit, the podium and pole, the sessions, and for an older round the finishing order and the table after it from the history (`HubHistory.classification`, `standingsAfter`). The parser gives the last weekend its sessions from the calendar, since `_f1.json` writes it without them.

**The tile** (f1/F1Tile.kt, `F1Tile`) draws the same `F1Hub` as the page: what is on track (`F1LiveGlance`), the place in the race cut beside its circuit with the clock to whatever runs next, the sessions as blocks (the next one filled, the ones already run showing who topped them), the top three in their teams' tints and the title line.

Its look (f1/F1Look.kt): Home's rooms tinted red, headlines in `F1Display`, a race cut of the same Google Sans Flex (leaned with `slnt`, narrow, heavy, `ROND` 0); timing figures in `F1Figure`. `DriverFace` (a ringed face that tries each candidate URL, then the code), `TeamBadge` (SVG from the dashboard, else the code), `TyreDot`, `StintBar`, `FormRow`. Purple is the session's best and green a personal best, as on every timing screen.

### Health tab
The Health tab has its own look and is **one page in one order**: the owner asked for it to be alive and unlike the rest of the app, and to drop the reordering and hiding of sections (2.0 beta). There is no layout setting, no pencil and no drag; `HealthScreen` is a LazyColumn of five fixed items (`SectionOrder`: `DAILY_HEALTH`, `SLEEP`, `VITALS`, `ACTIVITIES`, `FOOD`, the names an island tap scrolls to; the owner asked for Body & Vitals above Activity). Don't bring the grey bordered cards, the "At a glance" tile grid or list rows with hairlines back.

**Look** (health/HealthLook.kt). Every number and title is set in `HealthDisplay`: Google Sans Flex with its `ROND` axis at 100, heavy and a little narrow, read from the phone's system fonts (`/product/fonts`), so a Pixel gets it and any other phone its default bold. A section is a **room** (`HealthSection(tone = …)`): a flat dark tint of its subject's colour (`roomColor`), 28 dp corners, no edge; `LocalRoomColor` tells a chart what it stands on, for the ring it cuts round a dot. Six category tones in Color.kt (`HealthActivityTone`, `HealthHeartTone`, `HealthSleepTone`, `HealthBodyTone`, `HealthVitalsTone`, `HealthNutritionTone`; the per-measure names alias them). Nothing inside a room gets a box of its own. Numbers are white and labels grey, a worse delta is grey (never red), and decimals are written with a point everywhere (`Locale.US`). Times follow the phone's 12/24-hour setting (`rememberClock24`, `rememberClockFormatter`, `clockOf`).

**Motion and touch.** The tab's blocks rise into place once, top down (`Modifier.riseIn(index, progress)`, `MacroMotion.HealthEntrance`), read in the draw phase. A chart further down waits until it scrolls into view before it draws itself in (`Modifier.onFirstSeen`: the bar chart's wave, the sleep score's ring, the night chart). Tiles that are buttons sink a little under a finger (`Modifier.pressSink`). Every pick ticks; a chart read under a finger ticks as the reading appears and again at each step it crosses (`scrubGesture(scrub, haptics, steps)`); taking the clock over is `gestureStart`, letting go `gestureEnd`. While the day is read the body clock stands empty with a light sweeping its rim and the headline says "Reading your day…"; once today's and yesterday's hours and the nights are in (`ready`), the hand makes one turn and paints the day in. A room still loading shows `RoomLoading` (bars in its colour with a wave running down them), never `ContentSkeleton`.

**Today** (`DailyHealthSection`, no room: it stands on the page). A week of days as small rings (`DayStrip`; tap one and everything below becomes that day, its hours read on demand by `HealthViewModel.loadDayHours`), a streak chip beside the headline, one sentence on the day (`dailyHeadline`: readiness from last night's sleep + HRV + resting HR against the person's own 30-day baseline, then the step pace; no verdict before a usual day has 500 steps; `pastDayHeadline` for a day that is over), then the **body clock** and four meters (Steps, Sleep, Calories, Heart). A hold on Steps, Sleep or Calories opens the goals (`HealthGoalsSheet`: minus and plus that run while held, and how often each goal was reached lately). The goals are `HealthGoals` in `SettingsRepository.healthGoals`, provided app-wide as `LocalHealthGoals` by MainScreen; every meter, ring, goal line, streak, Home's health card, the island's steps and the phone hub read them, so never write 10,000 steps, 2,500 kcal or 8 h into a screen again. The night that counts for today is last night, or the newest sleep while it ended under `LAST_SLEEP_HOURS` ago, so someone who sleeps into the day still gets theirs.

**Body clock** (health/BodyClockDial.kt, data/health/BodyClock.kt, BodyClockTest). A 24-hour face, midnight at the top: sleep round the rim in its stage colours, each hour's steps a block standing on the ring inside it, heart rate a line over its own shadow inside that, the layer on show as one big number in the hub. Live it is **today so far** (`liveBodyClock`: today's hours from midnight to now, the hours still to come empty, and last night's sleep whole, back to 18:00 the evening before), so a day that has not begun shows the night and nothing else. The owner asked for this: the face used to be the last 24 hours and read as a morning already full of steps. Don't bring yesterday's hours back onto today's face. A hand marks now, a light circles the rim once a minute, and a ring beats at the latest heart rate when there is a reading under 90 minutes old. A past day is its midnight to midnight (`dayBodyClock`). A finger held still for `MacroMotion.BodyClock.HOLD_MS` takes the face over and reads any time of day as it moves round (`readingAt`: steps that hour, bpm, sleep stage), with a tick per hour; a tap reads that time for a moment; a finger that moves first is the page scrolling. Under a finger the hub is the time and a line per reading beside its glyph (`HubLine`: stage, bpm, steps, kcal); keep the hub's text to what fits its circle (`softWrap = false`, sizes stepped by length), since a long line is cut by the round edge. A meter, or a tap on the hub, shows one layer alone (`ClockLayer`; Calories swaps the step blocks for active kcal by the hour where a watch writes them, which Garmin doesn't); the others sink back over a short fade. Motion is on its own overlay canvas off one frame clock, only while the dial is on screen and the app resumed.

**Sleep** (`SleepSection`). The night's length set large with bedtime and wake, the score as a ring that fills (`ScoreRing`), the night stage by stage straight on the room (`SleepNightHypnogram`: four lanes of blocks joined by thin risers, each lane's name, time and share beside it so the chart is its own legend; hold a finger on it to read a stretch), then a bar per night of the last two weeks against the goal (`RangeBarChart`; tap or drag to read another night, kept by its date and only until a newer night syncs). "Score and schedule" opens what the score is made of (`SleepNightScore.durationPoints` / `stagePoints` / `continuityPoints`), in-bed stats, the bedtime-to-waking chart and regularity and sleep debt. The night on a clock face is the body clock's; there is no second dial. `sleepDayLabel` names a night ("Last night", "Last sleep", "Saturday night").

**Body & Vitals** (`VitalsSection`). A sentence (`vitalsHeadline`; two measures that went the same way are named), then every measure as a card in the carousel Home's YouTube and Twitch use (`VitalCarousel` on `MediaCarousel`, whose `onFocus` says which card is in the middle): the reading large, where it sits ("Below usual · Usual 58 bpm") and a dot on its usual range (`RangeGauge`) or its trend where there is no range; a peek is the measure's glyph. Under the carousel stands the history of the card in the middle, with the usual range as a band; hold a finger on it to read any day. It covers every measure Health Connect has, plus estimates marked "Est." where nothing writes the measure (resting HR, VO₂ max, resting energy; a paragraph only for an estimate, to say how it was worked out). The usual range is data/health/VitalRange.kt (`usualRange`: 30-day mean ± one SD, never narrower than `minUsualHalfWidth`; `standingOf`; VitalRangeTest).

**Activity** (`ActivitySection`, `ActivityTrends`). One range control in the header, D · W · M · 3M · Y (`ActivityRange`; rolling, ending today, never a calendar week): D is today's steps against a usual day (`StepPaceChart`, with the day's distance, energy and floors under it), the others chart steps, active energy, distance or floors by the day, or by the week over three months and 52 weeks (`rangeBars`, `rangeSummary`, ActivityRangeTest), from one year of daily totals (`HealthViewModel.activityDays`, `readHistoryStatsBetween(enrich = false)`: the last 91 days first so the short ranges draw at once, then the rest a quarter at a time). The big number is the range's daily average against the stretch before it, or the bar under a finger. Under the chart, the **bests** of that year (`ActivityBests`, `healthBests`: most steps, calories, distance and floors in a day, lowest resting heart rate, longest run at the step goal), one set in the last week named in its colour. Health Connect hands an app only a month back from its first grant unless it holds `READ_HEALTH_DATA_HISTORY`, so a grant from before the app asked for it gets one line at the top of the room (`HistoryAsk`, from `HealthConnectRepository.NEW_PERMISSIONS` via `missingNewPermissions`; Allow opens Health Connect's sheet, the cross puts it away for good). Add a later permission to `NEW_PERMISSIONS` so an existing grant is asked for it too. Then the workouts synced through Health Connect, every one a folded row, newest first (the owner asked for the latest to fold like the rest), opening to its numbers three to a row in the colour of what they measure, its heart rate (hold to read) and time in zones (`WorkoutZones`); with none this month or last, the latest from the last six months (`ACTIVITY_FALLBACK_DAYS`, `olderOnly`), and with none at all Open Health Connect, never a permission request.

**Food** (`FoodSection`, health/FoodSection.kt; its pieces are health/FoodKit.kt: `FoodField`, `FoodNumberTile`, `FoodPrimaryButton`, `FoodGhostButton`, `FoodRoundButton`, `FoodChip`, all flat tints with no edge). One line to start logging from, "Log a meal", with two round buttons beside it for the AI card and the label scanner. Open, the composer (`FoodComposer`) is what it was, calories and protein as two tiles with the number set large and steps to tap under each (+50 +100 +250, +5 +10 +25), what was logged lately to log again in one tap (`MacroRepository.getRecentFoods`), and one button that says "Logged" for a moment when it has. Once something is logged today: two rings (calories outside, protein inside) with their numbers and the day's balance, and the day's meals as rows (`FoodRows`). Then 7, 14 or 30 days of calories or protein against the goal (`RangeBarChart`) with a picked day's meals under it; with nothing logged in the range, one line. Don't bring `MacroTextField`, `MacroButton`, `FoodLogForm` or `WidgetExpandFooter` back into this room.

**The two cards.** Estimating a meal and scanning a label are not screens: each is a `FoodSheet` (the room's green-tinted dark, round at the top, 92% of the screen, a header in the display face) that `DailyDashNavHost` shows over whatever tab asked (`foodSheet`), from Home as well as Health. `MealEstimateSheet` (on `AiViewModel`, whose messages it reads as cards, newest first): three ways in besides typing (a photo, the gallery, the label scanner) and quick bites while it is empty, then each answer as a card with calories and protein set large, how sure it is, a portion to pick, Adjust and one button to log it; the line to type on is at the foot with `AiPill` in the header. `ScanLabelSheet` (on `CameraScanViewModel`, reset each time it opens): the camera in a rounded window with a frame's four corners (`PreviewView` in COMPATIBLE mode, a TextureView, so it clips to the corners; the camera is unbound as soon as the card moves on), the shot to check, the wait while the AI reads it, then what it read with an amount to step or type, units as chips, the totals rolling to their new value, and "Fix what it read" for the label's own numbers.

**Charts.** `RangeBarChart` (health/HealthCharts.kt) is the tab's one bar chart: bars rise in a wave, a tap or a drag picks one, the scale tops out on a round number (`niceCeil`) written in the right margin with `goal` and `avg` named there. Line charts read under a finger through `rememberScrubX` / `Modifier.scrubGesture` and `drawScrubBubble`; a press only shows after a short hold, so a scroll that starts on a chart doesn't flash it. Fills under a line are a flat tint, never a gradient (the rings are the one exception, as on Home). The tab pulls to refresh (`HealthViewModel.refresh()`).

### App Widgets (Glance)
Five ready-made home-screen widgets: **Weather**, **Calendar**, **F1**, **Server** and **GitHub**, plus **Tilesmith**'s (below), registered in `DashWidgets.all` and the manifest; `DashWidgets.listed` is the five (Tilesmith has its own studio). Every one is resizable and switches layout on `WidgetDims(LocalSize.current).cols/rows`: a smaller size drops whole sections, never squeezes them.

**Each placed copy owns what it shows.** All five receivers draw through one `GlanceAppWidget`, `DashWidget`, which reads the copy's widget from `WidgetInstances.specFor(context, appWidgetId)` (a stored pick, else the receiver it was placed from) and composes that spec's `Content`. Re-renders go by id: `DashWidgets.render(spec)` updates only the ids showing `spec`, and `countPlaced`/`placed` count copies by what they show, not by receiver. Never `updateAll()` a widget class, and never read `getAppWidgetIds(receiver)` to mean "copies of this widget". Long-press a widget → its settings (`WidgetSettingsActivity`, `APPWIDGET_CONFIGURE`, a sheet over the home screen in `Theme.DailyDash.Sheet`: the preview, Ready-made or Tilesmith, the choices, that widget's AI line, one Done; every `*_widget_info.xml` declares it with `widgetFeatures="reconfigurable|configuration_optional"`, so placing never opens it) switches the copy to any widget and picks its `viewOption` (list tab, server), previewed at the copy's own size; `WidgetInstances.apply` stores it, clears the old widget's per-copy state keys (`tab`, `server`, `day`) and redraws. `setStateAction` taps from before a switch are ignored.

**Size classes.** `WidgetDims` reads the class from the size in dp, never the launcher's cell count: launchers disagree (Pixel Launcher on a 20:9 phone gives rows 118–130 dp, where `WidgetDims.cells` budgets 102), and a 2-row Pixel widget once got the 3-row layout and clipped. A class starts a little under its design size (`WidgetDims.minSize`); every layout must fit there. `WidgetDimsTest` pins Pixel sizes to their classes.

**Pixel text.** Glance styles all text with `TextAppearance.DeviceDefault`: Google Sans on a Pixel, about 6% taller than the Roboto every dp budget was measured in. `WidgetText.fontFit` (measured once per process by `WidgetText.calibrate` in `WidgetFrame`) draws that font just small enough to take Roboto's room; `ts()` applies it. The Roboto reference constants are pinned by the harness's `font` test. `WidgetRefreshWorker` (periodic, 15 min, no network constraint) calls each placed widget's `refresh()`, which honours its own TTL. Faster than 15 minutes (WorkManager's periodic floor) is the fast lane: one-off passes booked at the soonest `DashWidgetSpec.refreshEveryMs` any placed widget asks for (server and calendar 5 min, weather and GitHub 10, a Tilesmith design its own pick; booked from inside a pass with APPEND_OR_REPLACE so a pass never cancels itself), and `onUnlocked` (ACTION_USER_PRESENT, registered in DailyDashApp while the process runs) runs a pass when the last is over 3 min old. The server and GitHub stores' TTLs sit just under their cadence and take a `maxAgeMs` for Tilesmith; the header's refresh button enqueues a forced refresh of that widget (`enqueueForcedRefresh`) rather than running it inside the tap's broadcast. Widgets read only their own cached snapshot while rendering, inside composition via `rememberWidgetData`; `DashWidgets.render` bumps it. The server widget borrows SSH polling with `acquire("widget")` only when nothing else is polling and always releases it. AI lines come from `WidgetAi.brief` in `refresh` only (fingerprinted, rate-limited). Each ready-made widget has its own switch (`WidgetAi.isEnabled(context, key)`, keys in `WidgetAi.WIDGETS`; Settings → Widgets and the widget's long-press sheet); Tilesmith designs ignore them and use `TileDesign.ai`. `WidgetUpdater.updateAllWidgets(context)` is still the weather hook the app calls when the forecast changes.

**Previews are the widget itself.** Each spec's `renderPreview` (e.g. `WeatherWidgetPreview.render()`, which composes `WeatherRoot(data, preview = true)`) into `RemoteViews` through `GlanceRemoteViews` — using the cached data, or sample data when there is none. It feeds the in-app Widgets screen (whose size chips re-render it at each of the spec's `showcase` cell sizes) and, on Android 15+, `AppWidgetManager.setWidgetPreview` for the launcher picker (`DashWidgets.publishPreview`, self-throttled to 30 min per widget; the system rate-limits it). Preview hosts can't show a collection, so `preview = true` swaps every `LazyColumn` for a plain `Column` of the rows that fit whole (measured row heights, never more than ten). Older launchers and the pin dialog use the static `drawable-nodpi/widget_preview_<key>.png`, one per widget — captures of the real widget; re-capture them (below) whenever a widget's layout changes, never hand-draw them.

**Glance traps** (each has bitten these widgets; the render harness below catches them):
- A wrap-content Row/Column/Box with any `fillMaxHeight()`/`fillMaxWidth()` child is silently made fill-parent itself (Glance's size normalization), all the way up. A "full-height accent bar" in a wrap-content card makes the card swallow the rest of the column. Give the parent a fixed height, or draw the edge as a coloured outer box showing a few dp beside the inner one (the calendar hero).
- A Row/Column/Box keeps its first ten children and drops the rest with only a log line ("Truncated … container"). `HGap`/`VGap` count. Group into nested rows.
- A wrap-content `Text` in a `Row` still pushes later siblings off-edge — weight it or clip it.
- Layout budgets are dp constants measured from Roboto renders at font scale 1.0. `ts()` caps widget text at `WidgetText.MAX_SCALE` (1.15×) of the system font so larger system text doesn't cut off bottom sections, and applies `WidgetText.fontFit` so a taller device font (Google Sans) keeps to them; `WidgetFrame` records both. Every widget Text must go through `ts()`.

**Rendering widgets off-device.** Don't run the app to check a widget; render it. `./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*WidgetShotsTest*'` (opt-in: Robolectric is only on the test classpath with `-PwidgetShots`; the harness lives in `app/src/widgetShots/`) draws every widget through the real Glance and Android layout code, hardware-rendered so rounded corners show, into `app/build/widget-shots/`: `<key>_sheet.png` (2–5 × 1–5 cells), `v_<key>.png` (other tabs and states), `report_*.txt` (text ELLIPSIZED, SQUASHED or CLIPPED, and GLANCE truncation warnings; long titles ellipsizing is expected, the rest are bugs), and `previews/widget_preview_<key>.png` to copy into `res/drawable-nodpi`. `-PwidgetShotsFont=1.3` renders at a larger system font. Renders use the Pixel's font, Google Sans Flex (fetched once from Google Fonts into `app/build/widget-shots-fonts`; `-PwidgetShotsRoboto` keeps Robolectric's Roboto). Besides the representative sheets, `device_<key>.png` renders every widget at Pixel Launcher sizes on a 411 dp phone (5- and 4-column grids, 118 and 130 dp rows) and `min_<key>.png` at each class's `minSize`, with `report_device.txt` / `report_min.txt`; both must stay free of CLIPPED and SQUASHED. `WidgetSettingsShotTest` draws the settings screen into `settings.png`. `ChromeShotsTest` (same flag, `--tests '*ChromeShotsTest*'`) draws the island and the navbar, haze and all, over a white and a dark backdrop into `chrome_*.png`; look at them after touching Glass, TopIsland or PillNavigationBar. Look at the sheets before and after a layout change. The cloud container needs the Android SDK (`sdkmanager "platforms;android-36" "build-tools;36.0.0"`, `sdk.dir` in `local.properties`) to run it.

### Tilesmith (widget/tilesmith/, ui/screens/tilesmith/)
Widgets you describe and AI builds, in Material 3 Expressive. Reached from Settings → **Widgets & Tilesmith** (the row says "New" until `TilesmithStore.seen`), the Widgets screen's top card (`TilesmithHero`: prompt, one-tap starters, your designs; long-press a design to delete or duplicate), Hermes' menu in the AI tab (a header button while Tech support is Sysop), a widget's long-press settings (pick a design for that copy, or "Edit in Tilesmith"), and widget taps (`MainActivity.EXTRA_OPEN` `tilesmith:<id>`; also `home`, `health`, `ai`, `settings`, `widgets`, `servers`). The studio (`TilesmithStudioScreen`, route `SubScreenRoutes.TILESMITH_PATTERN`) opened with no design is the front door: example asks, **Your widgets** (every design, newest first; tap to open in place, hold to duplicate or delete) and the starters. Its top bar never scrolls: back, the name (tap to rename), Undo, Your widgets (a sheet, with **New**) and **Add** (to the home screen). With a design open: the design on a stage of fixed height (shorter with the keyboard up; light/dark toggle; the live line on its bottom edge), what it still needs as one strip of chips ("Allow all" for Android's own dialogs), then three panes: **Chat** (its own scroll, newest at the bottom, the AI's suggested next changes as chips, and while it works a line saying what it is doing, from `TilesmithAi.Progress`: the step, the heading of its thinking, a clock), **Blocks** (tap one to change what it shows as, its list style, colour, style, width, label, or move / remove it) and **Look** (size, colours, background, shape, updates, and this widget's AI switch, `TileDesign.ai`). The composer is always at the bottom: the AI pill (`AiPill` → `AiPickerSheet`), a picture (a widget you like, a sketch) through the photo picker, send / stop. Every change saves at once (placed copies redraw); Undo steps back through the last 20.

**A design** (TileModel.kt) is a `TileSize` (Mini 2×1, Small 2×2, Bar 4×1, Wide 4×2, Large 4×3; each caps rows, blocks per row and blocks), a `TilePalette` (Wallpaper or ten fixed M3 schemes generated with material-color-utilities), a `TileSurface` (soft, tonal, tinted, vivid) and up to four rows of `TileBlock`s (metric, ring, bar, chart, list, text, badge, clock, date, countdown, button, ai, image, carousel, media) with a tone, a style (tile, plain, filled, shape) and one of the 35 `TileShape`s. Parameterised sources read `query` (a Gmail search, YouTube channels, a Tasks list, an album, apps), `url`/`path`/`fields` (web.json lists map each row's title, sub, trailing, image and link), `cmd`/`server` and `text`. `TileCodec.parse` reads anything an AI sends (unknown fields dropped, values clamped, aliases mapped, old source names through `TileSources.canonical`) and `TileRules.sanitize` keeps it inside its size; `reflow` pours blocks into another size. TilesmithTest and TilesmithSourcesTest pin the codec, rules, layout, AI parsing, feeds, JSON and AI rows.

**Sources** (TileSources.kt) are the only things a block can `bind`: time, phone (incl. Bluetooth devices and headphone battery), now playing and notifications (the phone hub's `PhoneNotificationListener`), Health Connect (incl. workouts, weight, HRV), food, weather, calendar, Gmail and Google Tasks (`GoogleAccess`, read-only, with the dashboard's copy of the inbox as the fallback), contacts (birthdays, favourites), photos, YouTube (followed channels, or any named in `query`, via RSS) and Twitch, the dashboard's Coming up, F1, servers (stats and `server.cmd`, a shell command you approve first), GitHub, the dashboard (island, needs you, mail, Claude's windows), and the web: `web.feed` (any RSS/Atom feed, pictures and all, `TileFeeds`), `web.json`, `web.image`, `ai.text` (a line) and `ai.list` (rows), both with `web: true` to search, only while the design's AI switch is on (`TileDesign.ai`; not Settings → Widgets). Each has a sample for previews, its `TileNeed`s and a floor in minutes. `TileResolver` reads a design's due sources, one fetch per group (the newer ones in `TileReaders`), keeps the last good value on a failed read, runs AI sources last so they can see the rest, then prefetches the pictures the design draws (`TileImages`: downloaded once, shrunk, cached on disk; a render never touches the network). `TilesmithLive` redraws media and notification widgets when the listener sees a change (`refresh(only = …)`, which leaves the design's own pace alone). A design checks at `TileRules.effectiveRefreshMinutes` (its pick, never faster than its quickest source) and each source keeps `bindingMaxAgeMinutes`. `TileNeeds.check` lists what is still missing (runtime permissions, Health Connect incl. background reads, usage and notification access, Google scopes, followed channels, a server, GitHub, the dashboard, an AI provider, command and site approvals in `TilesmithStore`); the studio asks for each in one tap. **Settings → Permissions** (`PermissionsSettingsScreen`) lists everything the app can be allowed in one place, with "Allow everything" for Android's dialogs.

**The AI** (TilesmithAi.kt) gets the whole design language and every source with what is allowed, plus three example designs, and answers one JSON object `{"reply", "suggestions", "design"}` through `AiApiClient.stream` (jsonMode; Claude at `effort = medium` with its thinking summarized; a picture rides along as `base64Jpeg`). As it streams, `TilesmithProgress` (TilesmithProgressTest) turns the partial answer into the live line: the newest heading of the thinking, then "Designing …" and "Adding a ring · Steps" block by block, then Checking the design. It is told never to answer "not possible": anything without a source goes through the web sources and AI rows, and swipe/carousel asks become a carousel (widgets can't scroll sideways). A revision sends the current design and the last turns. An answer that isn't a design, or binds a source that doesn't exist, goes back once with what was wrong (`repairPrompt`); the better answer wins. `AiApiClient.GenerateParams.webSearch` turns on each provider's web search (Claude's `web_search_20260209`, or `_20250305` on Haiku, with `pause_turn` resumed; Gemini's `google_search`; OpenAI's search-preview models).

**Rendering** (TileRender.kt): `TileLayout.plan` lays the design out to the dp first (rows by height weight, cells by width weight, dropping whole rows or cells that would fall under a block's minimum), and `TileText.fit` sizes every text on the M3 type scale in the phone's own widget font, so the Glance tree is all fixed sizes. Colours are M3 roles (`TileRole`) as Glance `ColorProvider`s: resources for Wallpaper (`tilesmith_colors.xml`: the system's own tokens on Android 14+, the wallpaper's tonal palettes by tone on 12–13, Ocean before), day/night pairs otherwise, so widgets follow the wallpaper and dark mode on the launcher with no redraw. Shapes are the `tsm_shape_*` vectors, generated from `M3Shapes` (a port of Compose's `MaterialShapes` onto graphics-shapes) and pinned by TileShapeVectorsTest (`-PtilesmithWriteShapes` rewrites them); icons are white `tsm_ic_*` vectors; rings, wavy bars and charts are ALPHA_8 `TileMasks` stacked and tinted per role. Clocks are a TextClock and countdowns under a day a Chronometer (`AndroidRemoteViews`), so they tick with no refresh. A carousel is a StackView (`tsm_stack` / `tsm_card`, Android 12+, `RemoteCollectionItems`) whose cards share one mutable template PendingIntent to `TileLinkActivity`, filled in with each card's link (Android 14 forbids a mutable implicit one); before 12 it shows the first card. Lists with `variant: scroll` are a Glance `LazyColumn`; rows with pictures show them (16:9 for videos and feeds, round for people); rows and cards with a link open it through `TilesmithWidgetSpec.linkAction`. The player's buttons are `TileMediaAction` on `PhoneMedia.controller`. `TilesmithShotsTest` (`-PwidgetShots --tests '*TilesmithShotsTest*'`) renders the shapes, every starter light and dark, the surfaces, Pixel sizes with `report_tilesmith.txt`, and the picker previews (`widget_preview_tilesmith_<size>.png`).

**Placing**: one receiver per size (`TilesmithMiniReceiver` … `TilesmithLargeReceiver`, `DashWidgetSpec.receivers`) so a pinned design lands at its size. `TilesmithPin.request` saves the design, marks a pending pin, and calls `requestPinAppWidget` with the real render as the dialog's preview and `TilesmithPinReceiver` as the callback, which assigns the new copy (`TilesmithStore.assign`); a copy that renders before the callback adopts the pending design. A copy placed from the picker shows "Tap to pick a design" and opens its settings.

### In-app updates (GitHub Releases)
Sideload/tester path — not Play Core. CI publishes `DailyDash-{versionName}-vc{versionCode}.apk` on master merges. `AppUpdateRepository` polls GitHub while foregrounded; `AppUpdateWorker` checks every 6 h in the background and `AppUpdateNotifier` posts "DailyDash X is ready" with an Update action (`EXTRA_SHOW_UPDATE` / `EXTRA_START_UPDATE`, read in `MainScreen`), once per versionCode (`markAnnounced`, also set when the in-app sheet shows). The install is a process-scoped state machine, `AppUpdateInstaller`: Downloading (`.part` file, byte progress, cancellable; a finished APK is reused) → Installing (session committed) → Installing awaiting confirmation (Android's prompt is up; its intent is kept for "Show Android's prompt") → relaunch, or Ready with a note when the prompt was closed / the install failed / nothing came back in 90 s. `UpdateInstallActivity` reports every PackageInstaller status through `UpdateInstallEvents`. `AppUpdateViewModel` lays the phase over the GitHub check; `AppUpdateSheet` (bottom sheet, after Essentials) shows exactly one step's controls. No permission yet → `NeedsPermission`; the update starts on return from Android's settings. `UpdateInstallActivity` relaunches `MainActivity` with `EXTRA_RELAUNCHED_AFTER_UPDATE` / `EXTRA_SHOW_WHATS_NEW`; `PackageReplacedReceiver` posts a tap-to-open notification if relaunch is blocked. Post-update, `WhatsNewDialog` shows once (notes cached at download time, enriched from `/releases`). Soft-snooze is 12h (`Later`); Settings badge deep-links to About + opens the sheet. Key files: `data/update/*`, `ui/components/AppUpdateDialog.kt` (`AppUpdateSheet`), `WhatsNewDialog.kt`, `AppUpdateViewModel`, `.github/scripts/package-release.sh`.

## Commit, push & release (mandatory when user asks)

When the user asks to **commit**, **push**, or **commit and push** (with or without a suggested message), follow the normal git safety rules **and** this release hygiene so the in-app updater + What's New stay correct.

### Pipeline (do not fight it)
1. Push / merge to **`master`** triggers `.github/workflows/build-apk.yml`.
2. CI runs `ensure-unique-version.sh` — bumps `versionCode` / `versionName` only if needed, commits `chore: bump version to … [skip ci]`, then builds.
3. `package-release.sh` builds **What's New** from **commit subjects since the previous `v*` tag** (plus PR titles when present), publishes the GitHub Release + `DailyDash-{versionName}-vc{versionCode}.apk`.
4. Installed apps poll GitHub, download, PackageInstaller self-update, relaunch, show What's New.

### Commit message = release notes (critical)
**Your commit subject line is what users see in What's New.** CI copies non-noise subjects from `git log prev_tag..HEAD` into the GitHub Release body; the app shows that body after update.

Write the subject as a human release note:

- **One clear, user-facing sentence** describing what changed and why it matters (imperative or past tense is fine).
- Prefer product language over file lists:  
  ✅ `Refresh Health with clearer metric icons and a cleaner Daily Health card`  
  ✅ `Show clothing icons on the weather card for today's conditions`  
  ❌ `Update AppUpdateRepository.kt and MainScreen.kt`  
  ❌ `wip` / `fix` / `stuff`
- Keep it to the **subject line** (≈72 chars is ideal). Extra body text is fine for reviewers but is **not** used in What's New.
- Do **not** put `[skip ci]` on feature/fix commits (that skips the APK release entirely).
- Do **not** hand-bump `versionCode` / `versionName` in `app/build.gradle.kts` — CI owns that.
- Do **not** create git tags or GitHub Releases yourself for normal ship flow — the workflow does.
- Noise filtered out of notes: `chore: bump version…`, anything with `[skip ci]`, merge-only titles, `wip` / `fix stuff`.
- If the user supplies a commit message, use it when it is already release-note quality; otherwise lightly tighten it into a clear What's New bullet **without** changing their intent. Confirm only if their message would ship as useless notes (e.g. only `wip`).
- When shipping several changes in one push, either one strong subject covering the theme, or multiple commits each with its own user-facing subject (each becomes its own bullet).

### Push target
- Default ship path: commit on the current branch, then **`git push -u origin HEAD`** (or push the tracked branch).
- If the work is already on **`master`** (or the user asked to ship to master), pushing `master` is what publishes the APK. Prefer that when they said "commit and push" for a finished feature on master.
- If on a feature branch and they did **not** ask to merge/PR, push the branch; remind them the APK releases only after it lands on `master`.
- Never `--force` to `master` / `main`. Never commit secrets (`local.properties`, keystores, API keys).

### After push (when shipping to master)
Briefly tell the user:
- Commit hash + **subject** (this is the What's New bullet).
- That **Build & Release APK** will publish the tester APK with that subject in the release notes.
- They can update from the app (Settings badge / dialog); after install it should reopen with What's New.

### External APIs
| Service | Client | Notes |
|---|---|---|
| Claude / Cursor / Hermes / Gemini / OpenAI | OkHttp (`AiApiClient`, `AiModels`) | Provider, key and model from Settings; Claude signs in with Claude Code OAuth (`ClaudeAuthClient`); Cursor and Hermes through the dashboard's `/_api/v1/chat/completions`; BuildConfig fallback for keys |
| OpenF1 | Ktor (`HttpClient`) | Base URL `https://api.openf1.org/v1/`; browser User-Agent set in `AppModule` |
| YouTube | RSS feed (OkHttp) + Data API v3 OAuth | Manual channels via RSS; Connect Google imports `subscriptions.list` into Watching |
| Twitch | Helix (OkHttp) + Device Code (Custom Tabs) | `twitch.tv/activate` (no runtime redirect); imports follows; live board (60s cache) |
| Weather | OkHttp (`WeatherRepository`) | met.no Locationforecast 2.0 (Yr symbol codes); clothing advice is local (`ClothingAdvice`) |
| Health Connect | SDK | Read-only; lazy client; gracefully returns null if SDK unavailable |
| GitHub (home card) | OkHttp (`GitHubRepository` + `GitHubAuthClient`) | Device Code OAuth (Custom Tabs → github.com/login/device); REST `/user`, search issues/PRs `involves:@me`, `/user/repos`, `/users/{login}/events`; GraphQL `contributionsCollection`; scopes `repo read:user` |
| F1 circuits | OkHttp (`F1CircuitRepository`) | raw.githubusercontent.com/bacinger/f1-circuits: `f1-locations.json` (30-day cache) + one GeoJSON per circuit (kept for good) |
| t3lluz dashboard | OkHttp (`UpcomingRepository`, `DashboardLinkRepository`, `F1HubRepository`, `HermesClient`) | Tailnet-only static JSON (`_stats.json`, `_history.json`, `_f1.json`, `_f1final.json`, `_f1live.json`, tiles in `index.html`) and the bridge under `/_api/ai/*` for Hermes |
| GitHub Releases | OkHttp (`AppUpdateRepository`) | In-app APK updates + changelog |

### Background work, lifecycle and shared helpers (2.0)
Nothing polls or ticks unseen. A loop in a composable (a refresh every N minutes, a clock) runs in
`LaunchedWhileResumed(keys) { … }` (ui/util/Motion.kt), which cancels it when the screen or the app goes to
the background and starts it again on return; a hold that must be released (server polling) keys a
`DisposableEffect` on `rememberIsResumed()`. View-model loops check `ProcessLifecycleOwner` STARTED.
`rememberToday()` is a date that turns at midnight. Food totals follow `MacroRepository.changes` (fires on
every log, delete and goal change) instead of throttled reloads. Health Connect reloads on return to the tab
only after `HealthViewModel.RESUME_RELOAD_MS`; pull to refresh forces it.

**Load when seen** (after Pixel's Settings: a RecyclerView binds only the rows on screen, and a slow summary
shows a placeholder until it lands). Long pages are LazyColumns; Home activates a card (its data and full
content) only when it scrolls in, plus the one LazyColumn prefetches (`rememberVisibleHomeWidgetIds`). In a plain
scrolling Column (settings pages, a card's `WidgetScrollBox`) wrap what is below the first screen, or a long
list's rows, in `LoadWhenSeen(placeholderHeight)` (ui/util/LoadWhenSeen.kt), or use `Modifier.onFirstSeen { }`
when the content has its own skeleton (the Widgets screen's previews). Both use Compose's `onFirstVisible`;
`WidgetScrollBox` provides its own bounds as the viewport (`LocalSeenViewport`). Once seen, it stays built.

Shared helpers to use rather than re-write: `notifyIfAllowed` / `canPostNotifications` (data/Notifications.kt)
for every notification; `agoShort(ms)` ("5m", "3h") and `agoWords(ms)` ("5 min ago") for ages, `tookShort(ms)` ("46s", "2m 05s") for how long something took, `rememberClock24()` /
`rememberClockFormatter()` for the phone's clock (ui/util/LastUpdated.kt); `Hairline` (components/Hairline.kt) for a
rule between rows; `findActivity()`, `openUrl(url)` (never a bare `startActivity(ACTION_VIEW)`) and
`requestOrOpenSettings(permission) { launch }` (opens app settings once Android stops prompting) in
ui/util/Activity.kt; `preparePhoto(...)` (ui/util/Images.kt) for any photo an AI reads: scaled, upright,
off the main thread; `toDecimalOrNull()` (util/Numbers.kt) for typed numbers ("2,5" and "2.5");
`readAtMost(limit)` (util/Streams.kt) for picked files. UI: `WidgetStateSwitch(state, contentKey = { it::class })`
fades between a card's kinds of state; `FoodLogForm` / `FoodLogList` are the one food entry form and list (`onAskAi` adds Estimate with AI beside Add);
`ServiceButton` is a hub's Connect / Add channels button (the caller gives the haptic); `HeroListSkeleton`
is an opened media hub's first load; `WidgetPromptCard` is a tile with one line and one `TonePill`. Repositories restore their
disk caches off the main thread (`restored = scope.async { restoreDiskCache() }`, awaited by the calls that
need it). The baseline profile ships as `.dm` files the updater installs beside the APK; an install that fails
with one is retried at once without it.

### Compose Strong Skipping
Strong skipping is the Compose compiler default on Kotlin 2.x, so there is no flag in `app/build.gradle.kts`. Composables with unstable parameters will skip recomposition automatically — avoid fighting this with `@Stable`/`@Immutable` unless you observe real correctness issues.

**Per-frame values stay out of composition.** Anything that changes every frame (a fade, a pulse, a chevron's turn, a press scale, a chart's reveal) is kept as a `State` and read inside `graphicsLayer { }`, a `Canvas`/`drawBehind` block or a layout lambda, never as a plain value in the composable body: `MacroCard`'s entrance fade, `LivePulseDot`, `TypingDots`, the expand chevrons, the island's pulse, server dials and Health's `RangeBarChart` and body clock do this. A list row gets only what it reads, not a whole screen state: a streamed token makes a new state, and every row that took it re-ran. `MacroCard` also stops reading `LocalTickersPaused` once its entrance has run, so scroll starts and stops don't recompose every card. Home's draggable list gives each card its own `contentType` (its id), as Health's fixed list does, so a slot is only reused for the same card.

## Important Files to Read First
- `di/AppModule.kt` — understand what is injected and how
- `data/local/Entities.kt` — the two Room entities (only calories + protein tracked)
- `ui/navigation/Screen.kt` + `DailyDashNavHost.kt` — full route map
- `ui/screens/home/HomeLook.kt` + `HomePage.kt` — Home's tiles and the page a tile opens
- `ui/theme/Animation.kt` (`MacroMotion`) — all animation specs
- `ui/viewmodel/DashboardViewModel.kt` — per-metric Health Connect states (today/yesterday) consumed by `HealthScreen`
- `ui/components/BodyStats.kt` — `HealthMetricUiState` data class
- `widget/WidgetUpdater.kt` + `WidgetRefreshWorker.kt` — widget update strategy
- `widget/kit/WidgetKit.kt` + `WeatherWidgetPreview.kt` — Glance chrome and how previews are rendered
- `ui/screens/onboarding/` — multi-step onboarding flow (SplashScreen overlay + 3 nav-routed screens)

### Copy
Minimal. A card says what it is and its numbers; it doesn't explain itself. No subtitles that describe a card's purpose, no footnotes on sheets, no paragraphs under a reading (Body & Vitals keeps one only for an estimate, to say how it was worked out). Empty states are a short line. Horizontally scrolling chip rows fade at the end that has more (`Modifier.horizontalEdgeFade`, components/EdgeFade.kt).
