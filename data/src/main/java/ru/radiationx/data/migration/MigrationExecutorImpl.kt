package ru.radiationx.data.migration

import timber.log.Timber
import javax.inject.Inject

class MigrationExecutorImpl
    @Inject
    constructor() : MigrationExecutor {
        override fun execute(
            current: Int,
            lastSaved: Int,
            history: List<Int>,
        ) {
            // Сейчас миграции не описаны — делаем безопасный no-op.
            // Когда появятся реальные шаги миграций, их удобно выполнять тут по диапазону версий.
            Timber.i(
                "MigrationExecutor.execute: lastSaved=%d, current=%d, history=%s",
                lastSaved,
                current,
                history.joinToString(prefix = "[", postfix = "]"),
            )
        }
    }
