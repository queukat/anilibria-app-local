package ru.radiationx.data.datasource.remote.interceptors

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import javax.inject.Inject

/**
 * Adds `Authorization: Bearer <token>` for AniLiberty API requests.
 *
 * Applied only for `aniliberty.top` host (and its subdomains) to avoid affecting legacy API calls.
 */
class AniLibertyAuthInterceptor @Inject constructor(
    private val tokenHolder: AuthTokenHolder,
) : Interceptor {

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val HOST = "aniliberty.top"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val host = request.url.host
        val isAniLibertyHost = host == HOST || host.endsWith(".$HOST")
        if (!isAniLibertyHost) {
            return chain.proceed(request)
        }

        // Do not override if caller already sets Authorization explicitly.
        if (request.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenHolder.getToken() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return chain.proceed(request)

        val authedRequest = request.newBuilder()
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
            .build()

        return chain.proceed(authedRequest)
    }
}
