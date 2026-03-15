package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogPublishStatus
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogRequest
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyLimit
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPage
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseInclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.mapper.toGenreItemOrNull
import ru.radiationx.data.entity.mapper.toLegacyReleaseOrNull
import ru.radiationx.data.entity.mapper.toSeasonItemOrNull
import ru.radiationx.data.entity.mapper.toYearItem
import ru.radiationx.data.system.ApiUtils
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
    private val aniLibertyApi: AniLibertyApi,
    private val apiUtils: ApiUtils,
) : TvSearchUseCase {

    private val genresState = MutableStateFlow<List<GenreItem>>(emptyList())
    private val yearsState = MutableStateFlow<List<YearItem>>(emptyList())

    override fun observeGenres(): Flow<List<GenreItem>> {
        return genresState.asStateFlow()
    }

    override fun observeYears(): Flow<List<YearItem>> {
        return yearsState.asStateFlow()
    }

    override suspend fun loadGenres(): List<GenreItem> {
        return aniLibertyApi
            .getCatalogReferenceGenres()
            .mapNotNull { it.toGenreItemOrNull() }
            .sortedBy(GenreItem::title)
            .also { genresState.value = it }
    }

    override suspend fun loadYears(): List<YearItem> {
        return aniLibertyApi
            .getCatalogReferenceYears()
            .distinct()
            .sortedDescending()
            .map(Int::toYearItem)
            .also { yearsState.value = it }
    }

    override suspend fun loadSeasons(): List<SeasonItem> {
        return aniLibertyApi
            .getCatalogReferenceSeasons()
            .mapNotNull { it.toSeasonItemOrNull() }
            .distinctBy(SeasonItem::value)
    }

    override suspend fun searchReleases(form: SearchForm, page: Int): List<Release> {
        val years = form.years.mapNotNull { it.value.toIntOrNull() }
        return aniLibertyApi.getCatalogReleases(
            AniLibertyCatalogRequest(
                page = AniLibertyPage(page),
                limit = AniLibertyLimit(SEARCH_PAGE_LIMIT),
                genres = form.genres.mapNotNull { it.value.toIntOrNull() }.ifEmpty { null },
                fromYear = years.minOrNull(),
                toYear = years.maxOrNull(),
                seasons = form.seasons
                    .map { AniLibertySeason(it.value) }
                    .ifEmpty { null },
                publishStatuses = if (form.onlyCompleted) {
                    listOf(AniLibertyCatalogPublishStatus.IsNotOngoing)
                } else {
                    null
                },
                sorting = when (form.sort) {
                    SearchForm.Sort.RATING -> AniLibertyCatalogSorting.RatingDesc
                    SearchForm.Sort.DATE -> AniLibertyCatalogSorting.YearDesc
                },
                fields = SEARCH_FIELDS,
            )
        ).data
            .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
    }

    private companion object {
        val SEARCH_FIELDS: AniLibertyReleaseFields = AniLibertyReleaseFields.Suggestions.copy(
            include = setOf(
                AniLibertyReleaseInclude.GENRES,
                AniLibertyReleaseInclude.LATEST_EPISODE,
            )
        )
        const val SEARCH_PAGE_LIMIT = 20
    }
}
