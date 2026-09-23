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
    server/                ← server monitor: SSH probes (SshClient/ServerProbe), ServerMonitorService +
                              ServerNotifier, encrypted ServerStore, ServerAdvisories
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
    f1/                    ← F1Repository via Ktor + OpenF1 API (https://api.openf1.org/v1/);
                              15-min in-memory + SharedPrefs disk cache
    youtube/               ← YouTubeRepository via RSS feeds + optional Google OAuth subscription
                              import (AuthorizationClient / youtube.readonly); tracked channels in SharedPrefs
    twitch/                ← TwitchRepository via Helix + Device Code OAuth (Custom Tabs →
                              twitch.tv/activate, scope `user:read:follows`);
                              imports followed channels; live streams with 60s cache + auto-refresh
    github/                ← GitHubRepository via REST (OkHttp); authenticated user dashboard
                              (issues/PRs/activity/repos across every repo the account can see);
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
                             + ai/ (ChatKit.kt shared chat bubbles/header/composer + IME helpers, SysopChatPane)
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
                              WidgetEditor, WidgetExpandBar, PillButton, …). ScreenHeader.kt: tab
                              `ScreenHeader`, pushed-screen `SubScreenHeader`, `TabContentBottomPadding`,
                              `Modifier.subScreenBottomPadding()`. HomeWidgetShell.kt: card chrome shared by
                              every Home/Health card — `CardHeader`, `HubCardHeader` + `HubHeaderAction`,
                              `HubErrorState` (message + Retry), `WidgetPromptCard`, `ChannelSheetHeader`,
                              expand/scroll-box helpers. DeviceCodePanel.kt: GitHub/Twitch device-code
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
  widget/                  ← The one Glance home-screen widget, weather (fixed 5×3):
                              WeatherWidget + WeatherWidgetReceiver;
                              WidgetComponents.kt (`WidgetHeader`, `WidgetStateMessage`, `WScale`/`WidgetClr`
                              tokens, `WEATHER_WIDGET_PREVIEW_SIZE`);
                              WeatherWidgetDataProvider (reads the cached forecast directly — no Hilt);
                              WeatherWidgetData.kt (WeatherWidgetData snapshot + HourlyForecast + WidgetSourceState);
                              WeatherWidgetPreview (real render for the widget picker + in-app Widgets screen);
                              RefreshWidgetAction (Glance ActionCallback); WidgetUpdater + WidgetRefreshWorker;
                              WidgetStateProvider (is a widget placed?)
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

Callers must not add haptics to components that already fire their own: `MacroButton`, `HubHeaderAction`, `HubErrorState`, `WidgetExpandBar`, `DeviceCodePanel`.

### Home Screen Widgets (draggable)
Widget order and visibility are persisted as a single colon-and-comma encoded string in SharedPrefs:
```
"WEATHER:true,CALENDAR:true,UPCOMING:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,GITHUB:true,YOUTUBE:true,TWITCH:true"
```
`DraggableWidgetColumn` + `WidgetEditor` read/write this via `SettingsRepository`. **`GITHUB`** is the home GitHub hub (`GitHubCard`): account-wide issues, PRs, activity, and repos for the connected GitHub user (not a single project). Connect with Device Code OAuth on the Account tab (`repo` + `read:user`).

**`UPCOMING`** is Coming up (`UpcomingCard`): a Material 3 `HorizontalCenteredHeroCarousel` with `singleAdvanceFlingBehavior`, one card per entry in time order. Items are masked, not resized, so anything that grows or fades with a card reads `carouselItemDrawInfo` inside `graphicsLayer`/draw blocks (`openness()`, `followMask()`) and never in composition. Programmatic moves use `MacroMotion.carouselTravel`.

The **Health screen** uses the same draggable pattern with a separate key (`healthWidgetOrder`):
```
"DAILY_HEALTH:true,ACTIVITIES:true,BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true"
```
`DAILY_HEALTH` is the hero Daily Health card (Apple-style activity rings + dynamic today metrics). **`ACTIVITIES`** lists the **last month** of workouts synced through Health Connect (Garmin Connect, Google Fit, Samsung Health, Strava, and others): type, source, duration, distance, pace, heart rate, elevation, and a GPS route map when the session includes one. A featured hero card sits above three compact rows, with **Show N more** revealing the rest in a scroll box. GPS is read up front only for the newest `EAGER_ROUTE_COUNT` sessions; the rest resolve through `HealthViewModel.onActivityExpanded()` when a row is opened (`routeResolved` gates the "Loading map…" placeholder). `WEEK_AT_A_GLANCE` is the Macro Trends widget (7/14/30-day nutrition chart + per-day food logs), moved from the former History tab.

### App Widget (Glance)
The only home-screen widget is **`WeatherWidget`** (fixed 5×3, `SizeMode.Single`). It is refreshed via `WidgetUpdater.updateAllWidgets(context)` (call from the app when weather changes) or `WidgetRefreshWorker` (periodic WorkManager task, 15-min interval, no network constraint). `WeatherWidgetDataProvider` reads the forecast the app caches in the `daily_dash_weather_cache` SharedPrefs without Hilt (use `EntryPointAccessors` / `WidgetEntryPoint` when an injected dependency is needed).

**Previews are the widget itself.** `WeatherWidgetPreview.render()` composes `WeatherRoot(data, preview = true)` into `RemoteViews` through `GlanceRemoteViews` — using the cached forecast, or sample data when there is none. It feeds the in-app Widgets screen and, on Android 15+, `AppWidgetManager.setWidgetPreview` for the launcher picker (`publish()`, self-throttled to 30 min; the system rate-limits it). Preview hosts can't show a collection, so `preview = true` swaps the hourly `LazyColumn` for a plain `Column`. Older launchers and the pin dialog use the static `drawable-nodpi/widget_preview_weather.png` — a capture of the real widget; re-capture it whenever the widget layout changes, never hand-draw it.

Glance text does ellipsize, but a wrap-content `Text` in a `Row` still pushes later siblings off-edge — weight it or clip it.

### In-app updates (GitHub Releases)
Sideload/tester path — not Play Core. CI publishes `DailyDash-{versionName}-vc{versionCode}.apk` on master merges. `AppUpdateRepository` polls GitHub while foregrounded; `AppUpdateDialog` downloads + `PackageInstaller` self-updates; `UpdateInstallActivity` relaunches `MainActivity` with `EXTRA_RELAUNCHED_AFTER_UPDATE` / `EXTRA_SHOW_WHATS_NEW`. `PackageReplacedReceiver` posts a tap-to-open notification if relaunch is blocked. Post-update, `WhatsNewDialog` shows once (notes cached at download time, enriched from `/releases`). Soft-snooze is 12h (`Later`); Settings badge deep-links to About + opens the update dialog. Key files: `data/update/*`, `ui/components/AppUpdateDialog.kt`, `WhatsNewDialog.kt`, `AppUpdateViewModel`, `.github/scripts/package-release.sh`.

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
| GitHub (home card) | OkHttp (`GitHubRepository` + `GitHubAuthClient`) | Device Code OAuth (Custom Tabs → github.com/login/device); REST `/user`, search issues/PRs `involves:@me`, `/user/repos`, `/users/{login}/events`; scopes `repo read:user` |
| GitHub Releases | OkHttp (`AppUpdateRepository`) | In-app APK updates + changelog |

### Compose Strong Skipping
Strong skipping is the Compose compiler default on Kotlin 2.x, so there is no flag in `app/build.gradle.kts`. Composables with unstable parameters will skip recomposition automatically — avoid fighting this with `@Stable`/`@Immutable` unless you observe real correctness issues.

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

