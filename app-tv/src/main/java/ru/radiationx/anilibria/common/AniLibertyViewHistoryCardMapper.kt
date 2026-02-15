package ru.radiationx.anilibria.common

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserViewHistoryItem
import ru.radiationx.data.entity.domain.types.ReleaseId
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Small UI mapper for AniLiberty /accounts/users/me/views/history items.
 *
 * Keeps formatting logic (ordinal/time/title/poster) in one place to reuse across TV screens.
 */
internal object AniLibertyViewHistoryCardMapper {

    fun toContinueCardOrNull(item: AniLibertyUserViewHistoryItem): LibriaCard? {
        // Continue = "not watched yet" entries (filter is usually done by caller).
        return toBaseCardOrNull(item) { episodeOrdinal, timeText, _ ->
            when {
                episodeOrdinal != null && timeText != null ->
                    "Вы остановились на серии $episodeOrdinal • $timeText"

                episodeOrdinal != null ->
                    "Вы остановились на серии $episodeOrdinal"

                else -> ""
            }
        }
    }

    fun toHistoryCardOrNull(item: AniLibertyUserViewHistoryItem): LibriaCard? {
        return toBaseCardOrNull(item) { episodeOrdinal, timeText, isWatched ->
            when {
                isWatched && episodeOrdinal != null ->
                    "Просмотрено • серия $episodeOrdinal"

                isWatched ->
                    "Просмотрено"

                episodeOrdinal != null && timeText != null ->
                    "Вы остановились на серии $episodeOrdinal • $timeText"

                episodeOrdinal != null ->
                    "Вы остановились на серии $episodeOrdinal"

                else -> ""
            }
        }
    }

    private fun toBaseCardOrNull(
        item: AniLibertyUserViewHistoryItem,
        descriptionBuilder: (episodeOrdinal: String?, timeText: String?, isWatched: Boolean) -> String,
    ): LibriaCard? {
        val release = item.release ?: return null
        val releaseId = release.id?.value ?: return null

        val title = release.name?.main
            ?: release.name?.english
            ?: release.name?.alternative
            ?: "id$releaseId"

        val imageRaw = release.poster?.optimized?.preview
            ?: release.poster?.optimized?.thumbnail
            ?: release.poster?.preview
            ?: release.poster?.thumbnail
            ?: ""

        // Важно: AniLiberty может отдавать относительные пути `/...`
        val image = imageRaw.toAbsoluteAniLibertyUrl().orEmpty()

        val episodeNumber = item.episode?.ordinal?.let(::formatEpisodeOrdinal)
        val timeText = item.time?.let(::formatSeconds)
        val watched = item.isWatched == true

        return LibriaCard(
            title = title,
            description = descriptionBuilder(episodeNumber, timeText, watched),
            image = image,
            type = LibriaCard.Type.Release(ReleaseId(releaseId)),
        )
    }

    private fun formatEpisodeOrdinal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun formatSeconds(value: Double): String {
        val totalSeconds = value.roundToLong().coerceAtLeast(0L)
        val hours = TimeUnit.SECONDS.toHours(totalSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
