package ru.radiationx.anilibria.common

import ru.radiationx.data.interactors.tv.DetailHeaderRemoteData
import timber.log.Timber
import javax.inject.Inject

class AniLibertyDetailsOverlay @Inject constructor() {

    private val host = "https://aniliberty.top"
    private val durationRegex = Regex("""\b\d+\s*мин\.?\b""", RegexOption.IGNORE_CASE)

    fun apply(base: LibriaDetails, v1: DetailHeaderRemoteData): LibriaDetails {

        val titleRu = v1.titleRu.takeIfNotBlank() ?: base.titleRu
        val titleEn = v1.titleEn.takeIfNotBlank() ?: base.titleEn

        val baseExtra = base.extra

        val durationRaw = v1.averageDurationOfEpisode
            ?.takeIf { it > 0 }
            ?.let { "$it мин." }

        val durationToAdd = durationRaw?.takeIf { !durationRegex.containsMatchIn(baseExtra) }

        val extraAddon = listOfNotNull(
            durationToAdd,
        ).joinToString(" • ")


        Timber.tag("AniLibertyDetailsOverlay")
            .d("duration=${v1.averageDurationOfEpisode}")
        Timber.tag("AniLibertyDetailsOverlay").d("extraAddon='$extraAddon'")

        val extra = listOf(base.extra, extraAddon)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        val announceAddon = buildAnnounce(v1)
        val announce = listOf(base.announce, announceAddon)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" • ")

        // v1 often returns relative paths (/storage/...), normalize to absolute
        val v1ImagePath = v1.posterPreview
            ?: v1.posterThumbnail

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

    private fun buildAnnounce(v1: DetailHeaderRemoteData): String {
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
