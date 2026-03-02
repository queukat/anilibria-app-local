package ru.radiationx.anilibria.data.pagination

import ru.radiationx.anilibria.domain.pagination.LoadPageUseCase

class SuspendLoadPageUseCase<T>(
    private val loader: suspend (page: Int) -> List<T>,
) : LoadPageUseCase<T> {
    override suspend fun execute(page: Int): List<T> {
        return loader(page)
    }
}
