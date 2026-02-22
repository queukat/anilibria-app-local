package ru.radiationx.data.datasource.remote.interceptors

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.UserHolder
import ru.radiationx.data.system.ApplicationCoroutineScope
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicReference

class UnauthorizedInterceptor @Inject constructor(
    private val userHolder: UserHolder,
    private val cookieHolder: CookieHolder,
    private val authTokenHolder: AuthTokenHolder,
    private val applicationScope: ApplicationCoroutineScope,
) : Interceptor {

    companion object {
        private const val ANI_LIBERTY_HOST = "aniliberty.top"
    }

    private val tokenSnapshot = AtomicReference<String?>(null)

    init {
        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            tokenSnapshot.set(authTokenHolder.getToken())
        }
        applicationScope.launch {
            authTokenHolder.observeToken().collectLatest { token ->
                tokenSnapshot.set(token)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val host = request.url.host
            val isAniLibertyHost = host == ANI_LIBERTY_HOST || host.endsWith(".$ANI_LIBERTY_HOST")

            applicationScope.launch {
                cookieHolder.removeAuthCookie()

                if (isAniLibertyHost) {
                    authTokenHolder.deleteToken()
                    userHolder.delete()
                } else {
                    val hasAniLibertyToken = !tokenSnapshot.get().isNullOrBlank()
                    if (!hasAniLibertyToken) {
                        userHolder.delete()
                    }
                }
            }
        }

        return response
    }
}
