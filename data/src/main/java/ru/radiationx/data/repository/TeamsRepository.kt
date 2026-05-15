package ru.radiationx.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.TeamsHolder
import ru.radiationx.data.datasource.remote.api.TeamsApi
import ru.radiationx.data.entity.domain.team.Teams
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class TeamsRepository
    @Inject
    constructor(
        private val teamsApi: TeamsApi,
        private val teamsHolder: TeamsHolder,
    ) {
        suspend fun requestUpdate() =
            withContext(AppDispatchers.io) {
                teamsApi
                    .getTeams()
                    .also { teamsHolder.save(it) }
            }

        fun observeTeams(): Flow<Teams> =
            teamsHolder
                .observe()
                .map { it.toDomain() }
                .flowOn(AppDispatchers.io)
    }
