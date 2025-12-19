package ru.radiationx.anilibria.common

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import timber.log.Timber
import javax.inject.Inject

class AniLibertyDetailsOverlay @Inject constructor() {

    private val host = "https://aniliberty.top"

    fun apply(base: LibriaDetails, v1: AniLibertyRelease): LibriaDetails {

        val titleRu = v1.name?.main.takeIfNotBlank() ?: base.titleRu
        val titleEn = (v1.name?.english ?: v1.name?.alternative).takeIfNotBlank() ?: base.titleEn

        val age = v1.ageRating?.label ?: v1.ageRating?.value
        val publish = v1.publishDay?.description ?: v1.publishDay?.value?.toString()
        val duration = v1.averageDurationOfEpisode
            ?.takeIf { it > 0 }
            ?.let { "$it мин." }

        val extraAddon = listOfNotNull(
            age?.let { "Рейтинг: $it" },
            duration,
        ).joinToString(" • ")

        Timber.tag("AniLibertyDetailsOverla")
            .d("ageRating=${v1.ageRating} publishDay=${v1.publishDay} duration=${v1.averageDurationOfEpisode}")
        Timber.tag("AniLibertyDetailsOverla").d("extraAddon='$extraAddon'")

        val extra = listOf(base.extra, extraAddon)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        val announceAddon = buildAnnounce(v1)
        val announce = listOf(base.announce, announceAddon)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" • ")

        // v1 часто отдаёт относительные пути (/storage/...), поэтому приводим к абсолютным
        val v1ImagePath = v1.poster?.optimized?.preview
            ?: v1.poster?.preview
            ?: v1.poster?.thumbnail

        val image = v1ImagePath.toAbsoluteAniLibertyUrl()
            ?: base.image

        val description = v1.description.takeIfNotBlank() ?: base.description

        return base.copy(
            titleRu = titleRu,
            titleEn = titleEn,
            extra = extra,
            announce = announce,
            image = image,
            description = description,
        )
    }

    private fun buildAnnounce(v1: AniLibertyRelease): String {
        val parts = mutableListOf<String>()

        if (v1.isBlockedByGeo == true) parts += "Недоступно в вашем регионе"
        if (v1.isBlockedByCopyrights == true) parts += "Недоступно по запросу правообладателя"

        v1.notification?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
        if (v1.isOngoing == false) parts += "Релиз завершен"
        if (v1.isInProduction == true) parts += "В производстве"

        return parts.distinct().joinToString(" • ")
    }

    private fun String?.takeIfNotBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.toAbsoluteAniLibertyUrl(): String? {
        val s = this?.trim().orEmpty()
        if (s.isEmpty()) return null
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.startsWith("//") -> "https:$s"
            s.startsWith("/") -> host + s
            else -> s
        }
    }
}
