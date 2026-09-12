<div align="center">
  <img src="docs/assets/icon.png" width="88" alt="DailyDash icon" />
  <h1>DailyDash</h1>
  <p><b>Your whole day on one screen.</b><br/>Nutrition, health, weather, calendar, F1, GitHub, YouTube, Twitch, and your servers in one Android app.</p>
  <p>
    <a href="https://github.com/T3lluz/DailyDash/releases/latest"><img alt="Download the latest APK" src="https://img.shields.io/github/v/release/T3lluz/DailyDash?label=Download%20APK&logo=android&logoColor=white&color=4F7CFF&style=for-the-badge" /></a>
  </p>
  <p>
    <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" />
    <img alt="Data stays on device" src="https://img.shields.io/badge/data-on--device-22C55E" />
  </p>
</div>

## Get started

1. Download the APK from [**Releases**](https://github.com/T3lluz/DailyDash/releases/latest) and install it. Allow installs from unknown sources if Android asks.
2. Open DailyDash and turn on the services you want in **Settings → Connections**.
3. Press and hold a card on **Home** or **Health** to reorder or hide it.

Updates install from inside the app. When a new release is out, DailyDash downloads it, restarts, and shows **What's new**.

## What's inside

| | Feature | What you get | Setup |
| :---: | --- | --- | --- |
| <img src="docs/integrations/nutrition.svg" width="20" alt="" /> | **Nutrition** | Calories and protein against daily goals, Quick Add, 7/14/30-day trends | None |
| <img src="docs/integrations/ai.svg" width="20" alt="" /> | **AI** | Log a meal by describing it or scanning its label, and chat with two built-in bots: **Clanker** for nutrition and **Sysop** for servers | Claude subscription or your own API key |
| <img src="docs/integrations/health-connect.svg" width="20" alt="" /> | **Health** | Activity rings, today vs. yesterday, workouts from the last month | [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) |
| <img src="docs/integrations/weather.svg" width="20" alt="" /> | **Weather** | Local forecast, sunrise and sunset, what to wear | Location permission |
| <img src="docs/integrations/calendar.svg" width="20" alt="" /> | **Calendar** | Today's upcoming events | Calendar permission |
| <img src="docs/integrations/f1.svg" width="20" alt="" /> | **Formula 1** | Next race countdown, standings, latest results, season schedule | None |
| <img src="docs/integrations/github.svg" width="20" alt="" /> | **GitHub** | Your issues, PRs, activity, and repos | Connect GitHub |
| <img src="docs/integrations/youtube.svg" width="20" alt="" /> | **YouTube** | Newest videos from channels you follow | None, or Connect Google to import subscriptions |
| <img src="docs/integrations/twitch.svg" width="20" alt="" /> | **Twitch** | Who's live right now from channels you follow | Connect Twitch |
| <img src="docs/integrations/servers.svg" width="20" alt="" /> | **Servers** | Live CPU, memory, disk, network, temperatures, systemd services, Docker containers, and pending updates, plus alerts and an ongoing notification | SSH login, with nothing to install on the server |
| <img src="docs/integrations/widgets.svg" width="20" alt="" /> | **Home-screen widgets** | Dashboard, Nutrition, Health, Weather, Calendar, F1 Next Race, F1 Standings, F1 Schedule | Long-press your home screen |

### <img src="docs/integrations/health-connect.svg" width="20" alt="" /> Health metrics

Read-only through Health Connect, each with its own toggle: **steps, heart rate, resting HR, SpO₂, respiratory rate, distance, floors, elevation, sleep, active and total calories, and workouts**. Workouts can come from any app that syncs to Health Connect, such as Garmin Connect, Samsung Health, Google Fit, or Strava.

### <img src="docs/integrations/ai.svg" width="20" alt="" /> AI providers

Choose a provider in **Settings → AI**. Meal estimates show a confidence level, so check them against a label or enter values yourself when accuracy matters.

| | Provider | How to connect |
| :---: | --- | --- |
| <img src="docs/integrations/claude.svg" width="20" alt="" /> | **Claude** | **Connect** with a Claude Pro, Max, Team, or Enterprise plan, or use an API key from [console.anthropic.com](https://console.anthropic.com/) |
| <img src="docs/integrations/gemini.svg" width="20" alt="" /> | **Gemini** | [aistudio.google.com](https://aistudio.google.com/) (free tier) |
| <img src="docs/integrations/openai.svg" width="20" alt="" /> | **OpenAI** | [platform.openai.com](https://platform.openai.com/api-keys) |
| <img src="docs/integrations/openrouter.svg" width="20" alt="" /> | **OpenRouter** | [openrouter.ai/keys](https://openrouter.ai/keys) (includes a picker for low-cost models with prices) |

### <img src="docs/integrations/privacy.svg" width="20" alt="" /> Privacy

- Food logs, chats, goals, and settings are stored only on your phone.
- Health Connect data is read on the phone and never uploaded.
- AI requests go only to the provider you pick. They contain your message, plus a server's readings when you tap the ✨ on a server card.
- Server passwords and SSH keys are encrypted with the Android Keystore. Monitoring only reads stats and never changes anything on your servers.
- There's no DailyDash account, analytics, or tracking.

## Data sources

[MET Norway / Yr](https://api.met.no/) (weather) · [Jolpica](https://github.com/jolpica/jolpica-f1) and [OpenF1](https://openf1.org/) (F1) · YouTube RSS and Data API · Twitch Helix · GitHub REST · SSH to your own servers · Android Health Connect and Calendar

---

## Build from source

**Requirements:** Android Studio or JDK 17, Android SDK 36, and a device or emulator running Android 8.0+ (API 26).

```bash
git clone https://github.com/T3lluz/DailyDash.git
cd DailyDash
cp local.properties.example local.properties   # set sdk.dir
./gradlew installDebug                          # build and install on a connected device
```

The app builds and runs without any keys. Add keys to `local.properties` only for the features you want:

| Key | Enables | Where to get it |
| --- | --- | --- |
| `GITHUB_CLIENT_ID` | Connect GitHub (Device Flow, no secret needed) | [GitHub OAuth App](https://github.com/settings/developers), with Device Flow turned on |
| `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` | Connect Twitch | [Twitch console](https://dev.twitch.tv/console) (Confidential app) |
| `GEMINI_API_KEY`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `ANTHROPIC_API_KEY` | Default AI keys (keys and Claude sign-ins in Settings take priority) | Links above |

Connect Google for YouTube doesn't use a key. It needs a Google Cloud **Android OAuth client** for `com.macrotracker` with the `app/tester.jks` SHA-1, and **YouTube Data API v3** turned on. The SHA-1 and step-by-step setup are in [`local.properties.example`](local.properties.example).

> [!WARNING]
> Never commit `local.properties` or real API keys. `app/tester.jks` is a shared test signing key, committed on purpose so sideloaded builds can update each other.

## Releases

Every push to `master` runs [`build-apk.yml`](.github/workflows/build-apk.yml). The workflow bumps the version, builds a signed APK, and publishes a GitHub Release. **Commit subjects become the in-app What's new notes**, so write them for users.

To enable Twitch and GitHub in published builds, add these Actions secrets: `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`, and `GH_OAUTH_CLIENT_ID`. The last one isn't named `GITHUB_CLIENT_ID` because GitHub reserves the `GITHUB_*` prefix.

## Project layout

Kotlin · Jetpack Compose · Hilt · Room · Ktor and OkHttp · JSch · Glance · WorkManager

```
app/src/main/kotlin/com/macrotracker/
├── data/     Room, settings, AI and chat, weather, F1, GitHub, YouTube, Twitch, Health Connect, servers, updates
├── di/       Hilt modules
├── ui/       screens, components, navigation, theme
└── widget/   Glance home-screen widgets
```

The app is named **DailyDash**, but its package name is still `com.macrotracker`. Architecture notes are in [`AGENTS.md`](AGENTS.md).
