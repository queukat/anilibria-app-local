package ru.radiationx.anilibria.screen.auth

import ru.radiationx.data.entity.domain.auth.CriticalSecureStorageUnavailableException

internal fun mapAuthErrorMessage(error: Throwable): String {
    return when (error) {
        is CriticalSecureStorageUnavailableException ->
            "Безопасное хранилище недоступно. Включите защиту экрана устройства и повторите вход."

        else -> error.message.orEmpty()
    }
}
