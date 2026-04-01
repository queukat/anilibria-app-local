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

## 2. Что не сделано

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
- В `search` и `suggestions` controller seams уже убраны, но shared filter/grid и focus primitives всё ещё дублируются между фичами.
- В worktree остаётся много чужих/предсуществующих изменений в крупных UI-файлах, поэтому любой следующий medium refactor надо снова начинать с локальной инвентаризации.
- Smoke test теперь компилируется и опирается на актуальные текстовые маркеры, но сам `connectedAndroidTest` не запускался в этой сессии.
- Для `search` прогнаны unit tests, но UI/runtime поведение на реальном TV-девайсе или эмуляторе в этом проходе не проверялось.

## 5. Что было самым полезным изменением

Самыми полезными изменениями были локальные refactors `suggestions` и `search`:
- оба убрали controller/event-bus seam и свели feature flow к прямому state ownership;
- в `suggestions` результат и row visibility теперь живут в одном `uiState`;
- в `search` form coordination теперь идёт через `SearchFragment`, а не через отдельный bus;
- оба шага обошлись без rewrite navigation, player runtime или shared TV infra.

## 6. Что стоит делать следующим этапом

- Следующим отдельным проходом брать либо:
  - shared filter/grid scaffold,
  - либо shared section/focus coordinator.
- В обоих случаях идти только после повторной проверки текущего состояния больших файлов и не тянуть оба направления в один проход.

Сравнение `search` с уже упрощённым `suggestions`:
- `suggestions` стал feature-first по `uiState` и rows;
- `search` теперь тоже feature-first по form/result coordination;
- разница в том, что `search` пока осознанно оставляет fragment как локальный binder между двумя VM, а не склеивает всё в один state holder. Для текущего scope это нормальный safe compromise.

## Not included (because ...)

- Полная замена Fragment/Cicerone navigation не включена, потому что это уже architectural rewrite, а не safe cleanup.
- Удаление fragment-host слоя не включено, потому что это сломало бы текущие navigation/lifecycle seams ради слишком большого scope.
- Попытка убрать `PlayerView` не включена, потому что это нормальный и локализованный runtime compromise для Media3.
- Перепридумывание background/runtime abstractions не включено, потому что `GradientBackgroundManager` сейчас не был blocker-ом.
- Полный rewrite auth/config/update flow не включён, потому что там нет сопоставимого по выгоде безопасного refactor window в рамках этого прохода.
