# app-tv Compose UI/UX Audit

Date: 2026-03-14
Scope: `app-tv` only

## Summary

This pass focused on post-Compose migration drift in TV-specific UX without redesigning the product. The main issues were not architectural breakages, but inconsistent layout rules, state rendering, focus restoration, and overlay behavior that made screens feel uneven after migration.

## Findings

### Layout / spacing / insets

- Severity: major
- Reproduced in: `Search`, `Catalog`, `Favorites`, `Profile`, guided overlays, player pickers
- Probable root cause: screens migrated to Compose with local padding and width decisions instead of a shared TV layout rhythm
- Fix strategy: unify layout rules through small shared TV helpers, section paddings, overlay panel surface, and state-panel composition

### Typography / hierarchy

- Severity: major
- Reproduced in: `Search`, `Catalog`, `Profile`, overlays, player controls
- Probable root cause: text roles were recreated per screen and drifted away from consistent TV viewing-distance sizing
- Fix strategy: local polish plus shared overlay/state-panel patterns; increase hierarchy contrast where controls or helper text were too subtle

### Focus / d-pad navigation

- Severity: critical
- Reproduced in: `Catalog` filter chips, `Favorites` filters, detail header actions, player inline pickers, guided overlays
- Probable root cause: Compose migration broke old implicit focus restore paths; focus and scroll were restored separately or not at all
- Fix strategy: restore focus through explicit requesters and tokens, couple filter focus with scroll position, preserve last action focus in detail header, and anchor temporary picker focus to trigger controls

### Back behavior / flow continuity

- Severity: major
- Reproduced in: filter pickers, guided overlays, player exit path
- Probable root cause: overlays and temporary guided fragments were treated like full flow exits
- Fix strategy: restore focus to the originating control after dismiss, and ignore temporary guided overlays when deciding whether the player was actually leaving

### State handling

- Severity: critical
- Reproduced in: `Catalog`, `Favorites`, `Schedule`, `Suggestions`
- Probable root cause: loading, empty, and error cards were rendered inside normal content rows, so empty states looked like broken content instead of deliberate screens
- Fix strategy: normalize empty/loading/error rendering through a reusable TV state panel with optional CTA and consistent focus behavior

### Consistency / reuse

- Severity: major
- Reproduced in: multiple Compose screens and overlays
- Probable root cause: migration introduced many one-off implementations of the same UI pattern
- Fix strategy: extract only repeated TV-specific rules: state panel, overlay panel, action button focus surface, and filter-row focus/scroll restore

### TV readability / viewing distance

- Severity: major
- Reproduced in: profile summary area, overlays, player controls
- Probable root cause: desktop-or-phone sized text and narrow content blocks persisted after Compose migration
- Fix strategy: widen constrained panels where needed and increase critical text sizes in overlays and player chrome

### Player overlays / controls UX

- Severity: major
- Reproduced in: inline speed/quality picker, end-of-episode and end-of-season guided actions, temporary overlay exit handling
- Probable root cause: picker placement and close ordering were not preserving spatial or action continuity
- Fix strategy: anchor picker panels to the actual triggering button and close guided overlays after selection/navigation is committed

## Changes Applied

- Added a reusable TV content-state panel with optional CTA for empty, error, and helper states.
- Unified overlay panel surface, padding, and CTA focus behavior.
- Reworked `Catalog` and `Favorites` filter rows to use focus-aware `LazyRow` restore instead of manual scroll containers.
- Fixed `Catalog`, `Favorites`, `Schedule`, and `Suggestions` so state-only content is rendered as a proper screen block instead of a fake content row.
- Simplified `Profile` header/content composition and widened the account panel for TV readability.
- Preserved detail-header action continuity by restoring focus to the last invoked action.
- Prevented temporary guided overlays from being treated as real player exit.
- Anchored player inline pickers to the triggering control and improved control typography.

## Validation

- Static checks passed:
  - `:app-tv:detekt`
  - `:app-tv:lintDebug`
  - `:app-tv:testDebugUnitTest`
  - `:app-tv:assembleRelease`
- Device smoke checks completed on connected TV device:
  - `Main` opened with expected initial card focus
  - `Search` opened with stable initial focus on the query field
  - `Catalog` opened as a dedicated screen with the new state-panel layout
  - `Catalog` filter picker loaded live year data (`2026`, `2025`, `2024`, `2023`, `2022`)
  - `Back` from the picker restored focus to the originating year chip

## Residual Risk

- The pass deliberately avoided navigation-architecture rewrites and did not redesign the product.
- Device smoke coverage was strongest for `Main`, `Search`, `Catalog`, and picker/back continuity; other flows were validated mainly through code review, focused fixes, and build/test verification.
