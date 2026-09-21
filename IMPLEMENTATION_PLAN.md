# Implementation plan — the four core fixes

Source: `PROCESS_LOG.md`, which names four items needed to make the app functional and AC-compliant:

1. The empty-cache/offline crash.
2. The missing relative "added ago" time on feed rows (currently a fabricated 5-minute stagger).
3. The undo/delete timing (delete fires immediately via `GlobalScope` instead of once the undo window elapses).
4. The detail panel losing or misrepresenting the selected user — on device rotation, and when the selected user is deleted.

This document is written so that a fresh Claude Code session — with no memory of this conversation — can pick up **one** workstream and implement it end-to-end using TDD: write the failing test(s) first, confirm/explain why they fail, then make them pass with the smallest correct change, then verify.

Each workstream below is self-contained: problem, root cause (with exact file/line references as they stood on `chore/build-config` on 2026-09-20), the RED tests to add, the GREEN implementation, and how to verify. Hand each workstream's section to its own session verbatim as the task brief.

## Coordination note (read this before splitting the work across sessions)

The three workstreams are **not** fully independent at the file level:

- Workstream 1 and Workstream 2 both touch `UserRepositoryImpl.getUsers()`'s catch block.
- Workstream 2 and Workstream 3 both touch `UserFeedViewModel.kt`, but different methods (`load()`/mapping vs. `onDeleteConfirmed()`/`onUndo()`), so they should merge cleanly.

**Recommended sequencing:** land Workstream 1 first (it's a 5-minute fix, isolated to one method). Once merged, run Workstream 2 and Workstream 3 in parallel against the updated code — they touch different methods in the shared files they overlap on, so conflicts should be mechanical (line-adjacent, not logical). Do not run Workstream 1 and 2 fully in parallel from the same base commit; whichever lands second will need to reapply Workstream 1's reordering by hand.

---

## Workstream 1 — Empty-cache/offline crash

**Module:** `data`
**File:** `data/src/commonMain/kotlin/com/userhub/data/repository/UserRepositoryImpl.kt`
**Test file:** `data/src/commonTest/kotlin/com/userhub/data/UserRepositoryImplTest.kt`

### Problem

Confirmed by manual reproduction (PROCESS_LOG.md, "empty-cache crash" entry): clearing app storage and launching offline produces a fatal `NoSuchElementException` at `UserRepositoryImpl.kt:24`, crashing the app outright instead of surfacing `UsersResult.NoInternet`.

### Root cause

```kotlin
} catch (e: Exception) {
    val cached = localDataSource.getUsers()
    val lastSync = cached.maxOf { it.cachedAt }   // <-- throws NoSuchElementException on empty list
    if (cached.isEmpty()) {
        UsersResult.NoInternet
    } else {
        UsersResult.Success(cached.map { it.user }, lastSyncMillis = lastSync)
    }
}
```

`cached.maxOf { ... }` is evaluated unconditionally, before the `isEmpty()` check that was meant to guard exactly this case. On an empty cache it throws, and because this throw happens *inside* the `catch` block, nothing downstream catches it — it propagates out of the suspend function and crashes whatever coroutine called `getUsers()` (in production, `UserFeedViewModel.load()`'s `viewModelScope.launch`).

### RED — add this test to `UserRepositoryImplTest.kt`

```kotlin
@Test
fun `returns NoInternet instead of throwing when the network fails and the cache is empty`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
    val repository = UserRepositoryImpl(
        GoRestApi(createHttpClient(engine)),
        FakeLocalDataSource() // empty cache
    )

    val result = repository.getUsers()

    assertIs<UsersResult.NoInternet>(result)
}
```

Run it: `./gradlew :data:testDebugUnitTest --tests "com.userhub.data.UserRepositoryImplTest"`.

Expected RED behaviour: the test does not fail on an assertion — it fails because `getUsers()` throws `NoSuchElementException` out of the `runTest` block. That thrown exception, uncaught, *is* the bug reproduced in a unit test. Confirm this before touching the implementation.

### GREEN

Reorder so the empty check happens before the `maxOf`:

```kotlin
} catch (e: Exception) {
    val cached = localDataSource.getUsers()
    if (cached.isEmpty()) {
        UsersResult.NoInternet
    } else {
        val lastSync = cached.maxOf { it.cachedAt }
        UsersResult.Success(cached.map { it.user }, lastSyncMillis = lastSync)
    }
}
```

### Verify

- `./gradlew :data:test` — new test passes, existing three tests in the file still pass unchanged.
- Manually on Android: clear app storage, disable network, launch. App should land on the `NoInternet` UI state instead of crashing (existing `UserFeedUiState.NoInternet` / `UserFeedViewModel` wiring already handles this correctly once the exception stops escaping).

---

## Workstream 2 — Real "added ago" time on feed rows

**Modules:** `data`, `domain`, `composeApp`
**Files:**
- `data/src/commonMain/sqldelight/com/userhub/data/db/UserCache.sq`
- `data/src/commonMain/kotlin/com/userhub/data/local/UserLocalDataSource.kt`
- `data/src/commonMain/kotlin/com/userhub/data/local/SqlDelightUserLocalDataSource.kt`
- `data/src/commonMain/kotlin/com/userhub/data/repository/UserRepositoryImpl.kt`
- `data/src/commonMain/kotlin/com/userhub/data/repository/UserRepository.kt` (for `UsersResult`)
- `data/src/commonTest/kotlin/com/userhub/data/FakeLocalDataSource.kt`
- `data/src/commonTest/kotlin/com/userhub/data/UserRepositoryImplTest.kt`
- `composeApp/src/commonMain/kotlin/com/userhub/presentation/UserUiMapper.kt`
- `composeApp/src/commonMain/kotlin/com/userhub/presentation/UserFeedViewModel.kt` (one call-site change)
- `composeApp/src/commonTest/kotlin/com/userhub/presentation/UserUiMapperTest.kt` (new)
- `domain/src/commonMain/kotlin/com/userhub/domain/time/TimeProvider.kt`, `TimeProvider.android.kt`, `TimeProvider.ios.kt`
- `domain/src/androidUnitTest/kotlin/com/userhub/domain/time/TimeProviderTest.kt` (new source set + test)

This is the largest of the three workstreams because the bug has two independent causes that both have to be fixed for the AC to actually be met end to end. Do them in the order below; each has its own RED/GREEN.

### Problem

AC: *"Each row shows the user's name, email address, and how long ago they were added, expressed relatively ('5 minutes ago')."*

What's implemented instead (`composeApp/src/commonMain/kotlin/com/userhub/presentation/UserUiMapper.kt`):

```kotlin
private const val FEED_STAGGER_MILLIS = 5 * 60 * 1_000L

fun List<UserDto>.toUiModels(): List<UserUiModel> {
    val now = TimeProvider.nowEpochMillis()
    return mapIndexed { index, user ->
        val addedAt = now - index * FEED_STAGGER_MILLIS
        UserUiModel(user = user, createdLabel = formatRelativeTime(addedAt, now))
    }
}
```

Every row gets a fabricated timestamp based purely on its position in the list (row 0 = "just now", row 1 = "5 minutes ago", row 2 = "10 minutes ago", ...), regardless of when the user was actually added. Confirmed visually in PROCESS_LOG.md ("added-relative-time-mismatch.png"): a genuinely new user correctly shows "Just now" sitting next to neighbours whose times are an obviously artificial neat 5-minute staircase.

### Root cause 2a — nothing preserves a per-user "first seen" time

GoRest's public users API returns no creation timestamp, so the only honest signal the app has for "how long ago was this user added" is *when this device first observed the user locally*. Today that signal doesn't survive even a single resync:

`data/src/commonMain/sqldelight/com/userhub/data/db/UserCache.sq`:
```sql
CREATE TABLE user_cache (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    gender TEXT NOT NULL,
    status TEXT NOT NULL,
    cached_at INTEGER NOT NULL
);

insertUser:
INSERT OR REPLACE INTO user_cache(id, name, email, gender, status, cached_at)
VALUES (?, ?, ?, ?, ?, ?);
```

`data/src/commonMain/kotlin/com/userhub/data/local/SqlDelightUserLocalDataSource.kt`:
```kotlin
override fun saveUsers(users: List<UserDto>, timestamp: Long) {
    queries.transaction {
        queries.clearAll()
        users.forEach { user -> queries.insertUser(..., cached_at = timestamp) }
    }
}
```

Every successful fetch wipes the table and reinserts every row stamped with the *current* sync time — there is no column anywhere that means "when did we first see this user." `cached_at` means "last synced at," not "added at." That's presumably *why* the stagger hack exists: there was no real per-user timestamp to reach for.

### Root cause 2b — the two clocks used disagree by the local UTC offset

Even once a real per-user timestamp exists, it will display incorrectly outside UTC. This explains the separate "last updated ~1 hour off" oddity parked earlier in the log (PROCESS_LOG.md, `UserFeedViewModel` entry, point 1).

`domain/src/commonMain/kotlin/com/userhub/domain/time/TimeProvider.kt`:
```kotlin
object TimeProvider {
    fun nowEpochMillis(): Long = systemEpochMillis() + utcOffsetMillis()
}
```

`domain/src/androidMain/kotlin/com/userhub/domain/time/TimeProvider.android.kt`:
```kotlin
internal actual fun systemEpochMillis(): Long = System.currentTimeMillis()
internal actual fun utcOffsetMillis(): Long =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
```

`TimeProvider.nowEpochMillis()` adds the local UTC offset *on top of* true epoch millis (e.g. +1 hour during BST). Meanwhile, `data/src/commonMain/kotlin/com/userhub/data/Platform.android.kt`'s `currentTimeMillis()` — used to stamp every row saved to the cache — is plain `System.currentTimeMillis()`, with no offset added. `formatRelativeTime(epochMillis, nowMillis)` is pure millisecond subtraction (see `RelativeTimeFormatter.android.kt`/`.ios.kt`), so it needs both timestamps on the same basis. Feeding it an offset-shifted "now" against a non-shifted "added at" inflates every elapsed-time calculation by exactly the local UTC offset — matching the "roughly an hour earlier than expected" observation exactly. If this isn't fixed alongside 2a, freshly-added users will still appear to have been added "1 hour ago" the instant they're created (BST), undermining the very fix this workstream is making.

### Design for the fix

1. Add a `first_seen_at` column that is set once per user id and never overwritten by later syncs; keep `cached_at` meaning "last successful sync touched this row" (still needed for the existing "Offline — last updated ..." banner).
2. Switch `saveUsers` from clear-then-reinsert to an upsert that preserves `first_seen_at` for ids already known, and removes rows for ids no longer present in the latest fetch (so deleted-server-side users don't linger forever).
3. Thread the per-user `first_seen_at` value up through `UserRepositoryImpl` into `UsersResult.Success` as a `Map<Long, Long>` keyed by user id (default `emptyMap()`, so existing call sites/fakes that don't care about this remain source-compatible).
4. Rewrite `UserUiMapper.toUiModels()` to read the real timestamp out of that map instead of fabricating one from list position.
5. Remove the double-counted UTC offset in `TimeProvider.nowEpochMillis()`.

### RED — data layer

**`data/src/commonMain/sqldelight/com/userhub/data/db/UserCache.sq`** — new schema (write this, then the Kotlin below won't compile until it's in place, which is expected):

```sql
CREATE TABLE user_cache (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    gender TEXT NOT NULL,
    status TEXT NOT NULL,
    first_seen_at INTEGER NOT NULL,
    cached_at INTEGER NOT NULL
);

upsertUser:
INSERT INTO user_cache(id, name, email, gender, status, first_seen_at, cached_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    email = excluded.email,
    gender = excluded.gender,
    status = excluded.status,
    cached_at = excluded.cached_at;

selectFirstSeen:
SELECT first_seen_at FROM user_cache WHERE id = ?;

deleteMissing:
DELETE FROM user_cache WHERE id NOT IN ?;

clearAll:
DELETE FROM user_cache;

selectAll:
SELECT * FROM user_cache;
```

**`UserLocalDataSource.kt`** — extend `CachedUser` and change `saveUsers`'s return type so callers can observe the (preserved) first-seen timestamps without a second round trip:

```kotlin
data class CachedUser(
    val user: UserDto,
    val cachedAt: Long,
    val firstSeenAt: Long
)

interface UserLocalDataSource {
    fun saveUsers(users: List<UserDto>, timestamp: Long): List<CachedUser>
    fun getUsers(): List<CachedUser>
}
```

**`FakeLocalDataSource.kt`** (test infra — update so the RED test below can even compile/run):

```kotlin
class FakeLocalDataSource(initial: List<CachedUser> = emptyList()) : UserLocalDataSource {

    private var stored = initial.toMutableList()

    var savedUsers: List<UserDto> = emptyList()
        private set

    override fun saveUsers(users: List<UserDto>, timestamp: Long): List<CachedUser> {
        savedUsers = users
        val previousFirstSeen = stored.associate { it.user.id to it.firstSeenAt }
        val updated = users.map { user ->
            CachedUser(
                user = user,
                cachedAt = timestamp,
                firstSeenAt = previousFirstSeen[user.id] ?: timestamp
            )
        }
        stored = updated.toMutableList()
        return stored
    }

    override fun getUsers(): List<CachedUser> = stored
}
```

**Add to `UserRepositoryImplTest.kt`:**

```kotlin
@Test
fun `preserves a user's first-seen time across repeated successful fetches`() = runTest {
    val local = FakeLocalDataSource()
    val repository = UserRepositoryImpl(GoRestApi(createHttpClient(successEngine())), local)

    val first = repository.getUsers()
    val second = repository.getUsers()

    assertIs<UsersResult.Success>(first)
    assertIs<UsersResult.Success>(second)
    assertEquals(first.addedAtMillis.getValue(1L), second.addedAtMillis.getValue(1L))
}
```

This won't compile until `UsersResult.Success` gains an `addedAtMillis` field — that compile failure *is* the RED state for this part (there is currently no way to even express "when was this user added" through the repository's public API; that's the bug). Confirm the test as written references code that doesn't exist yet, then implement.

**`UserRepository.kt`:**

```kotlin
sealed interface UsersResult {
    data class Success(
        val users: List<UserDto>,
        val addedAtMillis: Map<Long, Long> = emptyMap(),
        val lastSyncMillis: Long?
    ) : UsersResult
    data object NoInternet : UsersResult
}
```

### GREEN — data layer

**`SqlDelightUserLocalDataSource.kt`:**

```kotlin
override fun saveUsers(users: List<UserDto>, timestamp: Long): List<CachedUser> =
    queries.transactionWithResult {
        users.forEach { user ->
            val firstSeen = queries.selectFirstSeen(user.id).executeAsOneOrNull() ?: timestamp
            queries.upsertUser(
                id = user.id,
                name = user.name,
                email = user.email,
                gender = user.gender,
                status = user.status,
                first_seen_at = firstSeen,
                cached_at = timestamp
            )
        }
        if (users.isEmpty()) queries.clearAll() else queries.deleteMissing(users.map { it.id })
        getUsers()
    }

override fun getUsers(): List<CachedUser> =
    queries.selectAll().executeAsList().map { row ->
        CachedUser(
            user = UserDto(row.id, row.name, row.email, row.gender, row.status),
            cachedAt = row.cached_at,
            firstSeenAt = row.first_seen_at
        )
    }
```

**`UserRepositoryImpl.kt`** (builds on Workstream 1's fix — apply that fix to this file first if it hasn't already landed):

```kotlin
override suspend fun getUsers(): UsersResult = withContext(Dispatchers.Default) {
    try {
        val users = api.fetchLastPageUsers().reversed()
        val cachedRows = localDataSource.saveUsers(users, currentTimeMillis())
        UsersResult.Success(
            users = users,
            addedAtMillis = cachedRows.associate { it.user.id to it.firstSeenAt },
            lastSyncMillis = null
        )
    } catch (e: Exception) {
        val cached = localDataSource.getUsers()
        if (cached.isEmpty()) {
            UsersResult.NoInternet
        } else {
            UsersResult.Success(
                users = cached.map { it.user },
                addedAtMillis = cached.associate { it.user.id to it.firstSeenAt },
                lastSyncMillis = cached.maxOf { it.cachedAt }
            )
        }
    }
}
```

Also add, in the same test file, a test proving deletion sync actually removes stale rows (this is a real behavioural change from clear+reinsert-everything, worth locking in):

```kotlin
@Test
fun `drops cached users that are no longer present in the latest fetch`() = runTest {
    val local = FakeLocalDataSource(
        initial = listOf(CachedUser(UserDto(99, "Stale User", "stale@example.com", "male", "active"), cachedAt = 1L, firstSeenAt = 1L))
    )
    val repository = UserRepositoryImpl(GoRestApi(createHttpClient(successEngine())), local)

    repository.getUsers()

    assertTrue(local.getUsers().none { it.user.id == 99L })
}
```

### RED/GREEN — `TimeProvider` UTC offset bug

**Add new source set `domain/src/androidUnitTest/kotlin/com/userhub/domain/time/TimeProviderTest.kt`** (this source set doesn't exist yet; creating it is expected and is exactly what the documented command `./gradlew :domain:testDebugUnitTest --tests "..."` in CLAUDE.md is for):

```kotlin
package com.userhub.domain.time

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TimeProviderTest {

    private lateinit var originalZone: TimeZone

    @BeforeTest
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London")) // BST = UTC+1 in summer
    }

    @AfterTest
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `nowEpochMillis matches true wall-clock epoch millis regardless of local timezone offset`() {
        val before = System.currentTimeMillis()
        val now = TimeProvider.nowEpochMillis()
        val after = System.currentTimeMillis()

        assertTrue(now in before..after, "expected $now to be within [$before, $after]")
    }
}
```

Run: `./gradlew :domain:testDebugUnitTest --tests "com.userhub.domain.time.TimeProviderTest"`.

RED: with `Europe/London` forced to a summer date... note the test as written uses the *host machine's current date*, so it only reliably reproduces the bug when run during BST (roughly late March–late October). If you're implementing this outside that window, force a fixed instant instead — the important thing to prove is that `nowEpochMillis()` is currently `System.currentTimeMillis() + 3_600_000` during BST, which is provably outside the `[before, after]` window and fails the assertion.

### GREEN

```kotlin
// TimeProvider.kt
object TimeProvider {
    fun nowEpochMillis(): Long = systemEpochMillis()
}

internal expect fun systemEpochMillis(): Long
```

Delete `utcOffsetMillis()` (the `expect` declaration and both `actual` implementations in `TimeProvider.android.kt` / `TimeProvider.ios.kt`) — it's now unused. `formatRelativeTime` only ever does millisecond subtraction between two values from the same clock basis, so no timezone conversion belongs here at all.

### RED/GREEN — mapper

**New file `composeApp/src/commonTest/kotlin/com/userhub/presentation/UserUiMapperTest.kt`:**

```kotlin
package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserUiMapperTest {

    private val userA = UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active")
    private val userB = UserDto(2, "Alan Turing", "alan@example.com", "male", "active")

    @Test
    fun `each row's label reflects its own real added-at time, not its position in the list`() {
        val now = com.userhub.domain.time.TimeProvider.nowEpochMillis()
        val result = UsersResult.Success(
            users = listOf(userA, userB),
            addedAtMillis = mapOf(
                1L to now - 60 * 60_000, // 1 hour ago
                2L to now                // just now
            ),
            lastSyncMillis = null
        )

        val uiModels = result.toUiModels()

        assertEquals("1 hour ago", uiModels.first { it.id == 1L }.createdLabel)
        assertEquals("Just now", uiModels.first { it.id == 2L }.createdLabel)
        assertNotEquals(uiModels[0].createdLabel, uiModels[1].createdLabel)
    }

    @Test
    fun `a user missing from the added-at map still gets a label instead of crashing`() {
        val result = UsersResult.Success(users = listOf(userA), lastSyncMillis = null)

        val uiModels = result.toUiModels()

        assertEquals("Just now", uiModels.single().createdLabel)
    }
}
```

RED: this fails to compile today — `toUiModels()` is currently an extension on `List<UserDto>`, not on `UsersResult.Success`, and there is no `addedAtMillis` to read.

**`UserUiMapper.kt`:**

```kotlin
package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import com.userhub.domain.time.TimeProvider
import com.userhub.domain.time.formatRelativeTime

fun UsersResult.Success.toUiModels(): List<UserUiModel> {
    val now = TimeProvider.nowEpochMillis()
    return users.map { user ->
        val addedAt = addedAtMillis[user.id] ?: now
        UserUiModel(user = user, createdLabel = formatRelativeTime(addedAt, now))
    }
}

fun UserDto.toNewUiModel(): UserUiModel {
    val now = TimeProvider.nowEpochMillis()
    return UserUiModel(user = this, createdLabel = formatRelativeTime(now, now))
}
```

Delete the `FEED_STAGGER_MILLIS` constant.

**`UserFeedViewModel.kt`** — update the one call site in `load()`:

```kotlin
is UsersResult.Success -> {
    val lastSyncLabel = result.lastSyncMillis?.let {
        "Offline — last updated ${formatRelativeTime(it, TimeProvider.nowEpochMillis())}"
    }
    _state.value = UserFeedUiState.Content(result.toUiModels(), lastSyncLabel)
}
```

### Verify

- `./gradlew :domain:test :data:test :composeApp:test`
- Manual check on device/simulator: fresh install, load feed, force-quit and relaunch a few minutes later — rows should now show elapsed time that actually grows between launches, not a static 5/10/15-minute staircase. Add a new user — it should show "Just now" without any of the *other* rows' times jumping.
- Known coverage gap worth flagging in the PR: `SqlDelightUserLocalDataSource` itself has no direct test (the project currently has no JVM SQLite test driver wired up for the `data` module — `data/build.gradle.kts` only targets `androidTarget`/`iosArm64`/`iosSimulatorArm64`, with `commonTest` covering the repository via the hand-written `FakeLocalDataSource`, same as the pre-existing tests in this file did). The upsert logic above is exercised indirectly through `UserRepositoryImplTest` via the fake. Adding a real SQLDelight-backed test would require adding a JVM target + `app.cash.sqldelight:sqlite-driver` test dependency — worth a follow-up ticket, out of scope here.

---

## Workstream 3 — Delete only happens once the undo window elapses

**Module:** `composeApp`
**Files:**
- `composeApp/src/commonMain/kotlin/com/userhub/presentation/UserFeedViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/userhub/ui/DirectoryScreen.kt`
- `composeApp/src/commonTest/kotlin/com/userhub/presentation/UserFeedViewModelTest.kt`
- `composeApp/src/commonTest/kotlin/com/userhub/presentation/FakeUserRepository.kt`

### Problem

AC requires a snackbar-driven undo: the delete should only actually happen once the undo window has closed, not be fired immediately with undo attempting to claw it back. Confirmed broken by manual test (PROCESS_LOG.md): delete a user, tap undo, restart the app — the user does not come back, because "undo" today only ever mutated in-memory UI state, never the network/cache.

### Root cause

`UserFeedViewModel.kt`:

```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun onDeleteConfirmed(user: UserUiModel) {
    val content = _state.value as? UserFeedUiState.Content ?: return
    val index = content.users.indexOf(user)
    if (index < 0) return
    pendingUndo = index to user
    _state.value = content.copy(users = content.users - user)
    // run outside the screen scope so the delete always completes
    GlobalScope.launch {
        runCatching { deleteUser(user.id) }
    }
}

fun onUndo() {
    val (index, user) = pendingUndo ?: return
    val content = _state.value as? UserFeedUiState.Content ?: return
    val users = content.users.toMutableList()
    users.add(index.coerceAtMost(users.size), user)
    _state.value = content.copy(users = users)
    pendingUndo = null
}
```

The network delete fires the instant the user confirms, in an un-cancellable `GlobalScope` coroutine, before the undo snackbar has even appeared. `onUndo()` only reinserts the row into the in-memory list — it never re-adds the user server-side, and by the time undo is tapped the delete has typically already gone through, so there's nothing coherent to undo even in principle (re-adding would re-issue a new id, hit duplicate/relationship errors, etc. — the log calls this "inherently broken as designed," correctly).

The fix isn't to make undo smarter — it's to not delete until there's nothing left to undo. `DirectoryScreen.kt` already has exactly the signal needed for this: `SnackbarHostState.showSnackbar(...)` suspends until the snackbar is dismissed, times out, or its action is tapped, returning a `SnackbarResult`. That result *is* "the undo window has elapsed" vs. "undo was tapped in time" — it's just not being used for anything except triggering `onUndo()`.

### Design for the fix

- `onDeleteConfirmed(user)` still removes the row from UI state immediately (optimistic UI, unchanged), but no longer touches the network.
- A new `onUndoWindowElapsed()` sends the actual network delete, called by the screen when `showSnackbar` returns anything other than `SnackbarResult.ActionPerformed`.
- `onUndo()` just restores the row and clears the pending state — it never needs to touch the repository, because the delete was never sent.
- `GlobalScope` and `@DelicateCoroutinesApi` are removed entirely; the (now-deferred) delete runs in `viewModelScope`, which is the normal, non-delicate choice — there's no longer a reason to outlive the screen, since the delete is only sent after the undo window has already closed while the screen was presumably still around to observe it. (If the screen really can be torn down mid-window in this app's navigation model, that's a separate, smaller follow-up — not needed to satisfy the AC here.)

### RED

`FakeUserRepository.kt` (composeApp test) doesn't currently record delete calls at all — add that first, mirroring the equivalent fake already in `domain/src/commonTest/kotlin/com/userhub/domain/FakeUserRepository.kt`:

```kotlin
var deletedIds = mutableListOf<Long>()
    private set

override suspend fun deleteUser(id: Long): HttpResponse {
    deletedIds.add(id)
    return respondWith(HttpStatusCode.NoContent, "")
}
```

Add to `UserFeedViewModelTest.kt`:

```kotlin
@Test
fun `deleting does not call the repository until the undo window elapses`() = runTest {
    val repository = FakeUserRepository(users)
    val viewModel = viewModel(repository)
    val target = (viewModel.state.value as UserFeedUiState.Content).users.first()

    viewModel.onDeleteConfirmed(target)

    assertTrue(repository.deletedIds.isEmpty())
}

@Test
fun `the delete is sent once the undo window elapses`() = runTest {
    val repository = FakeUserRepository(users)
    val viewModel = viewModel(repository)
    val target = (viewModel.state.value as UserFeedUiState.Content).users.first()
    viewModel.onDeleteConfirmed(target)

    viewModel.onUndoWindowElapsed()

    assertEquals(listOf(target.id), repository.deletedIds)
}

@Test
fun `undo prevents the delete from ever being sent`() = runTest {
    val repository = FakeUserRepository(users)
    val viewModel = viewModel(repository)
    val target = (viewModel.state.value as UserFeedUiState.Content).users.first()
    viewModel.onDeleteConfirmed(target)

    viewModel.onUndo()

    assertTrue(repository.deletedIds.isEmpty())
}
```

RED: the first test currently fails (the existing implementation calls `deleteUser` immediately inside `onDeleteConfirmed`, via `GlobalScope` — with `UnconfinedTestDispatcher` this runs synchronously enough to be observed as a non-empty `deletedIds` before the assertion). The other two fail to compile — `onUndoWindowElapsed()` doesn't exist yet.

The two existing tests `delete removes the user from the list` and `undo restores the deleted user` must keep passing unchanged — they assert on UI state, not network calls, and that behaviour isn't changing.

### GREEN

`UserFeedViewModel.kt`:

```kotlin
fun onDeleteConfirmed(user: UserUiModel) {
    val content = _state.value as? UserFeedUiState.Content ?: return
    val index = content.users.indexOf(user)
    if (index < 0) return
    pendingUndo = index to user
    _state.value = content.copy(users = content.users - user)
}

fun onUndoWindowElapsed() {
    val (_, user) = pendingUndo ?: return
    pendingUndo = null
    viewModelScope.launch {
        runCatching { deleteUser(user.id) }
    }
}

fun onUndo() {
    val (index, user) = pendingUndo ?: return
    pendingUndo = null
    val content = _state.value as? UserFeedUiState.Content ?: return
    val users = content.users.toMutableList()
    users.add(index.coerceAtMost(users.size), user)
    _state.value = content.copy(users = users)
}
```

Remove the now-unused `import kotlinx.coroutines.DelicateCoroutinesApi` and `import kotlinx.coroutines.GlobalScope`.

`DirectoryScreen.kt` — wire the new callback into the existing snackbar result handling:

```kotlin
confirmButton = {
    TextButton(
        onClick = {
            pendingDelete = null
            feedViewModel.onDeleteConfirmed(user)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "User deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    feedViewModel.onUndo()
                } else {
                    feedViewModel.onUndoWindowElapsed()
                }
            }
        }
    ) { Text("Delete") }
}
```

### Verify

- `./gradlew :composeApp:test`
- Manual: delete a user, let the snackbar time out without tapping Undo, restart the app — user should be gone (network delete went through). Delete a user, tap Undo before the snackbar closes, restart the app — user should still be present (network delete was never sent). This is the exact repro steps from PROCESS_LOG.md, now expected to behave correctly in both directions.

---

## Workstream 4 — Detail panel loses/misrepresents the selected user

**Module:** `composeApp`
**Files:**
- `composeApp/src/commonMain/kotlin/com/userhub/ui/DirectoryScreen.kt`
- `composeApp/src/commonMain/kotlin/com/userhub/presentation/UserUiMapper.kt` (or a new small file alongside it — either is fine)
- `composeApp/src/commonTest/kotlin/com/userhub/presentation/SelectedUserResolverTest.kt` (new)

This is deliberately the smallest of the four — a targeted fix, not the broader MVI/MVVM restructure covered separately (see the "Structure & State Review" artifact). It happens to fix two symptoms already logged as if they were separate bugs, because both trace back to the same line: `DirectoryScreen` holds the selection as `remember { mutableStateOf<UserUiModel?>(null) }`.

### Problem

Two symptoms, one cause:
- **Stale on delete** (PROCESS_LOG.md, tablet screenshot): deleting the currently-selected user leaves the detail panel showing their now-deleted details until a different row is tapped.
- **Lost on rotation** (PROCESS_LOG.md): rotating the device drops the selection entirely, silently falling back to the first item in the list.

### Root cause

`remember { mutableStateOf<UserUiModel?>(null) }` is (a) not saved across a configuration change — `remember` alone doesn't survive rotation, only `rememberSaveable` does — and (b) holding a reference to a specific `UserUiModel` object rather than an id looked up against the current list, so when that object is removed from `current.users` by a delete, nothing re-derives the selection; the stale object is simply still sitting in the `remember` slot.

### Design for the fix

Swap the stored value from the full object to just the selected user's `id: Long` (trivially saveable, unlike a custom data class), kept in `rememberSaveable`. Derive the actual displayed user fresh from the current list on every recomposition via a small pure function, rather than trusting a cached object reference. One change, both symptoms:

```kotlin
fun resolveSelectedUser(users: List<UserUiModel>, selectedId: Long?): UserUiModel? =
    users.firstOrNull { it.id == selectedId } ?: users.firstOrNull()
```

- Rotation: `rememberSaveable` persists the `Long` id across the configuration change; `resolveSelectedUser` re-looks-up the matching user once the screen recomposes with the restored id.
- Delete: the next recomposition after a delete calls `resolveSelectedUser` with the same `selectedId` against the now-shorter list; if that id is gone, it falls back to `users.firstOrNull()` (preserving today's "always show something if the list isn't empty" behaviour) instead of holding on to the deleted object.

This is a pure function with no Compose/Android dependency, so it's unit-testable with the same `kotlin.test` style already used everywhere else in this project — no Compose UI test framework needs to be added for this fix (there currently isn't one in the project at all; introducing one is a bigger, separate decision, out of scope here).

### RED

**New file `composeApp/src/commonTest/kotlin/com/userhub/presentation/SelectedUserResolverTest.kt`:**

```kotlin
package com.userhub.presentation

import com.userhub.data.remote.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectedUserResolverTest {

    private val userA = UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active").toNewUiModel()
    private val userB = UserDto(2, "Alan Turing", "alan@example.com", "male", "active").toNewUiModel()

    @Test
    fun `returns the user matching the selected id`() {
        assertEquals(userB, resolveSelectedUser(listOf(userA, userB), selectedId = userB.id))
    }

    @Test
    fun `falls back to the first user when the selected id is no longer in the list`() {
        // simulates: userB was selected, then deleted
        assertEquals(userA, resolveSelectedUser(listOf(userA), selectedId = userB.id))
    }

    @Test
    fun `returns null when the list is empty`() {
        assertNull(resolveSelectedUser(emptyList(), selectedId = userA.id))
    }
}
```

RED: fails to compile — `resolveSelectedUser` doesn't exist yet.

### GREEN

Add to `UserUiMapper.kt`:

```kotlin
fun resolveSelectedUser(users: List<UserUiModel>, selectedId: Long?): UserUiModel? =
    users.firstOrNull { it.id == selectedId } ?: users.firstOrNull()
```

`DirectoryScreen.kt` — replace the object-based selection state with an id-based one, saved across rotation, and resolve the displayed user through the new function:

```kotlin
var selectedUserId by rememberSaveable { mutableStateOf<Long?>(null) }
...
is UserFeedUiState.Content -> BoxWithConstraints {
    val selected = resolveSelectedUser(current.users, selectedUserId)
    if (maxWidth >= 700.dp) {
        Row {
            UserList(
                users = current.users,
                lastSyncLabel = current.lastSyncLabel,
                onClick = { selectedUserId = it.id },
                onLongPress = { pendingDelete = it },
                modifier = Modifier.weight(2f)
            )
            DetailPanel(user = selected, modifier = Modifier.weight(3f))
        }
    } else {
        UserList(
            users = current.users,
            lastSyncLabel = current.lastSyncLabel,
            onClick = { selectedUserId = it.id },
            onLongPress = { pendingDelete = it },
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

(`rememberSaveable` requires `androidx.compose.runtime.saveable.rememberSaveable` — add the import.)

### Verify

- `./gradlew :composeApp:test`
- Manual on tablet: select a user, rotate the device — selection should persist. Select a user, delete them — detail panel should switch to another user (or clear, if none remain) instead of showing stale data. Repeat both from PROCESS_LOG.md's original repro steps.

---

## Summary table

| # | Area | Modules touched | Depends on |
|---|------|------------------|------------|
| 1 | Empty-cache crash | `data` | — |
| 2 | Real "added ago" time (+ TimeProvider UTC offset bug) | `data`, `domain`, `composeApp` | 1 (same catch block) |
| 3 | Delete/undo timing | `composeApp` | — (independent of 1 and 2 at the logic level; touches `UserFeedViewModel.kt` alongside 2 but different methods) |
| 4 | Detail panel selection (rotation + delete) | `composeApp` | — (independent; touches `DirectoryScreen.kt`, doesn't overlap with 3's `UserFeedViewModel.kt` changes) |

Workstream 4 is intentionally the smallest and most isolated — safe to hand to a fifth session, or to do last as a quick wrap-up, without waiting on the other three.
