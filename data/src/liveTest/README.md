# Live API Tests (`data/src/liveTest`)

This directory contains tests that can call a real AniLiberty server.

## Test Types

- `*LiveContractTest`:
  - Purpose: live contract checks for API compatibility.
  - Gradle task: `:data:liveApiTest`.
- `*SmokeTest`:
  - Purpose: quick read-only health checks against production-like API.
  - Gradle task: `:data:liveApiSmokeTest`.

By default, regular unit test tasks exclude both `*LiveContractTest` and `*SmokeTest`.

## Safety Rules

- Never hardcode secrets in repository code, fixtures, or Gradle files.
- Pass tokens only through environment variables.
- Do not print `Authorization` headers, tokens, or user-identifying payloads in test logs.
- Keep smoke tests non-destructive by default.
- Write operations are opt-in and must require explicit env flags.

## Required Environment Variables

- `ANILIBERTY_BASE_URL`:
  - Optional.
  - Default: `https://aniliberty.top`.
  - If provided without `/api/v1`, tests append it automatically.
- `ANILIBERTY_TOKEN`:
  - Required for `*SmokeTest` execution.
  - If missing, smoke tests are skipped via JUnit `Assume`.

## Optional Environment Variables

- `ANILIBERTY_E2E_WRITE`:
  - Optional.
  - Set to `1` to enable write smoke checks.
- `ANILIBERTY_TEST_EPISODE_ID`:
  - Required only when `ANILIBERTY_E2E_WRITE=1`.
  - Must reference a safe test episode id.

## Run Commands

### PowerShell (Windows)

```powershell
$env:GRADLE_USER_HOME='.gradle-user-home'
$env:ANDROID_USER_HOME='.android-user-home'

# Regular unit tests (live tests excluded by default)
./gradlew.bat :data:testDebugUnitTest --console=plain --stacktrace

# Live smoke (read-only if write flags are not set)
$env:ANILIBERTY_BASE_URL='https://aniliberty.top'
$env:ANILIBERTY_TOKEN='<TOKEN>'
./gradlew.bat :data:liveApiSmokeTest --console=plain --stacktrace
```

### Bash

```bash
export GRADLE_USER_HOME=.gradle-user-home
export ANDROID_USER_HOME=.android-user-home

# Regular unit tests (live tests excluded by default)
./gradlew :data:testDebugUnitTest --console=plain --stacktrace

# Live smoke (read-only if write flags are not set)
export ANILIBERTY_BASE_URL=https://aniliberty.top
export ANILIBERTY_TOKEN='<TOKEN>'
./gradlew :data:liveApiSmokeTest --console=plain --stacktrace
```

## Write Smoke Mode (Explicit Opt-In)

Use only when you intentionally validate write path behavior:

```powershell
$env:ANILIBERTY_E2E_WRITE='1'
$env:ANILIBERTY_TEST_EPISODE_ID='<SAFE_TEST_EPISODE_ID>'
./gradlew.bat :data:liveApiSmokeTest --console=plain --stacktrace
```

If either variable is missing, write smoke checks are skipped.

## CI Guidance

- Do not run live smoke in default CI pipelines.
- If enabled in a dedicated pipeline, inject secrets via CI secret store only.
- Prefer read-only smoke in scheduled jobs; run write smoke sparingly.
