# ICMP Client for Android

A simple Android app for running ICMP ping tests with real-time statistics and history tracking.

## Demo

Screen recording of the app is hosted on GitHub’s asset CDN (not committed here) so the repository stays small and clones stay fast.

1. Open a **new issue** on this repository (a draft is fine — you do not need to submit it).
2. Drag your `.mp4` into the comment box and wait for the upload to finish.
3. Copy the `https://user-images.githubusercontent.com/...` URL GitHub inserts.
4. Replace the placeholder line below with that URL alone on one line (no backticks) — GitHub will show an inline player on the README.

https://github.com/user-attachments/assets/e3c3de42-d0cc-44f5-a7b5-352f953cec41

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
