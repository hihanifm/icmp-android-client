# ICMP Client for Android

A simple Android app for running ICMP ping tests with real-time statistics and history tracking.

## Features

- Ping any host with configurable count and interval
- Real-time latency chart and statistics (min/avg/max RTT, packet loss)
- Background pinging via foreground service
- Network selection (WiFi, Cellular, Satellite, etc.)
- Ping history with per-session detail view
- CSV export for individual sessions or all sessions at once
- Configurable max ping limit for continuous mode

## Build

Open the project in Android Studio and run on a device or emulator.

- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 34

## Tech Stack

- Kotlin, Jetpack Compose (Material3)
- Room for local persistence
- icmp4a for native ICMP ping
- Coroutines + Flow for reactive state
