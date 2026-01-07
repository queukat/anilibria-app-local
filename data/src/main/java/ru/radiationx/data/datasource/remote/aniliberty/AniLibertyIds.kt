package ru.radiationx.data.datasource.remote.aniliberty

@JvmInline
value class AniLibertyReleaseId(val value: Int) {
    init {
        require(value > 0) { "AniLibertyReleaseId must be positive" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class AniLibertyReleaseAlias(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyReleaseAlias must not be blank" }
    }

    override fun toString(): String = value
}

sealed interface AniLibertyReleaseKey {
    fun asPathSegment(): String

    data class ById(val id: AniLibertyReleaseId) : AniLibertyReleaseKey {
        override fun asPathSegment(): String = id.value.toString()
    }

    data class ByAlias(val alias: AniLibertyReleaseAlias) : AniLibertyReleaseKey {
        override fun asPathSegment(): String = alias.value
    }

    companion object {
        fun id(value: Int): AniLibertyReleaseKey = ById(AniLibertyReleaseId(value))
        fun alias(value: String): AniLibertyReleaseKey = ByAlias(AniLibertyReleaseAlias(value))
    }
}

@JvmInline
value class AniLibertyReleaseEpisodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyReleaseEpisodeId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class AniLibertyDeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyDeviceId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class AniLibertyOtpCode(val value: Int) {
    init {
        require(value >= 0) { "AniLibertyOtpCode must be non negative" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class AniLibertyEmail(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyEmail must not be blank" }
        require('@' in value) { "AniLibertyEmail must contain @" }
    }

    override fun toString(): String = value
}
