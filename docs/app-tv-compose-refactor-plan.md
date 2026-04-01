# `app-tv` Compose Refactor Plan

## Goal

Сделать целевой cleanup/refactor `app-tv` по фактическому состоянию кода:
- закрыть безопасные quick wins;
- выполнить только те medium refactors, которые реально уменьшают migration glue / дублирование / лишние seams;
- не заходить в большой архитектурный rewrite.

## Stages

### 1. Inventory and audit re-check

Done when:
- перепроверены все quick wins из аудита по текущему коду;
- проверены реальные usages и скрытые зависимости для потенциального удаления;
- зафиксированы расхождения между аудитом и текущим кодом;
- зафиксирован факт, что worktree уже грязный и это ограничивает безопасный scope.

Risks:
- слепо следовать аудиту и делать уже неактуальную работу;
- пересечься с уже существующими незакоммиченными изменениями;
- принять старый хвост за dead code без полной проверки usages.

### 2. Quick wins

Scope:
- cleanup `colors.xml`;
- cleanup `ids.xml` + smoke test;
- удалить пустые legacy resource dirs, если они реально пустые;
- подчистить dead tail в `ConfiguringViewModel`;
- удалить pagination dead code только если usages действительно отсутствуют.

Done when:
- все quick wins либо выполнены, либо явно отклонены с причиной;
- затронутые файлы собираются в рамках `app-tv`.

Risks:
- сломать smoke test, подменив честную проверку декоративной;
- удалить resource/id, который на самом деле нужен runtime или тестам;
- удалить pagination code, который скрыто используется вне `app-tv`.

### 3. Medium refactors, по одному направлению

Candidate directions:
- shared section/focus coordinator;
- shared filter/grid scaffold;
- page content wrappers cleanup;
- shared TV primitives to neutral package;
- search/suggestions controller seams;
- player cleanup without runtime model rewrite.

Current selection:
- первым и единственным medium step в этом проходе выбран `suggestions` controller seam;
- остальные направления оставляются только при явном low-risk окне после верификации, иначе уходят в следующий этап.

Current selection for the next pass:
- после закрытия `suggestions` следующим узким кандидатом выбран `search` controller seam;
- успехом считается либо локальное удаление `SearchController`/`SearchModule` с прямым flow `form VM -> fragment -> cards VM`, либо честная фиксация safe boundary без дальнейшего churn.

Status update:
- `search` controller seam выбран и закрыт как safe local simplification;
- следующими кандидатами остаются только:
  - shared filter/grid scaffold;
  - shared section/focus coordinator;
- брать их вместе в одном проходе не планируется.

Status update for current pass:
- `shared filter/grid scaffold` выбран следующим отдельным шагом;
- критерий успеха для этого прохода сужен до двух допустимых исходов:
  - либо минимальный shared Compose scaffold между `search` и `favorites`;
  - либо честная фиксация safe boundary без захода в section/focus rewrite;
- VM/filter engines, shell navigation и shared TV primitives migration не входят в этот проход.

Status update for current pass:
- `shared section/focus coordinator` выбран следующим отдельным шагом;
- критерий успеха для этого прохода сужен до двух допустимых исходов:
  - либо минимальный shared helper/coordinator layer для section restore/focus mechanics;
  - либо честная фиксация safe boundary без page-content cleanup и без общего mega-engine;
- player/navigation/page-content wrapper cleanup и shared TV primitives migration не входят в этот проход.

Execution rule:
- брать только одно направление за раз;
- перед началом каждой итерации перепроверять: "а не фигню ли я делаю?";
- если направление разрастается в архитектурный rewrite, остановиться и зафиксировать причину.

Done when:
- выполнен минимум один средний рефакторинг с понятным выигрышем;
- после каждого шага есть проверка сборкой/тестами;
- оставшиеся направления честно классифицированы как safe-later или out-of-scope-for-now.

Risks:
- создать новый shared layer сложнее старого дублирования;
- размыть ownership state сильнее вместо упрощения;
- сломать TV focus behavior ради красивой структуры;
- сделать churn в уже модифицированных пользователем файлах без достаточного выигрыша.

### 4. Final verification and reporting

Done when:
- собран итог по сделанному / не сделанному / сознательно не включённому;
- перечислены оставшиеся риски;
- указаны следующие шаги уже вне текущего scope.

## Success criteria

- Есть практический выигрыш, а не только “красивее код”.
- Нет большой перепланировки навигации, player runtime или всего auth/config/update flow.
- Изменения локальны, проверены и документированы по ходу работы.

## Not included

- Полная замена Fragment/Cicerone navigation.
- Удаление fragment-host слоя целиком.
- Попытка убрать `PlayerView` / `AndroidView` из player surface.
- Перепридумывание `GradientBackgroundManager` и background runtime model.
- Полный rewrite auth/config/update flow.
- Rename storm по всему модулю без реального architectural payoff.
