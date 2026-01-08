package ru.radiationx.data.datasource.remote.aniliberty

enum class AniLibertyReleaseInclude(val apiName: String) {
    GENRES("genres"),
    AGE_RATING("age_rating"),
    SPONSOR("sponsor"),
    LATEST_EPISODE("latest_episode"),
}

enum class AniLibertyReleaseExclude(val apiName: String) {
    EPISODES("episodes"),
    MEMBERS("members"),
    TORRENTS("torrents"),
}

data class AniLibertyReleaseFields(
    val include: Set<AniLibertyReleaseInclude> = emptySet(),
    val exclude: Set<AniLibertyReleaseExclude> = emptySet(),
    val excludeRaw: Set<AniLibertyFieldName> = emptySet(),
) : AniLibertyFieldSpec {

    override fun includeParam(): String? =
        include.takeIf { it.isNotEmpty() }?.joinToString(",") { it.apiName }

    override fun excludeParam(): String? {
        val items = buildSet {
            exclude.forEach { add(it.apiName) }
            excludeRaw.forEach { add(it.value) }
        }
        return items.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    companion object Presets {
        val DetailsHeader: AniLibertyReleaseFields = AniLibertyReleaseFields(
            exclude = setOf(
                AniLibertyReleaseExclude.EPISODES,
                AniLibertyReleaseExclude.MEMBERS,
                AniLibertyReleaseExclude.TORRENTS,
            )
        )
    }
}
