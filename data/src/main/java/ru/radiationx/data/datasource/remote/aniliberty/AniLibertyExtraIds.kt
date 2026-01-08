package ru.radiationx.data.datasource.remote.aniliberty

@JvmInline
value class AniLibertyGenreId(val value: Int) {
    init {
        require(value > 0) { "AniLibertyGenreId must be positive" }
    }

    override fun toString(): String = value.toString()
}

/**
 * Torrents endpoints accept either numeric id or hash.
 * Keep it typed to avoid "Stringly typed" code in callers.
 */
sealed interface AniLibertyTorrentKey {
    fun asPathSegment(): String

    data class ById(val id: Int) : AniLibertyTorrentKey {
        init {
            require(id > 0) { "AniLibertyTorrentKey.ById id must be positive" }
        }
        override fun asPathSegment(): String = id.toString()
    }

    data class ByHash(val hash: String) : AniLibertyTorrentKey {
        init {
            require(hash.isNotBlank()) { "AniLibertyTorrentKey.ByHash hash must not be blank" }
        }
        override fun asPathSegment(): String = hash
    }

    companion object {
        fun id(value: Int): AniLibertyTorrentKey = ById(value)
        fun hash(value: String): AniLibertyTorrentKey = ByHash(value)
    }
}
