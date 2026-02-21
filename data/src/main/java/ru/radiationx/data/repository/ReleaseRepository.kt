package ru.radiationx.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogRequest
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFranchiseDetails
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFranchiseReleaseItem
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyLimit
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPage
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseInclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.api.ReleaseApi
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Franchise
import ru.radiationx.data.entity.domain.release.FranchiseInfo
import ru.radiationx.data.entity.domain.release.FranchiseRelease
import ru.radiationx.data.entity.domain.release.RandomRelease
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.data.entity.mapper.toLegacyFullReleaseOrNull
import ru.radiationx.data.entity.mapper.toLegacyReleaseOrNull
import ru.radiationx.data.interactors.ReleaseUpdateMiddleware
import ru.radiationx.data.system.ApiUtils
import timber.log.Timber
import javax.inject.Inject

/**
 * Releases repository.
 *
 * После миграции на AniLiberty используем token-first подход:
 * - v1 (AniLiberty) — основной источник
 * - legacy (ReleaseApi) — fallback, чтобы не ломать старые сценарии/данные
 *
 * Domain-модель [Release] остаётся прежней (legacy), поэтому маппим wire-модели v1 в неё.
 */
class ReleaseRepository @Inject constructor(
    private val aniLibertyApi: AniLibertyApi,
    private val releaseApi: ReleaseApi,
    private val updateMiddleware: ReleaseUpdateMiddleware,
    private val apiUtils: ApiUtils,
    private val apiConfig: ApiConfig,
) {

    /**
     * Поля под карточки/списки: максимально лёгкий ответ + нужные include.
     * Без GENRES/LATEST_EPISODE карточки теряют жанр/серии.
     */
    private val fieldsForCards: AniLibertyReleaseFields =
        AniLibertyReleaseFields.Suggestions.copy(
            include = setOf(
                AniLibertyReleaseInclude.GENRES,
                AniLibertyReleaseInclude.LATEST_EPISODE,
            )
        )

    suspend fun getRandomRelease(): RandomRelease = withContext(Dispatchers.IO) {
        val v1 = runCatching {
            aniLibertyApi.getRandomReleases(
                limit = 1,
                fields = AniLibertyReleaseFields.Suggestions,
            ).firstOrNull()
        }.getOrNull()

        val v1Code = v1?.alias?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: v1?.id?.value?.toString()

        if (!v1Code.isNullOrBlank()) {
            return@withContext RandomRelease(code = ReleaseCode(v1Code))
        }

        // legacy fallback
        releaseApi.getRandomRelease().toDomain()
    }

    suspend fun getRelease(releaseId: ReleaseId): Release = withContext(Dispatchers.IO) {
        val v1 = loadV1FullReleaseOrNull(
            key = AniLibertyReleaseKey.id(releaseId.id),
            withFranchises = true,
        )
        if (v1 != null) {
            updateMiddleware.handle(v1)
            return@withContext v1
        }

        legacyGetReleaseById(releaseId)
            .also { updateMiddleware.handle(it) }
    }

    suspend fun getRelease(releaseIdName: ReleaseCode): Release = withContext(Dispatchers.IO) {
        val v1 = runCatching {
            // AniLiberty "alias" ~= legacy "code"
            loadV1FullReleaseOrNull(
                key = AniLibertyReleaseKey.alias(releaseIdName.code),
                withFranchises = true,
            )
        }.getOrNull()

        if (v1 != null) {
            updateMiddleware.handle(v1)
            return@withContext v1
        }

        legacyGetReleaseByCode(releaseIdName)
            .also { updateMiddleware.handle(it) }
    }

    suspend fun getReleasesById(ids: List<ReleaseId>): List<Release> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        val v1 = runCatching {
            aniLibertyApi.getReleasesList(
                ids = ids.map { AniLibertyReleaseId(it.id) },
                aliases = null,
                page = 1,
                limit = ids.size.coerceAtLeast(1),
                fields = fieldsForCards,
            )
        }.map { response ->
            val mapped = response.data
                .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }

            updateMiddleware.handle(mapped)
            mapped
        }.getOrNull()

        v1 ?: releaseApi
            .getReleasesByIds(ids.map { it.id })
            .map { it.toDomain(apiUtils, apiConfig) }
            .also { updateMiddleware.handle(it) }
    }

    suspend fun getFullReleasesById(ids: List<ReleaseId>): List<Release> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        // 1) v1: берём релизы по id параллельно
        val v1ById: Map<ReleaseId, Release?> = coroutineScope {
            ids.map { rid ->
                async {
                    rid to loadV1FullReleaseOrNull(
                        key = AniLibertyReleaseKey.id(rid.id),
                        withFranchises = false,
                    )
                }
            }.awaitAll().toMap()
        }

        val v1Releases = v1ById.values.filterNotNull()

        // 2) legacy fallback для тех, что не удалось получить из v1
        val missingIds = ids.filter { v1ById[it] == null }
        val legacyMissing = if (missingIds.isNotEmpty()) {
            runCatching {
                releaseApi
                    .getFullReleasesByIds(missingIds.map { it.id })
                    .map { it.toDomain(apiUtils, apiConfig) }
            }.getOrElse { error ->
                Timber.w(error, "Legacy getFullReleasesByIds failed, return v1-only")
                emptyList()
            }
        } else {
            emptyList()
        }

        val all = (v1Releases + legacyMissing)
            .distinctBy { it.id }

        if (all.isNotEmpty()) {
            updateMiddleware.handle(all)
        }

        all
    }

    suspend fun getReleases(page: Int): Paginated<Release> = withContext(Dispatchers.IO) {
        val v1 = runCatching {
            val request = AniLibertyCatalogRequest(
                page = AniLibertyPage(page),
                limit = AniLibertyLimit(DEFAULT_PAGE_LIMIT),
                sorting = AniLibertyCatalogSorting.FreshAtDesc,
                fields = fieldsForCards,
            )
            aniLibertyApi.getCatalogReleases(request)
        }.map { response ->
            val mapped = response.data
                .mapNotNull { it.toLegacyReleaseOrNull(apiUtils, isFavorite = false) }

            updateMiddleware.handle(mapped)

            Paginated(
                data = mapped,
                page = response.meta.page,
                allPages = response.meta.allPages,
                perPage = response.meta.perPage,
                allItems = response.meta.allItems,
            )
        }.getOrNull()

        v1 ?: releaseApi
            .getReleases(page)
            .toDomain { it.toDomain(apiUtils, apiConfig) }
            .also { updateMiddleware.handle(it.data) }
    }

    // ---------------------------------------------------------------------

    private suspend fun legacyGetReleaseById(releaseId: ReleaseId): Release =
        releaseApi.getRelease(releaseId.id).toDomain(apiUtils, apiConfig)

    private suspend fun legacyGetReleaseByCode(releaseCode: ReleaseCode): Release =
        releaseApi.getRelease(releaseCode.code).toDomain(apiUtils, apiConfig)

    private suspend fun loadV1FullReleaseOrNull(
        key: AniLibertyReleaseKey,
        withFranchises: Boolean,
    ): Release? {
        val v1 = runCatching {
            aniLibertyApi.getRelease(
                key = key,
                fields = null, // full (episodes/torrents/members) — needed for player/details
            )
        }.getOrElse { error ->
            Timber.w(error, "AniLiberty: getRelease failed for key=%s", key)
            null
        } ?: return null

        val franchiseList = if (withFranchises) {
            v1.id?.value?.let { idValue ->
                runCatching {
                    aniLibertyApi.getFranchisesByRelease(
                        releaseId = AniLibertyReleaseId(idValue),
                        fields = null,
                    )
                }.getOrElse { error ->
                    Timber.w(error, "AniLiberty: getFranchisesByRelease failed for releaseId=%s", idValue)
                    emptyList()
                }.mapNotNull { it.toLegacyFranchiseOrNull() }
            }.orEmpty()
        } else {
            emptyList()
        }

        return v1.toLegacyFullReleaseOrNull(
            apiUtils = apiUtils,
            isFavorite = false,
            franchises = franchiseList,
        )
    }

    private fun AniLibertyFranchiseDetails.toLegacyFranchiseOrNull(): Franchise? {
        val franchiseId = id?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val title = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: nameEnglish?.trim()?.takeIf { it.isNotEmpty() }
            ?: franchiseId

        val releases = franchiseReleases
            .orEmpty()
            .mapNotNull { it.toLegacyFranchiseReleaseOrNull() }

        if (releases.isEmpty()) return null

        return Franchise(
            info = FranchiseInfo(
                id = franchiseId,
                name = title,
            ),
            releases = releases,
        )
    }

    private fun AniLibertyFranchiseReleaseItem.toLegacyFranchiseReleaseOrNull(): FranchiseRelease? {
        val rid = releaseId ?: release?.id?.value ?: return null

        val nameMain = release?.name?.main
            ?.let { apiUtils.escapeHtml(it).toString() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val nameEn = (release?.name?.english ?: release?.name?.alternative)
            ?.let { apiUtils.escapeHtml(it).toString() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val code = release?.alias?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: rid.toString()

        val names = listOfNotNull(nameMain, nameEn)
            .ifEmpty { listOf(code) }

        return FranchiseRelease(
            id = ReleaseId(rid),
            names = names,
            code = ReleaseCode(code),
        )
    }

    private companion object {
        const val DEFAULT_PAGE_LIMIT = 20
    }
}
