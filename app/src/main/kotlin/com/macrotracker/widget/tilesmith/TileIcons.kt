package com.macrotracker.widget.tilesmith

import com.macrotracker.R

/**
 * The icons a Tilesmith block can carry: white vectors (`res/drawable/tsm_ic_*.xml`, from the
 * app's own Lucide set) that the widget tints with a colour role. Weather blocks also take the
 * forecast's own sky icon (`wx:<symbol>`).
 */
object TileIcons {
    val all: Map<String, Int> = linkedMapOf(
        "activity" to R.drawable.tsm_ic_activity,
        "bars" to R.drawable.tsm_ic_bars,
        "battery" to R.drawable.tsm_ic_battery,
        "battery_charging" to R.drawable.tsm_ic_battery_charging,
        "bell" to R.drawable.tsm_ic_bell,
        "bike" to R.drawable.tsm_ic_bike,
        "bluetooth" to R.drawable.tsm_ic_bluetooth,
        "bolt" to R.drawable.tsm_ic_bolt,
        "book" to R.drawable.tsm_ic_book,
        "bot" to R.drawable.tsm_ic_bot,
        "briefcase" to R.drawable.tsm_ic_briefcase,
        "cake" to R.drawable.tsm_ic_cake,
        "calendar" to R.drawable.tsm_ic_calendar,
        "chart" to R.drawable.tsm_ic_chart,
        "chat" to R.drawable.tsm_ic_chat,
        "check" to R.drawable.tsm_ic_check,
        "clock" to R.drawable.tsm_ic_clock,
        "cloud" to R.drawable.tsm_ic_cloud,
        "code" to R.drawable.tsm_ic_code,
        "coffee" to R.drawable.tsm_ic_coffee,
        "coins" to R.drawable.tsm_ic_coins,
        "cpu" to R.drawable.tsm_ic_cpu,
        "disk" to R.drawable.tsm_ic_disk,
        "download" to R.drawable.tsm_ic_download,
        "droplet" to R.drawable.tsm_ic_droplet,
        "dumbbell" to R.drawable.tsm_ic_dumbbell,
        "eye" to R.drawable.tsm_ic_eye,
        "flag" to R.drawable.tsm_ic_flag,
        "flame" to R.drawable.tsm_ic_flame,
        "food" to R.drawable.tsm_ic_food,
        "gauge" to R.drawable.tsm_ic_gauge,
        "git" to R.drawable.tsm_ic_git,
        "globe" to R.drawable.tsm_ic_globe,
        "headphones" to R.drawable.tsm_ic_headphones,
        "heart" to R.drawable.tsm_ic_heart,
        "heart_pulse" to R.drawable.tsm_ic_heart_pulse,
        "home" to R.drawable.tsm_ic_home,
        "image" to R.drawable.tsm_ic_image,
        "inbox" to R.drawable.tsm_ic_inbox,
        "key" to R.drawable.tsm_ic_key,
        "leaf" to R.drawable.tsm_ic_leaf,
        "link" to R.drawable.tsm_ic_link,
        "lock" to R.drawable.tsm_ic_lock,
        "mail" to R.drawable.tsm_ic_mail,
        "map_pin" to R.drawable.tsm_ic_map_pin,
        "moon" to R.drawable.tsm_ic_moon,
        "mountain" to R.drawable.tsm_ic_mountain,
        "music" to R.drawable.tsm_ic_music,
        "newspaper" to R.drawable.tsm_ic_newspaper,
        "pause" to R.drawable.tsm_ic_pause,
        "phone" to R.drawable.tsm_ic_phone,
        "play" to R.drawable.tsm_ic_play,
        "radio" to R.drawable.tsm_ic_radio,
        "rain" to R.drawable.tsm_ic_rain,
        "refresh" to R.drawable.tsm_ic_refresh,
        "rocket" to R.drawable.tsm_ic_rocket,
        "rss" to R.drawable.tsm_ic_rss,
        "run" to R.drawable.tsm_ic_run,
        "scale" to R.drawable.tsm_ic_scale,
        "search" to R.drawable.tsm_ic_search,
        "server" to R.drawable.tsm_ic_server,
        "siren" to R.drawable.tsm_ic_siren,
        "skip_next" to R.drawable.tsm_ic_skip_next,
        "skip_prev" to R.drawable.tsm_ic_skip_prev,
        "sleep" to R.drawable.tsm_ic_sleep,
        "sparkles" to R.drawable.tsm_ic_sparkles,
        "star" to R.drawable.tsm_ic_star,
        "steps" to R.drawable.tsm_ic_steps,
        "sun" to R.drawable.tsm_ic_sun,
        "sunrise" to R.drawable.tsm_ic_sunrise,
        "swim" to R.drawable.tsm_ic_swim,
        "tag" to R.drawable.tsm_ic_tag,
        "tasks" to R.drawable.tsm_ic_tasks,
        "terminal" to R.drawable.tsm_ic_terminal,
        "thermometer" to R.drawable.tsm_ic_thermometer,
        "timer" to R.drawable.tsm_ic_timer,
        "tree" to R.drawable.tsm_ic_tree,
        "trophy" to R.drawable.tsm_ic_trophy,
        "tv" to R.drawable.tsm_ic_tv,
        "upload" to R.drawable.tsm_ic_upload,
        "user" to R.drawable.tsm_ic_user,
        "users" to R.drawable.tsm_ic_users,
        "walk" to R.drawable.tsm_ic_walk,
        "warning" to R.drawable.tsm_ic_warning,
        "water" to R.drawable.tsm_ic_water,
        "wifi" to R.drawable.tsm_ic_wifi,
        "wind" to R.drawable.tsm_ic_wind,
        "yoga" to R.drawable.tsm_ic_yoga,
        "youtube" to R.drawable.tsm_ic_youtube,
    )

    private val aliases = mapOf(
        "footprints" to "steps", "walking" to "walk", "running" to "run", "fire" to "flame", "calories" to "flame",
        "kcal" to "flame", "bed" to "sleep", "night" to "moon", "temperature" to "thermometer", "temp" to "thermometer",
        "weather" to "cloud", "umbrella" to "rain", "drop" to "droplet", "glass" to "water", "hydration" to "water",
        "event" to "calendar", "meeting" to "calendar", "time" to "clock", "alarm" to "bell", "notification" to "bell",
        "email" to "mail", "f1" to "flag", "race" to "flag", "car" to "flag", "memory" to "cpu", "ram" to "cpu",
        "storage" to "disk", "harddrive" to "disk", "shell" to "terminal", "github" to "git", "pr" to "git",
        "mobile" to "phone", "smartphone" to "phone", "house" to "home", "location" to "map_pin", "pin" to "map_pin",
        "money" to "coins", "price" to "coins", "stock" to "chart", "trend" to "chart", "ok" to "check",
        "done" to "check", "alert" to "warning", "error" to "warning", "video" to "youtube",
        "twitch" to "tv", "news" to "newspaper", "article" to "newspaper", "feed" to "rss", "blog" to "rss", "web" to "globe", "internet" to "globe", "ai" to "sparkles",
        "magic" to "sparkles", "power" to "bolt", "energy" to "bolt", "charging" to "battery_charging",
        "spotify" to "music", "song" to "music", "countdown" to "timer", "stopwatch" to "timer", "cafe" to "coffee",
        "plant" to "leaf", "nature" to "leaf", "gym" to "dumbbell", "workout" to "dumbbell", "heartrate" to "heart_pulse",
        "hr" to "heart_pulse", "pulse" to "heart_pulse", "weight" to "scale", "bot" to "bot", "robot" to "bot",
        "hermes" to "bot", "message" to "chat", "chat_bubble" to "chat", "network" to "wifi",
        "photo" to "image", "picture" to "image", "gallery" to "image", "camera" to "image", "people" to "users",
        "contacts" to "users", "friends" to "users", "person" to "user", "contact" to "user", "birthday" to "cake",
        "earbuds" to "headphones", "headset" to "headphones", "audio" to "headphones", "todo" to "tasks",
        "checklist" to "tasks", "list" to "tasks", "next" to "skip_next", "previous" to "skip_prev", "prev" to "skip_prev",
        "gmail" to "mail", "podcast" to "headphones", "reddit" to "rss",
    )

    /** An icon id the set has, from what an AI may call it; null when there is none. */
    fun normalize(id: String?): String? {
        val key = id?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
        if (key.startsWith("wx:")) return key
        if (key in all) return key
        return aliases[key]?.takeIf { it in all }
    }

    fun res(id: String?): Int? {
        val key = normalize(id) ?: return null
        if (key.startsWith("wx:")) return com.macrotracker.widget.weather.skyIcon(key.removePrefix("wx:"))
        return all[key]
    }
}
