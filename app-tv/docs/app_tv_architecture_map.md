# app-tv architecture map (stream 1)

## current request chains

1. `Fragment` in `screen/*` receives UI events and subscribes to state.
2. `ViewModel` in `screen/*` (often via `common/BaseCardsViewModel`) orchestrates loading and card state.
3. Data is requested from shared TV-oriented interactors/facades (for example `TvContentUseCase`, `TvFavoritesUseCase`, `ReleaseInteractor`).
4. DTO/domain models are converted by `CardsDataConverter`/other mappers into `CardItem` UI models.
5. Compose-first screen primitives render lists, overlays, and state panels. The main runtime interop exception is the player surface, which still uses Media3 `PlayerView` via `AndroidView`.

## current package map

- `screen/*`: feature-first entry points, Compose UI, and fragment hosts where navigation still expects `FragmentScreen`.
- `common/*`: shared card models, base viewmodel, converters.
- `ui/compose/*`: shared Compose TV primitives.
- `di/*`: app/activity scoped wiring.

## anti-patterns observed

- `BaseCardsViewModel` mixes responsibilities:
  - page state
  - async execution
  - retry/error policy
  - direct UI card assembly
- Pagination side-effects are coupled to view binding in multiple flows (`onLinkCardBind()` calls from presenter bind callbacks).
- Multiple pagination implementations exist (`BaseCardsViewModel`, custom loops in `WatchingFavoritesViewModel`), making policy drift likely.
- `screen/*` owns both presentation decisions and some domain/data policy choices, which limits reuse.

## additive stream 1 target structure

- `ui/*` (existing): rendering only.
- `presentation/*` (new): UI-facing state machines/controllers (paginator, card composition).
- `domain/*` (new): pagination policies and use-case contracts.
- `data/*` (new): adapters from concrete loaders to domain contracts.

This stream is additive only: no large package moves, no behavior-breaking migrations.

## migration notes for follow-up streams

1. Keep `screen/*` as entry points.
2. Replace direct `onLinkCardBind()` assumptions with explicit paginator events from click/selection or lifecycle callbacks.
3. Move per-screen paging rules into `domain/pagination` policies.
4. Move loader adapters into `data/pagination`, then wire through `presentation/pagination`.
