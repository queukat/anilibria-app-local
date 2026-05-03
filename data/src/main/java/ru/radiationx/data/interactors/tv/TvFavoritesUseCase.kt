package ru.radiationx.data.interactors.tv

import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.repository.FavoriteRepository
import javax.inject.Inject

interface TvFavoritesUseCase {
    suspend fun loadFavorites(page: Int): Paginated<Release>
}

class TvFavoritesUseCaseImpl
    @Inject
    constructor(
        private val favoriteRepository: FavoriteRepository,
    ) : TvFavoritesUseCase {
        override suspend fun loadFavorites(page: Int): Paginated<Release> {
            return favoriteRepository.getFavoritesAniLiberty(page)
        }
    }
