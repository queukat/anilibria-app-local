# AniLibria

<!-- public-repo-status -->
> Status: Local source fork. This repository is for source review and personal builds only; Android downloads are not published through GitHub Releases here.

Клиент для сайта [AniLibria.tv](https://anilibria.tv/)

Мобильное приложение: [RuStore](https://www.rustore.ru/catalog/app/ru.radiationx.anilibria.app)

Android TV приложение: [RuStore](https://www.rustore.ru/catalog/app/ru.radiationx.anilibria.app.tv)

# Сборка модулей
В репозитории есть два app-модуля: `:app-mobile` и `:app-tv`.
Используйте явные задачи модулей, потому что `:app:*` неоднозначен и не запускается.

- TV: `./gradlew.bat :app-tv:assembleDebug :app-tv:testDebugUnitTest :app-tv:lintDebug`
- Mobile: `./gradlew.bat :app-mobile:assembleDebug :app-mobile:testDebugUnitTest :app-mobile:lintDebug`

# Release signing (`:app-tv`)
Для release-подписи TV используются только ENV/Gradle properties (ключи не хранятся в репозитории):

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Локальная signed-сборка (PowerShell пример):

```powershell
$env:RELEASE_STORE_FILE="C:\keys\tv-release.jks"
$env:RELEASE_STORE_PASSWORD="***"
$env:RELEASE_KEY_ALIAS="tv_release"
$env:RELEASE_KEY_PASSWORD="***"
./gradlew.bat :app-tv:assembleRelease :app-tv:signingReport --console=plain
```

Если переменные/свойства не заданы, `:app-tv:assembleRelease` собирает unsigned release (`signingReport`: `Config: null`), и debug keystore для release не используется.

# Лицензия #
Исходный код распостраняется под лицензией GPL v3

> Copyright (C) 2017-2024  Evgeniy Nizamiev [(radiationx@yandex.ru)](mailto:radiationx@yandex.ru)
> 
> This program is free software; you can redistribute it and/or modify
> it under the terms of the GNU General Public License as published by
> the Free Software Foundation; either version 3 of the License.
