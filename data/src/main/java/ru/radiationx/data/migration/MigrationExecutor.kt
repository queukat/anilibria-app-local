package ru.radiationx.data.migration

fun interface MigrationExecutor {
    fun execute(
        current: Int,
        lastSaved: Int,
        history: List<Int>,
    )
}
