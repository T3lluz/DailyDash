<div align="center">
  <img src="docs/assets/icon.png" width="96" alt="DailyDash" />
  <h1>DailyDash</h1>
  <p><b>Your whole day on one Android screen.</b><br/>Food, health, weather, calendar, F1, GitHub, YouTube, Twitch and your own servers, with an AI that knows them.</p>
  <p>
    <a href="https://github.com/T3lluz/DailyDash/releases/latest"><img alt="Download APK" src="https://img.shields.io/github/v/release/T3lluz/DailyDash?label=Download%20APK&logo=android&logoColor=white&color=4F7CFF&style=for-the-badge" /></a>
  </p>
  <p>
    <a href="https://github.com/T3lluz/DailyDash/actions/workflows/build-apk.yml"><img alt="Build & Release APK" src="https://github.com/T3lluz/DailyDash/actions/workflows/build-apk.yml/badge.svg?branch=master" /></a>
    <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
    <img alt="Kotlin 2.2" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" />
    <img alt="No account, no tracking" src="https://img.shields.io/badge/account-none-22C55E" />
  </p>
  <p>
    <img src="docs/integrations/health-connect.svg" width="22" alt="Health Connect" title="Health Connect" />&nbsp;
    <img src="docs/integrations/claude.svg" width="22" alt="Claude" title="Claude" />&nbsp;
    <img src="docs/integrations/gemini.svg" width="22" alt="Gemini" title="Gemini" />&nbsp;
    <img src="docs/integrations/openai.svg" width="22" alt="OpenAI" title="OpenAI" />&nbsp;
    <img src="docs/integrations/openrouter.svg" width="22" alt="OpenRouter" title="OpenRouter" />&nbsp;
    <img src="docs/integrations/f1.svg" width="22" alt="Formula 1" title="Formula 1" />&nbsp;
    <img src="docs/integrations/github.svg" width="22" alt="GitHub" title="GitHub" />&nbsp;
    <img src="docs/integrations/youtube.svg" width="22" alt="YouTube" title="YouTube" />&nbsp;
    <img src="docs/integrations/twitch.svg" width="22" alt="Twitch" title="Twitch" />&nbsp;
    <img src="docs/integrations/google.svg" width="22" alt="Google" title="Google" />&nbsp;
    <img src="docs/integrations/tailscale.svg" width="22" alt="Tailscale" title="Tailscale" />&nbsp;
    <img src="docs/integrations/hermes.png" width="22" alt="Hermes" title="Hermes" />
  </p>
</div>

**Install:** grab the APK from [Releases](https://github.com/T3lluz/DailyDash/releases/latest) · turn services on in **Settings → Connections** · press and hold a Home or Health card to reorder it. The app updates itself from Releases and shows **What's new** after each update.

## Features

| | | What you get | Needs |
| :---: | --- | --- | --- |
| <img src="docs/integrations/nutrition.svg" width="20" alt="" /> | **Nutrition** | Calories and protein against goals, Quick Add, 7/14/30-day trends | — (AI for meal text and label photos) |
| <img src="docs/integrations/health-connect.svg" width="20" alt="" /> | **Health** | Activity rings, today vs. yesterday, sleep, a month of workouts with GPS routes | [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) |
| <img src="docs/integrations/weather.svg" width="20" alt="" /> | **Weather** | Now, hourly curve, daily ranges, UV, wind, sunrise/sunset, what to wear | Location |
| <img src="docs/integrations/calendar.svg" width="20" alt="" /> | **Calendar** | Day strip and agenda up to 31 days ahead | Calendar access |
| <img src="docs/integrations/calendar.svg" width="20" alt="" /> | **Coming up** | Episodes, films and F1 sessions on one swipeable timeline | Dashboard server <sup>†</sup> |
| <img src="docs/integrations/f1.svg" width="20" alt="" /> | **Formula 1** | Countdown over an animated circuit map, driver and constructor standings, results, schedule | — |
| <img src="docs/integrations/github.svg" width="20" alt="" /> | **GitHub** | Issues, PRs, activity and repos across your account; contribution year with the snake | Connect GitHub |
| <img src="docs/integrations/youtube.svg" width="20" alt="" /> | **YouTube** | Newest videos from followed channels in a carousel | — (Connect Google imports subscriptions) |
| <img src="docs/integrations/twitch.svg" width="20" alt="" /> | **Twitch** | Who is live now from the channels you follow | Connect Twitch |
| <img src="docs/integrations/servers.svg" width="20" alt="" /> | **Servers** | CPU, memory, disks, network, sensors, containers, ports, systemd, journal, updates, alerts; live notification with dials and an **Ask** button | SSH login, nothing installed server-side |
| <img src="docs/integrations/ai.svg" width="20" alt="" /> | **AI** | Log meals by describing them; **Tech support** chat, as **Hermes** on your server or **Sysop** on the phone | An AI provider; Hermes needs the dashboard server <sup>†</sup> |
| <img src="docs/integrations/phone.svg" width="20" alt="" /> | **Phone hub** | The phone on your desk dashboard: battery, media, notifications (dismiss, reply); ring, torch, open URL, clipboard, volume from the desk; **Send to desk** in the share sheet | Dashboard server <sup>†</sup> |
| <img src="docs/integrations/widgets.svg" width="20" alt="" /> | **Widget** | Weather on the home screen (5×3) | Long-press the home screen |

<sup>†</sup> The t3lluz dashboard, reached over <img src="docs/integrations/tailscale.svg" width="12" alt="" /> Tailscale and set in **Settings → Connections**.

**Health Connect metrics** (read-only, each with its own toggle): steps, heart rate, resting HR, SpO₂, respiratory rate, distance, floors, elevation, sleep, active and total calories, workouts from Garmin Connect, Samsung Health, Google Fit, Strava and others.

**AI providers** (**Settings → AI**): <img src="docs/integrations/claude.svg" width="14" alt="" /> **Claude** by signing in with Pro, Max, Team or Enterprise, or an [API key](https://console.anthropic.com/) · <img src="docs/integrations/gemini.svg" width="14" alt="" /> **Gemini** ([free key](https://aistudio.google.com/)) · <img src="docs/integrations/openai.svg" width="14" alt="" /> **OpenAI** ([key](https://platform.openai.com/api-keys)) · <img src="docs/integrations/openrouter.svg" width="14" alt="" /> **OpenRouter** ([key](https://openrouter.ai/keys), with a priced picker of cheap models).

## <img src="docs/integrations/privacy.svg" width="20" alt="" /> Privacy

- Food logs, chats, goals and settings stay on the phone. Health Connect data is never uploaded.
- AI requests go only to the provider you pick. Hermes threads live on your own server.
- SSH passwords and keys are encrypted with the Android Keystore; monitoring only reads.
- No DailyDash account, analytics or tracking.

**Data:** [MET Norway](https://api.met.no/) · [Jolpica](https://github.com/jolpica/jolpica-f1) + [OpenF1](https://openf1.org/) + [bacinger/f1-circuits](https://github.com/bacinger/f1-circuits) · YouTube RSS + Data API · Twitch Helix · GitHub REST + GraphQL · SSH · t3lluz dashboard bridge · Health Connect · Calendar Provider

## Build

JDK 17, Android SDK 36, a device on Android 8.0+ (API 26).

```bash
git clone https://github.com/T3lluz/DailyDash.git && cd DailyDash
cp local.properties.example local.properties   # set sdk.dir
./gradlew installDebug
```

It builds without keys. Add only what you need to `local.properties`:

| Key | Enables |
| --- | --- |
| `GITHUB_CLIENT_ID` | Connect GitHub: [OAuth App](https://github.com/settings/developers) with Device Flow on, no secret |
| `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` | Connect Twitch: [confidential app](https://dev.twitch.tv/console) |
| `GEMINI_API_KEY`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `ANTHROPIC_API_KEY` | Default AI keys; keys and Claude sign-in from Settings win |

Connect Google (YouTube) needs no key: an Android OAuth client for `com.macrotracker` with the `app/tester.jks` SHA-1 and YouTube Data API v3 on. Steps are in [`local.properties.example`](local.properties.example).

> [!WARNING]
> Never commit `local.properties` or API keys. `app/tester.jks` is a shared test key, committed so sideloaded builds can update each other.

## Releases

Each push to `master` runs [Build & Release APK](.github/workflows/build-apk.yml): it bumps the version, signs the APK and publishes `DailyDash-{version}-vc{code}.apk` to GitHub Releases. **Commit subjects since the last tag become the in-app What's new**, so write them for users. Published builds read the Actions secrets `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` and `GH_OAUTH_CLIENT_ID` (GitHub reserves the `GITHUB_*` prefix).

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · OkHttp + Ktor · JSch · Haze · Glance · WorkManager · CameraX

The app is **DailyDash**; the package is still `com.macrotracker`. Architecture notes live in [`AGENTS.md`](AGENTS.md).
