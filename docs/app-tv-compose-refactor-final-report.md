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

## 2. Что не сделано

- Не тронут `SearchController` и search flow в целом.
- Не вынесен shared section/focus coordinator из `MainCompose` / `WatchingCompose` / `DetailCompose` / `SuggestionsCompose` / `ScheduleCompose`.
- Не вынесен shared filter/grid scaffold между `CatalogCompose` и `WatchingFavoritesCompose`.
- Не сокращён wrapper-layer в `MainPageContent` / `WatchingPageContent`.
- Не переносились общие TV primitives из `screen.watching` / `screen.main` в новый shared пакет.
- Не делался player cleanup за пределами уже существующих изменений в worktree.

## 3. Что сознательно НЕ включено

- Не делался rewrite Fragment/Cicerone navigation.
- Не убирался fragment-host слой.
- Не трогался `PlayerView` / `AndroidView` seam.
- Не перепридумывался `GradientBackgroundManager`.
- Не переписывался auth/config/update flow целиком.

## 4. Какие риски остались

- Большие дубли по section/focus restore и filter/grid flow остались.
- `search` по-прежнему использует controller seam, в отличие от уже упрощённого `suggestions`.
- В worktree остаётся много чужих/предсуществующих изменений в крупных UI-файлах, поэтому любой следующий medium refactor надо снова начинать с локальной инвентаризации.
- Smoke test теперь компилируется и опирается на актуальные текстовые маркеры, но сам `connectedAndroidTest` не запускался в этой сессии.

## 5. Что было самым полезным изменением

Самым полезным изменением был локальный refactor `suggestions`:
- он убрал лишний controller/event-bus слой;
- сократил количество moving parts в feature;
- при этом не потребовал трогать navigation, player runtime или shared TV infra.

## 6. Что стоит делать следующим этапом

- Отдельным проходом разобрать `SearchController` и понять, можно ли схлопнуть search flow так же локально, как это удалось в `suggestions`.
- После этого брать либо:
  - shared filter/grid scaffold,
  - либо shared section/focus coordinator.
- В обоих случаях идти только после повторной проверки текущего состояния больших файлов из dirty worktree.

## Not included (because ...)

- Полная замена Fragment/Cicerone navigation не включена, потому что это уже architectural rewrite, а не safe cleanup.
- Удаление fragment-host слоя не включено, потому что это сломало бы текущие navigation/lifecycle seams ради слишком большого scope.
- Попытка убрать `PlayerView` не включена, потому что это нормальный и локализованный runtime compromise для Media3.
- Перепридумывание background/runtime abstractions не включено, потому что `GradientBackgroundManager` сейчас не был blocker-ом.
- Полный rewrite auth/config/update flow не включён, потому что там нет сопоставимого по выгоде безопасного refactor window в рамках этого прохода.
