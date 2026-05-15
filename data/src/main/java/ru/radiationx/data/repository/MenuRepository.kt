package ru.radiationx.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.MenuHolder
import ru.radiationx.data.datasource.remote.api.MenuApi
import ru.radiationx.data.entity.domain.other.LinkMenuItem
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class MenuRepository
    @Inject
    constructor(
        private val menuHolder: MenuHolder,
        private val menuApi: MenuApi,
    ) {
        fun observeMenu(): Flow<List<LinkMenuItem>> =
            menuHolder
                .observe()
                .flowOn(AppDispatchers.io)

        suspend fun getMenu(): List<LinkMenuItem> =
            withContext(AppDispatchers.io) {
                menuApi
                    .getMenu()
                    .map { it.toDomain() }
                    .also { menuHolder.save(it) }
            }
    }
