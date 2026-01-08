package ru.radiationx.data.datasource.remote.aniliberty

@JvmInline
value class AniLibertyFieldName(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyFieldName must not be blank" }
    }

    override fun toString(): String = value
}

interface AniLibertyFieldSpec {
    fun includeParam(): String?
    fun excludeParam(): String?
}

data class AniLibertyFields(
    val include: Set<AniLibertyFieldName> = emptySet(),
    val exclude: Set<AniLibertyFieldName> = emptySet(),
) : AniLibertyFieldSpec {

    override fun includeParam(): String? =
        include.takeIf { it.isNotEmpty() }?.joinToString(",") { it.value }

    override fun excludeParam(): String? =
        exclude.takeIf { it.isNotEmpty() }?.joinToString(",") { it.value }

    companion object {
        val None: AniLibertyFields = AniLibertyFields()
        fun field(name: String): AniLibertyFieldName = AniLibertyFieldName(name)
    }
}
