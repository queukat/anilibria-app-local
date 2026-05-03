package ru.radiationx.data.datasource.remote.interceptors

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.system.ApplicationCoroutineScope
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Adds `Authorization: Bearer <token>` for AniLiberty API requests.
 *
 * Applied only for `aniliberty.top` host (and its subdomains) to avoid affecting legacy API calls.
 */
class AniLibertyAuthInterceptor
    @Inject
    constructor(
        private val tokenHolder: AuthTokenHolder,
        private val applicationScope: ApplicationCoroutineScope,
    ) : Interceptor {
        companion object {
            private const val HEADER_AUTHORIZATION = "Authorization"
            private const val BEARER_PREFIX = "Bearer "
            private const val HOST = "aniliberty.top"
        }

        private val tokenSnapshot = AtomicReference<String?>(null)

        init {
            applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                tokenSnapshot.set(tokenHolder.getToken())
            }
            applicationScope.launch {
                tokenHolder.observeToken().collectLatest { token ->
                    tokenSnapshot.set(token)
                }
            }
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

            val token =
                tokenSnapshot.get()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return chain.proceed(request)

            val authedRequest =
                request.newBuilder()
                    .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
                    .build()

            return chain.proceed(authedRequest)
        }
    }
