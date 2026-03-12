package ru.radiationx.anilibria.common

import ru.radiationx.data.entity.domain.release.Release

internal fun Release.resolveTvSeriesText(): String {
    val explicitSeries = series?.trim()?.takeIf { it.isNotEmpty() }
    if (explicitSeries != null) {
        return explicitSeries
    }

    episodes.size.takeIf { it > 0 }?.toString()?.let { return it }

    extractEpisodesCountFromTypeText(types.firstOrNull())?.let { return it }

    return when (statusCode) {
        Release.STATUS_CODE_COMPLETE -> "Завершен"
        Release.STATUS_CODE_PROGRESS -> "Онгоинг"
        Release.STATUS_CODE_NOT_ONGOING -> "Анонс"
        else -> if (days.isNotEmpty()) "Анонс" else "Неизвестно"
    }
}

internal fun extractEpisodesCountFromTypeText(typeText: String?): String? {
    if (typeText.isNullOrBlank()) {
        return null
    }
    val regex = Regex("""\((\d+)\s*эп""", RegexOption.IGNORE_CASE)
    return regex.find(typeText)?.groupValues?.getOrNull(1)
}
