package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.radiationx.data.CriticalSecureDataPreferences
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.CookieHolder.Companion.cookieNames
import ru.radiationx.data.di.CriticalSecureStorageStatus
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import timber.log.Timber

/**
 * Created by radiationx on 30.12.17.
 */
class CookiesStorage
    @Inject
    constructor(
        @DataPreferences private val plaintextPreferences: SharedPreferences,
        @CriticalSecureDataPreferences private val encryptedPreferences: SharedPreferences,
        private val criticalSecureStorageStatus: CriticalSecureStorageStatus,
    ) : CookieHolder {
        private val migrationDone = AtomicBoolean(false)
        private val degradedModeWarningPrinted = AtomicBoolean(false)

        private val cookiesState =
            SuspendMutableStateFlow {
                migrateIfNeeded()
                loadCookies()
            }

        override fun observeCookies(): Flow<Map<String, Cookie>> {
            return cookiesState
        }

        override suspend fun getCookies(): Map<String, Cookie> {
            return cookiesState.getValue()
        }

        override suspend fun putCookie(
            url: String,
            cookie: Cookie,
        ) {
            migrateIfNeeded()
            withContext(AppDispatchers.io) {
                encryptedPreferences
                    .edit()
                    .putString("cookie_${cookie.name}", convertCookie(url, cookie))
                    .apply()
            }
            updateCookies()
        }

        override suspend fun removeCookie(name: String) {
            migrateIfNeeded()
            withContext(AppDispatchers.io) {
                encryptedPreferences
                    .edit()
                    .remove("cookie_$name")
                    .apply()
            }
            updateCookies()
        }

        override suspend fun removeAuthCookie() {
            removeCookie(CookieHolder.PHPSESSID)
        }

        private suspend fun updateCookies() {
            cookiesState.setValue(loadCookies())
        }

        private suspend fun loadCookies(): Map<String, Cookie> {
            migrateIfNeeded()
            return withContext(AppDispatchers.io) {
                val result = mutableMapOf<String, Cookie>()
                cookieNames.forEach { s ->
                    encryptedPreferences
                        .getString("cookie_$s", null)
                        ?.let { parseCookie(it) }
                        ?.let { cookie -> result[s] = cookie }
                }
                result
            }
        }

        private fun migrateIfNeeded() {
            if (!migrationDone.compareAndSet(false, true)) {
                return
            }
            if (!criticalSecureStorageStatus.isAvailable()) {
                warnDegradedModeOnce()
            }
            val cookieKeys = cookieNames.map { "cookie_$it" }
            SensitivePreferenceMigrator.migrateKeys(
                keys = cookieKeys,
                source = preferencesStore(plaintextPreferences),
                target = preferencesStore(encryptedPreferences),
            )
        }

        private fun preferencesStore(sharedPreferences: SharedPreferences): StringKeyValueStore {
            return object : StringKeyValueStore {
                override fun getString(key: String): String? = sharedPreferences.getString(key, null)

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    sharedPreferences.edit().putString(key, value).apply()
                }

                override fun remove(key: String) {
                    sharedPreferences.edit().remove(key).apply()
                }
            }
        }

        private fun warnDegradedModeOnce() {
            if (degradedModeWarningPrinted.compareAndSet(false, true)) {
                Timber.w(
                    criticalSecureStorageStatus.getUnavailableCause(),
                    "Secure cookies storage unavailable. Critical cookies are not persisted in plaintext.",
                )
            }
        }

        private fun parseCookie(cookieFields: String): Cookie? {
            val fields = cookieFields.split("\\|:\\|".toRegex())
            val httpUrl =
                fields[0].toHttpUrlOrNull()
                    ?: throw RuntimeException("Unknown cookie url = ${fields[0]}")
            val cookieString = fields[1]
            return Cookie.parse(httpUrl, cookieString)
        }

        private fun convertCookie(
            url: String,
            cookie: Cookie,
        ): String {
            return "$url|:|$cookie"
        }
    }
