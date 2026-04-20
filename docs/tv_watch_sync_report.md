## TV Watch Sync Report

### What Was

Before this pass, TV watch-sync mixed two different models:

- local `EpisodeAccess` / local history for immediate device state
- direct AniLiberty reads in TV UI/read paths
- global reconcile logic in `UserViewsSyncInteractor`

That produced several wrong incentives:

- `WatchingContinueViewModel` and `WatchingHistoryViewModel` were effectively `remote first, local fallback`
- `WatchingViewModel` depended on remote availability probing to decide row visibility
- `DetailHeaderViewModel` and `PlayerViewModel` used remote history/seek on the user-facing startup path
- `PlayerPlaybackStart` hid conflicts behind `max(localSeek, remoteSeek)`
- `AuthRepository` awaited watch sync after auth/profile, so startup/auth UX could wait on background replica work

### What Became

This pass flips the model to `local authoritative` for TV:

- TV UI now reads local projections only
- remote AniLiberty is treated as async replica / cross-device restore source
- remote import updates local first, then UI reacts to the changed local state
- local mutations enqueue best-effort background remote upload
- dirty local progress protects itself from remote overwrite during import

In practice:

- watching rows render from local availability only
- continue/history cards are local-only projections
- detail continue/play decisions are local-first
- player start/resume uses local progress only on the critical UX path
- auth/startup no longer waits for watch sync

### Key Changed Files

Read path / UI:

- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingContinueViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingHistoryViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/TvWatchSyncDecisions.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/PlayerViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/PlayerPlaybackStart.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/details/DetailHeaderViewModel.kt`

TV/data contracts:

- `data/src/main/java/ru/radiationx/data/contracts/tv/TvWatchingFacade.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/impl/TvWatchingFacadeImpl.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/TvPlayerFacade.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/impl/TvPlayerFacadeImpl.kt`
- `data/src/main/java/ru/radiationx/data/interactors/tv/TvDetailHeaderUseCase.kt`

Sync/storage:

- `data/src/main/java/ru/radiationx/data/repository/AuthRepository.kt`
- `data/src/main/java/ru/radiationx/data/repository/UserViewsRepository.kt`
- `data/src/main/java/ru/radiationx/data/interactors/UserViewsSyncInteractor.kt`
- `data/src/main/java/ru/radiationx/data/datasource/holders/UserViewsSyncHolder.kt`
- `data/src/main/java/ru/radiationx/data/datasource/storage/UserViewsSyncStorage.kt`
- `data/src/main/java/ru/radiationx/data/entity/domain/watching/UserViewPendingUpload.kt`

Tests:

- updated TV/player/watch-sync tests in `app-tv` and `data` to reflect local-authoritative semantics

### Decisions Taken

1. TV read-path is local-only.
2. Remote availability is no longer a prerequisite for showing TV watching rows.
3. Remote import is allowed to improve local state, but not to directly drive UI.
4. Local progress writes create persisted pending-upload items instead of immediate blocking remote upserts.
5. Pending local uploads make local state stronger than remote import for the same episode.
6. Release-wide remote actions stay best-effort side-effects, but no longer need to block immediate local UX.

### Swagger / API Audit

#### What Was Found

- `data/aniliberty-api-v1-docs.json` already documents the main watch endpoints:
  - `GET/POST/DELETE /accounts/users/me/views/timecodes`
  - `GET /accounts/users/me/views/history`
  - `GET /anime/releases/{idOrAlias}/episodes/timecodes`
  - `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
- The codebase already had client methods for all of them in `AniLibertyApi`.
- The important question was not "do we have methods?", but "does the live payload actually match swagger and our DTO assumptions?"

#### What Was Already Transferred Correctly

- Global timecodes parsing was already resilient because `AniLibertyTupleAdapterFactory` supports both tuple and object payloads.
- History DTOs were already nullable enough to survive missing top-level `release_id` / `created_at`.
- History mapping in `UserViewsRepository` and `UserViewsSyncInteractor` already had nested release fallbacks, which turned out to be necessary on live payloads.
- Episode/release timecode parsing was already tolerant of object payloads with extra fields (`id`, `user_id`, `updated_at`).

#### Real Server Validation

Live checks were run against the real server for:

- `GET /accounts/users/me/views/timecodes`
- `GET /accounts/users/me/views/history?page=1&limit=...`
- `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
- `GET /anime/releases/{idOrAlias}/episodes/timecodes`
- public release probing on `GET /anime/releases/6687`
- host comparison between `https://aniliberty.top/api/v1` and `https://api.anilibria.app/api/v1`
- live write verification on release `6687` for:
  - `POST /accounts/users/me/views/timecodes`
  - `DELETE /accounts/users/me/views/timecodes`

Key live findings:

- `GET /accounts/users/me/views/timecodes`
  - swagger shape: array of objects
  - real shape: array of tuples like `["release_episode_id", time, is_watched]`
  - impact: client must not rely on swagger here; the existing tuple adapter is required

- `GET /accounts/users/me/views/history`
  - real payload included `id`, `user_id`, `updated_at`, `release_episode_id`, nested `release_episode`, nested `release`
  - sampled live payload did not include top-level `release_id` or `created_at`
  - `time` appeared as both integer-like and fractional numbers across sampled items
  - impact: nullable DTO fields and nested release fallbacks are the right strategy

- `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
  - real shape: object with extra fields (`id`, `user_id`, `updated_at`) plus `time`, `is_watched`, `release_episode_id`
  - impact: current object parsing is fine

- `GET /anime/releases/{idOrAlias}/episodes/timecodes`
  - real shape: array of objects with extra fields (`id`, `user_id`, `updated_at`) plus `time`, `is_watched`, `release_episode_id`
  - impact: current parsing is fine

- Host parity re-check
  - `GET /anime/releases/6687` matched across `api.anilibria.app` and `aniliberty.top`
  - unauthenticated `GET /anime/releases/{idOrAlias}/episodes/timecodes` and `GET /anime/releases/episodes/{releaseEpisodeId}/timecode` returned `403` on both hosts during re-check
  - earlier suspicion of host-level auth divergence was not reproduced and should be treated as a false alarm

- Live write-path behavior on release `6687`
  - `POST /accounts/users/me/views/timecodes` accepted a 2-item batch (`partial` + `watched`) and returned `200` with empty body
  - global `GET /accounts/users/me/views/timecodes` reflected the new state immediately
  - `GET /anime/releases/{idOrAlias}/episodes/timecodes`
  - `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
  - `GET /accounts/users/me/views/history`
    all lagged by roughly 25-30 seconds before reflecting the write
  - `DELETE /accounts/users/me/views/timecodes` returned `200` with empty body
  - global timecodes cleared immediately
  - release-scoped timecodes, episode timecode, and history cleared after the same ~25-30 second lag
  - impact: these release/episode/history endpoints are eventual-consistency reads and are unsuitable as immediate UI truth on TV

- Live history pagination constraint
  - `/accounts/users/me/views/history?limit=100` returned `422`
  - server message says `limit` must not exceed `50`
  - client now clamps this through a shared `MAX_USER_VIEWS_HISTORY_LIMIT = 50` guardrail in the AniLiberty API/repository path

- `since` on `/accounts/users/me/views/timecodes`
  - verified to work on the live server
  - future timestamp returned an empty set

#### What Helped Simplify The Implementation

- The audit showed that `include` / `exclude` on `views/history` are not a reliable optimization for the watch-sync path.
- In live probing, the current `exclude` set did not meaningfully slim the payload, and `include` usage could even remove nested `release` data.
- Because of that, `UserViewsRepository` and `UserViewsSyncInteractor` were simplified to use `fields = null` for history reads.
- Added mock contract fixtures/tests for live-like payloads so future refactors do not quietly regress to swagger-only assumptions.
- Added request-shape guardrails in tests for:
  - history limit clamping to `50`
  - `DELETE /accounts/users/me/views/timecodes` body shape
- Live write verification reinforced the local-authoritative model:
  - global account timecodes are the only immediate authoritative remote write confirmation
  - release/episode/history endpoints are delayed reflections and should stay out of the TV critical path

#### What Was Not Transferred / Not Included

- Did not switch watch-sync import to `since`-based incremental global timecodes.
  - reason: global timecodes still lack enough release context to remove the mapping/reconcile step

- Did not move TV startup/read UX onto `episode/timecode` or `release/episodes/timecodes`.
  - reason: that would reintroduce remote dependency into local-authoritative TV UX

- Did not add new API calls just because swagger exposes them.
  - reason: the goal was a simpler, safer local-first model, not endpoint proliferation

- Did not switch the client base URL to `api.anilibria.app`.
  - reason: this pass did not need an infrastructure migration, and host switching would add churn without improving the watch-sync model

### What I Intentionally Did Not Do

- did not rewrite the whole watch-domain into a full event/outbox/state-machine architecture
- did not add workers/heavy startup sync infrastructure
- did not partition local watch history by account in this pass
- did not remove every remote repository API, because sync/import still uses them and deleting them would create unnecessary churn
- did not reintroduce any remote dependency into the TV render/start path "for convenience"

### New Flow

#### Startup

- app startup / auth profile load updates local auth/profile state
- `AuthRepository` schedules watch sync in background instead of awaiting it
- if network is absent, TV still renders from existing local history/progress

#### Watching Screens

- `WatchingViewModel` shows rows based on local availability only
- `WatchingContinueViewModel` builds continue cards from local `EpisodeAccess` + local history
- `WatchingHistoryViewModel` builds history cards from local history only
- remote-only data becomes visible only after background import merges it into local storage

#### Detail Continue

- continue/play CTA chooses the latest local episode progress only
- remote history is no longer consulted directly on button click

#### Player Start

- explicit episode arg wins first
- otherwise player resumes from latest local continue episode if it exists
- resume position is derived from local seek only
- `max(local, remote)` conflict hiding was removed

#### Playback Save

- local episode progress is still saved immediately
- remote save remains best-effort and async through the repository queue path
- playback UX no longer waits for AniLiberty responses

#### Background Upload / Import

- local progress changes create/update persisted `UserViewPendingUpload` entries
- repository debounces and flushes pending uploads in background batches
- sync interactor also flushes pending uploads during scheduled sync
- remote history/timecodes import merges into local storage
- if local entry has a pending upload, remote import does not overwrite it

### Conflict Rules

- dirty local state beats remote import
- local is primary for TV resume/start decisions
- remote is fallback only when local projection is absent
- `watched` is not blindly stronger than partial progress unless the merge rules consider it valid in context
- the old `max(...)` resume rule is gone because it masked conflicts instead of resolving them

### Verification

Green:

- `./gradlew.bat :app-tv:compileDebugKotlin :app-tv:compileReleaseKotlin --console=plain`
- `./gradlew.bat :app-tv:testDebugUnitTest --tests '*PlayerViewModelTest' --tests '*PlayerPlaybackStartTest' --tests '*WatchingRowsSeparationTest' --tests '*TvWatchSyncDecisionsTest' --console=plain`
- `./gradlew.bat :data:testDebugUnitTest --tests '*UserViewsSyncInteractor*' --tests '*TvPlayerFacadeImplTest' --tests '*TvWatchingFacadeImplTest' --console=plain`
- `./gradlew.bat :app-tv:detekt --console=plain`
- `./gradlew.bat :app-tv:lintDebug --console=plain`

Checked but not available / not fully green globally:

- `./gradlew.bat :data:detekt --console=plain` cannot run because this module has no `detekt` task in the current build setup
- an earlier broad `./gradlew.bat :data:testDebugUnitTest --console=plain` run still had unrelated pre-existing failure `AppCookieJarTest > loadForRequest_doesNotReturnCookieForWrongPath`

### Risks And Remaining Limits

- local watch state is still device-local rather than user-scoped, so logout/login account switching remains a semantic limitation
- pending uploads are a lightweight queue, not a full durable sync state machine with retries/backoff/telemetry
- remote import still lives inside the existing `UserViewsSyncInteractor`, so the sync domain is improved but not fully re-designed
- release-wide remote actions are best-effort and intentionally do not guarantee immediate server convergence before UI returns
