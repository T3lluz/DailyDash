package com.macrotracker.widget.tilesmith

/** The kind of value a source gives, which decides the blocks it fits. */
enum class ValueKind { NUMBER, TEXT, LIST, SERIES, TIME, IMAGE }

/** Something a source can't be read without; [TileNeeds] checks them and the studio asks for them. */
enum class TileNeed(val label: String) {
    CALENDAR("Calendar access"),
    LOCATION("Location, for the weather"),
    HEALTH("Health Connect"),
    USAGE("Usage access"),
    SERVER("A server in Settings → Servers"),
    GITHUB("GitHub, in Settings → Connections"),
    DASHBOARD("The dashboard server, in Settings → Connections"),
    AI("An AI provider, in Settings → AI"),
    COMMAND("Your OK to run the command"),
    SITE("Your OK to read the site"),
    GOOGLE("Google sign-in for Gmail or Tasks, in Settings → Permissions"),
    YOUTUBE("YouTube channels to follow, on Home's YouTube card"),
    TWITCH("Twitch, on Home's Twitch card"),
    NOTIFICATIONS("Notification access, in Settings → Permissions"),
    CONTACTS("Contacts access"),
    PHOTOS("Photos access"),
    BLUETOOTH("Bluetooth access"),
}

enum class SourceGroup(val label: String) {
    TIME("Time"),
    PHONE("Phone"),
    MEDIA("Now playing & notifications"),
    HEALTH("Health"),
    FOOD("Food"),
    WEATHER("Weather"),
    CALENDAR("Calendar"),
    GOOGLE("Gmail & Google Tasks"),
    PEOPLE("Contacts"),
    PHOTOS("Photos"),
    VIDEO("YouTube & Twitch"),
    UPCOMING("Coming up"),
    F1("Formula 1"),
    SERVER("Servers"),
    GITHUB("GitHub"),
    DASHBOARD("Dashboard"),
    WEB("Web & AI"),
}

/**
 * One thing a widget can show. [floorMinutes] is how often it is worth reading (a refresh any
 * faster reads the same answer); [sample] fills previews until the real value is in.
 */
data class TileSource(
    val key: String,
    val label: String,
    val group: SourceGroup,
    val kind: ValueKind,
    val hint: String,
    val floorMinutes: Int,
    val needs: Set<TileNeed> = emptySet(),
    val icon: String? = null,
    val sample: TileValue,
)

/**
 * Every source a Tilesmith block can [TileBlock.bind] to. The AI is handed this list (with what
 * is already allowed) and may only use these keys; [TileResolver] reads them.
 */
object TileSources {
    private fun n(text: String?, number: Double?, goal: Double? = null, unit: String? = null, sub: String? = null, icon: String? = null) =
        TileValue(text = text, number = number, goal = goal, unit = unit, sub = sub, icon = icon)

    private fun list(vararg rows: TileItem) = TileValue(items = rows.toList())

    private fun series(vararg v: Float, start: String? = null, end: String? = null, text: String? = null, unit: String? = null) =
        TileValue(series = v.toList(), seriesStart = start, seriesEnd = end, text = text, unit = unit)

    private fun inMinutes(m: Long) = System.currentTimeMillis() + m * 60_000L

    val all: List<TileSource> = listOf(
        // ── Time ───────────────────────────────────────────────────────────
        TileSource("time.clock", "Time", SourceGroup.TIME, ValueKind.TIME, "The time now; use a clock block, it ticks on its own", 60, icon = "clock", sample = TileValue(text = "14:05")),
        TileSource("time.date", "Date", SourceGroup.TIME, ValueKind.TEXT, "Today's date; use a date block", 60, icon = "calendar", sample = TileValue(text = "Thu 15 Oct")),
        TileSource("time.countdown", "Countdown to a date", SourceGroup.TIME, ValueKind.TIME, "Counts down to the ISO date-time in the block's text, e.g. 2026-12-24T18:00", 60, icon = "timer", sample = TileValue(text = "Christmas", atMs = inMinutes(60 * 24 * 70))),

        // ── Phone ──────────────────────────────────────────────────────────
        TileSource("phone.battery", "Battery", SourceGroup.PHONE, ValueKind.NUMBER, "Battery percent (goal 100), sub says charging or time left", 1, icon = "battery", sample = n("78%", 78.0, 100.0, "%", "Charging", "battery_charging")),
        TileSource("phone.storage", "Storage used", SourceGroup.PHONE, ValueKind.NUMBER, "Percent of storage used (goal 100), sub is GB free", 30, icon = "disk", sample = n("61%", 61.0, 100.0, "%", "48 GB free")),
        TileSource("phone.alarm", "Next alarm", SourceGroup.PHONE, ValueKind.TIME, "The next alarm's time", 5, icon = "bell", sample = TileValue(text = "07:00", sub = "Tomorrow", atMs = inMinutes(16 * 60))),
        TileSource("phone.screen_time", "Screen time today", SourceGroup.PHONE, ValueKind.NUMBER, "Minutes of screen time today, text like 2h 14m", 15, setOf(TileNeed.USAGE), "phone", n("2h 14m", 134.0, 180.0, "min", "Today")),
        TileSource("phone.network", "Network", SourceGroup.PHONE, ValueKind.TEXT, "Wi-Fi, mobile data or offline", 5, icon = "wifi", sample = TileValue(text = "Wi-Fi", icon = "wifi")),
        TileSource("phone.bluetooth", "Bluetooth devices", SourceGroup.PHONE, ValueKind.LIST, "Paired Bluetooth devices, connected ones first with their battery at the end; number is how many are connected", 5, setOf(TileNeed.BLUETOOTH), "bluetooth", TileValue(number = 2.0, items = listOf(TileItem("Pixel Buds Pro", "Connected", "80%", "#3FA266"), TileItem("Pixel Watch 3", "Connected", "64%", "#3FA266"), TileItem("Car", null, "—")))),
        TileSource("phone.headphones", "Headphones battery", SourceGroup.PHONE, ValueKind.NUMBER, "Battery of the connected Bluetooth headphones or watch (goal 100), sub its name; query picks a device by name", 5, setOf(TileNeed.BLUETOOTH), "headphones", n("80%", 80.0, 100.0, "%", "Pixel Buds Pro", "headphones")),

        // ── Now playing and notifications (notification access) ────────────
        TileSource("media.now", "Now playing", SourceGroup.MEDIA, ValueKind.TEXT, "What is playing in any app: text the track, sub the artist, image the album art, active while it plays. A player block adds play / pause / skip", 1, setOf(TileNeed.NOTIFICATIONS), "music", TileValue(text = "Espresso", sub = "Sabrina Carpenter", icon = "music", active = true)),
        TileSource("notify.count", "Notifications", SourceGroup.MEDIA, ValueKind.NUMBER, "Notifications waiting on the phone; query limits it to apps by name (WhatsApp, Gmail)", 2, setOf(TileNeed.NOTIFICATIONS), "bell", n(null, 5.0, null, null, "waiting", "bell")),
        TileSource("notify.list", "Latest notifications", SourceGroup.MEDIA, ValueKind.LIST, "Newest notifications: who or what, the text, the app and time; query limits it to apps by name", 2, setOf(TileNeed.NOTIFICATIONS), "bell", list(TileItem("Ida", "Dinner at 7?", "WhatsApp · 2m", "#25D366"), TileItem("Wolt", "Your order is on its way", "Wolt · 9m"), TileItem("GitHub", "CI passed on #92", "GitHub · 20m"))),

        // ── Health (Health Connect) ────────────────────────────────────────
        TileSource("health.steps", "Steps", SourceGroup.HEALTH, ValueKind.NUMBER, "Steps today (goal 10,000)", 5, setOf(TileNeed.HEALTH), "steps", n(null, 7412.0, 10000.0, "steps", "2,588 to go", "steps")),
        TileSource("health.steps_week", "Steps this week", SourceGroup.HEALTH, ValueKind.SERIES, "Steps per day, the last 7 days, today last", 30, setOf(TileNeed.HEALTH), "steps", series(8200f, 6100f, 10400f, 9300f, 4800f, 11200f, 7412f, start = "Fri", end = "Today", text = "7,412", unit = "steps")),
        TileSource("health.active_cal", "Active calories", SourceGroup.HEALTH, ValueKind.NUMBER, "Active kcal burned today (goal 500)", 5, setOf(TileNeed.HEALTH), "flame", n(null, 342.0, 500.0, "kcal", null, "flame")),
        TileSource("health.distance", "Distance", SourceGroup.HEALTH, ValueKind.NUMBER, "Kilometres walked or run today", 5, setOf(TileNeed.HEALTH), "walk", n(null, 5.4, 8.0, "km", null, "walk")),
        TileSource("health.floors", "Floors", SourceGroup.HEALTH, ValueKind.NUMBER, "Floors climbed today", 5, setOf(TileNeed.HEALTH), "mountain", n(null, 9.0, 10.0, "floors")),
        TileSource("health.heart_rate", "Heart rate", SourceGroup.HEALTH, ValueKind.NUMBER, "Average heart rate today", 5, setOf(TileNeed.HEALTH), "heart_pulse", n(null, 72.0, null, "bpm", "Average today", "heart_pulse")),
        TileSource("health.resting_hr", "Resting heart rate", SourceGroup.HEALTH, ValueKind.NUMBER, "Resting heart rate", 30, setOf(TileNeed.HEALTH), "heart", n(null, 54.0, null, "bpm", "Resting", "heart")),
        TileSource("health.sleep", "Sleep", SourceGroup.HEALTH, ValueKind.NUMBER, "Last night's sleep, number in minutes (goal 480), text like 7h 12m", 30, setOf(TileNeed.HEALTH), "sleep", n("7h 12m", 432.0, 480.0, "min", "Last night", "sleep")),
        TileSource("health.workouts", "Workouts", SourceGroup.HEALTH, ValueKind.LIST, "Latest workouts: type, when, and duration or distance at the end", 30, setOf(TileNeed.HEALTH), "run", list(TileItem("Run", "Today 07:10", "5.2 km"), TileItem("Strength", "Yesterday", "48 min"), TileItem("Ride", "Sun", "21 km"))),
        TileSource("health.weight", "Weight", SourceGroup.HEALTH, ValueKind.NUMBER, "Latest weight in kg, sub the change over 30 days; series is the trend", 60, setOf(TileNeed.HEALTH), "scale", TileValue(number = 78.4, unit = "kg", sub = "−0.8 in 30 days", icon = "scale", series = listOf(79.2f, 79.0f, 78.9f, 78.7f, 78.8f, 78.5f, 78.4f))),
        TileSource("health.hrv", "Heart rate variability", SourceGroup.HEALTH, ValueKind.NUMBER, "Latest HRV (RMSSD, ms)", 60, setOf(TileNeed.HEALTH), "heart_pulse", n(null, 48.0, null, "ms", "HRV", "heart_pulse")),
        TileSource("health.spo2", "Blood oxygen", SourceGroup.HEALTH, ValueKind.NUMBER, "SpO2 percent", 30, setOf(TileNeed.HEALTH), "droplet", n(null, 97.0, 100.0, "%")),

        // ── Food (DailyDash's own log) ─────────────────────────────────────
        TileSource("food.calories", "Calories eaten", SourceGroup.FOOD, ValueKind.NUMBER, "kcal logged today against the daily goal", 1, icon = "food", sample = n(null, 1460.0, 2400.0, "kcal", "940 left", "food")),
        TileSource("food.protein", "Protein", SourceGroup.FOOD, ValueKind.NUMBER, "Grams of protein today against the goal", 1, icon = "dumbbell", sample = n(null, 96.0, 150.0, "g", "54 g left", "dumbbell")),
        TileSource("food.week", "Calories this week", SourceGroup.FOOD, ValueKind.SERIES, "kcal per day, the last 7 days", 30, icon = "food", sample = series(2210f, 2480f, 1990f, 2300f, 2650f, 2100f, 1460f, start = "Fri", end = "Today", text = "1,460", unit = "kcal")),

        // ── Weather ────────────────────────────────────────────────────────
        TileSource("weather.now", "Weather now", SourceGroup.WEATHER, ValueKind.NUMBER, "Temperature now; text like 14°, sub is the sky, icon is the sky", 10, setOf(TileNeed.LOCATION), "thermometer", n("14°", 14.0, null, "°", "Partly cloudy", "wx:partlycloudy_day")),
        TileSource("weather.high_low", "High and low", SourceGroup.WEATHER, ValueKind.TEXT, "Today's high and low, text like 17° / 9°", 30, setOf(TileNeed.LOCATION), "thermometer", TileValue(text = "17° / 9°", sub = "High / low")),
        TileSource("weather.feels", "Feels like", SourceGroup.WEATHER, ValueKind.NUMBER, "Feels-like temperature", 10, setOf(TileNeed.LOCATION), "thermometer", n("12°", 12.0, null, "°", "Feels like")),
        TileSource("weather.rain", "Rain", SourceGroup.WEATHER, ValueKind.TEXT, "When rain comes in the next hours and the chance, text like Rain at 15:00", 10, setOf(TileNeed.LOCATION), "rain", TileValue(text = "Rain at 15:00", number = 70.0, goal = 100.0, unit = "%", sub = "70% chance", icon = "rain")),
        TileSource("weather.wind", "Wind", SourceGroup.WEATHER, ValueKind.NUMBER, "Wind speed now", 10, setOf(TileNeed.LOCATION), "wind", n("6 m/s", 6.0, null, "m/s", "Gusts 11", "wind")),
        TileSource("weather.uv", "UV index", SourceGroup.WEATHER, ValueKind.NUMBER, "UV index now (goal 11)", 30, setOf(TileNeed.LOCATION), "sun", n(null, 3.0, 11.0, null, "Moderate", "sun")),
        TileSource("weather.sun", "Sunrise and sunset", SourceGroup.WEATHER, ValueKind.TEXT, "Next sunrise or sunset, text like Sunset 18:42", 60, setOf(TileNeed.LOCATION), "sunrise", TileValue(text = "18:42", sub = "Sunset", icon = "sunrise")),
        TileSource("weather.hourly", "Next 12 hours", SourceGroup.WEATHER, ValueKind.SERIES, "Temperature each hour for the next 12 hours", 10, setOf(TileNeed.LOCATION), "chart", series(14f, 15f, 15f, 16f, 15f, 14f, 13f, 12f, 11f, 10f, 10f, 9f, start = "Now", end = "+12h", text = "14°", unit = "°")),
        TileSource("weather.days", "Next days", SourceGroup.WEATHER, ValueKind.LIST, "The next days: day, sky, high / low", 30, setOf(TileNeed.LOCATION), "calendar", list(TileItem("Fri", "Rain", "13° / 8°"), TileItem("Sat", "Sunny", "16° / 7°"), TileItem("Sun", "Cloudy", "14° / 9°"), TileItem("Mon", "Showers", "12° / 8°"))),
        TileSource("weather.wear", "What to wear", SourceGroup.WEATHER, ValueKind.TEXT, "A line on what to wear for today's weather", 30, setOf(TileNeed.LOCATION), "sun", TileValue(text = "Light jacket, take an umbrella")),

        // ── Calendar ───────────────────────────────────────────────────────
        TileSource("calendar.next", "Next event", SourceGroup.CALENDAR, ValueKind.TIME, "The next event: text is its title, sub when and where, atMs its start", 2, setOf(TileNeed.CALENDAR), "calendar", TileValue(text = "Design review", sub = "14:30 · Room 4", atMs = inMinutes(25))),
        TileSource("calendar.today", "Today's events", SourceGroup.CALENDAR, ValueKind.LIST, "The rest of today's events, time at the end", 2, setOf(TileNeed.CALENDAR), "calendar", list(TileItem("Design review", "Room 4", "14:30", "#6EA8FE"), TileItem("Gym", null, "17:00", "#3FA266"), TileItem("Dinner with Ida", "Lille Herbern", "19:30", "#F1B467"))),
        TileSource("calendar.upcoming", "Coming days", SourceGroup.CALENDAR, ValueKind.LIST, "The next events over the coming days, day and time at the end", 5, setOf(TileNeed.CALENDAR), "calendar", list(TileItem("Design review", null, "Today 14:30"), TileItem("Dentist", null, "Fri 09:00"), TileItem("Cabin trip", null, "Sat"))),
        TileSource("calendar.count", "Events today", SourceGroup.CALENDAR, ValueKind.NUMBER, "How many events are left today", 2, setOf(TileNeed.CALENDAR), "calendar", n(null, 3.0, null, null, "left today", "calendar")),

        // ── Gmail and Google Tasks (Google sign-in; mail falls back to the dashboard) ──
        TileSource("gmail.unread", "Unread mail", SourceGroup.GOOGLE, ValueKind.NUMBER, "Unread mail in the Gmail inbox; query narrows it with a Gmail search (from:ida, label:work)", 5, setOf(TileNeed.GOOGLE), "mail", n(null, 4.0, null, null, "unread", "mail")),
        TileSource("gmail.inbox", "Gmail inbox", SourceGroup.GOOGLE, ValueKind.LIST, "Newest Gmail threads: sender, subject, time, unread ones marked; query is a Gmail search (is:unread, from:, label:, newer_than:2d); a tap opens the mail. Best as a list with variant scroll", 5, setOf(TileNeed.GOOGLE), "inbox", TileValue(number = 4.0, sub = "unread", icon = "inbox", items = listOf(TileItem("Ida", "Saturday?", "09:12", "#EA4335"), TileItem("Google Play", "Your order receipt", "Yesterday"), TileItem("Wolt", "Order delivered", "Wed"), TileItem("Zaptec", "Thanks for applying", "Mon")))),
        TileSource("tasks.list", "Google Tasks", SourceGroup.GOOGLE, ValueKind.LIST, "Open Google Tasks, soonest due first, the due day at the end; query picks a list by name", 10, setOf(TileNeed.GOOGLE), "tasks", list(TileItem("Renew passport", null, "Today", "#E34671"), TileItem("Book dentist", null, "Fri"), TileItem("Buy coffee beans"))),
        TileSource("tasks.count", "Tasks left", SourceGroup.GOOGLE, ValueKind.NUMBER, "Open Google Tasks; sub says how many are due today or overdue; query picks a list", 10, setOf(TileNeed.GOOGLE), "tasks", n(null, 6.0, null, null, "2 due today", "tasks")),

        // ── Contacts ───────────────────────────────────────────────────────
        TileSource("contacts.birthdays", "Birthdays", SourceGroup.PEOPLE, ValueKind.LIST, "Coming birthdays from your contacts: name, how old they turn, the day at the end, their photo", 360, setOf(TileNeed.CONTACTS), "cake", list(TileItem("Ida", "Turns 29", "Sat"), TileItem("Mum", null, "12 Nov"), TileItem("Jonas", "Turns 31", "3 Dec"))),
        TileSource("contacts.favorites", "Favourite people", SourceGroup.PEOPLE, ValueKind.LIST, "Your starred contacts with their photos; a tap opens the contact to call or message", 360, setOf(TileNeed.CONTACTS), "users", list(TileItem("Ida"), TileItem("Mum"), TileItem("Jonas"))),

        // ── Photos ─────────────────────────────────────────────────────────
        TileSource("photos.random", "A photo", SourceGroup.PHOTOS, ValueKind.IMAGE, "A different photo from the phone each refresh (every sets how often, default 30 min); query names an album (Camera, Screenshots). Best in an image block", 30, setOf(TileNeed.PHOTOS), "image", TileValue(text = "Lofoten", sub = "12 Aug 2025", icon = "image")),
        TileSource("photos.recent", "Newest photos", SourceGroup.PHOTOS, ValueKind.LIST, "Your newest photos, a tap opens one; query names an album. Best as a carousel", 30, setOf(TileNeed.PHOTOS), "image", list(TileItem("Today", null, "14:02"), TileItem("Yesterday", null, "19:40"), TileItem("Sun", null, "11:15"))),

        // ── YouTube and Twitch ─────────────────────────────────────────────
        TileSource("youtube.latest", "Latest videos", SourceGroup.VIDEO, ValueKind.LIST, "Newest uploads from the YouTube channels followed in DailyDash, or from the channels named in query (comma separated names or handles): title, channel, how long ago, thumbnail; a tap opens the video. Best as a carousel", 15, setOf(TileNeed.YOUTUBE), "youtube", list(TileItem("I built a widget maker in a weekend", "Fireship", "2h"), TileItem("F1 US GP highlights", "FORMULA 1", "5h"), TileItem("Pixel 11 review", "MKBHD", "1d"))),
        TileSource("youtube.new", "New videos", SourceGroup.VIDEO, ValueKind.NUMBER, "How many videos the followed channels (or the ones in query) posted in the last 24 hours", 15, setOf(TileNeed.YOUTUBE), "youtube", n(null, 3.0, null, null, "new today", "youtube")),
        TileSource("twitch.live", "Live on Twitch", SourceGroup.VIDEO, ValueKind.LIST, "Followed Twitch channels live now: name, game, viewers, stream thumbnail; a tap opens the stream. Best as a carousel or list", 5, setOf(TileNeed.TWITCH), "tv", list(TileItem("Lirik", "Elden Ring", "12k", "#9146FF"), TileItem("ThePrimeagen", "Software", "8k", "#9146FF"))),
        TileSource("twitch.count", "Live channels", SourceGroup.VIDEO, ValueKind.NUMBER, "How many followed Twitch channels are live", 5, setOf(TileNeed.TWITCH), "tv", n(null, 2.0, null, null, "live now", "tv")),

        // ── Coming up (the dashboard's timeline) ───────────────────────────
        TileSource("upcoming.list", "Coming up", SourceGroup.UPCOMING, ValueKind.LIST, "The dashboard's Coming up: next episodes (Sonarr, Stremio), films (Radarr), F1 sessions and your events, with posters and when; best as a carousel or list", 30, setOf(TileNeed.DASHBOARD), "tv", list(TileItem("The Bear", "S04E01", "Fri"), TileItem("Dune: Part Three", "Movie", "18 Dec"), TileItem("Qualifying", "US GP", "Sat 23:00"))),
        TileSource("upcoming.next", "Next on Coming up", SourceGroup.UPCOMING, ValueKind.TIME, "The next thing on Coming up: text the title, sub the episode or session, atMs when; for a countdown", 30, setOf(TileNeed.DASHBOARD), "tv", TileValue(text = "The Bear", sub = "S04E01", atMs = inMinutes(60 * 30))),

        // ── Formula 1 ──────────────────────────────────────────────────────
        TileSource("f1.next", "Next F1 session", SourceGroup.F1, ValueKind.TIME, "The next session: text like Qualifying, sub the Grand Prix, atMs its start (live while it runs)", 15, icon = "flag", sample = TileValue(text = "Qualifying", sub = "United States GP", atMs = inMinutes(60 * 26), icon = "flag")),
        TileSource("f1.drivers", "Driver standings", SourceGroup.F1, ValueKind.LIST, "Drivers by points, team colour on each row", 60, icon = "trophy", sample = list(TileItem("1  Norris", "McLaren", "357", "#FF8000"), TileItem("2  Piastri", "McLaren", "341", "#FF8000"), TileItem("3  Verstappen", "Red Bull", "306", "#3671C6"), TileItem("4  Russell", "Mercedes", "252", "#27F4D2"))),
        TileSource("f1.teams", "Constructor standings", SourceGroup.F1, ValueKind.LIST, "Teams by points", 60, icon = "trophy", sample = list(TileItem("1  McLaren", null, "698", "#FF8000"), TileItem("2  Mercedes", null, "398", "#27F4D2"), TileItem("3  Red Bull", null, "366", "#3671C6"))),
        TileSource("f1.last", "Last race result", SourceGroup.F1, ValueKind.LIST, "The last race's podium and on", 60, icon = "flag", sample = list(TileItem("1  Verstappen", "Red Bull", "1:24:57", "#3671C6"), TileItem("2  Norris", "McLaren", "+1.4", "#FF8000"), TileItem("3  Leclerc", "Ferrari", "+3.8", "#E8002D"))),
        TileSource("f1.leader", "Championship leader", SourceGroup.F1, ValueKind.TEXT, "Who leads the drivers' championship and by how much", 60, icon = "trophy", sample = TileValue(text = "Norris", sub = "+16 pts", number = 357.0, unit = "pts")),

        // ── Servers (SSH, Settings → Servers) ──────────────────────────────
        TileSource("server.status", "Server health", SourceGroup.SERVER, ValueKind.TEXT, "One word on the server: Healthy, 2 warnings, Offline", 5, setOf(TileNeed.SERVER), "server", TileValue(text = "Healthy", sub = "t3lluz · up 12d", icon = "check")),
        TileSource("server.cpu", "CPU", SourceGroup.SERVER, ValueKind.NUMBER, "CPU percent (goal 100)", 5, setOf(TileNeed.SERVER), "cpu", n("23%", 23.0, 100.0, "%", "8 cores", "cpu")),
        TileSource("server.mem", "Memory", SourceGroup.SERVER, ValueKind.NUMBER, "Memory used percent (goal 100)", 5, setOf(TileNeed.SERVER), "cpu", n("58%", 58.0, 100.0, "%", "9.2 of 16 GB")),
        TileSource("server.disk", "Disk", SourceGroup.SERVER, ValueKind.NUMBER, "The fullest disk's used percent (goal 100)", 15, setOf(TileNeed.SERVER), "disk", n("71%", 71.0, 100.0, "%", "/ · 210 GB free", "disk")),
        TileSource("server.temp", "Temperature", SourceGroup.SERVER, ValueKind.NUMBER, "CPU temperature °C (goal 100)", 5, setOf(TileNeed.SERVER), "thermometer", n("48°", 48.0, 100.0, "°C", "CPU")),
        TileSource("server.uptime", "Uptime", SourceGroup.SERVER, ValueKind.TEXT, "How long it has been up, like 12d 4h", 15, setOf(TileNeed.SERVER), "clock", TileValue(text = "12d 4h", sub = "Uptime")),
        TileSource("server.containers", "Containers", SourceGroup.SERVER, ValueKind.NUMBER, "Docker containers running against total", 5, setOf(TileNeed.SERVER), "server", n("31/32", 31.0, 32.0, null, "running", "server")),
        TileSource("server.updates", "Updates", SourceGroup.SERVER, ValueKind.NUMBER, "Packages waiting to update", 60, setOf(TileNeed.SERVER), "download", n(null, 4.0, null, null, "1 security", "download")),
        TileSource("server.cpu_history", "CPU history", SourceGroup.SERVER, ValueKind.SERIES, "CPU percent over the last day", 5, setOf(TileNeed.SERVER), "chart", series(12f, 18f, 22f, 15f, 40f, 33f, 21f, 19f, 28f, 23f, 17f, 23f, start = "24 h", end = "Now", text = "23%", unit = "%")),
        TileSource("server.services", "Services", SourceGroup.SERVER, ValueKind.LIST, "The dashboard's services, down ones first", 5, setOf(TileNeed.SERVER), "server", list(TileItem("Jellyfin", null, "Up", "#3FA266"), TileItem("Sonarr", null, "Up", "#3FA266"), TileItem("qBittorrent", null, "Down", "#E34671"))),
        TileSource("server.cmd", "Command output", SourceGroup.SERVER, ValueKind.TEXT, "The output of a shell command on the server (set cmd, and server if not the first); you approve it first. A number in the output becomes the value", 5, setOf(TileNeed.SERVER, TileNeed.COMMAND), "terminal", TileValue(text = "42", sub = "Output")),

        // ── GitHub ─────────────────────────────────────────────────────────
        TileSource("github.reviews", "Reviews waiting", SourceGroup.GITHUB, ValueKind.NUMBER, "Pull requests waiting on your review", 10, setOf(TileNeed.GITHUB), "eye", n(null, 2.0, null, null, "to review", "eye")),
        TileSource("github.prs", "Open pull requests", SourceGroup.GITHUB, ValueKind.NUMBER, "Your open pull requests", 10, setOf(TileNeed.GITHUB), "git", n(null, 5.0, null, null, "open", "git")),
        TileSource("github.issues", "Issues", SourceGroup.GITHUB, ValueKind.NUMBER, "Open issues assigned to or involving you", 10, setOf(TileNeed.GITHUB), "inbox", n(null, 7.0, null, null, "open", "inbox")),
        TileSource("github.inbox", "Notifications", SourceGroup.GITHUB, ValueKind.NUMBER, "Unread GitHub notifications", 10, setOf(TileNeed.GITHUB), "bell", n(null, 3.0, null, null, "unread", "bell")),
        TileSource("github.pr_list", "Pull requests", SourceGroup.GITHUB, ValueKind.LIST, "Your pull requests: title, repo, state", 10, setOf(TileNeed.GITHUB), "git", list(TileItem("Tilesmith widgets", "DailyDash #214", "Review"), TileItem("Fix island fold", "DailyDash #211", "Open"))),
        TileSource("github.contrib", "Contributions", SourceGroup.GITHUB, ValueKind.SERIES, "Contributions per day, the last 14 days; text is the current streak", 30, setOf(TileNeed.GITHUB), "chart", series(3f, 5f, 0f, 2f, 8f, 6f, 1f, 0f, 4f, 7f, 9f, 3f, 5f, 6f, start = "2 wk", end = "Today", text = "6", unit = "day streak")),

        // ── t3lluz dashboard server ────────────────────────────────────────
        TileSource("dash.island", "Island", SourceGroup.DASHBOARD, ValueKind.LIST, "What the dashboard's island shows: what waits on you, warnings, what's on", 5, setOf(TileNeed.DASHBOARD), "sparkles", list(TileItem("Hermes needs you", "Approve a deploy", "Now"), TileItem("Rain at 15:00", null, "in 2 h"), TileItem("Qualifying", "US GP", "Sat 23:00"))),
        TileSource("dash.needs", "Needs you", SourceGroup.DASHBOARD, ValueKind.NUMBER, "Things waiting on you in Hermes", 5, setOf(TileNeed.DASHBOARD), "bot", n(null, 1.0, null, null, "waiting", "bot")),
        TileSource("dash.mail", "Mail that needs you", SourceGroup.DASHBOARD, ValueKind.LIST, "Mail sorted as Needs you: sender, subject; number is the count", 10, setOf(TileNeed.DASHBOARD), "mail", TileValue(number = 2.0, items = listOf(TileItem("Skatteetaten", "Your tax return is ready", "09:12"), TileItem("Ida", "Saturday?", "Yesterday")))),
        TileSource("dash.claude", "Claude session", SourceGroup.DASHBOARD, ValueKind.NUMBER, "How much of Claude's five-hour window is used (goal 100), sub when it resets", 5, setOf(TileNeed.DASHBOARD), "bot", n("42%", 42.0, 100.0, "%", "Resets 16:00", "bot")),
        TileSource("dash.claude_week", "Claude this week", SourceGroup.DASHBOARD, ValueKind.NUMBER, "How much of Claude's weekly limit is used (goal 100)", 15, setOf(TileNeed.DASHBOARD), "bot", n("63%", 63.0, 100.0, "%", "Resets Mon")),

        // ── Web & AI ───────────────────────────────────────────────────────
        TileSource("ai.text", "AI line", SourceGroup.WEB, ValueKind.TEXT, "Your AI answers the block's text as an ask on every refresh (default hourly); web:true lets it search the web; it also sees this widget's other values", 30, setOf(TileNeed.AI), "sparkles", TileValue(text = "Busy morning, then free after 15:00. Rain later, so take the umbrella.")),
        TileSource("ai.list", "AI rows", SourceGroup.WEB, ValueKind.LIST, "Your AI answers the block's text with up to 8 rows (title, sub, trailing, link) on every refresh (default hourly); web:true lets it search first: scores, fixtures, headlines, prices, releases, anything", 30, setOf(TileNeed.AI), "sparkles", list(TileItem("Liverpool 2–1 Arsenal", "Full time", "FT"), TileItem("Man City – Spurs", "Kick-off", "18:30"), TileItem("Brentford – Chelsea", "Tomorrow", "16:00"))),
        TileSource("web.json", "Any web address", SourceGroup.WEB, ValueKind.TEXT, "Reads a JSON value from url at path (data.items[0].price); when path points at a list, fields maps each row's title, sub, trailing, image and link (paths inside one item) so it fills a list or carousel. You allow the site first. A number becomes the value", 5, setOf(TileNeed.SITE), "globe", TileValue(text = "1 042", number = 1042.0, sub = "From the web")),
        TileSource("web.feed", "RSS or Atom feed", SourceGroup.WEB, ValueKind.LIST, "Any RSS or Atom feed at url (news sites, blogs, podcasts, a subreddit's .rss, https://www.youtube.com/feeds/videos.xml?channel_id=…): title, source, age, picture; a tap opens it. You allow the site first", 15, setOf(TileNeed.SITE), "rss", list(TileItem("Glance 1.3 adds snap scrolling", "Android Developers", "3h"), TileItem("Pixel Drop: October", "The Keyword", "1d"))),
        TileSource("web.image", "Picture from the web", SourceGroup.WEB, ValueKind.IMAGE, "The image at url (a webcam, a radar map, a comic, a chart), fetched again each refresh; you allow the site first", 15, setOf(TileNeed.SITE), "image", TileValue(text = "Webcam", sub = "Live")),
    )

    private val byKey = all.associateBy { it.key }

    /** Names an AI (or an older design) may use for a source that has another key now. */
    private val aliases = mapOf(
        "dash.inbox" to "gmail.inbox",
        "mail.inbox" to "gmail.inbox",
        "gmail.list" to "gmail.inbox",
        "mail.unread" to "gmail.unread",
        "youtube.videos" to "youtube.latest",
        "youtube.uploads" to "youtube.latest",
        "youtube.recent" to "youtube.latest",
        "twitch.streams" to "twitch.live",
        "music.now" to "media.now",
        "spotify.now" to "media.now",
        "media.playing" to "media.now",
        "notifications.list" to "notify.list",
        "notifications.count" to "notify.count",
        "rss" to "web.feed",
        "web.rss" to "web.feed",
        "feed.rss" to "web.feed",
        "photos.photo" to "photos.random",
        "tasks.open" to "tasks.list",
        "calendar.birthdays" to "contacts.birthdays",
        "web.list" to "ai.list",
    )

    /** The key [key] stands for: itself, or the source it is an old name of. */
    fun canonical(key: String?): String? = key?.trim()?.lowercase()?.let { aliases[it] ?: it }

    fun byKey(key: String?): TileSource? = canonical(key)?.let { byKey[it] }

    /** How often [b]'s source is worth reading, in minutes; AI and web lines keep to their own pace. */
    fun floorMinutes(b: TileBlock): Int {
        val src = byKey(b.bind) ?: return 15
        return when (src.key) {
            "ai.text", "ai.list" -> maxOf(b.every ?: 60, if (b.web) 30 else 15)
            "photos.random" -> maxOf(b.every ?: 30, 5)
            else -> maxOf(src.floorMinutes, b.every ?: 0)
        }
    }

    /**
     * A key for what [b] reads, so two blocks reading the same thing share one fetch and a
     * parameterised source (a command, an address, an ask) is cached per parameter.
     */
    fun bindingId(b: TileBlock): String? {
        val key = byKey(b.bind)?.key ?: return null
        val params = when (key) {
            "server.cmd" -> listOf(b.server, b.cmd)
            "web.json" -> listOf(b.url, b.path, b.fields.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" })
            "ai.text", "ai.list" -> listOf(b.text, b.web.toString())
            "time.countdown" -> listOf(b.text)
            "web.feed", "web.image" -> listOf(b.url)
            "youtube.latest", "youtube.new", "gmail.unread", "gmail.inbox", "tasks.list", "tasks.count",
            "notify.count", "notify.list", "photos.random", "photos.recent", "phone.headphones" -> listOf(b.query)
            else -> if (key.startsWith("server.")) listOf(b.server) else emptyList()
        }.map { it.orEmpty() }
        if (params.all { it.isEmpty() }) return key
        return key + "#" + Integer.toHexString(params.joinToString("\u0000").hashCode())
    }

    /** Which blocks make sense for a source, for the AI's instructions and the studio's checks. */
    fun fits(type: BlockType, kind: ValueKind): Boolean = when (type) {
        BlockType.RING, BlockType.BAR -> kind == ValueKind.NUMBER
        BlockType.CHART -> kind == ValueKind.SERIES
        BlockType.LIST, BlockType.CAROUSEL -> kind == ValueKind.LIST
        BlockType.COUNTDOWN -> kind == ValueKind.TIME
        BlockType.IMAGE -> kind == ValueKind.IMAGE || kind == ValueKind.LIST || kind == ValueKind.TEXT
        BlockType.CLOCK, BlockType.DATE -> true
        else -> true
    }

    /** The block types that show [source] well, best first: what the studio's block editor offers. */
    fun typesFor(source: TileSource?): List<BlockType> = when (source?.kind) {
        null -> listOf(BlockType.TEXT, BlockType.BADGE, BlockType.BUTTON, BlockType.CLOCK, BlockType.DATE)
        ValueKind.NUMBER -> listOf(BlockType.METRIC, BlockType.RING, BlockType.BAR, BlockType.BADGE, BlockType.TEXT)
        ValueKind.TEXT -> if (source.key == "media.now") listOf(BlockType.MEDIA, BlockType.METRIC, BlockType.TEXT, BlockType.IMAGE)
            else listOf(BlockType.METRIC, BlockType.TEXT, BlockType.BADGE)
        ValueKind.LIST -> listOf(BlockType.LIST, BlockType.CAROUSEL, BlockType.IMAGE, BlockType.METRIC)
        ValueKind.SERIES -> listOf(BlockType.CHART, BlockType.METRIC)
        ValueKind.TIME -> listOf(BlockType.COUNTDOWN, BlockType.METRIC, BlockType.TEXT)
        ValueKind.IMAGE -> listOf(BlockType.IMAGE, BlockType.TEXT)
    }.let { types -> if (source?.key == "ai.text") listOf(BlockType.AI, BlockType.TEXT) else types }
}
