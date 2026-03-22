# ICMP Client for Android

A simple Android app for running ICMP ping tests with real-time statistics and history tracking.

## Features

- Ping any host with configurable count and interval
- **Ping engine:** in-app ICMP (icmp4a) or system `/system/bin/ping`
- Real-time latency chart and statistics (min/avg/max RTT, packet loss)
- Background pinging via foreground service
- Network selection (WiFi, Cellular, Satellite, etc.) — most relevant for the icmp4a path
- Ping history with per-session detail view
- CSV export for individual sessions or all sessions at once
- Configurable max ping limit when continuous mode is enabled

## Build

Open the project in Android Studio and run on a device or emulator.

- **Application ID / package:** `com.mh.icmpclient`
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 34

## Project layout

Kotlin sources live under `app/src/main/kotlin/com/mh/icmpclient/`, grouped by role: `db` (Room), `model`, `ping` (executors + backend enum), `repository`, `service`, `ui`, `viewmodel`, plus `IcmpApp` at the package root. See `CLAUDE.md` for a fuller architecture note for AI assistants.

## Tech Stack

- Kotlin, Jetpack Compose (Material3)
- Room for local persistence
- icmp4a for native ICMP ping (optional vs shell ping)
- Coroutines + Flow for reactive state
