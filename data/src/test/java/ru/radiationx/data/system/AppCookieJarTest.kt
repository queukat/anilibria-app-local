package ru.radiationx.data.system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.UserHolder
import ru.radiationx.data.entity.domain.other.ProfileItem

class AppCookieJarTest {
    @Test
    fun loadForRequest_returnsLatestSnapshotWithoutStorageRead() {
        val cookieHolder = FakeCookieHolder()
        val userHolder = FakeUserHolder()
        val jar =
            AppCookieJar(
                cookieHolder = cookieHolder,
                userHolder = userHolder,
                applicationScope = ApplicationCoroutineScope(),
            )
        val url = "https://example.org/".toHttpUrl()

        val cookie =
            Cookie.Builder()
                .name("PHPSESSID")
                .value("token")
                .domain("example.org")
                .path("/")
                .build()

        jar.saveFromResponse(url, listOf(cookie))
        val requestCookies = jar.loadForRequest(url)

        assertEquals(1, requestCookies.size)
        assertEquals("token", requestCookies.first().value)
    }

    @Test
    fun saveFromResponse_deletedAuthCookie_notifiesUserCleanup() {
        val cookieHolder = FakeCookieHolder()
        val userHolder = FakeUserHolder()
        val jar =
            AppCookieJar(
                cookieHolder = cookieHolder,
                userHolder = userHolder,
                applicationScope = ApplicationCoroutineScope(),
            )
        val url = "https://example.org/".toHttpUrl()

        val deleted =
            Cookie.Builder()
                .name(CookieHolder.PHPSESSID)
                .value("deleted")
                .domain("example.org")
                .path("/")
                .build()

        jar.saveFromResponse(url, listOf(deleted))
        waitUntil { userHolder.deleteCalls == 1 }

        assertTrue(jar.loadForRequest(url).isEmpty())
        assertEquals(1, userHolder.deleteCalls)
    }

    @Test
    fun loadForRequest_doesNotReturnCookiesForOtherHost() {
        val cookieHolder = FakeCookieHolder()
        val userHolder = FakeUserHolder()
        val jar =
            AppCookieJar(
                cookieHolder = cookieHolder,
                userHolder = userHolder,
                applicationScope = ApplicationCoroutineScope(),
            )
        val hostA = "https://a.com/".toHttpUrl()
        val hostB = "https://b.com/".toHttpUrl()

        val cookie =
            Cookie.Builder()
                .name("PHPSESSID")
                .value("token")
                .domain("a.com")
                .path("/")
                .build()

        jar.saveFromResponse(hostA, listOf(cookie))

        assertTrue(jar.loadForRequest(hostB).isEmpty())
        assertEquals(1, jar.loadForRequest(hostA).size)
    }

    @Test
    fun loadForRequest_doesNotReturnCookieForWrongPath() {
        val cookieHolder = FakeCookieHolder()
        val userHolder = FakeUserHolder()
        val jar =
            AppCookieJar(
                cookieHolder = cookieHolder,
                userHolder = userHolder,
                applicationScope = ApplicationCoroutineScope(),
            )
        val sourceUrl = "https://example.org/x/start".toHttpUrl()
        val sameHostOtherPath = "https://example.org/y/next".toHttpUrl()
        val matchingPath = "https://example.org/x/next".toHttpUrl()

        val cookie =
            Cookie.Builder()
                .name("PHPSESSID")
                .value("token")
                .domain("example.org")
                .path("/x")
                .build()

        jar.saveFromResponse(sourceUrl, listOf(cookie))

        assertTrue(jar.loadForRequest(sameHostOtherPath).isEmpty())
        assertEquals(1, jar.loadForRequest(matchingPath).size)
    }

    @Test
    fun loadForRequest_doesNotReturnSecureCookieForHttp() {
        val cookieHolder = FakeCookieHolder()
        val userHolder = FakeUserHolder()
        val jar =
            AppCookieJar(
                cookieHolder = cookieHolder,
                userHolder = userHolder,
                applicationScope = ApplicationCoroutineScope(),
            )
        val httpsUrl = "https://example.org/".toHttpUrl()
        val httpUrl = "http://example.org/".toHttpUrl()

        val secureCookie =
            Cookie.Builder()
                .name("PHPSESSID")
                .value("token")
                .domain("example.org")
                .path("/")
                .secure()
                .build()

        jar.saveFromResponse(httpsUrl, listOf(secureCookie))

        assertEquals(1, jar.loadForRequest(httpsUrl).size)
        assertTrue(jar.loadForRequest(httpUrl).isEmpty())
    }

    private fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) {
                return
            }
            Thread.sleep(10)
        }
    }
}

private class FakeCookieHolder : CookieHolder {
    private val state = MutableStateFlow<Map<String, Cookie>>(emptyMap())

    override fun observeCookies(): Flow<Map<String, Cookie>> = state

    override suspend fun getCookies(): Map<String, Cookie> = state.value

    override suspend fun putCookie(
        url: String,
        cookie: Cookie,
    ) {
        state.value = state.value + (cookie.name to cookie)
    }

    override suspend fun removeCookie(name: String) {
        state.value = state.value - name
    }

    override suspend fun removeAuthCookie() {
        removeCookie(CookieHolder.PHPSESSID)
    }
}

private class FakeUserHolder : UserHolder {
    var deleteCalls: Int = 0
        private set
    private val state = MutableStateFlow<ProfileItem?>(null)

    override suspend fun getUser(): ProfileItem? = state.value

    override fun observeUser(): Flow<ProfileItem?> = state

    override suspend fun saveUser(user: ProfileItem) {
        state.value = user
    }

    override suspend fun delete() {
        deleteCalls += 1
        state.value = null
    }
}
