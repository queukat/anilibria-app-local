package ru.radiationx.data.entity.domain.auth

sealed class CriticalSecureStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class CriticalSecureStorageUnavailableException(
    cause: Throwable? = null,
) : CriticalSecureStorageException(
    message = "Secure token storage is unavailable. Enable device lock screen security and sign in again.",
    cause = cause,
)
