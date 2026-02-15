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

        /**
         * Пресет под шапку деталей.
         *
         * Цель: убрать самые тяжёлые списки (episodes/members/torrents), оставив информацию,
         * достаточную для title/poster/extra (year, season, type, age, publish_day и т.п.).
         */
        val DetailsHeader: AniLibertyReleaseFields = AniLibertyReleaseFields(
            exclude = setOf(
                AniLibertyReleaseExclude.EPISODES,
                AniLibertyReleaseExclude.MEMBERS,
                AniLibertyReleaseExclude.TORRENTS,
            )
        )

        /**
         * Пресет под подсказки (TV GlobalSearch / Suggestions).
         *
         * В отличие от [DetailsHeader] дополнительно пытаемся "срезать" текстовые поля,
         * чтобы уменьшить размер ответа.
         *
         * Если бэкенд не поддержит excludeRaw для этих полей — он просто их проигнорирует,
         * и это не сломает функционал.
         */
        val Suggestions: AniLibertyReleaseFields = AniLibertyReleaseFields(
            exclude = setOf(
                AniLibertyReleaseExclude.EPISODES,
                AniLibertyReleaseExclude.MEMBERS,
                AniLibertyReleaseExclude.TORRENTS,
            ),
            excludeRaw = setOf(
                AniLibertyFieldName("description"),
                AniLibertyFieldName("notification"),
            )
        )
    }
}
