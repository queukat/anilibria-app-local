package ru.radiationx.data.entity.mapper

import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogReferenceSeason
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyGenre
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.response.release.ReleaseResponse
import ru.radiationx.data.entity.response.search.SuggestionResponse
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.shared.ktx.capitalizeDefault

fun SuggestionResponse.toDomain(
    apiUtils: ApiUtils,
    apiConfig: ApiConfig,
) = SuggestionItem(
    id = ReleaseId(id),
    code = ReleaseCode(code),
    names =
        names.map {
            apiUtils.escapeHtml(it).toString()
        },
    poster = poster?.appendBaseUrl(apiConfig.baseImagesUrl),
)

fun ReleaseResponse.toSuggestionDomain(
    apiUtils: ApiUtils,
    apiConfig: ApiConfig,
) = SuggestionItem(
    id = ReleaseId(id),
    code = ReleaseCode(code),
    names =
        names.orEmpty().map {
            apiUtils.escapeHtml(it).toString()
        },
    poster = poster?.appendBaseUrl(apiConfig.baseImagesUrl),
)

private const val ANI_LIBERTY_HOST = "https://aniliberty.top"

/**
 * Маппинг из AniLiberty v1 релиза в доменную модель подсказки для поиска.
 *
 * Возвращает `null`, если в ответе отсутствует id (на практике это не должно происходить,
 * но wire-модель допускает nullable поля).
 */
fun AniLibertyRelease.toSuggestionDomainOrNull(apiUtils: ApiUtils): SuggestionItem? {
    val idValue = id?.value ?: return null

    val titleRu =
        name?.main
            ?.let { apiUtils.escapeHtml(it).toString() }
            ?.trim()
            .orEmpty()

    val titleEn =
        (name?.english ?: name?.alternative)
            ?.let { apiUtils.escapeHtml(it).toString() }
            ?.trim()
            .orEmpty()

    val names = mutableListOf<String>()

    val fallback = alias?.value?.trim().orEmpty()
    val first = titleRu
    val second = titleEn

    when {
        first.isNotBlank() -> names.add(first)
        fallback.isNotBlank() -> names.add(fallback)
        else -> names.add(idValue.toString())
    }

    if (second.isNotBlank() && second != first) names.add(second)

    val posterUrl =
        (
            poster?.optimized?.preview
                ?: poster?.preview
                ?: poster?.thumbnail
        )
            .toAbsoluteAniLibertyUrl()

    val codeValue = alias?.value?.trim()?.takeIf { it.isNotEmpty() } ?: idValue.toString()

    return SuggestionItem(
        id = ReleaseId(idValue),
        code = ReleaseCode(codeValue),
        names = names,
        poster = posterUrl,
    )
}

private fun String?.toAbsoluteAniLibertyUrl(): String? {
    val s = this?.trim().orEmpty()
    if (s.isEmpty()) return null
    return when {
        s.startsWith("http://") || s.startsWith("https://") -> s
        s.startsWith("//") -> "https:$s"
        s.startsWith("/") -> ANI_LIBERTY_HOST + s
        else -> s
    }
}

fun String.toYearItem(): YearItem =
    YearItem(
        title = this,
        value = this,
    )

fun Int.toYearItem(): YearItem = toString().toYearItem()

fun String.toGenreItem(): GenreItem =
    GenreItem(
        title = this.capitalizeDefault(),
        value = this,
    )

fun AniLibertyGenre.toGenreItemOrNull(): GenreItem? {
    val genreId = id ?: return null
    val genreTitle = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return GenreItem(
        title = genreTitle,
        value = genreId.toString(),
    )
}

fun AniLibertyCatalogReferenceSeason.toSeasonItemOrNull(): SeasonItem? {
    val seasonValue = value ?: return null
    val title = description?.trim()?.takeIf { it.isNotEmpty() } ?: seasonValue.toSeasonTitle()
    return SeasonItem(
        title = title,
        value = seasonValue.value,
    )
}

fun AniLibertySeason.toSeasonTitle(): String =
    when (value.lowercase()) {
        AniLibertySeason.Winter.value -> "Зима"
        AniLibertySeason.Spring.value -> "Весна"
        AniLibertySeason.Summer.value -> "Лето"
        AniLibertySeason.Autumn.value -> "Осень"
        else -> value.capitalizeDefault()
    }
