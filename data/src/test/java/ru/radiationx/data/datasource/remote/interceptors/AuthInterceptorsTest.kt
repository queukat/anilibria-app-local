package ru.radiationx.data.datasource.remote.interceptors

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.UserHolder
import ru.radiationx.data.entity.domain.other.ProfileItem
import java.util.concurrent.TimeUnit

class AuthInterceptorsTest {

    @Test
    fun aniLibertyAuthInterceptor_addsAuthorizationHeader_forAniLibertyHost() {
        val tokenHolder = FakeAuthTokenHolder("token-123")
        val interceptor = AniLibertyAuthInterceptor(tokenHolder)
        val chain = FakeChain("https://anilibria.top/api/v1")

        interceptor.intercept(chain)

        assertEquals("Bearer token-123", chain.proceededRequest.header("Authorization"))
    }

    @Test
    fun aniLibertyAuthInterceptor_doesNotTouchNonAniLibertyHost() {
        val tokenHolder = FakeAuthTokenHolder("token-123")
        val interceptor = AniLibertyAuthInterceptor(tokenHolder)
        val chain = FakeChain("https://example.org/api")

        interceptor.intercept(chain)

        assertNull(chain.proceededRequest.header("Authorization"))
    }

    @Test
    fun unauthorizedInterceptor_clearsTokenAndUser_forAniLiberty401() {
        val userHolder = FakeUserHolder()
        val cookieHolder = FakeCookieHolder()
        val tokenHolder = FakeAuthTokenHolder("token-123")
        val interceptor = UnauthorizedInterceptor(
            userHolder = userHolder,
            cookieHolder = cookieHolder,
            authTokenHolder = tokenHolder,
        )
        val chain = FakeChain("https://api.anilibria.app/v1", responseCode = 401)

        interceptor.intercept(chain)

        assertEquals(1, cookieHolder.removeAuthCookieCalls)
        assertEquals(1, tokenHolder.deleteCalls)
        assertEquals(1, userHolder.deleteCalls)
    }

    @Test
    fun unauthorizedInterceptor_keepsUser_forLegacy401_whenTokenExists() {
        val userHolder = FakeUserHolder()
        val cookieHolder = FakeCookieHolder()
        val tokenHolder = FakeAuthTokenHolder("token-123")
        val interceptor = UnauthorizedInterceptor(
            userHolder = userHolder,
            cookieHolder = cookieHolder,
            authTokenHolder = tokenHolder,
        )
        val chain = FakeChain("https://legacy.example.org/api", responseCode = 401)

        interceptor.intercept(chain)

        assertEquals(1, cookieHolder.removeAuthCookieCalls)
        assertEquals(0, tokenHolder.deleteCalls)
        assertEquals(0, userHolder.deleteCalls)
        assertTrue(tokenHolder.currentToken == "token-123")
    }
}

private class FakeAuthTokenHolder(initialToken: String?) : AuthTokenHolder {
    private val tokenFlow = MutableStateFlow(initialToken)
    var deleteCalls = 0
        private set
    val currentToken: String?
        get() = tokenFlow.value

    override fun observeToken(): Flow<String?> = tokenFlow

    override suspend fun getToken(): String? = tokenFlow.value

    override suspend fun saveToken(token: String) {
        tokenFlow.value = token
    }

    override suspend fun deleteToken() {
        deleteCalls += 1
        tokenFlow.value = null
    }
}

private class FakeUserHolder : UserHolder {
    var deleteCalls = 0
        private set
    private val userFlow = MutableStateFlow<ProfileItem?>(null)

    override suspend fun getUser(): ProfileItem? = userFlow.value

    override fun observeUser(): Flow<ProfileItem?> = userFlow

    override suspend fun saveUser(user: ProfileItem) {
        userFlow.value = user
    }

    override suspend fun delete() {
        deleteCalls += 1
        userFlow.value = null
    }
}

private class FakeCookieHolder : CookieHolder {
    var removeAuthCookieCalls = 0
        private set

    override fun observeCookies(): Flow<Map<String, okhttp3.Cookie>> = MutableStateFlow(emptyMap())

    override suspend fun getCookies(): Map<String, okhttp3.Cookie> = emptyMap()

    override suspend fun putCookie(url: String, cookie: okhttp3.Cookie) = Unit

    override suspend fun removeCookie(name: String) = Unit

    override suspend fun removeAuthCookie() {
        removeAuthCookieCalls += 1
    }
}

private class FakeChain(
    url: String,
    private val responseCode: Int = 200,
) : Interceptor.Chain {
    private val initialRequest = Request.Builder().url(url).build()
    lateinit var proceededRequest: Request
        private set

    override fun request(): Request = initialRequest

    override fun proceed(request: Request): Response {
        proceededRequest = request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(responseCode)
            .message("test")
            .build()
    }

    override fun call(): Call {
        throw UnsupportedOperationException("Not needed in unit tests.")
    }

    override fun connection(): Connection? = null

    override fun connectTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun readTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun writeTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}
