package ru.radiationx.data.datasource.remote.aniliberty

@JvmInline
value class AniLibertyFranchiseId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyFranchiseId must not be blank" }
    }

    override fun toString(): String = value
}
