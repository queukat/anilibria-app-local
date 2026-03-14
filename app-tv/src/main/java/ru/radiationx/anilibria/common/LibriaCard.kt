package ru.radiationx.anilibria.common

import android.content.Context
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.shared.ktx.android.relativeDate
import ru.radiationx.shared.ktx.decapitalizeDefault
import java.util.Date

data class LibriaCard(
    val title: String,
    val description: String,
    val image: String,
    val type: Type,
    val relativeTimestampSec: Long? = null,
    val relativePrefix: String? = null,
) : CardItem {

    override fun getId(): Int = when (val t = type) {
        is LibriaCard.Type.Release -> t.releaseId.id
        is LibriaCard.Type.Youtube -> t.link.hashCode()
    }

    fun resolveDescription(context: Context): String {
        val timestampSec = relativeTimestampSec ?: return description
        val relativePart = Date(timestampSec * MILLIS_IN_SECOND).relativeDate(context).decapitalizeDefault()
        val prefix = relativePrefix.orEmpty().trim()
        val dynamicPart = if (prefix.isEmpty()) {
            relativePart
        } else {
            "$prefix $relativePart"
        }

        val mergedDescription = if (description.isBlank()) {
            dynamicPart
        } else {
            val staticPart = prefix
                .takeIf { it.isNotEmpty() }
                ?.let { nonBlankPrefix ->
                    val markerIndex = description.indexOf("$nonBlankPrefix ")
                    description
                        .takeIf { markerIndex >= 0 }
                        ?.substring(0, markerIndex)
                        ?.trim()
                        ?.trimEnd('•')
                        ?.trim()
                }
            if (staticPart == null) {
                "$description • $dynamicPart"
            } else if (staticPart.isEmpty()) {
                dynamicPart
            } else {
                "$staticPart • $dynamicPart"
            }
        }

        return mergedDescription
    }

    sealed class Type {
        data class Release(val releaseId: ReleaseId) : Type()
        data class Youtube(val link: String) : Type()
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1000L
    }
}
