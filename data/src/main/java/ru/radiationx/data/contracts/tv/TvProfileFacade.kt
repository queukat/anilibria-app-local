package ru.radiationx.data.contracts.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.domain.other.ProfileItem

interface TvProfileFacade {
    fun observeUser(): Flow<ProfileItem?>

    suspend fun signOut()
}
