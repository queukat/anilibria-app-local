## Context Anchor

TV watch-sync needs to move from a mixed `remote first + local fallback` model to a `local authoritative` model:

- local TV progress/history is the source of truth for UI/UX
- remote AniLiberty is an async replica for cross-device restore only
- TV UI must read local projections only
- remote import updates local first, then UI reacts to local changes
- remote upload is best-effort background work and must not block startup/auth/screen/player flows

## Initial Scheme (Before Refactor)

At the start of this refactor, the codebase had three active layers:

1. local `EpisodeAccess` / local history for immediate state
2. direct runtime reads from AniLiberty history/timecodes in TV UI paths
3. a global reconciler in `UserViewsSyncInteractor`

Initial `remote first` points:

- `WatchingContinueViewModel` loads AniLiberty history first and falls back to local
- `WatchingHistoryViewModel` loads AniLiberty history first and falls back to local
- `WatchingViewModel` probes remote availability to decide row visibility
- `DetailHeaderViewModel` uses remote history as continue/play fallback
- `PlayerViewModel` uses remote continue episode + remote seek on startup
- `PlayerPlaybackStart` resolves resume by `max(localSeek, remoteSeek)`
- `AuthRepository` eagerly awaits `syncIfNeeded()` after auth/profile flows

## Target Scheme

The target model for this pass:

1. UI reads local only
2. remote import runs in background and merges into local
3. remote upload is triggered by local mutations, but executed asynchronously
4. import and upload are decoupled from critical UI paths
5. merge rules are explicit and biased toward local TV state

Translated into concrete rules:

- `Watching` rows depend only on local availability
- `Continue`, `History`, detail continue CTA, player episode selection and player seek all read local state only
- remote history/timecodes never directly decide what TV renders right now
- local progress changes enqueue async remote upload work
- remote import skips overwriting dirty local state

## Main Change Surface

Primary files:

- `data/src/main/java/ru/radiationx/data/interactors/UserViewsSyncInteractor.kt`
- `data/src/main/java/ru/radiationx/data/repository/UserViewsRepository.kt`
- `data/src/main/java/ru/radiationx/data/repository/AuthRepository.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/impl/TvWatchingFacadeImpl.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingContinueViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingHistoryViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/PlayerViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/PlayerPlaybackStart.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/details/DetailHeaderViewModel.kt`

Likely supporting files:

- `data/src/main/java/ru/radiationx/data/contracts/tv/TvWatchingFacade.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/TvPlayerFacade.kt`
- `data/src/main/java/ru/radiationx/data/contracts/tv/impl/TvPlayerFacadeImpl.kt`
- `data/src/main/java/ru/radiationx/data/interactors/tv/TvDetailHeaderUseCase.kt`
- `data/src/main/java/ru/radiationx/data/datasource/holders/UserViewsSyncHolder.kt`
- `data/src/main/java/ru/radiationx/data/datasource/storage/UserViewsSyncStorage.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/details/other/DetailOtherViewModel.kt`
- related tests for watching/player/facades/sync interactor

## Planned Refactor Shape

1. Remove direct remote reads from TV watch/read paths.
2. Keep remote import in `UserViewsSyncInteractor`, but move auth-triggered execution to background scheduling.
3. Add a lightweight persisted sync metadata path for pending remote uploads from local episode changes.
4. Make dirty local episode state stronger than remote import during merge.
5. Keep explicit remote side-effects for actions like clear progress / mark watched, but make them best-effort and non-blocking for UX.

## Chosen Implementation For This Pass

The implementation should move in the right architectural direction without rewriting the whole watch-domain:

1. `Watching*` read-paths, detail continue CTA and player start decisions become local-only.
2. `TvWatchingFacade` stops exposing remote/auth-driven availability and becomes a local projection facade.
3. `UserViewsRepository` gains a lightweight persisted pending-upload queue instead of doing direct remote upsert on each local change.
4. `UserViewsSyncInteractor` still performs import/migration duties, but it now:
   - runs from deferred background scheduling after auth/profile
   - flushes pending uploads as side-effect work
   - protects dirty local entries from remote overwrite during merge
5. Release-wide remote actions remain explicit side-effects, but should no longer block the immediate local UX path.

## Swagger / API Audit

### Endpoints Already Present In Code

- `GET /accounts/users/me/views/timecodes`
  - implemented in `AniLibertyApi.getUserViewTimecodes(...)`
  - used by `UserViewsRepository` cache reads and `UserViewsSyncInteractor` import/upload conflict snapshot
- `POST /accounts/users/me/views/timecodes`
  - implemented in `AniLibertyApi.upsertUserViewTimecodes(...)`
  - used by pending upload flush and release-wide mark watched
- `DELETE /accounts/users/me/views/timecodes`
  - implemented in `AniLibertyApi.deleteUserViewTimecodes(...)`
  - used by clear progress paths
- `GET /accounts/users/me/views/history`
  - implemented in `AniLibertyApi.getUserViewsHistory(...)`
  - used by `UserViewsRepository` and `UserViewsSyncInteractor`
- `GET /anime/releases/{idOrAlias}/episodes/timecodes`
  - implemented in `AniLibertyApi.getReleaseEpisodesTimecodes(...)`
  - currently not used by the TV watch-sync path
- `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
  - implemented in `AniLibertyApi.getEpisodeTimecode(...)`
  - exposed via `UserViewsRepository.getEpisodeTimecode(...)`, but no longer used by TV startup/read UX

### Swagger Vs Real Payload Findings

Read-only live checks were performed against:

- `GET /accounts/users/me/views/timecodes`
- `GET /accounts/users/me/views/history?page=1&limit=...`
- `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`
- `GET /anime/releases/{idOrAlias}/episodes/timecodes`
- additional host comparison between `https://aniliberty.top/api/v1` and `https://api.anilibria.app/api/v1`
- live write verification on release `6687` using `POST` / `DELETE /accounts/users/me/views/timecodes`

Important findings:

- Swagger documents `/accounts/users/me/views/timecodes` as an array of objects.
- Real server returns tuple arrays like `["release_episode_id", time, is_watched]`.
- Current code already has the right resilience here via `AniLibertyTupleAdapterFactory`; this is a real contract mismatch, not a hypothetical one.

- Swagger for history allows `include` / `exclude`.
- Real server does not meaningfully slim the nested release payload for the watch-history case when using the current `exclude` set.
- Adding `include` in live probing could even drop nested `release` data entirely, which is unsafe for current history mapping/import logic.

- Real history payload in sampled live responses did not include top-level `release_id` or `created_at`, but did include:
  - `id`
  - `user_id`
  - `updated_at`
  - `release_episode`
  - nested `release`
- Current DTOs already tolerate this because `releaseId` / `createdAt` are nullable and the code falls back to nested release data.

- Real history `time` values are mixed integer-like and fractional numbers.
- Current Moshi parsing into `Double?` is fine for that.

- Real `/anime/releases/episodes/{releaseEpisodeId}/timecode` and `/anime/releases/{idOrAlias}/episodes/timecodes` return object/object-array payloads with extra fields like `id`, `user_id`, `updated_at`.
- Current tuple/object adapters already accept those shapes safely.

- Public release read endpoint parity:
  - `GET /anime/releases/6687` returned the same payload shape and byte size on both hosts
  - this means the hosts look interchangeable for public release reads

- Protected release-scoped timecode auth behavior:
  - on both `aniliberty.top` and `api.anilibria.app`, unauthenticated requests to `/anime/releases/{idOrAlias}/episodes/timecodes` and `/anime/releases/episodes/{releaseEpisodeId}/timecode` returned `403` on re-check
  - public release reads matched as well; the earlier host auth suspicion was not reproduced and should be treated as a false alarm

- Live write-path verification on release `6687`:
  - `POST /accounts/users/me/views/timecodes` accepted a batch with one partial progress item and one watched item and returned `200` with empty body
  - global `GET /accounts/users/me/views/timecodes` reflected both writes immediately
  - `GET /anime/releases/{idOrAlias}/episodes/timecodes`, `GET /anime/releases/episodes/{releaseEpisodeId}/timecode`, and `GET /accounts/users/me/views/history` lagged behind by roughly 25-30 seconds before showing the new state
  - `DELETE /accounts/users/me/views/timecodes` returned `200` with empty body
  - global timecodes cleared immediately, while release-scoped timecodes, episode timecode, and history again converged after roughly 25-30 seconds
  - this confirms that release/episode/history endpoints are eventual-consistency reads and should not drive immediate TV UX decisions

- Live history pagination constraint:
  - requesting `limit=100` on `/accounts/users/me/views/history` returned `422`
  - server error states that `limit` must not be greater than `50`
  - current client usage with smaller limits is safe

- Swagger `since` parameter on `/accounts/users/me/views/timecodes` works on the live server.
- It is still not enough by itself to simplify import logic because the global timecodes payload does not include enough release context to remove the local mapping/reconciliation step.

### What This Audit Changes In The Implementation

- `views/history` reads should use the unfiltered contract (`fields = null`) instead of the old `Suggestions` preset.
- Live API behavior shows that the old history `exclude` params do not buy useful slimming and can make assumptions about nested release data brittle.
- history reads must be clamped to `MAX_USER_VIEWS_HISTORY_LIMIT = 50`, because live AniLiberty returns `422` for larger values.
- Contract coverage should include live-like tuple/object shapes, not only the older object-only fixtures.

### Useful API Capabilities Not Included

- Incremental import via `since` on global timecodes:
  - not included because it still leaves the same `release_episode_id -> local episode` mapping problem
  - adding sync cursors/state here would expand scope without removing the core merge complexity

- Using release-wide or episode-specific timecode endpoints in TV UI/startup:
  - not included because that would reintroduce remote reads into the user-facing critical path
  - those endpoints are better kept as optional repository-level helpers, not TV UX drivers

- Switching the app host from `aniliberty.top` to `api.anilibria.app`:
  - not included because this pass is about sync semantics, not infrastructure migration
  - current audit did not find enough benefit to justify host migration churn

- Replacing history import with release-wide timecodes reads:
  - not included because it does not remove the need to discover the related release/episode mapping and does not materially simplify the current local-authoritative design

## Risks And Edge Cases

- imported remote-only history must still surface on TV after local storage is updated
- local dirty progress must not be overwritten by remote import racing in parallel
- replay/reset flows need special handling because `position=0 && !isWatched` cannot be inferred from the usual local upload heuristics
- release-wide clear/mark actions should not block the screen while still attempting remote propagation
- logout/login with another account still uses device-local history; this remains a semantic tradeoff and should be documented
- removing remote probes may change empty-state timing; row visibility must still behave reasonably with purely local state
- test fallout is expected because current tests encode `remote first` behavior

## Out Of Scope

- full watch-domain rewrite
- new backend contracts
- heavy worker/job infrastructure
- changing the overall local episode/history storage model beyond lightweight sync metadata
- per-account partitioning of device-local watch history/state
