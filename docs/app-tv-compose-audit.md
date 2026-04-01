# `app-tv` Compose Audit

Scope:
- просмотрен только текущий код `app-tv` в репозитории;
- цель аудита: отделить реальные проблемы миграции от нормальных Android TV / runtime-компромиссов;
- в рекомендации намеренно не включены большие переписывания навигации, player stack или всего модуля.

## 1. Executive summary

Состояние модуля после миграции скорее **Compose-first с fragment/Cicerone shell**, чем “старый Leanback-модуль, в который сверху добавили Compose”.

Что важно:
- в `app-tv` не осталось Leanback UI-зависимостей и Leanback-экранов как основной модели UI: в `app-tv/build.gradle` нет Leanback dependency, в коде не найдены `BrowseSupportFragment`, `RowsSupportFragment`, `PlaybackSupportFragment`, `GuidedStepFragment`, `Presenter` или `ArrayObjectAdapter`;
- `res/layout` и `res/animator` фактически пустые, то есть XML/View-подход как основной UI уже ушёл;
- реальный legacy теперь живёт в других местах: `Fragment`-хосты, Cicerone-навигация, TV global search provider, `PlayerView` bridge для Media3, старые resource/test-хвосты.

Если говорить прямо: “каша” есть, но в основном **не из-за невычищенного Leanback**, а из-за:
- дублированной Compose-логики focus restore / section navigation;
- дублированных filter/grid сценариев;
- migration glue между fragment shell, view model state и compose screens;
- shared TV primitives, которые уже стали общими, но всё ещё живут в feature-пакетах (`screen.watching`, `screen.main`).

Итоговая оценка:
- модуль уже далеко продвинулся в Compose-миграции;
- главная работа сейчас не “переписать всё”, а **свернуть промежуточные адаптеры и вынести повторяющийся TV Compose scaffolding в нормальные shared primitives**;
- часть legacy seams при этом выглядит нормальной и трогать её сейчас невыгодно.

## 2. Что уже хорошо

- `player` в целом уже Compose-first. В `screen/player/ComposePlayerScreen.kt` overlay, controls, picker, completion overlay и focus policy уже собраны на Compose; `AndroidView` используется только для Media3 surface через `PlayerView`, что для TV-плеера выглядит нормальным техническим компромиссом.
- В player-части уже есть хорошие выделения по ответственности: `screen/player/PlayerControlsFocusPolicy.kt`, `screen/player/PlayerOverlayUiTokens.kt`, набор unit-тестов вокруг player/focus logic.
- `auth` / `config` / `update` flow уже не Leanback-guided. `screen/auth/AuthCompose.kt`, `screen/config/ConfigCompose.kt`, `screen/update/UpdateCompose.kt` уже идут через Compose overlay/screen-подход.
- Есть неплохой shared Compose toolkit для TV: `ui/compose/TvOverlayCompose.kt`, `ui/compose/TvContentStateCompose.kt`, `ui/compose/TvBackgroundCompose.kt`, `ui/compose/TvUiDefaults.kt`, `screen/watching/WatchingCommonCompose.kt`.
- `WatchingFilterPickerDialog.kt` выглядит полезной общей Compose-обёрткой, а не мусорным adapter layer: его используют и search, и favorites.
- `GradientBackgroundManager` + `ProvideGradientBackground` дают понятный и рабочий TV-specific seam для динамического фона. Это legacy по форме, но не мусор по сути.
- `MainPagesFragment` и `MainPagesCompose.kt` тяжёлые, но в них есть реальная TV-специфика: rail/header/content focus choreography, restore поведения, скрытие/показ shell. Это не похоже на случайную кашу.

## 3. Что висит из legacy

| Файл / кусок | Что именно legacy | Насколько мешает | Нужно ли трогать сейчас |
| --- | --- | --- | --- |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/Screens.kt`, `app-tv/src/main/java/ru/radiationx/anilibria/screen/launcher/MainActivity.kt`, почти все `*Fragment.kt` | Fragment + Cicerone shell остаётся основным entrypoint для экранов | Средне: добавляет host boilerplate и state mirroring, но модуль уже адаптирован к этому | Нет большого переписывания сейчас. Трогать только локально, когда можно убрать лишний glue |
| `app-tv/src/main/AndroidManifest.xml`, `app-tv/src/main/res/xml/searchable.xml`, `app-tv/src/main/java/ru/radiationx/anilibria/contentprovider/suggestions/*` | TV platform / global search legacy seam, исторически связанный с Leanback/TV ecosystem | Низко: это интеграция платформы, а не мешающий UI-legacy | Оставить |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/ComposePlayerScreen.kt` (`AndroidView { PlayerView(...) }`) | View interop в плеере | Низко-средне: это единственный заметный View bridge, но он оправдан Media3 surface | Оставить, не пытаться “дожать до pure Compose” |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/LifecycleViewModel.kt` | Старый lifecycle-aware base class вокруг `DefaultLifecycleObserver` | Низко-средне: запах старой архитектуры есть, но он стабильно встроен в fragment shell | Не трогать отдельно |
| `app-tv/src/main/java/ru/radiationx/anilibria/presentation/pagination/Paginator.kt`, `PaginatorEvent.kt`, `CardsPaginatorFactory.kt` | Похоже на пережиток старой pagination abstraction; в основном коде `app-tv` реально используется только `LoadMoreCardsComposer` + `PaginatorState` | Низко-средне: создаёт шум и ощущение недомигрированной infra | Да, после точечной проверки usages |
| `app-tv/src/main/res/values/colors.xml` | Остались `app_guidedstep_*` цвета и временные имена `temp_red4`, `temp_red_` | Низко: не ломает архитектуру, но явно засоряет модуль | Да, quick win |
| `app-tv/src/main/res/values/ids.xml` + `app-tv/src/androidTest/java/ru/radiationx/anilibria/smoke/AppTvSmokeTest.kt` | Похоже на хвосты pre-Compose shell (`title_orb`, `title_other`, `title_alert`, `title_controls`) | Средне: вводит в заблуждение и держит тест на устаревших допущениях | Да, quick win |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfiguringViewModel.kt` | `endConfiguring()` с закомментированным `router.exit()` | Низко: маленький миграционный хвост | Да, quick win |
| пустые `app-tv/src/main/res/layout` и `app-tv/src/main/res/animator` | Формальный хвост старого view/xml слоя | Низко | Да, можно подчистить |

## 4. Основные проблемные зоны

### 4.1. Повторяющийся section/focus scaffolding

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/main/MainCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/details/DetailCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/schedule/ScheduleCompose.kt`

В чём проблема:
- одни и те же идеи повторяются почти дословно: `findRestoreTarget`, `requestSectionFocus`, `keepItemVisible`, `focusRequestToken`, `visibilityRestoreToken`, ручной restore по item id/index, ручной transition между section rows.

Почему это проблема:
- это уже не “TV-специфика”, а размножившийся scaffolding;
- любое исправление focus/restore поведения надо дублировать в нескольких экранах;
- такие дубли особенно опасны на TV: баги проявляются не как crash, а как “иногда фокус не туда восстановился”.

Что лучше сделать:
- вынести общий `sectioned tv rows` primitive/coordinator для:
  - restore target resolution;
  - vertical section transfer;
  - visibility/scroll restore;
  - focus request tokens.
- это именно **medium refactor**, а не переписывание экранов: UI-контент рядов можно оставить feature-specific.

### 4.2. Дублирование filter/grid flow

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/CatalogCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFormViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesViewModel.kt`

В чём проблема:
- практически одинаковый Compose flow вокруг filter chips, picker, grid focus restore, state-panel focus и picker restore;
- view models тоже дублируют filter-picker сценарий: open/toggle/apply/reset/dismiss/sync labels/options.

Почему это проблема:
- это уже заметная миграционная вилка: search и favorites решают одну TV-задачу двумя почти одинаковыми реализациями;
- дальше любое изменение picker UX или focus policy будет расходиться.

Что лучше сделать:
- собрать shared filter state engine на базе уже существующих `TvCollection*` моделей;
- поверх него сделать общий scaffold “filters + picker + content grid/state panel”;
- при этом business rules оставить разными: search-form и favorites-data одинаковыми быть не должны.

### 4.3. Migration glue в main shell page content

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/mainpages/MainShellPageContent.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/main/MainPageContent.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingPageContent.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesPageContent.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/profile/ProfilePageContent.kt`

В чём проблема:
- сам `MainShellPageContent` как seam ещё полезен, но конкретно `MainPageContent` и `WatchingPageContent` уже слишком много делают:
  - получают несколько view models;
  - держат mutable UI state;
  - собирают section order;
  - зеркалят titles/items;
  - ещё и управляют background restore/focus tokens.

Почему это проблема:
- ownership UI state становится неочевидным: часть в VM, часть в wrapper, часть в compose screen;
- `MainPageContent` и `WatchingPageContent` очень похожи по структуре, что выглядит именно как промежуточный миграционный слой;
- при этом `WatchingFavoritesPageContent` и `ProfilePageContent` уже заметно тоньше, то есть слой ведёт себя непоследовательно.

Что лучше сделать:
- не убирать `MainShellPageContent` целиком, пока жив shell;
- но постепенно сокращать wrapper-слой до ролей “bind + route callbacks”, а сборку section UI state двигать ближе к feature state holder или самому screen model.

### 4.4. Shared TV primitives застряли в feature-пакетах

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingCommonCompose.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingUiDefaults.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/main/MainCompose.kt` (`MainSectionUiModel`, `MainSectionBlock`)
- многочисленные импорты этих сущностей из других feature-пакетов

В чём проблема:
- по факту `WatchingFocusableSurface`, `rememberWatchingPalette`, `WatchingDescriptionBar`, `TvCardScreenHorizontalPadding` и даже `MainSectionUiModel` уже используются далеко за пределами “watching” или “main”;
- shared infrastructure замаскирована под feature code.

Почему это проблема:
- границы ответственности размываются: трудно понять, где infra, а где feature;
- нейминг врёт про ownership;
- это усложняет дальнейшую чистку, потому что любое общее переиспользование выглядит как “тащим код из соседней фичи”.

Что лучше сделать:
- перенести реально общие TV primitives в нейтральный пакет, например `ui/tv` или `screen/common/tv`;
- отдельно переименовать `MainSectionUiModel` / `MainSectionBlock` в нейтральные section-row primitives без изменения поведения.

### 4.5. Controller/event-bus seams в search/suggestions

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchController.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/SearchFormViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsController.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsResultViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsRowsViewModel.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/di/SearchModule.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/suggestions/SuggestionsFragment.kt`

В чём проблема:
- для связи между кусками feature state используются тонкие `EventFlow`-контроллеры;
- в search это controller живёт через module-level DI, в suggestions он вообще локально поднимается в fragment.

Почему это проблема:
- data flow становится менее очевидным, чем мог бы быть в Compose-first модуле;
- это типичный migration seam: новый UI уже декларативный, а координация всё ещё через bus;
- в suggestions это особенно заметно: один VM ищет, другой VM слушает bus и перестраивает row availability.

Что лучше сделать:
- схлопнуть эти контроллеры в shared feature state/VM coordination;
- для search минимумом может стать единый feature state holder для form + result;
- для suggestions разумно держать query/result/row-visibility в одном состоянии, а не прокидывать result через bus.

### 4.6. Player уже на правильной стороне миграции, но файлы слишком плотные

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/ComposePlayerScreen.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/BasePlayerFragment.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/PlayerFragment.kt`

В чём проблема:
- `ComposePlayerScreen.kt` на 1485 строк смешивает surface host, overlay, controls, picker, progress, quick actions, auto-hide, key handling и focus wiring;
- `BasePlayerFragment.kt` держит большой набор `mutableStateOf` и вручную бриджит runtime/player lifecycle в compose state;
- `PlayerFragment.kt` сейчас единственный наследник `BasePlayerFragment`, так что часть абстракции уже выглядит избыточной.

Почему это проблема:
- это усложняет локальные правки и повышает риск регрессий в focus/overlay behavior;
- boundaries player screen / overlay / controls / focus handling отделены не до конца.

Что лучше сделать:
- **не** трогать `PlayerView` / `AndroidView` seam;
- разрезать `ComposePlayerScreen.kt` по concern-ам: surface host, chrome, picker, progress, quick actions;
- в `BasePlayerFragment.kt` по возможности сгруппировать compose-facing state в более крупные UI models, а не в десятки разрозненных полей;
- вопрос “нужен ли отдельный `BasePlayerFragment` при одном наследнике” имеет смысл, но это низкий приоритет по сравнению с разрезанием UI-файла.

### 4.7. Auth/guided flow уже почти очищен, legacy там минимальный

Где находится:
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/auth/AuthFragment.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfigFragment.kt`
- `app-tv/src/main/java/ru/radiationx/anilibria/screen/update/UpdateFragment.kt`
- `app-tv/src/main/res/values/colors.xml`

В чём проблема:
- это уже не Leanback guided flow, но fragment hosts ещё вручную mirror-ят state через `mutableStateOf`;
- `colors.xml` хранит `app_guidedstep_*`, хотя guidedstep API в модуле уже не используется.

Почему это проблема:
- архитектурной катастрофы здесь нет;
- проблема в основном косметическая и в том, что путь миграции всё ещё читается в ресурсе и host boilerplate.

Что лучше сделать:
- guidedstep-цвета удалить, если usage не появится извне;
- fragment hosts пока оставить, но не плодить такой же pattern дальше;
- если когда-то трогать этот участок, то не “переписывать auth flow”, а свести `Fragment -> mutableState mirror -> Compose` к более тонкому route-host варианту.

### 4.8. Особенно подозрительные файлы

| Файл | Почему подозрителен | Комментарий |
| --- | --- | --- |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/ComposePlayerScreen.kt` | 1485 строк, смешение UI и focus/control orchestration | Главный кандидат на разрезание |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/player/BasePlayerFragment.kt` | lifecycle + player runtime + compose state bridge в одном месте | Нужен для runtime, но слишком плотный |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/details/ReleaseDetailsRowCompose.kt` | 984 строки | Большой, но пока выглядит скорее тяжёлым, чем грязным |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/mainpages/MainPagesCompose.kt` | 725 строк | Тяжёлый shell, но в основном оправдан TV-спецификой |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesViewModel.kt` | 625 строк, смешаны auth gating, sync, cache, filters, sort, rebuild | Сильный кандидат на выделение filter/rebuild кусков |
| `app-tv/src/main/java/ru/radiationx/anilibria/screen/search/CatalogCompose.kt` и `app-tv/src/main/java/ru/radiationx/anilibria/screen/watching/WatchingFavoritesCompose.kt` | почти один и тот же focus/filter/grid scaffold | Скорее проблема дублирования, чем каждой реализации по отдельности |
| `app-tv/src/main/java/ru/radiationx/anilibria/ui/compose/TvOverlayCompose.kt` | 585 строк shared overlay toolkit | Пока полезно, но важно не превращать его в “всё TV UI в одном файле” |

## 5. Приоритетный план рефакторинга

### Quick wins

- Удалить stale guided-step ресурсы и временные имена из `app-tv/src/main/res/values/colors.xml`: `app_guidedstep_actions_background`, `app_guidedstep_subactions_background`, `temp_red4`, `temp_red_`.
- Пересобрать `app-tv/src/main/res/values/ids.xml` под реальные текущие usage и обновить `app-tv/src/androidTest/java/ru/radiationx/anilibria/smoke/AppTvSmokeTest.kt`, который сейчас всё ещё ждёт `R.id.title_orb`.
- Удалить пустые `app-tv/src/main/res/layout` и `app-tv/src/main/res/animator`.
- Вычистить явный dead tail в `app-tv/src/main/java/ru/radiationx/anilibria/screen/config/ConfiguringViewModel.kt` (`endConfiguring()` с закомментированным `router.exit()`).
- Проверить и удалить неиспользуемые классы из `app-tv/src/main/java/ru/radiationx/anilibria/presentation/pagination/`, если не найдётся скрытых внешних usage вне модуля.

### Medium refactors

- Вынести shared section/focus coordinator из `MainCompose.kt`, `WatchingCompose.kt`, `DetailCompose.kt`, `SuggestionsCompose.kt`, `ScheduleCompose.kt`.
- Вынести общий filter/grid scaffold и базовый filter-picker state engine из `CatalogCompose.kt`, `WatchingFavoritesCompose.kt`, `SearchFormViewModel.kt`, `WatchingFavoritesViewModel.kt`.
- Сократить роль `MainPageContent.kt` и `WatchingPageContent.kt`, чтобы эти классы перестали быть state-aggregation слоями поверх нескольких view models.
- Перенести общие TV primitives из `screen.watching` и `screen.main` в нейтральные shared пакеты.
- Схлопнуть `SearchController` и `SuggestionsController` в более прямой feature state flow.
- Разрезать `ComposePlayerScreen.kt` и слегка оздоровить `BasePlayerFragment.kt`, не меняя runtime-модель плеера.

### Risky / not worth now

- Переводить всю навигацию с `FragmentScreen`/Cicerone на Compose Navigation.
- Убирать fragment-host уровень целиком.
- Пытаться заменить `PlayerView` на pure Compose player surface.
- Перепридумывать `GradientBackgroundManager` в рамках этого аудита.
- Полностью переписывать `MainPagesFragment` shell: он сложный, но сложность там в основном предметная.

## 6. Not included

- Не рекомендую сейчас трогать `app-tv/src/main/AndroidManifest.xml`, `app-tv/src/main/res/xml/searchable.xml` и `app-tv/src/main/java/ru/radiationx/anilibria/contentprovider/suggestions/*`, потому что это нормальный platform seam для Android TV global search, а не мешающий migration artifact.
- Не рекомендую выдавливать `PlayerView` из `screen/player/ComposePlayerScreen.kt`: для Media3/TV это нормальный interop, и он уже хорошо локализован.
- Не рекомендую объявлять `Fragment`-навигацию главной проблемой модуля. Она legacy по стилю, но не главный источник текущей каши; реальные проблемы лежат выше, в duplicated compose scaffolding и migration glue.
- Не рекомендую выносить `GradientBackgroundManager` в список “legacy на удаление”. Это некрасивая по форме, но полезная и понятная TV-runtime abstraction.
- Не рекомендую отдельно ломать `DetailHeaderViewModel` только из-за комментария про `local progress (legacy)`. Само dual-source поведение там выглядит оправданным: локальный прогресс как primary и remote progress как fallback для TV-сценария.
- Не рекомендую переписывать auth/config/update flow заново. Там уже почти нет реального Leanback legacy; достаточно подчистить хвосты и не плодить новый fragment-state boilerplate.
