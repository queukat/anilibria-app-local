# `app-tv` Quality Pass Plan

## Context anchor

- Проход делается по текущему состоянию `app-tv` и связанному root/config Gradle setup, а не по старым отчётам.
- Цель прохода: получить честный и воспроизводимый quality pass без опоры на старые baseline/suppression артефакты.
- Основной scope: `app-tv`; root/build/config можно трогать только там, где это реально нужно для quality setup.
- Worktree уже грязный внутри `app-tv` и `docs`, поэтому проход должен быть узким, проверяемым и не должен откатывать чужие изменения.
- Базовый принцип принятия решений: prefer real fixes over suppressions, prefer narrow safe changes over broad churn.

## Inventory snapshot

### Уже есть в текущем setup

- Android Lint от AGP:
  - `:app-tv:lint`
  - `:app-tv:lintDebug`
  - `:app-tv:lintRelease`
  - `:app-tv:lintVitalRelease`
  - `:app-tv:updateLintBaseline*`
- Unit tests:
  - `:app-tv:testDebugUnitTest`
  - `:app-tv:testReleaseUnitTest`
- Compile/build sanity tasks:
  - `:app-tv:compileDebugKotlin`
  - `:app-tv:compileReleaseKotlin`
  - `:app-tv:assemble*`
- Dependency inspection/listing:
  - `:app-tv:dependencies`
  - `:app-tv:dependencyInsight`
  - `:app-tv:analyzeDebugDependencies`
  - `:app-tv:analyzeReleaseDependencies`
- Compose используется в самом модуле, поэтому Compose-related signal уже частично должен приходить через Android Lint.
- В репо уже есть detekt-конфиг `config/detekt/detekt.yml`, а в `app-tv` лежит historical `detekt-baseline.xml`.

### Отсутствует или неактивно

- Активный Detekt Gradle plugin/task в текущем build не найден:
  - ни `:app-tv:tasks --all`, ни root `tasks --all` не показывают detekt tasks.
- `ktlint` не найден.
- Spotless не найден.
- Ktlint Gradle plugin не найден.
- Специализированный dependency analysis / unused deps quality gate не найден.
- Отдельный `lint.xml` не найден.
- `.editorconfig` не найден.

## Baseline / suppress / report inventory

### Найденные baseline/config артефакты

- `app-tv/lint-baseline.xml`
  - файл существует;
  - сейчас пустой, но всё равно участвует как baseline-артефакт AGP path.
- `app-tv/detekt-baseline.xml`
  - содержит большой historical список suppressions/findings;
  - в текущем build detekt task не активен, значит baseline сейчас выглядит как legacy artifact, а не как реально используемый check.
- `config/detekt/detekt.yml`
  - есть минимальный detekt config;
  - пока не видно активной wiring к Gradle tasks.

### Найденные stale/generated report артефакты

- `app-tv/build/reports/lint-results-debug.html`
- `app-tv/build/reports/lint-results-debug.txt`
- `app-tv/build/reports/lint-results-debug.xml`
- `app-tv/build/reports/detekt/detekt.html`
- `app-tv/build/reports/detekt/detekt.xml`
- `app-tv/build/reports/tests/testDebugUnitTest/**`
- `build/reports/problems/problems-report.html`
- `app-tv/assembleDebug-warning-all.log`
- `app-tv/assembleRelease-warning-all.log`

### Найденные локальные suppressions в `app-tv`

- Массового слоя `@Suppress` не найдено.
- Подтверждены точечные suppressions:
  - `app-tv/src/test/java/ru/radiationx/anilibria/screen/player/PlayerViewModelTest.kt`
    - два `@Suppress("UNCHECKED_CAST")`
- Подтверждены manifest-level lint ignores:
  - `app-tv/src/debug/AndroidManifest.xml`
  - `app-tv/src/main/AndroidManifest.xml`
- Отдельных stale suppression config файлов кроме baseline пока не видно.

## Checks to use in this pass

### Обязательный набор

1. `:app-tv:lint`
2. `:app-tv:lintDebug`
3. `:app-tv:testDebugUnitTest`
4. compile/build sanity checks для `app-tv`
5. Detekt, но только после честного подключения без baseline
6. Один style tool, если его сейчас нет:
   - предпочтительно `ktlint` как минимальный прозрачный вариант

### Что хочу подтвердить отдельно

- что `app-tv` реально проходит lint без опоры на `lint-baseline.xml`;
- что detekt действительно запускается, а не “существует только файлами конфигурации”;
- что style tool реально интегрирован в Gradle path и не создаёт скрытый suppress/baseline слой;
- что часть качества не “зелёная” просто потому, что нужные задачи вообще не wired.

## Что не включено сейчас и почему

- Отдельный dependency-analysis plugin для unused deps
  - reason: это уже следующий уровень ROI; сначала нужно поднять честный базовый набор quality gates.
- Экзотические Compose-specific rule packs поверх Android Lint
  - reason: сначала нужно использовать уже доступный lint signal и только потом решать, есть ли реальный gap.
- Широкий multi-module cleanup исторических baselines
  - reason: текущий проход должен оставаться сфокусированным на `app-tv`.
- Архитектурный rewrite ради style/smell предупреждений
  - reason: нарушает цель прохода и повышает runtime risk.

## Planned order

1. Зафиксировать inventory в docs.
2. Убрать stale report artifacts и старую baseline-зависимость из активного quality path.
3. Добавить недостающие проверки минимальным и прозрачным способом.
4. Прогнать полный набор checks по `app-tv`.
5. Исправить безопасные реальные проблемы.
6. Повторно прогнать checks.
7. Зафиксировать остаточные issues и ограничения в progress/report.

## Resolution After Inventory

- В ходе прохода для `app-tv` были реально подняты и подключены:
  - Detekt;
  - `ktlint` через Ktlint Gradle plugin.
- Для финального reproducible core pass используются:
  - `:app-tv:lint`
  - `:app-tv:lintDebug`
  - `:app-tv:detekt`
  - `:app-tv:testDebugUnitTest`
  - `:app-tv:compileDebugKotlin`
  - `:app-tv:compileReleaseKotlin`
- Для целевого `ktlint` follow-up был добавлен минимальный root `.editorconfig`:
  - правило `ktlint_function_naming_ignore_when_annotated_with = Composable` scoped к `app-tv/src/**/*.kt`;
  - это не suppress “ради тишины”, а выравнивание `ktlint` с Compose PascalCase naming.
- После этого follow-up:
  - `:app-tv:ktlintCheck` зелёный;
  - `:app-tv:check` зелёный;
  - без нового baseline и без возврата старых suppression-механизмов.
- `analyzeDebugDependencies` / `analyzeReleaseDependencies` были отдельно разобраны и затем починены:
  - причина оказалась в AGP `AnalyzeDependenciesTask`, где `DependenciesAnalyzer` всё ещё использует `Opcodes.ASM7`;
  - при JVM bytecode target 17 Kotlin `sealed` classes/interfaces получали `PermittedSubclasses`, и task падал;
  - итоговый fix: сохранить JDK toolchain 17, но понизить bytecode target до 11 для `app-tv` и его project dependency graph.
- Новый baseline не создаётся.
- Старые baseline-файлы не должны участвовать в финальном quality path.
- Единственный сознательный компромисс по suppression-like механике:
  - два тестовых файла были узко исключены из `ForbiddenImport` для `runBlocking`, потому что перевод именно этих тестов на `runTest` дал небезопасную поведенческую регрессию;
  - это нужно считать исключением, а не новой нормой.
