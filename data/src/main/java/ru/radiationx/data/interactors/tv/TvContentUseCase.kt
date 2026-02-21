package ru.radiationx.data.interactors.tv

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogRequest
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyLimit
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPage
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseInclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.mapper.toLegacyReleaseOrNull
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.ReleaseRepository
import ru.radiationx.data.repository.SearchRepository
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.shared.ktx.asDayNameDeclension
import ru.radiationx.shared.ktx.asDayPretext
import ru.radiationx.shared.ktx.asMsk
import ru.radiationx.shared.ktx.getDayOfWeek
import ru.radiationx.shared.ktx.isSameDay
import ru.radiationx.shared.ktx.lowercaseDefault
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

interface TvContentUseCase {
    suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release>
    suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload
    suspend fun loadWeekSchedule(): List<WeekSchedulePayload>
    suspend fun loadDetailHeaderRemote(releaseId: ReleaseId): DetailHeaderRemoteData?
    suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean?
    suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release>
    suspend fun loadLegacyRecommendations(releaseId: ReleaseId, requestPage: Int): List<Release>
}

data class MainSchedulePayload(
    val title: String,
    val releases: List<Release>,
)

data class WeekSchedulePayload(
    val calendarDay: Int?,
    val releases: List<Release>,
)

data class DetailHeaderRemoteData(
    val titleRu: String?,
    val titleEn: String?,
    val ageRatingLabel: String?,
    val ageRatingValue: String?,
    val averageDurationOfEpisode: Int?,
    val notification: String?,
    val isOngoing: Boolean?,
    val isInProduction: Boolean?,
    val isBlockedByGeo: Boolean?,
    val isBlockedByCopyrights: Boolean?,
    val posterPreview: String?,
    val posterThumbnail: String?,
    val description: String?,
)

class TvContentUseCaseImpl @Inject constructor(
    private val aniLibertyApi: AniLibertyApi,
    private val authRepository: AuthRepository,
    private val releaseRepository: ReleaseRepository,
    private val searchRepository: SearchRepository,
    private val apiUtils: ApiUtils,
) : TvContentUseCase {

    private val fieldsForCards: AniLibertyReleaseFields = AniLibertyReleaseFields.Suggestions.copy(
        include = setOf(
            AniLibertyReleaseInclude.GENRES,
            AniLibertyReleaseInclude.LATEST_EPISODE,
        )
    )

    override suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release> {
        return aniLibertyApi.getCatalogReleases(
            AniLibertyCatalogRequest(
                page = AniLibertyPage(requestPage),
                limit = AniLibertyLimit(pageLimit),
                sorting = AniLibertyCatalogSorting.FreshAtDesc,
                fields = fieldsForCards,
            )
        ).data.mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
    }

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload {
        val mskTime = currentTimeMs.asMsk()
        val mskCalendarDay = mskTime.getDayOfWeek()

        val title = if (Date(currentTimeMs).isSameDay(Date(mskTime))) {
            "Ожидается сегодня"
        } else {
            "Ожидается ${mskCalendarDay.asDayPretext()} ${
                mskCalendarDay.asDayNameDeclension().lowercaseDefault()
            } (по МСК)"
        }

        val todayReleases = runCatching {
            aniLibertyApi.getScheduleNow(fields = fieldsForCards)
        }.getOrNull()
            ?.today
            .orEmpty()
            .asSequence()
            .mapNotNull { it.release?.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
            .toList()

        if (todayReleases.isNotEmpty()) {
            return MainSchedulePayload(title = title, releases = todayReleases)
        }

        val mskPublishDay = calendarDayToAniLibertyPublishDay(mskCalendarDay)
        val weekReleases = aniLibertyApi
            .getScheduleWeek(fields = fieldsForCards)
            .data
            .orEmpty()
            .asSequence()
            .mapNotNull { it.release }
            .filter { release ->
                val day = release.publishDay?.value?.value
                mskPublishDay == null || day == mskPublishDay
            }
            .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
            .toList()

        return MainSchedulePayload(title = title, releases = weekReleases)
    }

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> {
        val releases = aniLibertyApi
            .getScheduleWeek(fields = fieldsForCards)
            .data
            .orEmpty()
            .mapNotNull { it.release }

        val grouped = releases.groupBy { release ->
            release.publishDay?.value?.value?.let(::publishDayToCalendarDayOrNull)
        }

        return grouped.map { (calendarDay, dayReleases) ->
            WeekSchedulePayload(
                calendarDay = calendarDay,
                releases = dayReleases
                    .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
                    .distinctBy { it.id },
            )
        }
    }

    override suspend fun loadDetailHeaderRemote(releaseId: ReleaseId): DetailHeaderRemoteData? {
        val release = runCatching {
            aniLibertyApi.getRelease(
                key = AniLibertyReleaseKey.id(releaseId.id),
                fields = AniLibertyReleaseFields.DetailsHeader,
            )
        }.getOrNull() ?: return null

        return DetailHeaderRemoteData(
            titleRu = release.name?.main,
            titleEn = release.name?.english ?: release.name?.alternative,
            ageRatingLabel = release.ageRating?.label,
            ageRatingValue = release.ageRating?.value?.value,
            averageDurationOfEpisode = release.averageDurationOfEpisode,
            notification = release.notification,
            isOngoing = release.isOngoing,
            isInProduction = release.isInProduction,
            isBlockedByGeo = release.isBlockedByGeo,
            isBlockedByCopyrights = release.isBlockedByCopyrights,
            posterPreview = release.poster?.optimized?.preview ?: release.poster?.preview,
            posterThumbnail = release.poster?.optimized?.thumbnail ?: release.poster?.thumbnail,
            description = release.description,
        )
    }

    override suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean? {
        if (authRepository.getAuthState() != AuthState.AUTH) {
            return null
        }
        return runCatching {
            aniLibertyApi
                .getUserFavoriteIds()
                .any { it.value == releaseId.id }
        }.getOrNull()
    }

    override suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release> {
        val releases = runCatching {
            aniLibertyApi.getRecommendedReleases(
                limit = limit,
                releaseId = seedReleaseId?.let { AniLibertyReleaseId(it) },
                fields = fieldsForCards,
            )
        }.getOrNull().orEmpty()

        return releases
            .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
    }

    override suspend fun loadLegacyRecommendations(releaseId: ReleaseId, requestPage: Int): List<Release> {
        val currentRelease = releaseRepository.getRelease(releaseId)
        val form = SearchForm(
            years = currentRelease.year?.let { setOf(YearItem(it, it)) }.orEmpty(),
            seasons = currentRelease.season?.let { setOf(SeasonItem(it, it)) }.orEmpty(),
            genres = currentRelease.genres.map { g -> GenreItem(g, g) }.toSet(),
            sort = SearchForm.Sort.RATING,
            onlyCompleted = false,
        )
        return searchRepository
            .searchReleases(form, requestPage)
            .data
            .filterNot { it.id == releaseId }
    }

    private fun publishDayToCalendarDayOrNull(day: Int): Int? = when (day) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> null
    }

    private fun calendarDayToAniLibertyPublishDay(calendarDay: Int): Int? = when (calendarDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> null
    }
}
