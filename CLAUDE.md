# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Building, running, and deploying is done through Android Studio. No test suite is configured.

## Architecture

Single-module Android app (`com.mh.icmpclient`, namespace matches `applicationId`) using Jetpack Compose + Room + coroutines. Manual dependency injection via `IcmpApp` Application class (singleton service locator).

**Data flow:** Composable screens (`ui`) → `PingViewModel` → `PingRepository` → `ping` executors (`Icmp4aPingExecutor` / `ShellPingExecutor`) + Room (`db`).

### Source layout (`app/src/main/kotlin/com/mh/icmpclient/`)

| Package | Contents |
|---------|----------|
| *(root)* | `IcmpApp` — wires `PingRepository` with `PingDatabase` |
| `db` | Room: entities, DAO, database |
| `model` | `PingResultItem`, `PingStats`, `PingState` |
| `ping` | `PingBackend`, `PingExecutor`, `PingChunk`, `Icmp4aPingExecutor`, `ShellPingExecutor` |
| `repository` | `PingRepository` |
| `service` | `PingForegroundService` |
| `ui` | `MainActivity`, `PingScreen`, `HistoryScreen`, `LatencyChart` |
| `viewmodel` | `PingViewModel` |

### Key components

- **IcmpApp** — Application subclass that lazy-initializes a single `PingRepository` shared across the activity and foreground service.
- **PingRepository** — Core logic layer. Chooses executor by `PingBackend`, maintains reactive `StateFlow<PingState>` and `SharedFlow<PingResultItem>`, writes results to Room incrementally during pinging, then finalizes aggregated session stats on completion.
- **PingViewModel** — Thin wrapper exposing repository state to UI. Handles dual execution: foreground (coroutine in `viewModelScope`) vs background (starts `PingForegroundService` via Intent).
- **PingForegroundService** — Runs pings independent of activity lifecycle. Shares the same `PingRepository` instance from `IcmpApp`. Receives start/stop via Intent actions with extras (host, count, interval, optional network handle, ping backend).
- **Room DB** — Two entities: `PingSessionEntity` (parent, aggregated stats) and `PingResultEntity` (per-packet, FK with CASCADE delete). DAO returns `Flow<T>` for reactive queries.

### UI screens (Compose, Material3)

- **PingScreen** — Real-time ping controls, stats, live results list, and `LatencyChart` (custom Canvas renderer).
- **HistoryScreen** — Session list → detail view with CSV export (temp file + FileProvider).
- Navigation via bottom nav bar in `MainActivity`.

### Non-obvious patterns

- **Continuous vs fixed count:** When “Continuous” is on, the ping count sent to the repository is `maxPingCount` (UI default 1000); when off, it is `pingCount` (UI default 10). There is no special sentinel for “infinite” in the current UI—raise `maxPingCount` for longer runs.
- **Ping backends:** `PingBackend.ICMP4A` uses icmp4a (can bind to a selected `Network`). `PingBackend.SHELL` runs `/system/bin/ping` and cannot honor network binding the same way (UI shows a warning when a specific network is selected).
- Results are written to the DB row-by-row during execution; the session row is finalized with aggregate stats only after completion (`finalizeSession()`).
- The foreground service and UI observe the same repository `StateFlow`, so state stays in sync regardless of execution mode.

## Dependencies

- **icmp4a** — External library for native ICMP ping operations (requires root or `NET_RAW` capability on some devices).
- **Room 2.6.1** with KSP for compile-time schema generation.
- **Compose BOM** for coordinated Compose library versions.
