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
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.mapper.toLegacyReleaseOrNull
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.ReleaseRepository
import ru.radiationx.data.repository.SearchRepository
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.data.system.AndroidTestMode
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

    private val scheduleFieldsForCards: AniLibertyReleaseFields = AniLibertyReleaseFields.Suggestions.copy(
        include = setOf(
            AniLibertyReleaseInclude.GENRES,
            AniLibertyReleaseInclude.LATEST_EPISODE,
        )
    )

    override suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release> {
        if (AndroidTestMode.enabled) {
            return if (requestPage == 1) {
                TvContentSmokeFixtures.releases.take(pageLimit)
            } else {
                emptyList()
            }
        }
        val releases = if (requestPage == 1) {
            val latestReleases = runCatching {
                aniLibertyApi.getLatestReleases(
                    limit = pageLimit,
                    fields = null,
                )
            }.getOrNull().orEmpty()

            if (latestReleases.isNotEmpty()) {
                latestReleases
            } else {
                aniLibertyApi.getCatalogReleases(
                    AniLibertyCatalogRequest(
                        page = AniLibertyPage(1),
                        limit = AniLibertyLimit(pageLimit),
                        sorting = AniLibertyCatalogSorting.FreshAtDesc,
                        fields = null,
                    )
                ).data
            }
        } else {
            emptyList()
        }

        return releases
            .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
    }

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload {
        if (AndroidTestMode.enabled) {
            return MainSchedulePayload(
                title = "Ожидается сегодня",
                releases = TvContentSmokeFixtures.releases.take(1),
            )
        }
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
            aniLibertyApi.getScheduleNow(fields = scheduleFieldsForCards)
        }.getOrNull()
            ?.today
            .orEmpty()
            .asSequence()
            .mapNotNull { it.release?.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
            .toList()

        val mainReleases = if (todayReleases.isNotEmpty()) {
            todayReleases
        } else {
            val mskPublishDay = calendarDayToAniLibertyPublishDay(mskCalendarDay)
            aniLibertyApi
                .getScheduleWeek(fields = scheduleFieldsForCards)
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
        }

        return MainSchedulePayload(title = title, releases = mainReleases)
    }

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> {
        if (AndroidTestMode.enabled) {
            return listOf(
                WeekSchedulePayload(
                    calendarDay = Calendar.MONDAY,
                    releases = TvContentSmokeFixtures.releases,
                )
            )
        }
        val releases = aniLibertyApi
            .getScheduleWeek(fields = scheduleFieldsForCards)
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
        if (AndroidTestMode.enabled) {
            return TvContentSmokeFixtures.releaseById(releaseId)?.let {
                DetailHeaderRemoteData(
                    titleRu = it.title,
                    titleEn = it.titleEng,
                    ageRatingLabel = "16+",
                    ageRatingValue = "16+",
                    averageDurationOfEpisode = 24,
                    notification = "Smoke data",
                    isOngoing = true,
                    isInProduction = true,
                    isBlockedByGeo = false,
                    isBlockedByCopyrights = false,
                    posterPreview = it.poster,
                    posterThumbnail = it.poster,
                    description = it.description,
                )
            }
        }
        val release = runCatching {
            aniLibertyApi.getRelease(
                key = AniLibertyReleaseKey.id(releaseId.id),
                fields = null,
            )
        }.getOrNull()

        return release?.let {
            DetailHeaderRemoteData(
                titleRu = it.name?.main,
                titleEn = it.name?.english ?: it.name?.alternative,
                ageRatingLabel = it.ageRating?.label,
                ageRatingValue = it.ageRating?.value?.value,
                averageDurationOfEpisode = it.averageDurationOfEpisode,
                notification = it.notification,
                isOngoing = it.isOngoing,
                isInProduction = it.isInProduction,
                isBlockedByGeo = it.isBlockedByGeo,
                isBlockedByCopyrights = it.isBlockedByCopyrights,
                posterPreview = it.poster?.optimized?.preview ?: it.poster?.preview,
                posterThumbnail = it.poster?.optimized?.thumbnail ?: it.poster?.thumbnail,
                description = it.description,
            )
        }
    }

    override suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean? {
        return when {
            AndroidTestMode.enabled -> false
            authRepository.getAuthState() != AuthState.AUTH -> null
            else -> runCatching {
                aniLibertyApi
                    .getUserFavoriteIds()
                    .any { it.value == releaseId.id }
            }.getOrNull()
        }
    }

    override suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release> {
        if (AndroidTestMode.enabled) {
            return TvContentSmokeFixtures.releases
                .filter { release -> seedReleaseId == null || release.id.id != seedReleaseId }
                .take(limit)
        }
        val releases = runCatching {
            aniLibertyApi.getRecommendedReleases(
                limit = limit,
                releaseId = seedReleaseId?.let { AniLibertyReleaseId(it) },
                fields = null,
            )
        }.getOrNull().orEmpty()

        return releases
            .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }
            .distinctBy { it.id }
    }

    override suspend fun loadLegacyRecommendations(releaseId: ReleaseId, requestPage: Int): List<Release> {
        if (AndroidTestMode.enabled) {
            return TvContentSmokeFixtures.releases
                .filterNot { it.id == releaseId }
                .take(smokeRecommendationsLimit)
        }
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

    private companion object {
        const val smokeRecommendationsLimit = 12
    }
}

private object TvContentSmokeFixtures {
    val releases: List<Release> = listOf(
        createRelease(id = 1001, title = "Smoke Release One"),
        createRelease(id = 1002, title = "Smoke Release Two"),
        createRelease(id = 1003, title = "Smoke Release Three"),
    )

    fun releaseById(releaseId: ReleaseId): Release? = releases.firstOrNull { it.id == releaseId }

    private fun createRelease(
        id: Int,
        title: String,
    ): Release {
        return Release(
            id = ReleaseId(id),
            code = ReleaseCode("smoke-$id"),
            names = listOf(title, "$title EN"),
            series = "1 из 12",
            poster = "/images/smoke/$id.jpg",
            torrentUpdate = 1_700_000_000,
            status = null,
            statusCode = Release.STATUS_CODE_PROGRESS,
            types = listOf("TV (12 эп.)"),
            genres = listOf("Приключения"),
            voices = emptyList(),
            members = null,
            year = "2025",
            season = "Зима",
            days = emptyList(),
            description = "Smoke fixture for android instrumentation tests",
            announce = null,
            favoriteInfo = FavoriteInfo(rating = 0, isAdded = false),
            link = null,
            franchises = emptyList(),
            showDonateDialog = false,
            blockedInfo = BlockedInfo(isBlocked = false, reason = null),
            moonwalkLink = null,
            episodes = emptyList(),
            sourceEpisodes = emptyList(),
            externalPlaylists = emptyList(),
            rutubePlaylist = emptyList(),
            torrents = emptyList(),
        )
    }
}
