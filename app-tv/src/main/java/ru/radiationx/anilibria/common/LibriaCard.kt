package ru.radiationx.anilibria.common

import ru.radiationx.data.entity.domain.types.ReleaseId

data class LibriaCard(
    val title: String,
    val description: String,
    val image: String,
    val type: Type
) : CardItem {

    override fun getId(): Int = when (val t = type) {
        is LibriaCard.Type.Release -> t.releaseId.id
        is LibriaCard.Type.Youtube -> t.link.hashCode()
    }


    sealed class Type {
        data class Release(val releaseId: ReleaseId) : Type()
        data class Youtube(val link: String) : Type()
    }
}