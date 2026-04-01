# `app-tv` Compose Refactor Progress

## Session start

- Зафиксирован исходный аудит в `docs/app-tv-compose-audit.md`.
- Зафиксировано, что worktree уже грязный: много изменённых файлов в `app-tv`, `data` и тестах. Это значит:
  - нельзя бездумно переписывать крупные файлы из аудита;
  - каждый затрагиваемый файл нужно перечитывать в текущем состоянии перед правкой;
  - medium refactors нужно выбирать особенно узко.

## Inventory

Проверено по текущему коду:
- `app-tv/src/main/res/values/colors.xml`: хвосты `app_guidedstep_*`, `temp_red4`, `temp_red_` действительно есть.
- `app-tv/src/main/res/values/ids.xml`: `fragmentContainer`, `title_orb`, `title_other`, `title_alert`, `title_controls` действительно есть.
- `app-tv/src/androidTest/java/ru/radiationx/anilibria/smoke/AppTvSmokeTest.kt`: тест реально ждёт `R.id.title_orb` и использует `findViewById`, что не соответствует Compose-first shell.
- `app-tv/src/main/res/layout` и `app-tv/src/main/res/animator`: директории действительно пустые.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfiguringViewModel.kt`: `endConfiguring()` содержит закомментированный `router.exit()`.
- `app-tv/src/main/java/ru/radiationx/anilibria/presentation/pagination/`: `LoadMoreCardsComposer` и `PaginatorState` реально используются из `BaseCardsViewModel`, а `Paginator.kt`, `PaginatorEvent.kt`, `CardsPaginatorFactory.kt` пока выглядят неиспользуемыми. Перед удалением нужна ещё одна полная проверка по всему репозиторию.

## Self-check

Проверка: а не фигню ли я делаю?
- Пока нет: quick wins подтверждены кодом напрямую.

Риск:
- Удаление pagination-классов без полной проверки по всему репозиторию.
- Слишком ранний заход в medium refactor по уже изменённым большим файлам.

Почему продолжаю:
- Есть несколько безопасных хвостов с прямой пользой и почти нулевым behavioural risk.

## Next step

- Закрыть quick wins с повторной проверкой usages перед удалением.
- После quick wins заново выбрать только один medium refactor с минимальным churn.

## Quick wins completed

Сделано:
- `app-tv/src/main/res/values/colors.xml`
  - удалены `app_guidedstep_actions_background`, `app_guidedstep_subactions_background`;
  - убраны временные aliases `temp_red4`, `temp_red_`;
  - `dark_colorAccent` теперь ссылается на итоговое значение напрямую.
- `app-tv/src/androidTest/java/ru/radiationx/anilibria/smoke/AppTvSmokeTest.kt`
  - убран pre-Compose `findViewById(R.id.title_orb)` подход;
  - smoke assertions переведены на реальные текстовые маркеры текущего Compose-first shell (`Главная`, `Поиск`, `Я смотрю`).
- `app-tv/src/main/res/values/ids.xml`
  - удалён целиком после repo-wide проверки: source usages не найдены.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfiguringViewModel.kt`
  - удалён пустой `endConfiguring()` с закомментированным `router.exit()`.
- удалены пустые директории:
  - `app-tv/src/main/res/layout`
  - `app-tv/src/main/res/animator`
- удалена реально неиспользуемая pagination-цепочка:
  - `presentation/pagination/Paginator.kt`
  - `presentation/pagination/PaginatorEvent.kt`
  - `presentation/pagination/CardsPaginatorFactory.kt`
  - вместе с завязанными только на неё `domain.pagination.*` и `data.pagination.SuspendLoadPageUseCase`.

Проверено дополнительно:
- repo-wide поиск больше не находит source usages удалённых `title_*` ids и pagination-классов;
- `window_dump.xml` всё ещё содержит старые id, но это сохранённый артефакт UI-дампа, а не текущая исходная зависимость.

## Self-check after quick wins

Проверка: а не фигню ли я делаю?
- На этом этапе нет: все удаления подтверждены repo-wide поиском usages, а smoke test переведён на реальные признаки текущего UI.

Риск:
- Возможен пропуск неочевидной внешней зависимости на удалённые ids через ручные инструменты/UI-дампы, но в исходниках репозитория такой зависимости нет.
- Pagination cleanup мог затронуть скрытый reflective/runtime usage, но для этих классов нет ни одного source reference вне удалённой цепочки.

Почему продолжаю:
- quick wins дали реальную очистку шума без архитектурного сдвига;
- дальше можно выбрать один средний рефактор уже после проверки сборки.

Почему останавливаюсь на этом участке:
- дальше без сборки/тестов идти нельзя: следующий шаг уже будет затрагивать feature flow, а не чистый dead-code cleanup.

## Verification after quick wins

Проверено:
- `./gradlew.bat :app-tv:testDebugUnitTest --console=plain` — успешно;
- `./gradlew.bat :app-tv:compileDebugAndroidTestKotlin --console=plain` — успешно, но потребовался elevated запуск из-за sandbox-проблемы с Gradle wrapper lock path;
- `connectedAndroidTest` не запускался: в рамках этой сессии не было гарантированного устройства/эмулятора.

## Medium refactor: `suggestions` controller seam

Что именно улучшаю:
- убираю `SuggestionsController` и `SuggestionsRowsViewModel`, потому что они были тонким bus/adaptor слоем между `SuggestionsResultViewModel` и `SuggestionsFragment`;
- row visibility теперь выводится напрямую из `SuggestionsResultUiState`.

Проверка: а не фигню ли я делаю?
- Нет, пока scope локальный:
  - затронута только `suggestions` feature-ветка;
  - `search` не тянулся в тот же rewrite;
  - новых “универсальных фреймворков” не добавлялось.

Риск:
- можно было случайно сломать поведение при коротком query, если `SearchLoader` держал бы старые данные;
- можно было создать новый shared слой сложнее старого контроллера.

Почему продолжаю:
- проверка `SearchLoader` показала, что на коротком query происходит `reset()`, значит локальный `uiState` здесь уместен;
- новый код проще: результат поиска, row visibility и cards теперь живут в одном feature state.

Что изменено:
- добавлен `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsUiState.kt`;
- `SuggestionsResultViewModel` больше не зависит от `SuggestionsController` и публикует `uiState`;
- `SuggestionsFragment` больше не поднимает локальный DI-модуль для `SuggestionsController` и не использует `SuggestionsRowsViewModel`;
- удалены:
  - `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsController.kt`
  - `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsRowsViewModel.kt`
- обновлён `app-tv/src/test/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsViewModelsTest.kt` под новый `uiState`/row visibility flow.

Найденный фейл и исправление:
- первый compile после рефактора упал, потому что `SuggestionsResultViewModel` оставлял public getter на internal `SuggestionsResultUiState`;
- исправлено локально: `uiState` сделан `internal`.

## Verification after medium refactor

Проверено:
- `./gradlew.bat :app-tv:testDebugUnitTest --console=plain` — успешно после `suggestions` refactor;
- `./gradlew.bat :app-tv:compileDebugAndroidTestKotlin --console=plain` — успешно после `suggestions` refactor, снова через elevated запуск из-за wrapper lock path.

## Stop decision

Проверка: а не фигню ли я делаю?
- Если сейчас продолжать в `search`, `player` или общие TV primitives, это уже будет заметно более широкий churn по большим и местами уже изменённым файлам.

Риск:
- следующий шаг почти наверняка заденет крупные файлы из dirty worktree и ухудшит соотношение “выигрыш / риск регрессии”.

Почему останавливаюсь:
- есть завершённый набор quick wins;
- есть один закрытый medium refactor с реальным упрощением feature flow;
- дальше лучше идти отдельным этапом, а не докручивать scope вширь в текущем проходе.

## New pass: `search` inventory

Сначала перечитаны:
- `docs/app-tv-compose-refactor-plan.md`
- `docs/app-tv-compose-refactor-progress.md`
- `docs/app-tv-compose-refactor-final-report.md`

Проверено по текущему коду:
- `SearchController` используется только внутри `search` feature и DI-модуля `SearchModule`.
- `SearchViewModel` использует controller только для одного потока: `applyFormEvent`.
- `SearchFormViewModel` использует controller как bus почти для всей фильтр-логики, включая пересылку части изменений обратно в собственный `searchForm`.
- `SearchFragment` уже и так является coordinator между `SearchViewModel` и `SearchFormViewModel`, то есть локальный fragment-mediated flow здесь уже существует.
- Отдельной row visibility VM, как было в `suggestions`, здесь нет.
- `CatalogCompose` не зависит напрямую от controller seam; ему нужен только итоговый state из fragment/view models.
- `SearchModule` нужен только для регистрации `SearchController`, а `MainActivity` ставит его только ради этого.

### Проверка: а не фигню ли я делаю?

- Это выглядит как реально следующий лучший шаг, а не как большой refactor: seam локальный и сравнимый по масштабу с уже упрощённым `suggestions`.
- Упрощение data flow здесь должно быть прямым: вместо `form VM -> controller -> cards VM` и `form VM -> controller -> form VM` можно перейти к `form VM state -> fragment -> cards VM`.
- Это не тянет автоматически rewrite `favorites`, shared filter engine или navigation, если держать scope только в `search`.

### Риск

- Самые опасные файлы:
  - `SearchFormViewModel.kt`
  - `SearchViewModel.kt`
  - `SearchFragment.kt`
- Основной риск роста churn:
  - случайно начать строить общий filter engine под `favorites`;
  - полезть в `CatalogCompose` глубже, чем нужно для state binding;
  - сломать initial load / repeated submit поведение при пересоздании view.
- Точка остановки:
  - если для удаления controller придётся трогать `favorites`, shared TV filter abstractions или navigation/DI шире `search`, дальше идти нельзя.

### Выбранный исход

- Исход A: safe local simplification possible.
- План на локальный refactor:
  - убрать `SearchController` и `SearchModule`;
  - дать `SearchFormViewModel` прямой `searchForm` state;
  - дать `SearchViewModel` явный метод приёма form без event-bus;
  - оставить coordination через `SearchFragment`, потому что он уже и так держит feature binding.

## New pass: `search` execution

Факт по текущему worktree:
- к моменту этой проверки локальный refactor уже частично лежал в незакоммиченном состоянии;
- поэтому сначала был перепроверен diff, чтобы не “делать то же самое второй раз” и не расширить scope задним числом.

Что реально изменено в `search`:
- удалены:
  - `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchController.kt`
  - `app-tv/src/main/java/ru/radiationx/anilibria/di/SearchModule.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFormViewModel.kt`
  - больше не зависит от controller/event-bus;
  - держит прямой `searchFormData`;
  - обновляет собственный `SearchForm` напрямую при sort/completed/year/season/genre изменениях.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchViewModel.kt`
  - больше не подписывается на `applyFormEvent`;
  - принимает form через явный `submitSearchForm`.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFragment.kt`
  - теперь связывает `formViewModel.searchFormData` с `cardsViewModel.submitSearchForm(...)`;
  - при этом UI/focus/picker binding остался локальным и без переписывания `CatalogCompose`.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/launcher/MainActivity.kt`
  - больше не устанавливает `SearchModule`.
- тесты:
  - `app-tv/src/test/java/ru/radiationx/anilibria/screen/search/SearchViewModelTest.kt` переведён на новый API;
  - добавлен `app-tv/src/test/java/ru/radiationx/anilibria/screen/search/SearchFormViewModelTest.kt` как smoke-check, что sort picker теперь обновляет direct form state без controller seam.
- сопутствующий cleanup:
  - `app-tv/detekt-baseline.xml` очищен от stale entries для уже удалённых `SearchController.kt`, `SearchModule.kt` и `SuggestionsController.kt`.

### Проверка: а не фигню ли я делаю?

- После просмотра diff ответ остаётся “нет”: refactor не вышел за пределы `search` плюс один DI-хвост в `MainActivity`.
- Новый state flow проще старого seam:
  - было: `form VM -> controller -> cards VM` и местами `form VM -> controller -> form VM`;
  - стало: `form VM state -> fragment binding -> cards VM`.
- В `favorites`, shared filter engine, navigation и player я не полез.

### Риск

- Самый тонкий участок здесь не удаление controller, а поведение при повторной подписке fragment на `searchFormData`.
- Для этого в `SearchViewModel` добавлен guard от повторного reload одного и того же `SearchForm`.
- Отдельно проверен stop point:
  - не понадобилось менять `CatalogCompose`;
  - не понадобилось вводить новый shared abstraction layer;
  - дальше полировать `search` без новых фактов уже не нужно.

### Почему продолжаю / почему останавливаюсь

- Продолжаю:
  - потому что текущий scope даёт прямое упрощение ownership и убирает migration bus.
- Останавливаюсь на этой границе:
  - потому что следующий шаг уже был бы либо про shared filter engine, либо про более широкий UI cleanup, а это другой проход.

## Verification after `search` refactor

Проверено:
- `./gradlew.bat :app-tv:testDebugUnitTest --console=plain` — успешно после `search` refactor и обновления unit tests.

Дополнительная проверка:
- repo-wide поиск больше не находит source usages `SearchController`/`SearchModule`; оставшиеся совпадения только в audit/progress/final-report и в исторических формулировках.

### Проверка: а не фигню ли я делаю?

- Нет: после unit-test прогона и repo-wide usage check новый direct flow выглядит проще старого controller seam и не потянул лишние cross-feature зависимости.
- Новый state holder не стал сложнее старого: лишний bus исчез, а fragment сохранил роль локального binder-а, которую и так уже выполнял.

### Почему продолжаю / почему останавливаюсь

- Останавливаюсь:
  - потому что цель прохода закрыта;
  - дальнейшие изменения в `search` уже были бы либо про shared abstractions, либо про UI-polish, а это вне safe local scope.

## New pass: `shared filter/grid scaffold` inventory

Сначала перечитаны:
- `docs/app-tv-compose-refactor-plan.md`
- `docs/app-tv-compose-refactor-progress.md`
- `docs/app-tv-compose-refactor-final-report.md`

Проверено по текущему коду:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/CatalogCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFormViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/common/TvCollectionFilters.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFilterPickerDialog.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesPageContent.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFragment.kt`

Что реально общее между `search` и `favorites`:
- один и тот же `TvCollectionFiltersUiState` / `TvCollectionFilterPickerState`;
- одинаковый набор chips: year / season / genre / sort / completed;
- одинаковый picker flow: open / toggle / apply / reset / dismiss;
- одинаковый `WatchingFilterPickerDialog`;
- почти одинаковый filter row UI на `WatchingFilterChip`;
- почти одинаковый grid/state-panel body:
  - `showStatePanel` logic;
  - `WatchingPosterCard` / `WatchingWideMessageCard`;
  - `TvContentStatePanel`;
  - picker overlay;
  - описание выбранной карточки снизу;
  - restore after picker close через `restoreFilterIndex`/`restoreFilterToken`;
- дублирующиеся helper-кусочки вокруг picker restore:
  - mapping `TvCollectionFilterPickerKind -> filter index`;
  - `shouldRequestPickerFocus(previous, next)`.

Что НЕ является общим:
- бизнес-логика фильтров и источники данных:
  - `SearchFormViewModel` работает с `TvSearchUseCase` и `SearchForm`;
  - `WatchingFavoritesViewModel` держит auth/sync/cache/rebuild flow и свои raw/available filters;
- query/search behavior и переход в отдельный поиск;
- favorites-specific auth/reload/rebuild behavior;
- focus topology вокруг scaffold:
  - `search` держит внутренний search button как отдельную focus target;
  - `favorites` завязан на внешний rail/header shell и `onContentMovedUp/Down`;
- state panel copy и progress semantics:
  - `search` показывает overlay progress spinner поверх grid;
  - `favorites` живёт через cache/reload states без такого overlay.

Минимальная safe abstraction, которая выглядит реалистично:
- общий Compose слой для:
  - filter chips row;
  - grid/state-panel body;
  - picker restore helper-функций;
- при этом оставить раздельными:
  - VM/filter engines;
  - shell/header/rail focus coordination;
  - feature-specific state panel text и description logic.

### Проверка: а не фигню ли я делаю?

- Это реально уменьшает дублирование, если ограничиться UI/scaffold-слоем; если пытаться вынести ещё и focus topology целиком, получится abstraction впрок.
- Вынести только UI/scaffold без смешивания business rules можно.
- Полный общий экран автоматически тянет section/focus coordinator и shell/header coupling; этого делать нельзя.

### Риск

- Самые опасные файлы:
  - `CatalogCompose.kt`
  - `WatchingFavoritesCompose.kt`
  - `WatchingFavoritesPageContent.kt`
  - `SearchFragment.kt`
- Основной риск:
  - сделать shared scaffold умнее двух исходных реализаций;
  - утащить внутрь shared слоя знания про search button или shell rail/header;
  - случайно начать решать section/focus problems, которые к этому проходу не относятся.
- Точка остановки:
  - если общий слой начнёт требовать знания про shell callbacks, navigation или cross-feature state engines, дальше идти нельзя.

### Выбранный исход

- Исход B: можно вынести только часть.
- План:
  - вынести общий Compose scaffold для filter row + grid/state-panel body;
  - вынести маленькие helper-функции для picker restore;
  - оставить VM/filter state engine и внешний focus topology раздельными.

## New pass: `shared filter/grid scaffold` execution

Что реально вынесено:
- добавлен `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/TvCollectionScaffoldCompose.kt`:
  - `TvCollectionFiltersRow`
  - `TvCollectionGridStateContent`
  - минимальные shared UI models для filter actions, state panel и wide message cards
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/CatalogCompose.kt`
  - переведён на shared filter row и shared grid/state-panel body;
  - search-specific header, progress overlay, state panel copy и description logic оставлены локально;
  - search-specific focus topology (`Search` / `Filter` / `Grid`) оставлена локально.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesCompose.kt`
  - переведён на тот же shared filter row и shared grid/state-panel body;
  - shell callbacks (`rail`, `header`, `onContentMovedUp/Down`) остались локальными;
  - favorites-specific state panel copy и description logic остались локальными.
- `app-tv/src/main/java/ru/radiationx/anilibria/common/TvCollectionFilters.kt`
  - добавлены маленькие shared helper-функции для picker restore:
    - `tvCollectionFilterIndex(...)`
    - `shouldRequestTvCollectionPickerFocus(...)`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFragment.kt`
  - переведён на shared picker restore helpers.
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesPageContent.kt`
  - переведён на shared picker restore helpers.

Что сознательно оставлено раздельным:
- `SearchFormViewModel` и `WatchingFavoritesViewModel` не объединялись;
- filter engines и бизнес-правила не выносились в общий слой;
- внешний focus shell (`search` button vs `rail/header`) не тащился в shared scaffold;
- progress semantics `search` и cache/auth/reload semantics `favorites` остались feature-specific.

### Проверка: а не фигню ли я делаю?

- Новый shared слой правда проще, потому что он знает только про общий UI:
  - chips row;
  - grid/state-panel body;
  - picker restore helpers.
- Он не стал “умным” фреймворком:
  - не знает про `SearchForm`;
  - не знает про favorites auth/rebuild;
  - не знает про navigation или shell selection.
- Самый опасный момент был в directional behavior grid items:
  - для `LibriaCard` и wide message cards пришлось сохранить разные `onUp`/`onLeft`;
  - это перепроверено до тестового прогона и оставлено локальными callback-ами.

### Почему продолжаю / почему останавливаюсь

- Продолжаю:
  - потому что shared UI слой дал заметное сокращение дублей без смешивания business rules.
- Останавливаюсь на этой границе:
  - потому что следующий шаг уже был бы про section/focus coordinator, а это отдельный проход.

## Verification after `shared filter/grid scaffold`

Проверено:
- `./gradlew.bat :app-tv:testDebugUnitTest --console=plain` — успешно после выноса shared scaffold.

Что не проверено:
- TV runtime/focus behavior на реальном девайсе или эмуляторе;
- `connectedAndroidTest` не запускался в этом проходе.

### Проверка: а не фигню ли я делаю?

- После сборки и unit tests ответ остаётся “нет”: новый слой не потянул соседние фичи и не потребовал новых shared VM/base classes.
- Полного общего экрана не появилось; остался только общий UI scaffold и локальные callbacks.

### Почему продолжаю / почему останавливаюсь

- Останавливаюсь:
  - потому что цель прохода достигнута в safe варианте `Исход B`;
  - дальше без отдельного решения по section/focus coordinator будет уже другой тип рефактора.
