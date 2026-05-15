package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.withContext
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

/**
 * Вспомогательные DTO и функции для работы с обёрткой AniLiberty:
 * {
 *   "data": [...],
 *   "meta": {
 *     "pagination": {
 *       "total": ...,
 *       "count": ...,
 *       "per_page": ...,
 *       "current_page": ...,
 *       "total_pages": ...
 *     }
 *   }
 * }
 */

@JsonClass(generateAdapter = true)
data class AniLibertyPagination(
    @Json(name = "total") val total: Int?,
    @Json(name = "count") val count: Int?,
    @Json(name = "per_page") val perPage: Int?,
    @Json(name = "current_page") val currentPage: Int?,
    @Json(name = "total_pages") val totalPages: Int?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyMeta(
    @Json(name = "pagination") val pagination: AniLibertyPagination?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyPaginatedResponse<T>(
    @Json(name = "data") val data: List<T>?,
    @Json(name = "meta") val meta: AniLibertyMeta?,
)

/**
 * Парсинг "сырых" JSON-ответов AniLiberty в AniLibertyPaginatedResponse<T>
 */
suspend inline fun <reified T> String.fetchAniLibertyPaginated(moshi: Moshi): AniLibertyPaginatedResponse<T> =
    withContext(AppDispatchers.default) {
        val type =
            Types.newParameterizedType(
                AniLibertyPaginatedResponse::class.java,
                T::class.java,
            )
        val adapter = moshi.adapter<AniLibertyPaginatedResponse<T>>(type)

        adapter.fromJson(this@fetchAniLibertyPaginated)
            ?: throw IllegalStateException("Can't parse AniLiberty response, result is null")
    }

/**
 * Конвертер в уже существующий PaginatedResponse<T>,
 * чтобы можно было минимальными изменениями подключить новый API
 * к старому доменному уровню.
 */
fun <T> AniLibertyPaginatedResponse<T>.toPaginatedResponse(): PaginatedResponse<T> {
    val pagination = meta?.pagination
    val items = data.orEmpty()

    return PaginatedResponse(
        data = items,
        meta =
            PaginatedResponse.PaginationResponse(
                page = pagination?.currentPage ?: 1,
                allPages = pagination?.totalPages ?: 1,
                perPage = pagination?.perPage ?: items.size,
                allItems = pagination?.total ?: items.size,
            ),
    )
}
