# Process Log

A running, append-only record of the process, findings, and decisions made while working on this tech task. Entries are chronological (oldest first). Entries are never edited or removed after the fact — corrections are added as new entries.

Each entry is attributed:
- **Note (dictated)** — the author's own words, lightly cleaned up for clarity, meaning unchanged.
- **Action (assistant)** — things done, found, or decided by the assistant while helping.

A final `## Summary` section will be added at the end, once ready for submission, synthesizing the log as a whole.

---

## 2026-09-20 — Action (assistant)

Set up this log. Rules agreed with the author:
1. Append-only; new entries go at the bottom in chronological order; no edits/deletions of past entries.
2. Dictated notes are recorded under their own headings, distinct from assistant actions.
3. The log gets its own commits (e.g. "Update process log"), kept separate from code/config commits.
4. A `## Summary` section is added last, once the author is ready to submit, synthesizing the full log.

Context: local repo was forked from `sliide/mobile-engineering-tech-task` to `daydreamapps/mobile-engineering-tech-task`. `origin` now points at the fork, `upstream` points at the original. Branch `chore/build-config` was created and commit `27b31b7` ("Add JVM toolchain and test setup") was made on it, covering changes to `build.gradle.kts` and `settings.gradle.kts`.

---

## 2026-09-20 — Note (dictated)

For clarity and an accurate record: I received this tech task on 16 September 2026. Before reading or running the code myself, I immediately set off an AI-assisted code review as an initial scan of the repository — this is entirely accurate and reflects the order in which things happened. That review is captured in this artifact, which I'm including here and will make publicly viewable: https://claude.ai/code/artifact/80e12594-bd45-4fe7-9c31-2e184fb4ea82

After that, I was away with no further involvement in this task — it was my birthday — until today, 20 September 2026. Today marks the actual start of my active working time on this task.

---

## 2026-09-20 — Note (dictated)

Process note: this stage is initial scoping — getting a feel for the app as a whole (it's a new project to me, though not large or complex) before moving on to review the most recent code changes specifically.

I ran the app on both an iOS simulator and an Android simulator. Both compiled and operated correctly at a basic level. I captured the full runtime logging output from both and asked for a review of it for issues.

## 2026-09-20 — Action (assistant)

Reviewed the full Android and iOS runtime logs provided. Findings:

- **Confirmed**: the bearer token is logged in plaintext on every network request, on both platforms (`Authorization: Bearer ...` visible in full in the log output), consistent with the verbose `Logging(LogLevel.ALL)` configuration already noted in this project's CLAUDE.md.
- **Needs verification**: on Android, immediately after a DELETE request completed successfully (204), the log shows `InputDispatcher: Channel is unrecoverably broken and will be disposed!` followed by the app process restarting entirely (old PID → new PID). No crash stack trace (`FATAL EXCEPTION`/`AndroidRuntime`) was present in the captured log to confirm this was a crash rather than a manual reinstall/relaunch from the IDE — flagged to check with a full unfiltered logcat if it recurs.
- No issue found with cache/backend consistency — record counts and deleted-user visibility across subsequent fetches were consistent with expected full-resync behaviour.
- Minor observation: user creation succeeded server-side with unusual input (mixed-case email, non-standard domains) with no apparent client-side rejection — worth checking against the AddUser screen's validation claims later, not treated as a confirmed bug yet.

---

## 2026-09-20 — Note (dictated)

Initial sweep of the code, starting with `AddUserViewModel` (`composeApp/src/commonMain/kotlin/com/userhub/presentation/AddUserViewModel.kt`).

Two things jump out:

1. **Weak error handling.** Only the success case from the HTTP call is checked — there's no handling of the different failure status codes that could come back, and no inspection of response content on failure either (though that would be a secondary concern).

2. **Both failure paths collapse to the same message.** Whether the response comes back as a non-200 status, or the request fails to go out at all, the result is reported to the user as "no internet connection" in both cases. This stood out because offline-first is one of the project's headline features — the README does note that offline-first only applies to the fetch path, but it's still strange that the error messaging/handling on the write side shows no offline-first thinking at all. It raises a broader question about how genuinely "offline-first" the project is, which I'll keep testing as I go through more of the code.

---

## 2026-09-20 — Note (dictated)

`AddUserViewModelTest` (`composeApp/src/commonTest/kotlin/com/userhub/presentation/AddUserViewModelTest.kt`) mostly looks okay. It's simple and only covers two cases — submission and success — both using a fake repository, which is an interesting implementation choice, but it seems fine, returning real HTTP response values via a Ktor mock library.

There's no failure case tested at all — the only check performed is that the error state is cleared, never that an error is actually surfaced correctly.

Additionally, a couple of minor compiler warnings here: use of `Duration` at the delay's timing point, and a missing opt-in for an annotation. This isn't the first opt-in annotation point I've seen across the project. Low importance given this is a test exercise, but this kind of thing would definitely matter in a larger production project.
