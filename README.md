# UserHub

A production-ready cross-platform user management client built with Kotlin Multiplatform and Compose Multiplatform. One shared codebase drives both Android and iOS — including 100% of the UI.

## Features

- **Smart user feed** — loads the most recent users from the last page of the GoRest directory, with name, email and a relative timestamp ("5 minutes ago") computed entirely in shared code.
- **Shimmer loading states** — no spinners anywhere; the list skeleton animates while data loads.
- **Offline-first** — every fetch is written through to a local SQLDelight database, so the feed keeps working when the network doesn't.
- **Add user** — a FAB opens a polished bottom sheet with real-time name and email validation. On a successful `201` the new user appears at the top of the feed immediately.
- **Delete with undo** — long-press a user, confirm, and the row animates out with a snackbar offering Undo. Nothing is finalised until the undo window closes.
- **Adaptive layout** — a single list in portrait, a master-detail split on landscape and tablet, driven by available width rather than device type.
- **Material 3 + dark mode** — the full palette adapts to the system theme.

## Architecture

Strict Clean Architecture with MVVM, split across three Gradle modules so the boundaries are enforced by the build, not by convention:

```
┌───────────────┐
│  composeApp   │  Compose Multiplatform UI + ViewModels
└───────┬───────┘
        │ depends on
        ▼
┌───────────────┐
│    domain     │  Use cases, business rules, time logic
└───────┬───────┘  Pure Kotlin — no Ktor, no SQLDelight, no Android
        │ depends on
        ▼
┌───────────────┐
│     data      │  Ktor client, SQLDelight cache, repository implementation
└───────────────┘
```

Dependencies point inward. The domain module knows nothing about how data is fetched or stored — swapping GoRest for another backend, or SQLDelight for Room, touches exactly one module. ViewModels depend only on use cases; the UI depends only on ViewModels and immutable state objects.

- **Networking** — Ktor 3.5 with content negotiation and kotlinx.serialization.
- **Persistence** — SQLDelight 2.3, typed queries generated at build time, native drivers per platform.
- **DI** — Koin, initialised once in `MainApplication` on Android and in `MainViewController` on iOS.
- **Concurrency** — structured coroutines throughout; state exposed as `StateFlow` and collected lifecycle-aware.

## Error handling

Production-ready error handling covers every failure mode. Network calls are wrapped so no exception can reach the UI unhandled; failures degrade gracefully to a dedicated "No Internet" screen with a retry action, and the cached feed is served whenever the live call cannot complete. The user never sees a stack trace, an error code, or an empty screen with no explanation.

## Testing

Comprehensive unit test coverage across all three modules — 17 tests, green on both platforms:

| Module | Covers |
| --- | --- |
| `domain` | relative time formatting, all three use cases |
| `data` | repository success path, cache write-through, offline fallback |
| `composeApp` | feed ViewModel load/delete/undo, add-user ViewModel submission |

Hand-written fakes are used instead of a mocking framework, so the tests stay readable and multiplatform. Run them with `./gradlew test`.

## How I used AI tooling

AI assistance was central to hitting this scope in a day, and I treated it as a force multiplier rather than an autopilot:

- **Scaffolding** — I had the assistant generate the module skeletons, Gradle wiring and Compose boilerplate from a written architecture brief, so the structure was decided by me and typed by the tool.
- **Iteration** — feature by feature: I described the behaviour I wanted, reviewed the diff, and pushed back on anything that broke a layer boundary.
- **Tests** — I generated the test suite from the finished implementation and iterated until everything was green, which caught several integration mistakes early.
- **Review** — I finished with an assistant-driven pass over the whole codebase looking for inconsistencies before committing.

The takeaway: the architecture, the module boundaries and the UX decisions are mine; the typing was largely automated.

## Build and run

Requires JDK 17, Android SDK 36, and Xcode 15+ for iOS.

```bash
# Android
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug

# Unit tests (all modules)
./gradlew test

# iOS framework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator. The shared framework is built automatically by a run-script phase.

## Tech stack

| | |
| --- | --- |
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 8.13.0 |
| Ktor | 3.5.0 |
| SQLDelight | 2.3.2 |
| Koin | 4.1.1 |
| Gradle | 8.14 |
| min / target SDK | 24 / 36 |
