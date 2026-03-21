# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Building, running, and deploying is done through Android Studio. No test suite is configured.

## Architecture

Single-module Android app (`com.example.icmpclient`) using Jetpack Compose + Room + coroutines. Manual dependency injection via `IcmpApp` Application class (singleton service locator).

**Data flow:** Composable screens → `PingViewModel` → `PingRepository` → `icmp4a` library + Room DB

### Key components

- **IcmpApp** — Application subclass that lazy-initializes a single `PingRepository` shared across the activity and foreground service.
- **PingRepository** — Core logic layer. Drives the `icmp4a` ICMP library, maintains reactive `StateFlow<PingState>` and `SharedFlow<PingResultItem>`, writes results to Room incrementally during pinging, then finalizes aggregated session stats on completion.
- **PingViewModel** — Thin wrapper exposing repository state to UI. Handles dual execution: foreground (coroutine in viewModelScope) vs background (starts `PingForegroundService` via Intent).
- **PingForegroundService** — Runs pings independent of activity lifecycle. Shares the same `PingRepository` instance from `IcmpApp`. Receives start/stop via Intent actions with extras (host, count, interval).
- **Room DB** — Two entities: `PingSessionEntity` (parent, aggregated stats) and `PingResultEntity` (per-packet, FK with CASCADE delete). DAO returns `Flow<T>` for reactive queries.

### UI screens (Compose, Material3)

- **PingScreen** — Real-time ping controls, stats, live results list, and `LatencyChart` (custom Canvas renderer).
- **HistoryScreen** — Session list → detail view with CSV export (temp file + FileProvider).
- Navigation via bottom nav bar in `MainActivity`.

### Non-obvious patterns

- `Int.MAX_VALUE` ping count encodes continuous/infinite mode; converted to `null` when passed to icmp4a.
- Results are written to DB row-by-row during execution; session record is finalized with aggregate stats only after completion (`finalizeSession()`).
- The foreground service and UI observe the same repository `StateFlow`, so state stays in sync regardless of execution mode.

## Dependencies

- **icmp4a** — External library for native ICMP ping operations (requires root or `NET_RAW` capability on some devices).
- **Room 2.6.1** with KSP for compile-time schema generation.
- **Compose BOM** for coordinated Compose library versions.
