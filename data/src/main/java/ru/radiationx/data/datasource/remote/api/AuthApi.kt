package ru.radiationx.data.datasource.remote.api

import android.net.Uri
import com.squareup.moshi.Moshi
import okhttp3.Response
import org.json.JSONObject
import ru.radiationx.data.ApiClient
import ru.radiationx.data.datasource.remote.ApiError
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.fetchApiResponse
import ru.radiationx.data.datasource.remote.fetchEmptyApiResponse
import ru.radiationx.data.datasource.remote.fetchListApiResponse
import ru.radiationx.data.datasource.remote.parsers.AuthParser
import ru.radiationx.data.entity.domain.auth.SocialAuth
import ru.radiationx.data.entity.domain.auth.SocialAuthException
import ru.radiationx.data.entity.response.auth.OtpInfoResponse
import ru.radiationx.data.entity.response.auth.SocialAuthResponse
import ru.radiationx.data.entity.response.other.ProfileResponse
import ru.radiationx.data.system.HttpException
import ru.radiationx.shared.ktx.android.nullString
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Created by radiationx on 30.12.17.
 */
class AuthApi @Inject constructor(
    @ApiClient private val client: IClient,
    private val authParser: AuthParser,
    private val apiConfig: ApiConfig,
    private val moshi: Moshi,
) {

    suspend fun loadUser(): ProfileResponse {
        val args: MutableMap<String, String> = mutableMapOf(
            "query" to "user"
        )
        return client.post(apiConfig.apiUrl, args)
            .fetchApiResponse(moshi)
    }

    suspend fun loadOtpInfo(deviceId: String): OtpInfoResponse {
        val args: MutableMap<String, String> = mutableMapOf(
            "query" to "auth_get_otp",
            "deviceId" to deviceId
        )
        return try {
            client
                .post(apiConfig.apiUrl, args)
                .fetchApiResponse(moshi)
        } catch (ex: Throwable) {
            throw authParser.checkOtpError(ex)
        }
    }

    suspend fun acceptOtp(code: String) {
        val args: MutableMap<String, String> = mutableMapOf(
            "query" to "auth_accept_otp",
            "code" to code
        )
        try {
            client
                .post(apiConfig.apiUrl, args)
                .fetchEmptyApiResponse(moshi)
        } catch (ex: Throwable) {
            throw authParser.checkOtpError(ex)
        }
    }

    suspend fun signInOtp(code: String, deviceId: String): ProfileResponse {
        val args: MutableMap<String, String> = mutableMapOf(
            "query" to "auth_login_otp",
            "deviceId" to deviceId,
            "code" to code
        )
        return try {
            client
                .post(apiConfig.apiUrl, args)
                .fetchEmptyApiResponse(moshi)
                .let { loadUser() }
        } catch (ex: Throwable) {
            throw authParser.checkOtpError(ex)
        }
    }

    suspend fun signIn(login: String, password: String, code2fa: String): ProfileResponse {
        val args: MutableMap<String, String> = mutableMapOf(
            "mail" to login,
            "passwd" to password,
            "fa2code" to code2fa
        )
        val url = "${apiConfig.baseUrl}/public/login.php"
        return client.post(url, args)
            .let { authParser.authResult(it) }
            .let { loadUser() }
    }

    suspend fun loadSocialAuth(): List<SocialAuthResponse> {
        val args: MutableMap<String, String> = mutableMapOf(
            "query" to "social_auth"
        )
        return client
            .post(apiConfig.apiUrl, args)
            .fetchListApiResponse(moshi)
    }

    suspend fun signInSocial(resultUrl: String, item: SocialAuth): ProfileResponse {
        val args: MutableMap<String, String> = mutableMapOf()

        val fixedUrl = Uri.parse(apiConfig.baseUrl).host?.let { redirectDomain ->
            resultUrl.replace("www.anilibria.tv", redirectDomain)
        } ?: resultUrl

        return client
            .getFull(fixedUrl, args)
            .also { response ->
                val matcher = Pattern.compile(item.errorUrlPattern).matcher(response.redirect)
                if (matcher.find()) {
                    throw SocialAuthException()
                }
            }
            .also {
                val message = try {
                    JSONObject(it.body).nullString("mes")
                } catch (ignore: Exception) {
                    null
                }
                if (message != null) {
                    throw ApiError(400, message, null)
                }
            }
            .let { loadUser() }
    }

    suspend fun signOut() {
        val logoutUrl = "${apiConfig.baseUrl}/public/logout.php"
        val args: Map<String, String> = emptyMap()

        try {
            client.post(logoutUrl, args)
        } catch (ex: HttpException) {
            // Legacy logout может вернуть 302 + Set-Cookie(PHPSESSID=deleted),
            // а OkHttp доходит по редиректу до "/" и получает 404.
            // Если в цепочке priorResponse был редирект именно с logout.php — считаем logout успешным.
            if (isLegacyLogoutRedirectSuccess(ex.response, logoutUrl)) {
                return
            }
            throw ex
        }
    }

    private fun isLegacyLogoutRedirectSuccess(response: Response, logoutUrl: String): Boolean {
        var current: Response? = response
        while (current != null) {
            val reqUrl = current.request.url.toString()
            val isLogoutCall = (reqUrl == logoutUrl) || reqUrl.endsWith("/public/logout.php")
            if (isLogoutCall && current.code in 300..399) {
                return true
            }
            current = current.priorResponse
        }
        return false
    }
}
