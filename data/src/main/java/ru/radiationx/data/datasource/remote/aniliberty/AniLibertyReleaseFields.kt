package ru.radiationx.data.datasource.remote.aniliberty

/**
 * include-поля, которые реально могут быть полезны как "доп. расширения".
 * Тяжёлые поля тут не держим.
 */
enum class AniLibertyReleaseInclude(val apiName: String) {
    GENRES("genres"),
    AGE_RATING("age_rating"),
    SPONSOR("sponsor"),
    LATEST_EPISODE("latest_episode"),
}

/**
 * exclude-поля, которые имеет смысл типизировать (обычно тяжёлые/редко нужные).
 * Не пытаемся покрыть вообще всё — для этого остаётся excludeRaw.
 */
enum class AniLibertyReleaseExclude(val apiName: String) {
    EPISODES("episodes"),
    MEMBERS("members"),
    TORRENTS("torrents"),
}

/**
 * Обёртка для include/exclude.
 *
 * Важно:
 * - excludeRaw оставляем строками, чтобы исключать любые поля без enum.
 * - exclude (enum) — для частых и безопасных кейсов.
 * - include используем только если ты точно хочешь "получить только эти поля".
 */
data class AniLibertyReleaseFields(
    val include: Set<AniLibertyReleaseInclude> = emptySet(),

    val exclude: Set<AniLibertyReleaseExclude> = emptySet(),
    val excludeRaw: Set<String> = emptySet(),
) {
    fun includeParam(): String? =
        include.takeIf { it.isNotEmpty() }?.joinToString(",") { it.apiName }

    fun excludeParam(): String? {
        val items = buildSet {
            exclude.forEach { add(it.apiName) }
            excludeRaw.forEach { add(it) }
        }
        return items.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    companion object Presets {

        /**
         * Для «шапки» деталей:
         * - НЕ используем include, иначе API вернёт только include-поля (как у тебя в логах)
         * - просто вырезаем тяжёлое через exclude
         */
        val DetailsHeader = AniLibertyReleaseFields(
            exclude = setOf(
                AniLibertyReleaseExclude.EPISODES,
                AniLibertyReleaseExclude.MEMBERS,
                AniLibertyReleaseExclude.TORRENTS,
            )
        )
    }
}
