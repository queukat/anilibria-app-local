# AniLibria
Клиент для сайта [AniLibria.tv](https://anilibria.tv/)

Мобильное приложение: [RuStore](https://www.rustore.ru/catalog/app/ru.radiationx.anilibria.app) | [Releases](https://github.com/anilibria/anilibria-app/releases?q=version)

Android TV приложение: [RuStore](https://www.rustore.ru/catalog/app/ru.radiationx.anilibria.app.tv) | [Releases](https://github.com/anilibria/anilibria-app/releases?q=tv)

# Сборка модулей
В репозитории есть два app-модуля: `:app-mobile` и `:app-tv`.
Используйте явные задачи модулей, потому что `:app:*` неоднозначен и не запускается.

- TV: `./gradlew.bat :app-tv:assembleDebug :app-tv:testDebugUnitTest :app-tv:lintDebug`
- Mobile: `./gradlew.bat :app-mobile:assembleDebug :app-mobile:testDebugUnitTest :app-mobile:lintDebug`

# Лицензия #
Исходный код распостраняется под лицензией GPL v3

> Copyright (C) 2017-2024  Evgeniy Nizamiev [(radiationx@yandex.ru)](mailto:radiationx@yandex.ru)
> 
> This program is free software; you can redistribute it and/or modify
> it under the terms of the GNU General Public License as published by
> the Free Software Foundation; either version 3 of the License.
