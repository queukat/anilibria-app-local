package ru.radiationx.data.datasource.remote.interceptors

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.UserHolder
import javax.inject.Inject

class UnauthorizedInterceptor @Inject constructor(
    private val userHolder: UserHolder,
    private val cookieHolder: CookieHolder,
    private val authTokenHolder: AuthTokenHolder,
) : Interceptor {

    companion object {
        private const val ANI_LIBERTY_HOST = "aniliberty.top"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val host = request.url.host
            val isAniLibertyHost = host == ANI_LIBERTY_HOST || host.endsWith(".$ANI_LIBERTY_HOST")

            runBlocking {
                // Legacy session cleanup (PHPSESSID)
                cookieHolder.removeAuthCookie()

                if (isAniLibertyHost) {
                    // AniLiberty token-based auth: drop token + cached user
                    authTokenHolder.deleteToken()
                    userHolder.delete()
                } else {
                    // Legacy API can return 401 when cookie is missing (after migration).
                    // Do NOT drop AniLiberty token in this case.
                    val hasAniLibertyToken = !authTokenHolder.getToken().isNullOrBlank()
                    if (!hasAniLibertyToken) {
                        userHolder.delete()
                    }
                }
            }
        }

        return response
    }
}
