# `app-tv` Compose Refactor Final Report

## 1. Что реально сделано

- Подтверждены quick wins по текущему коду, а не по тексту аудита.
- Вычищены resource tails в `app-tv`:
  - удалены stale guided-step цвета и временные color aliases из `app-tv/src/main/res/values/colors.xml`;
  - удалён целиком `app-tv/src/main/res/values/ids.xml` после repo-wide проверки отсутствия source usages;
  - удалены пустые `app-tv/src/main/res/layout` и `app-tv/src/main/res/animator`.
- Обновлён `app-tv/src/androidTest/java/ru/radiationx/anilibria/smoke/AppTvSmokeTest.kt`:
  - тест больше не зависит от `R.id.title_orb`;
  - проверки переведены на реальные признаки Compose-first shell (`Главная`, `Поиск`, `Я смотрю`).
- Удалён dead tail из `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfiguringViewModel.kt`.
- Удалена реально неиспользуемая pagination-цепочка:
  - `presentation/pagination/Paginator.kt`
  - `presentation/pagination/PaginatorEvent.kt`
  - `presentation/pagination/CardsPaginatorFactory.kt`
  - и связанная только с ней `domain.pagination.*` / `data.pagination.SuspendLoadPageUseCase`.
- Выполнен один medium refactor по направлению `search/suggestions controller seams`:
  - удалены `SuggestionsController` и `SuggestionsRowsViewModel`;
  - введён прямой `SuggestionsResultUiState`;
  - `SuggestionsFragment` теперь строит rows напрямую из feature state без event-bus/adaptor слоя.
- Выполнен ещё один локальный medium refactor по `search`:
  - удалены `SearchController` и `SearchModule`;
  - `SearchFormViewModel` теперь публикует прямой `searchFormData` и обновляет `SearchForm` без bus-слоя;
  - `SearchFragment` напрямую связывает form state с `SearchViewModel.submitSearchForm(...)`;
  - `SearchViewModel` больше не зависит от controller и держит только cards/result ownership;
  - unit tests обновлены под новый flow, добавлен отдельный smoke-test на direct form state в `SearchFormViewModel`.
- Подчищен хвост после удаления controller seams:
  - `app-tv/detekt-baseline.xml` больше не содержит stale entries на удалённые `SearchController.kt`, `SearchModule.kt` и `SuggestionsController.kt`.
- Выполнен частичный shared filter/grid refactor между `search` и `favorites`:
  - добавлен общий Compose scaffold `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/TvCollectionScaffoldCompose.kt`;
  - `CatalogCompose` и `WatchingFavoritesCompose` теперь делят:
    - общий filter chips row;
    - общий grid/state-panel body;
    - общие UI contracts для state panel / wide message cards;
  - `SearchFragment` и `WatchingFavoritesPageContent` теперь делят маленькие helper-функции для picker restore.

## 2. Что не сделано

- Не вынесен shared section/focus coordinator из `MainCompose` / `WatchingCompose` / `DetailCompose` / `SuggestionsCompose` / `ScheduleCompose`.
- Не сокращён wrapper-layer в `MainPageContent` / `WatchingPageContent`.
- Не переносились общие TV primitives из `screen.watching` / `screen.main` в новый shared пакет.
- Не делался player cleanup за пределами уже существующих изменений в worktree.

Что осталось раздельным внутри filter/grid flow:
- `SearchFormViewModel` и `WatchingFavoritesViewModel`, потому что их business rules и data sources разные.
- Внешний focus shell:
  - `search` screen button как отдельная focus target;
  - `favorites` rail/header callbacks через shell.
- Feature-specific state panel copy, progress semantics и description behavior.

## 3. Что сознательно НЕ включено

- Не делался rewrite Fragment/Cicerone navigation.
- Не убирался fragment-host слой.
- Не трогался `PlayerView` / `AndroidView` seam.
- Не перепридумывался `GradientBackgroundManager`.
- Не переписывался auth/config/update flow целиком.

## 4. Какие риски остались

- Большие дубли по section/focus restore остались.
- Shared filter/grid scaffold вынесен только частично: внешний focus topology и filter engines остались локальными, и это сознательный компромисс.
- В worktree остаётся много чужих/предсуществующих изменений в крупных UI-файлах, поэтому любой следующий medium refactor надо снова начинать с локальной инвентаризации.
- Smoke test теперь компилируется и опирается на актуальные текстовые маркеры, но сам `connectedAndroidTest` не запускался в этой сессии.
- Для `search` прогнаны unit tests, но UI/runtime поведение на реальном TV-девайсе или эмуляторе в этом проходе не проверялось.
- Для shared filter/grid scaffold прогнана только компиляция через `:app-tv:testDebugUnitTest`; реальный D-pad/focus runtime на TV не проверялся.

## 5. Что было самым полезным изменением

Самыми полезными изменениями были локальные refactors `suggestions`, `search` и partial shared filter/grid scaffold:
- оба убрали controller/event-bus seam и свели feature flow к прямому state ownership;
- в `suggestions` результат и row visibility теперь живут в одном `uiState`;
- в `search` form coordination теперь идёт через `SearchFragment`, а не через отдельный bus;
- shared scaffold затем сократил явный UI duplicate code между `search` и `favorites`, не смешивая их VM/data rules;
- все шаги обошлись без rewrite navigation, player runtime или shared TV infra.

## 6. Что стоит делать следующим этапом

- Следующим отдельным проходом лучший кандидат теперь `shared section/focus coordinator`.
- Идти в него стоит только после повторной проверки текущего состояния больших файлов и без попытки параллельно трогать player/navigation.

Сравнение `search` с уже упрощённым `suggestions`:
- `suggestions` стал feature-first по `uiState` и rows;
- `search` теперь тоже feature-first по form/result coordination;
- разница в том, что `search` пока осознанно оставляет fragment как локальный binder между двумя VM, а не склеивает всё в один state holder. Для текущего scope это нормальный safe compromise.

Что именно вынесено в shared scaffold:
- filter chips row на `WatchingFilterChip`;
- общий grid/state-panel body на `WatchingPosterCard`, `WatchingWideMessageCard`, `TvContentStatePanel`;
- picker restore helpers для `filterIndex` и `shouldRequestPickerFocus`.

Что осталось раздельным и почему:
- filter state engines и data sources, потому что `search` и `favorites` различаются по business rules;
- внешний focus shell, потому что `search` и `favorites` имеют разную focus topology;
- state panel copy и progress semantics, потому что они завязаны на разные product states.

## Not included (because ...)

- Полная замена Fragment/Cicerone navigation не включена, потому что это уже architectural rewrite, а не safe cleanup.
- Удаление fragment-host слоя не включено, потому что это сломало бы текущие navigation/lifecycle seams ради слишком большого scope.
- Попытка убрать `PlayerView` не включена, потому что это нормальный и локализованный runtime compromise для Media3.
- Перепридумывание background/runtime abstractions не включено, потому что `GradientBackgroundManager` сейчас не был blocker-ом.
- Полный rewrite auth/config/update flow не включён, потому что там нет сопоставимого по выгоде безопасного refactor window в рамках этого прохода.
