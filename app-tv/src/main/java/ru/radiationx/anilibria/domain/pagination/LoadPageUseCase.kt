package ru.radiationx.anilibria.domain.pagination

fun interface LoadPageUseCase<T> {
    suspend fun execute(page: Int): List<T>
}
