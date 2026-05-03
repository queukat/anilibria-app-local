package ru.radiationx.data.datasource.remote.aniliberty

data class AniLibertyQueryFields(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    fun includeParam(): String? = include.takeIf { it.isNotEmpty() }?.joinToString(",")

    fun excludeParam(): String? = exclude.takeIf { it.isNotEmpty() }?.joinToString(",")
}
