# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

UserHub — a cross-platform (Android + iOS) user management client built with Kotlin Multiplatform and Compose Multiplatform. One shared codebase drives both platforms, including 100% of the UI. It lists users from the GoRest public API, caches them offline in SQLDelight, and supports adding/deleting users with optimistic UI and undo.

## Commands

```bash
# Android debug build / install
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug

# Run all unit tests (domain, data, composeApp)
./gradlew test

# Run tests for a single module
./gradlew :domain:test
./gradlew :data:test
./gradlew :composeApp:test

# Run a single test class (JVM/Android target)
./gradlew :domain:testDebugUnitTest --tests "com.userhub.domain.RelativeTimeFormatterTest"

# Build the iOS shared framework (for simulator)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator — the shared KMP framework is rebuilt automatically by an Xcode run-script build phase. Requires JDK 17, Android SDK 36, Xcode 15+.

Tests use hand-written fakes (e.g. `FakeUserRepository`, `FakeLocalDataSource`) instead of a mocking framework, so they stay readable and multiplatform-compatible.

## Architecture

Strict Clean Architecture / MVVM enforced by Gradle module boundaries, not convention. Dependencies only point inward:

```
composeApp  →  domain  →  (nothing)
composeApp  →  data     (data also depends on domain)
```

- **`domain`** — pure Kotlin, no Ktor/SQLDelight/Android dependencies. Contains use cases (`GetUsersUseCase`, `AddUserUseCase`, `DeleteUserUseCase`) and relative-time formatting logic (`RelativeTimeFormatter`, `TimeProvider`) with `expect/actual` platform implementations under `androidMain`/`iosMain`.
- **`data`** — Ktor client (`GoRestApi`, `HttpClientFactory`) + SQLDelight local cache (`SqlDelightUserLocalDataSource`) behind a `UserRepository` interface, implemented by `UserRepositoryImpl`. This is the only module allowed to know about networking or persistence.
- **`composeApp`** — Compose Multiplatform UI (`ui/`) and ViewModels (`presentation/`). ViewModels depend only on domain use cases and expose immutable `StateFlow` UI state (see `UserFeedUiState`); the UI depends only on ViewModels and UI models (`UserUiModel`), never on `data`-layer DTOs directly (mapping happens in `UserUiMapper`).

Swapping the backend (GoRest → something else) or the persistence layer (SQLDelight → Room) should only ever touch the `data` module.

### Key data flow

- `UserRepositoryImpl.getUsers()` fetches the last page from GoRest, reverses it (most recent first), and writes it through to the local SQLDelight cache. On any network failure it falls back to the cache and returns `UsersResult.NoInternet` only when the cache is also empty — see `data/src/commonMain/kotlin/com/userhub/data/repository/UserRepositoryImpl.kt`.
- `UserFeedViewModel` surfaces a stale-data banner (`"Offline — last updated …"`) when serving cached results, and implements delete-with-undo: the row is removed from state immediately, the network delete is fired in `GlobalScope` (deliberately outside `viewModelScope` so it isn't cancelled if the screen is torn down mid-undo-window), and `onUndo()` reinserts the user at its original index if the snackbar action is triggered.
- `DirectoryScreen` switches between a single-pane list and a master-detail split at a `maxWidth >= 700.dp` breakpoint (via `BoxWithConstraints`), independent of device type.

### DI (Koin)

Wired once via `initKoin()` (`composeApp/src/commonMain/kotlin/com/userhub/di/InitKoin.kt`), called from `MainApplication` on Android and `MainViewController` on iOS. `appModule` (shared) declares the Ktor client, SQLDelight database, repository, use cases, and ViewModels; `platformModule` (`PlatformModule.android.kt` / `PlatformModule.ios.kt`) supplies platform-specific pieces like the Ktor engine and SQLDelight driver.

### Networking notes

- `ApiConfig` (`data/src/commonMain/kotlin/com/userhub/data/remote/ApiConfig.kt`) holds the GoRest base URL and bearer token.
- `HttpClientFactory` currently has `Logging` installed at `LogLevel.ALL` (full request/response bodies to stdout) — this was left on from debugging the add-user flow; be aware of it when working on networking code, and consider whether it should stay this verbose before shipping.
