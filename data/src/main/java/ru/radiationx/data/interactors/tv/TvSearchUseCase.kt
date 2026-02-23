package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

interface TvSearchUseCase {
    fun observeGenres(): Flow<List<GenreItem>>
    fun observeYears(): Flow<List<YearItem>>
    suspend fun loadGenres(): List<GenreItem>
    suspend fun loadYears(): List<YearItem>
    suspend fun loadSeasons(): List<SeasonItem>
    suspend fun searchReleases(form: SearchForm, page: Int): List<Release>
}

class TvSearchUseCaseImpl @Inject constructor(
    private val searchRepository: SearchRepository,
) : TvSearchUseCase {

    override fun observeGenres(): Flow<List<GenreItem>> {
        return searchRepository.observeGenres()
    }

    override fun observeYears(): Flow<List<YearItem>> {
        return searchRepository.observeYears()
    }

    override suspend fun loadGenres(): List<GenreItem> {
        return searchRepository.getGenres()
    }

    override suspend fun loadYears(): List<YearItem> {
        return searchRepository.getYears()
    }

    override suspend fun loadSeasons(): List<SeasonItem> {
        return searchRepository.getSeasons()
    }

    override suspend fun searchReleases(form: SearchForm, page: Int): List<Release> {
        return searchRepository.searchReleases(form, page).data
    }
}
