package ru.radiationx.data.system

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.UserHolder
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicReference

class AppCookieJar @Inject constructor(
    private val cookieHolder: CookieHolder,
    private val userHolder: UserHolder,
    private val applicationScope: ApplicationCoroutineScope,
) : CookieJar {

    private val cookiesSnapshot = AtomicReference<Map<String, Cookie>>(emptyMap())

    init {
        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cookiesSnapshot.set(cookieHolder.getCookies())
        }
        applicationScope.launch {
            cookieHolder.observeCookies().collectLatest { cookies ->
                cookiesSnapshot.set(cookies)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        var authDestroyed = false
        val updated = cookiesSnapshot.get().toMutableMap()
        cookies.forEach { cookie ->
            if (cookie.value == "deleted") {
                if (cookie.name == CookieHolder.PHPSESSID) {
                    authDestroyed = true
                }
                updated.remove(cookie.name)
                applicationScope.launch {
                    cookieHolder.removeCookie(cookie.name)
                }
            } else {
                updated[cookie.name] = cookie
                applicationScope.launch {
                    cookieHolder.putCookie(url.toString(), cookie)
                }
            }
        }
        cookiesSnapshot.set(updated.toMap())
        if (authDestroyed) {
            applicationScope.launch {
                userHolder.delete()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookiesSnapshot
            .get()
            .values
            .filter { cookie -> cookie.matches(url) }
    }
}
