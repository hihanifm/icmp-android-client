# ICMP Client for Android

Android app for ICMP ping tests: live latency chart, session statistics, and history stored on device. Uses Jetpack Compose and Room.

## Demo

Screen recording of the UI (hosted on GitHub; not stored in the git tree):

https://github.com/user-attachments/assets/e3c3de42-d0cc-44f5-a7b5-352f953cec41

## Features

- Ping any host with configurable count and interval
- **Engines:** in-app ICMP via [icmp4a](https://github.com/marsounjan/icmp4a) or system `/system/bin/ping`
- Real-time chart and stats (min / average / max RTT, packet loss)
- Foreground service for background pings while you use other apps
- Optional network binding (Wi‑Fi, cellular, satellite, etc.) where the icmp4a path supports it
- History with per-session detail and CSV export (single session or all sessions)
- Continuous mode with a configurable upper cap on ping count

## Getting started

Open the project in **Android Studio**, sync Gradle, and run on a **physical device** or emulator. Native ICMP may need appropriate capabilities on some devices; the shell ping path is a fallback when you pick that engine in the app.

| | |
| --- | --- |
| **Application ID** | `com.mh.icmpclient` |
| **Min SDK** | 23 (Android 6.0) |
| **Target SDK** | 34 |

## Project structure

Kotlin code lives under `app/src/main/kotlin/com/mh/icmpclient/`: `db` (Room), `model`, `ping` (executors and backend), `repository`, `service` (foreground ping), `ui`, `viewmodel`, plus `IcmpApp` at the package root. For data flow, components, and non-obvious behavior (continuous vs fixed count, backends), see [`CLAUDE.md`](CLAUDE.md).

## Tech stack

- Kotlin, Jetpack Compose (Material 3)
- Room, coroutines, Flow
- icmp4a for raw ICMP where available
