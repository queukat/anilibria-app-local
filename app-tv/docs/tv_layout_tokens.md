# TV Layout Tokens

This note keeps the TV Compose layout budget in one place after the compact shell
and solid title substrate passes.

## Shell

`TvShellDefaults` owns the main-pages shell only: header height/spacing, header
actions, expanded rail width, rail padding, stripe sizes, and shell-only alpha
values. Runtime and previews should read shell size from these tokens so there is
no second source of truth for header height or rail width.

Current shell budget:

- Header: `HeaderHeight = 68.dp`, `HeaderSpacing = 4.dp`.
- Expanded rail: `RailWidth = 232.dp`.
- Header action: `HeaderActionWidth = 148.dp`.

## Page Rhythm

`WatchingUiDefaults` owns shared TV page rhythm:

- `TvPageContentPadding` is the default root padding for full pages with headers
  or panels, such as config, profile, update, schedule, search, and catalog.
- `TvRowsContentPadding` is the tighter root padding for row-heavy shell content,
  such as main, watching, and favorites.
- `TvPageRowsTopContentPadding` and `TvRowsTopContentPadding` are LazyColumn
  content insets inside those already-padded roots.
- `TvCardScreenHorizontalPadding` intentionally aliases `TvPageHorizontalPadding`
  and is kept for grid/column math where the card layout needs the same root
  horizontal budget.

Collection filters are part of page rhythm, not shell rhythm:

- Top filters panel padding: `16.dp` horizontal, `12.dp` vertical.
- Filter panel spacing: `10.dp`.
- Leading collection action width: `136.dp`.
- Filter chip row spacing: `8.dp`.

## Detail

Detail uses `TvDetailHorizontalPadding` and `TvDetailRowsTopContentPadding`
because the hero/header page aligns larger text, action rows, and content rows
against a wider detail-specific axis. Do not reuse detail padding for ordinary
page or collection roots.

## Title Substrates

Solid title/description substrates use `TvUiDefaults.SOLID_SURFACE_ALPHA = 1f`
through `WatchingDescriptionBar(solidSurface = true)`. Non-solid player/top
overlays keep their translucent scrim behavior.

Current solid title budget:

- Regular title bar min height: `TvSolidDescriptionBarMinHeight = 80.dp`.
- Collection title bar min height: `TvCollectionSolidDescriptionBarMinHeight = 76.dp`.
- Bottom fallbacks: `TvBottomDescriptionInset = 112.dp`,
  `TvGridBottomDescriptionInset = 124.dp`,
  `TvCollectionGridBottomDescriptionInset = 108.dp`.

## Player

Player controls remain player-specific in `PlayerOverlayUiTokens`. Do not move
player panel alpha, controls padding, or transport button sizes into the shared
page/shell tokens unless the player layout itself is being redesigned.
