## Progress Log

### 2026-04-18 - Final Polishing Pass

- Started a narrow post-audit polishing pass instead of a new refactor.
- Focus for this round:
  - make docs internally consistent after live write verification
  - add code-level guardrail for `views/history` limit `<= 50`
  - reinforce comments/tests around eventual consistency of delayed endpoints
  - keep the existing `local authoritative` architecture intact

### 2026-04-18 - Final Polishing Applied

- Added shared guardrail `MAX_USER_VIEWS_HISTORY_LIMIT = 50` in AniLiberty client code and routed history callers through it.
- Reinforced repository/sync comments that history/release/episode remote reads are eventual-consistency inputs, not immediate TV UI truth.
- Expanded contract coverage with:
  - history limit clamp assertion
  - `DELETE /accounts/users/me/views/timecodes` request-body assertion
- Removed stale documentation wording that still claimed live write calls were not executed after the release `6687` audit had already verified them.
- Cleaned the host audit narrative to keep only the final conclusion: the earlier suspicion was not reproduced and should be treated as a false alarm.

### 2026-04-18 - Inventory Start

- Re-checked the current `app-tv` / `data` watch-sync paths.
- Confirmed the main architectural mismatch: TV UI still performed direct remote reads for continue/history/player start while the requested model is `local authoritative`.
- Confirmed eager sync was awaited from `AuthRepository.loadUser()`, `signInOtp()`, and `signIn()`.
- Confirmed player startup still read remote continue episode and remote seek.
- Confirmed `WatchingViewModel` still depended on remote probing for row visibility.

### 2026-04-18 - Chosen Direction Sanity Check

- Locked the pass to a narrow but meaningful target: flip TV read-path to local-only, keep remote sync as side-effect infrastructure, avoid rewriting the full domain.
- Explicitly rejected the bad half-measure where UI still waits for remote "just a little bit". That would preserve the original failure mode under a nicer name.
- Kept asking "am I doing nonsense?" at each step: if a change reintroduced network dependency into TV render/start paths, it was not acceptable.

### 2026-04-18 - TV Read Path Flipped To Local

- Simplified `TvWatchingFacade` / `TvWatchingFacadeImpl` to expose only local availability projections.
- Removed remote probing from `WatchingViewModel`; row visibility now depends only on local continue/history presence.
- Rebuilt `WatchingContinueViewModel` around local `EpisodeAccess` + local history only.
- Rebuilt `WatchingHistoryViewModel` around local history only.
- Reworked `TvWatchSyncDecisions` into a local-authoritative helper with `pickLatestLocalProgressOrNull(...)`.
- Updated `DetailHeaderViewModel` to use only local progress for continue/play decisions.
- Updated `PlayerViewModel` and `PlayerPlaybackStart` so TV start/resume decisions no longer depend on remote continue/seek or `max(local, remote)`.

### 2026-04-18 - Sync Removed From Critical UX Paths

- Replaced awaited `userViewsSyncInteractor.syncIfNeeded()` calls in `AuthRepository` with debounced background scheduling via `scheduleSyncIfNeeded(...)`.
- Preserved sync as best-effort work, but made startup/auth/profile flows stop waiting on it.
- Kept player/runtime remote save as side-effect only; UI startup now resolves from local state immediately.

### 2026-04-18 - Lightweight Pending Upload Queue Added

- Added persisted `UserViewPendingUpload` records in `UserViewsSyncHolder` / `UserViewsSyncStorage`.
- Changed `UserViewsRepository.upsertEpisodeTimecode(...)` from direct network upsert to:
  1. persist pending local upload
  2. debounce background flush
- Made release-wide remote actions (`deleteAllTimecodesForRelease`, `markAllWatchedForRelease`) optimistic for local/cache state and asynchronous for network propagation.
- Left the repository's remote read APIs in place for sync/import use, but removed their influence on TV UI decisions.

### 2026-04-18 - Merge Rules Made Explicit

- `UserViewsSyncInteractor` now flushes pending uploads as part of background sync.
- Remote import now reads the pending-upload set before merge.
- If an episode has a dirty pending local upload, remote import does not overwrite the local entry.
- This intentionally prefers "the TV device just changed this" over a conflicting remote snapshot.

### 2026-04-18 - Swagger / Live API Audit

- Audited `data/aniliberty-api-v1-docs.json` against:
  - current `AniLibertyApi`
  - `UserViewsRepository`
  - `UserViewsSyncInteractor`
  - live read-only responses from the real AniLiberty server
- Verified live read-only responses for:
  - `/accounts/users/me/views/timecodes`
  - `/accounts/users/me/views/history`
  - `/anime/releases/episodes/{releaseEpisodeId}/timecode`
  - `/anime/releases/{idOrAlias}/episodes/timecodes`
- At this stage write-path was still deferred; it was later verified live on sandbox release `6687`.
- Found the most important real-vs-swagger mismatch:
  - global `/views/timecodes` is documented as object-array in swagger
  - live server actually returns tuple arrays
  - current tuple adapter was already the right defensive choice
- Found that live `/views/history` does not meaningfully benefit from the current `exclude` fields preset, and `include` usage can even drop nested release data.
- Based on that, simplified history calls in `UserViewsRepository` and `UserViewsSyncInteractor` to `fields = null`.
- Added mock contract coverage with live-like fixtures so this knowledge is not trapped only in the report.
- Compared `https://api.anilibria.app/api/v1` vs `https://aniliberty.top/api/v1` on release/timecode endpoints.
- Re-checked the earlier suspicious host result and did not reproduce any auth leak:
  - public `GET /anime/releases/6687` matches on both hosts
  - protected release-scoped timecode endpoints returned `403` without auth on both hosts
- Corrected the audit conclusion accordingly: the earlier host auth suspicion should be treated as a false alarm.

### 2026-04-18 - Live POST / DELETE Verification On Release 6687

- Used release `6687` as write sandbox and verified the real write cycle with the provided token.
- Pre-state was clean for the tested episode ids in global timecodes, release-scoped timecodes, and history.
- `POST /accounts/users/me/views/timecodes` with a 2-item batch (`partial` + `watched`) returned `200` with empty body.
- Global `/accounts/users/me/views/timecodes` reflected the write immediately.
- Release-scoped `/anime/releases/{id}/episodes/timecodes`, episode `/anime/releases/episodes/{id}/timecode`, and `/accounts/users/me/views/history` converged after about 6 polling attempts with 5-second interval, i.e. roughly 25-30 seconds.
- `DELETE /accounts/users/me/views/timecodes` returned `200` with empty body.
- Global timecodes cleared immediately again.
- Release-scoped timecodes, episode timecode, and history cleared after the same ~25-30 second window.
- This strongly supports the architectural choice already made in code:
  - write to global account timecodes
  - do not use release/episode/history reads as immediate authoritative runtime feedback
- Also confirmed live server validation rule:
  - `/accounts/users/me/views/history` rejects `limit > 50` with `422`

### 2026-04-18 - Tests Updated

- Reworked TV watch-sync tests to assert local-authoritative behavior instead of `remote first`.
- Updated player-start tests to validate local-only resume behavior.
- Updated TV facade tests to validate local availability projections.
- Added/updated merge-guard tests so pending local uploads beat remote import during reconciliation.

### 2026-04-18 - Verification

- `./gradlew.bat :app-tv:compileDebugKotlin :app-tv:compileReleaseKotlin --console=plain`
- `./gradlew.bat :app-tv:testDebugUnitTest --tests '*PlayerViewModelTest' --tests '*PlayerPlaybackStartTest' --tests '*WatchingRowsSeparationTest' --tests '*TvWatchSyncDecisionsTest' --console=plain`
- `./gradlew.bat :data:testDebugUnitTest --tests '*UserViewsSyncInteractor*' --tests '*TvPlayerFacadeImplTest' --tests '*TvWatchingFacadeImplTest' --console=plain`
- `./gradlew.bat :app-tv:detekt --console=plain`
- `./gradlew.bat :app-tv:lintDebug --console=plain`

Results:

- `app-tv` compile/test/detekt/lint path is green for this change set.
- Targeted `data` tests covering the changed sync/facade areas are green.
- Targeted API contract tests for live-like watch-sync payloads are green.
- Live POST / DELETE audit on release `6687` succeeded end-to-end.
- `:data:detekt` is not available in this build setup, so there was no module detekt task to run there.
- A wider earlier `:data:testDebugUnitTest` run still showed pre-existing unrelated failure `AppCookieJarTest > loadForRequest_doesNotReturnCookieForWrongPath`; that was not introduced by this watch-sync pass.

### 2026-04-18 - Residual Limits Kept Explicit

- Did not build a full outbox/state-machine/worker system; this pass adds a lightweight persisted pending-upload queue only.
- Did not partition local history/progress by account in this pass; device-local state semantics remain shared and should be considered a known tradeoff on logout/login.
- Did not remove every remote repository API, because some are still used by sync/import and keeping them avoided needless churn.
