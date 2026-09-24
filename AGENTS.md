# DailyDash — Agent Guide

## Project Identity
Single-module Android app (Kotlin + Jetpack Compose). The **app label is "DailyDash"**; the package/module name is `com.macrotracker`. Keep both names in mind — they differ intentionally.

**Ship note:** When the user asks to commit/push, follow **[Commit, push & release](#commit-push--release-mandatory-when-user-asks)** so GitHub Releases + in-app What's New stay accurate (message quality, no manual version bumps, no `[skip ci]` on real changes).

## Build & API Keys
```bash
./gradlew assembleDebug      # standard debug build
./gradlew installDebug       # build + deploy to connected device
```
**Do not assemble, install, or run the app to verify UI.** The user keeps an Android emulator in the same directory as this project and checks changes themselves.

API keys go in `local.properties` (never committed):
```
GEMINI_API_KEY=...
OPENAI_API_KEY=...
OPENROUTER_API_KEY=...
YOUTUBE_API_KEY=...
TWITCH_CLIENT_ID=...
TWITCH_CLIENT_SECRET=...   # optional locally; required for confidential Twitch apps / CI search
GITHUB_CLIENT_ID=...       # GitHub OAuth App Client ID for the home GitHub card (Device Flow; no secret in the APK)
GITHUB_TOKEN=...           # optional PAT fallback if OAuth Client ID is not set
```
**GitHub Releases:** `.github/workflows/build-apk.yml` writes the same keys from repo Actions secrets into `local.properties` before `assembleRelease`. Required for Twitch in published APKs: `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`. Required for GitHub Connect in published APKs: Actions secret `GH_OAUTH_CLIENT_ID` (OAuth App Client ID — **not** a PAT and **not** the automatic Actions `GITHUB_TOKEN`; GitHub forbids secrets named `GITHUB_*`). CI writes it as `GITHUB_CLIENT_ID` in `local.properties`. Optional mirrors of local keys: `GEMINI_API_KEY`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `YOUTUBE_API_KEY`. YouTube **Connect Google** does not use BuildConfig keys — it needs Google Cloud Console (YouTube Data API v3 + Android OAuth client for package `com.macrotracker` + `tester.jks` SHA-1); CI already signs releases with `app/tester.jks`.

At runtime, Settings lets the user pick **Gemini**, **OpenAI**, **OpenRouter**, or **Claude**. Gemini / OpenAI / OpenRouter need a pasted API key. Claude can **Connect** with Claude Code's public OAuth (Pro / Max / Team / Enterprise — same login T3 Code uses via `claude auth login`) so usage comes from the Claude.ai subscription; an `sk-ant-…` key remains an optional fallback. For OpenRouter, Settings also shows a curated cheap-model picker with list prices. Stored keys take priority over build-time keys; a Claude OAuth session wins over a stored Anthropic key. `NutritionAiRepository` / chat (`data/chat/AiChatClient`) all route through `AiApiClient` based on `SettingsRepository.aiProvider` (`AiCredentialResolver` + `ClaudeAuthClient`).

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
    remote/                ← Gemini/OpenAI/OpenRouter/Claude via OkHttp (`AiApiClient`; NutritionAiRepository),
                              WeatherRepository (met.no forecast) + ClothingAdvice, LocationProvider
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
                              turn started on the web shows in the navbar tab, the ongoing notification and
                              the open chat; the feed reports it as follower FEED, which never overrides PANE
                              or SERVICE, and hands its turns to the service (`handOffFeed`) when the app
                              goes to the background. Duty and staff threads are skipped, as the web skips
                              them. It also re-emits `events` (the chat pane uses it instead of its own
                              connection) and `files` (stats/history/f1 changed; the server dashboard and
                              Coming up refresh off it)
    server/                ← server monitor: SSH probes (SshClient/ServerProbe), ServerMonitorService +
                              ServerNotifier, encrypted ServerStore, ServerAdvisories.
                              ServerProbe has three lanes: the fast script (every poll: /proc, df, hwmon
                              temps, PSI, diskstats, cpu MHz, battery, ps with RSS), the detail script (every
                              30 s, only while a `startPolling(detailed = true)` consumer holds it: docker
                              stats, ps by RSS, `ss -tln`, running units, journal errors) and the news script
                              (15 min: updates). Scripts are POSIX sh with no variables, every command guarded.
                              DashboardSettingsSync: the web's settings blob (`/_api/sync`, last write wins)
                              shares the new-chat Hermes mode (`ai.perm`), `units` and `wind` with the phone.
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
                              (`SettingsRepository.birthYear`, asked in the card) sharpens both. `readSleepNights()` groups
                              two weeks of sessions by the 18:00 → 18:00 sleep day; `readHourlySteps()`
                              is today in 24 one-hour slices. HealthVitals.kt holds the pure maths
                              (daily means, 30-day baselines, readiness, sleep consistency, BMI / blood
                              pressure / VO₂ max / glucose bands, the heart-rate derivations and
                              estimates); HealthVitalsTest pins it.
    f1/                    ← F1Repository via Ktor + OpenF1 API (https://api.openf1.org/v1/);
                              15-min in-memory + SharedPrefs disk cache. F1Circuits: circuit outlines from
                              bacinger/f1-circuits matched by coordinates (nearest within ~25 km), folded into a
                              0–100 box exactly as the dashboard's `f1_path()` does, cached per circuit for good
                              and attached to each `RaceScheduleEntry.outline`. A cancelled load must never be
                              cached (it used to cache a season with no circuits); a cache hit fills in any
                              circuits it is missing
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
                              `_stats.json` (`rows.upcoming`: Sonarr, Radarr, Stremio, F1 sessions) plus
                              `_f1.json` circuits. Server URL is `SettingsRepository.dashboardServerUrl`
                              (Settings → Connections); tailnet-only, so the last good copy is kept on disk.
                              UpcomingTimeline.kt holds the strip's rules (rest card, default focus, jumps)
    calendar/              ← CalendarRepository (READ_CALENDAR permission). All-day instances are stored
                              at UTC midnight — always convert with `calendarLocalDateTime(millis, allDay, zone)`
                              so they land on the right day
  di/
    AppModule.kt           ← all @Provides (DB, DAO, OkHttpClient, KtorClient);
                              @Binds abstract modules for F1, YouTube, Twitch, and GitHub interface → impl
  ui/
    screens/               ← one file per tab screen (HomeScreen, HealthScreen, AIScreen,
                             SettingsScreen) + sub-screens (StatsScreen, HelpScreen, CameraScanScreen)
                             + onboarding/ (SplashScreen overlay, WelcomeScreen, PermissionsScreen, TutorialScreen)
                             + ai/ (ChatKit.kt shared chat bubbles/header/composer + IME helpers, SysopChatPane,
                             HermesChatPane + HermesChrome.kt: the web panel without its side pane — thread
                             drawer (ModalNavigationDrawer, opened by button only), tall composer with mode /
                             model / depth chips, attach, send-or-Stop, queue while busy, slash palette, picker
                             sheets. A chat only restores its saved model when the person opens it; the model
                             is Hermes-global. The AI tab has no page title: one compact SegmentedTabs row, then
                             each pane's one-row header. Composers sit `composerBottomGap()` above the pane
                             bottom (follows the keyboard frame by frame; never switch on "ime > 0"), and
                             `FollowChatOnKeyboard` pins the list with plain scrolls, never an animation per
                             frame. Tech support is Hermes when `HermesViewModel.usesHermes`: an
                             explicit `techSupportBrain` choice, or in `auto` whenever Hermes answers, else Sysop)
                             + server/ (the server screen's cards: hero, history, services wall as tiles,
                             activity, compute, memory, network, storage, sensors, processes, containers,
                             system). Sections reorder and toggle like Home (pencil in the header;
                             `serverSectionOrder`); a section with nothing to show keeps its slot
                             + health/ (Health tab sections) + settings/ (category sub-screens)
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
                              F1UiState.kt / GitHubUiState.kt (dedicated files for sealed UI state)
    navigation/            ← Screen.kt (sealed class, 4 bottom-nav tabs) + OnboardingRoutes / SettingsRoutes /
                             SubScreenRoutes (const routes) + DailyDashNavHost.kt + NavigationActions.kt
                             (`navigateToTab`, `popSubScreen`, `subScreen` destination builder)
    components/            ← shared Composables (MacroCard, PillNavigationBar, DraggableWidgetColumn,
                              WidgetEditor, WidgetExpandBar, PillButton, …). PillNavigationBar grows a
                              tab from its top-left edge while Hermes works (NavActivityTab.kt:
                              `NavWithTabShape` is pill + tab as one outline, so the glass has no seam;
                              `LocalNavTabRise` lifts the chat composers with it). WorkingScanner.kt is
                              opencode's Knight Rider scanner (KnightRiderTest pins it to opencode's
                              frames). ScreenHeader.kt: tab
                              `ScreenHeader`, pushed-screen `SubScreenHeader`, `TabContentBottomPadding`,
                              `Modifier.subScreenBottomPadding()`. HomeWidgetShell.kt: card chrome shared by
                              every Home/Health card — `CardHeader`, `HubCardHeader` + `HubHeaderAction`,
                              `HubErrorState` (message + Retry), `WidgetPromptCard`, `ChannelSheetHeader`,
                              expand/scroll-box helpers. MarkdownParser.kt + MarkdownText.kt: the web's
                              `hermes-markdown.js` rules (GFM tables, nested/task lists, callouts, `run`/`search`
                              fences, diffs); `breaks`/`streaming` for chat. MarkdownParserTest pins it.
                              WeatherForecast.kt: the opened weather card (one "now" panel, HourlyTimeline with
                              its temperature curve, a day per row with a range bar that unfolds that day's
                              `DailyForecast.steps`). CalendarStrip.kt: the calendar card (summary line, a day
                              strip to the last busy day within `CalendarRepository.WINDOW_DAYS`, events in
                              MediaCarousel, agenda). A peek never shows a badge or words: it shows who
                              (`PeekAvatar`, the creator's picture) or when (the calendar's date column), faded
                              with `peekAlpha`; badges and words come in with `textAlpha`. MediaCarousel.kt: the M3 multi-browse carousel the
                              collapsed YouTube and Twitch cards use (`MediaItemLook` read in draw/layer blocks
                              only; tap a peek to bring it in). F1CircuitMap.kt: the dashboard's four-pass circuit
                              (kerb, bed, marque line, car) that paints its lap on screen, with a
                              `CircuitMapWeight` per place and `CircuitMotion` STILL / PAINT_ONCE / BACKDROP
                              (replays, and keeps a dim car lapping). F1Card.kt: the F1 hub in the app's look
                              (inset wells, stat tiles and pills like Health; round team-ringed faces; logo tiles
                              that fall back to `F1Format.teamCode`), its five tabs a `SegmentedTabs(stacked =
                              true)` (icon over label). GitHubContributionGraph.kt: the year with
                              the snake. ServerCharts.kt / ServerVitals.kt: 270° dials with an average notch,
                              mirrored area charts, the scrubbable history chart, stacked meters, uptime bars,
                              fact chips. DeviceCodePanel.kt: GitHub/Twitch device-code
                              sign-in (copyable code + equal-width Open / Cancel). DottedFrost.kt holds the
                              Cinema-Info glass chrome — use `Modifier.dottedGlass(hazeState, shape)`
                              on any frosted surface above a `hazeSource` (nav pill, AI composer).
                              It stacks two masked haze passes: heavy blur everywhere *except* the
                              dot cores, then a light blur *only* at the cores. `Modifier.dottedFrost()`
                              is just the painted-dot fallback for surfaces with nothing to blur
                              (and for API < 31) — don't reach for it as the effect itself.
    theme/                 ← Color, Theme, Animation (MacroMotion object — single source for all specs),
                              AppIcons.kt — the app's only icon set (generated Lucide/Tabler ImageVectors).
                              The Material icons dependency is gone: never import
                              `androidx.compose.material.icons`. Text colours use `TextPrimary` /
                              `TextSecondary` / `TextTertiary`, never `TextSecondary.copy(alpha = …)`
    util/                  ← HapticHelper (Compose-friendly performHapticFeedback wrapper, ui/util/Haptics.kt)
                              + LastUpdatedText composable + rememberRelativeTime (ui/util/LastUpdated.kt)
  widget/                  ← Five Glance home-screen widgets, all resizable (`SizeMode.Exact`), listed in
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
                              TTL) and runs refresh-button taps; WidgetStateProvider (what is placed?).
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

Health Connect metrics use `HealthMetricUiState(value, today, yesterday, isEnabled)` from `ui/components/BodyStats.kt`. Use `calculatePercentageChange(today, yesterday)` to derive the delta arrow shown on each metric card.

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

Sub-screens (`SubScreenRoutes.STATS/HELP/WIDGETS/CAMERA_SCAN` and the `SettingsRoutes` categories, including the `servers` dashboard) are registered with the `subScreen(route) { … }` builder from `NavigationActions.kt`, which applies the `MacroMotion.subScreenEnter/Exit/PopEnter/PopExit` transitions. Parent screens push them imperatively (e.g. `SettingsScreen` → `help`, `AIScreen`/`HealthScreen` → `camera_scan`). Every sub-screen uses `SubScreenHeader` and closes with `navController.popSubScreen(entry)` so a double-tapped back arrow can't pop the tab underneath.

**Tab switches must go through `navigateToTab(route)`** (pop to the start destination with `saveState`, `launchSingleTop`, `restoreState`). Never `navigate()` a tab route on top of another tab or a sub-screen — that tab's saved stack then ends in the other tab and the nav pill stops responding. (The one exception is finishing onboarding, which replaces the onboarding stack with Home.) The server dashboard → AI hand-off uses `navigateToTab(Screen.AI.withSeed(id), restoreState = false)` so the seed args beat the AI tab's saved state.

Callers must not add haptics to components that already fire their own: `MacroButton`, `PillButton`, `SettingsCategoryRow`, `HubHeaderAction`, `HubErrorState`, `WidgetExpandBar`, `DeviceCodePanel`.

### Decorative motion
Two long-running animations mirror the t3lluz web dashboard and take their specs from `MacroMotion.CircuitPaint`
and `MacroMotion.Snake`. Both run off a clock read only in the draw phase (a redraw per frame, never a
recomposition), take frames only while on screen (`Modifier.trackOnScreen`) and the app is resumed
(`rememberIsResumed`), and show their finished state when animations are off (`rememberReducedMotion`).

Home's pull to refresh is Essentials' (sameerasw/essentials): a tick every tenth of the pull, then a click and
`Modifier.liquidRipple` (components/LiquidRipple.kt, its AGSL shader ported as is; Android 13+, off with
animations off) out from the header (`rippleAnchor`) the moment the pull passes the threshold, only while a
finger drives it. Specs in `MacroMotion.LiquidRipple`.

### Home Screen Widgets (draggable)
Widget order and visibility are persisted as a single colon-and-comma encoded string in SharedPrefs:
```
"WEATHER:true,CALENDAR:true,UPCOMING:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,GITHUB:true,YOUTUBE:true,TWITCH:true"
```
`DraggableWidgetColumn` + `WidgetEditor` read/write this via `SettingsRepository`. **`GITHUB`** is the home GitHub hub (`GitHubCard`): account-wide issues, PRs, activity, and repos for the connected GitHub user (not a single project). Connect with Device Code OAuth on the Account tab (`repo` + `read:user`).

**`YOUTUBE`** and **`TWITCH`** collapsed show their videos / live streams in `MediaCarousel`; the Twitch channel chips carry the live dot there too.

**`UPCOMING`** is Coming up (`UpcomingCard`): a Material 3 `HorizontalCenteredHeroCarousel` with `singleAdvanceFlingBehavior`, one card per entry in time order. Items are masked, not resized, so anything that grows or fades with a card reads `carouselItemDrawInfo` inside `graphicsLayer`/draw blocks (`openness()`, `followMask()`) and never in composition. Programmatic moves use `MacroMotion.carouselTravel`.

The **Health screen** uses the same draggable pattern with a separate key (`healthWidgetOrder`):
```
"DAILY_HEALTH:true,SLEEP:true,ACTIVITIES:true,VITALS:true,BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true"
```
A card missing from a saved order (one added in an update) is slotted in after the card that precedes it by default (`parseWidgetConfig`), not appended. `DAILY_HEALTH` is the hero Daily Health card (Apple-style activity rings, readiness from last night's sleep + HRV + resting HR against the person's own 30-day baseline, today's steps hour by hour, and dynamic today metrics). **`SLEEP`** is last night in full (score, bed → wake, stage mix, hypnogram) over a two-week schedule chart; tap or drag a night to read it, with bedtime/wake regularity and sleep debt below. **`VITALS`** (Body & Vitals) is one tile per measure Health Connect has (HRV, resting HR, sleeping HR, daily heart rate, weight + BMI, body fat, lean mass, body water, bone mass, VO₂ max, blood pressure, glucose, SpO₂, respiration, body and skin temperature, hydration, resting energy), plus estimates marked "Est." where nothing writes the measure (resting HR, VO₂ max, resting energy); a tile opens its scrubbable trend with the person's usual range. Shared pieces (chips, delta pills that know which way is good, sparklines, stat tiles) live in `health/HealthUiKit.kt`. The tab pulls to refresh (`HealthViewModel.refresh()`), and Trends pages back `HealthViewModel.MAX_WEEKS_BACK` weeks with week-over-week deltas. **`ACTIVITIES`** lists the **last month** of workouts synced through Health Connect (Garmin Connect, Google Fit, Samsung Health, Strava, and others): type, source, duration, distance, pace, heart rate, elevation, and a GPS route map when the session includes one. A featured hero card sits above three compact rows, with **Show N more** revealing the rest in a scroll box. GPS is read up front only for the newest `EAGER_ROUTE_COUNT` sessions; the rest resolve through `HealthViewModel.onActivityExpanded()` when a row is opened (`routeResolved` gates the "Loading map…" placeholder). `WEEK_AT_A_GLANCE` is the Macro Trends widget (7/14/30-day nutrition chart + per-day food logs), moved from the former History tab.

### App Widgets (Glance)
Five home-screen widgets: **Weather**, **Calendar**, **F1**, **Server** and **GitHub**, registered in `DashWidgets.all` and the manifest. Every one is resizable and switches layout on `WidgetDims(LocalSize.current).cols/rows`: a smaller size drops whole sections, never squeezes them.

**Each placed copy owns what it shows.** All five receivers draw through one `GlanceAppWidget`, `DashWidget`, which reads the copy's widget from `WidgetInstances.specFor(context, appWidgetId)` (a stored pick, else the receiver it was placed from) and composes that spec's `Content`. Re-renders go by id: `DashWidgets.render(spec)` updates only the ids showing `spec`, and `countPlaced`/`placed` count copies by what they show, not by receiver. Never `updateAll()` a widget class, and never read `getAppWidgetIds(receiver)` to mean "copies of this widget". Long-press a widget → its settings (`WidgetSettingsActivity`, `APPWIDGET_CONFIGURE`; every `*_widget_info.xml` declares it with `widgetFeatures="reconfigurable|configuration_optional"`, so placing never opens it) switches the copy to any widget and picks its `viewOption` (list tab, server), previewed at the copy's own size; `WidgetInstances.apply` stores it, clears the old widget's per-copy state keys (`tab`, `server`, `day`) and redraws. `setStateAction` taps from before a switch are ignored.

**Size classes.** `WidgetDims` reads the class from the size in dp, never the launcher's cell count: launchers disagree (Pixel Launcher on a 20:9 phone gives rows 118–130 dp, where `WidgetDims.cells` budgets 102), and a 2-row Pixel widget once got the 3-row layout and clipped. A class starts a little under its design size (`WidgetDims.minSize`); every layout must fit there. `WidgetDimsTest` pins Pixel sizes to their classes.

**Pixel text.** Glance styles all text with `TextAppearance.DeviceDefault`: Google Sans on a Pixel, about 6% taller than the Roboto every dp budget was measured in. `WidgetText.fontFit` (measured once per process by `WidgetText.calibrate` in `WidgetFrame`) draws that font just small enough to take Roboto's room; `ts()` applies it. The Roboto reference constants are pinned by the harness's `font` test. `WidgetRefreshWorker` (periodic, 15 min, no network constraint) calls each placed widget's `refresh()`, which honours its own TTL; the header's refresh button enqueues a forced refresh of that widget (`enqueueForcedRefresh`) rather than running it inside the tap's broadcast. Widgets read only their own cached snapshot while rendering, inside composition via `rememberWidgetData`; `DashWidgets.render` bumps it. The server widget borrows SSH polling with `acquire("widget")` only when nothing else is polling and always releases it. AI lines come from `WidgetAi.brief` in `refresh` only (fingerprinted, rate-limited, off with Settings → Widgets). `WidgetUpdater.updateAllWidgets(context)` is still the weather hook the app calls when the forecast changes.

**Previews are the widget itself.** Each spec's `renderPreview` (e.g. `WeatherWidgetPreview.render()`, which composes `WeatherRoot(data, preview = true)`) into `RemoteViews` through `GlanceRemoteViews` — using the cached data, or sample data when there is none. It feeds the in-app Widgets screen (whose size chips re-render it at each of the spec's `showcase` cell sizes) and, on Android 15+, `AppWidgetManager.setWidgetPreview` for the launcher picker (`DashWidgets.publishPreview`, self-throttled to 30 min per widget; the system rate-limits it). Preview hosts can't show a collection, so `preview = true` swaps every `LazyColumn` for a plain `Column` of the rows that fit whole (measured row heights, never more than ten). Older launchers and the pin dialog use the static `drawable-nodpi/widget_preview_<key>.png`, one per widget — captures of the real widget; re-capture them (below) whenever a widget's layout changes, never hand-draw them.

**Glance traps** (each has bitten these widgets; the render harness below catches them):
- A wrap-content Row/Column/Box with any `fillMaxHeight()`/`fillMaxWidth()` child is silently made fill-parent itself (Glance's size normalization), all the way up. A "full-height accent bar" in a wrap-content card makes the card swallow the rest of the column. Give the parent a fixed height, or draw the edge as a coloured outer box showing a few dp beside the inner one (the calendar hero).
- A Row/Column/Box keeps its first ten children and drops the rest with only a log line ("Truncated … container"). `HGap`/`VGap` count. Group into nested rows.
- A wrap-content `Text` in a `Row` still pushes later siblings off-edge — weight it or clip it.
- Layout budgets are dp constants measured from Roboto renders at font scale 1.0. `ts()` caps widget text at `WidgetText.MAX_SCALE` (1.15×) of the system font so larger system text doesn't cut off bottom sections, and applies `WidgetText.fontFit` so a taller device font (Google Sans) keeps to them; `WidgetFrame` records both. Every widget Text must go through `ts()`.

**Rendering widgets off-device.** Don't run the app to check a widget; render it. `./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*WidgetShotsTest*'` (opt-in: Robolectric is only on the test classpath with `-PwidgetShots`; the harness lives in `app/src/widgetShots/`) draws every widget through the real Glance and Android layout code, hardware-rendered so rounded corners show, into `app/build/widget-shots/`: `<key>_sheet.png` (2–5 × 1–5 cells), `v_<key>.png` (other tabs and states), `report_*.txt` (text ELLIPSIZED, SQUASHED or CLIPPED, and GLANCE truncation warnings; long titles ellipsizing is expected, the rest are bugs), and `previews/widget_preview_<key>.png` to copy into `res/drawable-nodpi`. `-PwidgetShotsFont=1.3` renders at a larger system font. Renders use the Pixel's font, Google Sans Flex (fetched once from Google Fonts into `app/build/widget-shots-fonts`; `-PwidgetShotsRoboto` keeps Robolectric's Roboto). Besides the representative sheets, `device_<key>.png` renders every widget at Pixel Launcher sizes on a 411 dp phone (5- and 4-column grids, 118 and 130 dp rows) and `min_<key>.png` at each class's `minSize`, with `report_device.txt` / `report_min.txt`; both must stay free of CLIPPED and SQUASHED. `WidgetSettingsShotTest` draws the settings screen into `settings.png`. Look at the sheets before and after a layout change. The cloud container needs the Android SDK (`sdkmanager "platforms;android-36" "build-tools;36.0.0"`, `sdk.dir` in `local.properties`) to run it.

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
| Gemini / OpenAI / OpenRouter / Claude | OkHttp (`AiApiClient`) | Provider + key from Settings; Claude can Connect via Claude Code OAuth (`ClaudeAuthClient`) to use a subscription; OpenRouter model picker; BuildConfig fallback |
| OpenF1 | Ktor (`HttpClient`) | Base URL `https://api.openf1.org/v1/`; browser User-Agent set in `AppModule` |
| YouTube | RSS feed (OkHttp) + Data API v3 OAuth | Manual channels via RSS; Connect Google imports `subscriptions.list` into Watching |
| Twitch | Helix (OkHttp) + Device Code (Custom Tabs) | `twitch.tv/activate` (no runtime redirect); imports follows; live board (60s cache) |
| Weather | OkHttp (`WeatherRepository`) | met.no Locationforecast 2.0 (Yr symbol codes); clothing advice is local (`ClothingAdvice`) |
| Health Connect | SDK | Read-only; lazy client; gracefully returns null if SDK unavailable |
| GitHub (home card) | OkHttp (`GitHubRepository` + `GitHubAuthClient`) | Device Code OAuth (Custom Tabs → github.com/login/device); REST `/user`, search issues/PRs `involves:@me`, `/user/repos`, `/users/{login}/events`; GraphQL `contributionsCollection`; scopes `repo read:user` |
| F1 circuits | OkHttp (`F1CircuitRepository`) | raw.githubusercontent.com/bacinger/f1-circuits: `f1-locations.json` (30-day cache) + one GeoJSON per circuit (kept for good) |
| t3lluz dashboard | OkHttp (`UpcomingRepository`, `DashboardLinkRepository`, `HermesClient`) | Tailnet-only static JSON (`_stats.json`, `_history.json`, `_f1.json`, tiles in `index.html`) and the bridge under `/_api/ai/*` for Hermes |
| GitHub Releases | OkHttp (`AppUpdateRepository`) | In-app APK updates + changelog |

### Compose Strong Skipping
Strong skipping is the Compose compiler default on Kotlin 2.x, so there is no flag in `app/build.gradle.kts`. Composables with unstable parameters will skip recomposition automatically — avoid fighting this with `@Stable`/`@Immutable` unless you observe real correctness issues.

**Per-frame values stay out of composition.** Anything that changes every frame (a fade, a pulse, a chevron's turn, a press scale) is kept as a `State` and read inside `graphicsLayer { }`, a `Canvas`/`drawBehind` block or a layout lambda, never as a plain value in the composable body: `MacroCard`'s entrance fade, `LivePulseDot`, `TypingDots` and the expand chevrons do this. `MacroCard` also stops reading `LocalTickersPaused` once its entrance has run, so scroll starts and stops don't recompose every card. The draggable Home/Health lists give each card its own `contentType` (its id), so a slot is only reused for the same card.

## Important Files to Read First
- `di/AppModule.kt` — understand what is injected and how
- `data/local/Entities.kt` — the two Room entities (only calories + protein tracked)
- `ui/navigation/Screen.kt` + `DailyDashNavHost.kt` — full route map
- `ui/theme/Animation.kt` (`MacroMotion`) — all animation specs
- `ui/viewmodel/DashboardViewModel.kt` — per-metric Health Connect states (today/yesterday) consumed by `HealthScreen`
- `ui/components/BodyStats.kt` — `HealthMetricUiState` data class + `calculatePercentageChange()`
- `widget/WidgetUpdater.kt` + `WidgetRefreshWorker.kt` — widget update strategy
- `widget/WidgetComponents.kt` + `WeatherWidgetPreview.kt` — Glance chrome and how previews are rendered
- `ui/screens/onboarding/` — multi-step onboarding flow (SplashScreen overlay + 3 nav-routed screens)

