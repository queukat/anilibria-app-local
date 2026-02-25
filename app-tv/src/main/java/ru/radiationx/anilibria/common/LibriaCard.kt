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
        val relativePart = Date(timestampSec * 1000L).relativeDate(context).decapitalizeDefault()
        val prefix = relativePrefix.orEmpty().trim()
        val dynamicPart = if (prefix.isEmpty()) {
            relativePart
        } else {
            "$prefix $relativePart"
        }
        if (description.isBlank()) {
            return dynamicPart
        }
        if (prefix.isNotEmpty()) {
            val marker = "$prefix "
            val markerIndex = description.indexOf(marker)
            if (markerIndex >= 0) {
                val staticPart = description
                    .substring(0, markerIndex)
                    .trim()
                    .trimEnd('•')
                    .trim()
                return if (staticPart.isEmpty()) dynamicPart else "$staticPart • $dynamicPart"
            }
        }
        return "$description • $dynamicPart"
    }

    sealed class Type {
        data class Release(val releaseId: ReleaseId) : Type()
        data class Youtube(val link: String) : Type()
    }
}
