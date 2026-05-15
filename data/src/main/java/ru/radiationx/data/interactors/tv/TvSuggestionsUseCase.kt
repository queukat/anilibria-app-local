package ru.radiationx.data.interactors.tv

import javax.inject.Inject
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyAppSearchReleasesRequest
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.entity.mapper.toSuggestionDomainOrNull
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

fun interface TvSuggestionsUseCase {
    suspend fun loadSuggestions(query: String): List<SuggestionItem>
}

class TvSuggestionsUseCaseImpl
    @Inject
    constructor(
        private val aniLibertyApi: AniLibertyApi,
        private val apiUtils: ApiUtils,
    ) : TvSuggestionsUseCase {
        override suspend fun loadSuggestions(query: String): List<SuggestionItem> {
            return withContext(AppDispatchers.io) {
                val releaseId =
                    searchIdRegex.find(query.trim())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()

                if (releaseId != null) {
                    return@withContext listOfNotNull(
                        runCatching {
                            aniLibertyApi.getRelease(
                                key = AniLibertyReleaseKey.id(releaseId),
                                fields = AniLibertyReleaseFields.Suggestions,
                            ).toSuggestionDomainOrNull(apiUtils)
                        }.getOrNull(),
                    )
                }

                aniLibertyApi
                    .searchAppReleases(
                        AniLibertyAppSearchReleasesRequest(
                            query = query,
                            fields = AniLibertyReleaseFields.Suggestions,
                        ),
                    )
                    .mapNotNull { it.toSuggestionDomainOrNull(apiUtils) }
            }
        }

        private companion object {
            val searchIdRegex = Regex("^id(\\d{3,})$")
        }
    }
