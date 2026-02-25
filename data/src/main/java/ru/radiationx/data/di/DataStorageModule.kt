@file:Suppress("DEPRECATION")

package ru.radiationx.data.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import ru.radiationx.data.CriticalSecureDataPreferences
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.SecureDataPreferences
import ru.radiationx.data.datasource.holders.AuthHolder
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.DonationHolder
import ru.radiationx.data.datasource.holders.DownloadsHolder
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.datasource.holders.GenresHolder
import ru.radiationx.data.datasource.holders.HistoryHolder
import ru.radiationx.data.datasource.holders.MenuHolder
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.datasource.holders.ReleaseUpdateHolder
import ru.radiationx.data.datasource.holders.SocialAuthHolder
import ru.radiationx.data.datasource.holders.TeamsHolder
import ru.radiationx.data.datasource.holders.UserHolder
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.datasource.holders.YearsHolder
import ru.radiationx.data.datasource.storage.ApiConfigStorage
import ru.radiationx.data.datasource.storage.AuthStorage
import ru.radiationx.data.datasource.storage.AuthTokenStorage
import ru.radiationx.data.datasource.storage.CookiesStorage
import ru.radiationx.data.datasource.storage.DonationStorage
import ru.radiationx.data.datasource.storage.DownloadsStorage
import ru.radiationx.data.datasource.storage.EpisodesCheckerStorage
import ru.radiationx.data.datasource.storage.GenresStorage
import ru.radiationx.data.datasource.storage.HistoryStorage
import ru.radiationx.data.datasource.storage.MenuStorage
import ru.radiationx.data.datasource.storage.PreferencesStorage
import ru.radiationx.data.datasource.storage.ReleaseUpdateStorage
import ru.radiationx.data.datasource.storage.SocialAuthStorage
import ru.radiationx.data.datasource.storage.TeamsStorage
import ru.radiationx.data.datasource.storage.UserStorage
import ru.radiationx.data.datasource.storage.UserViewsSyncStorage
import ru.radiationx.data.datasource.storage.YearsStorage
import ru.radiationx.data.downloader.RemoteFileHolder
import ru.radiationx.data.downloader.RemoteFileStorage
import ru.radiationx.data.migration.MigrationDataSource
import ru.radiationx.data.migration.MigrationDataSourceImpl
import ru.radiationx.quill.QuillModule
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import ru.radiationx.data.ads.AdsConfigStorage

class DataStorageModule(context: Context) : QuillModule() {

    init {
        instance<Context> { context.applicationContext }

        singleProvider<SharedPreferences, PreferencesProvider>()
        singleProvider<SharedPreferences, DataPreferencesProvider>(DataPreferences::class)
        singleProvider<SharedPreferences, SecureDataPreferencesProvider>(SecureDataPreferences::class)
        singleProvider<SharedPreferences, CriticalSecureDataPreferencesProvider>(CriticalSecureDataPreferences::class)
        single<CriticalSecureStorageStatus>()

        singleImpl<MigrationDataSource, MigrationDataSourceImpl>()

        single<PreferencesStorage>()
        singleImpl<PreferencesHolder, PreferencesStorage>()
        singleImpl<EpisodesCheckerHolder, EpisodesCheckerStorage>()
        singleImpl<HistoryHolder, HistoryStorage>()
        singleImpl<ReleaseUpdateHolder, ReleaseUpdateStorage>()
        singleImpl<GenresHolder, GenresStorage>()
        singleImpl<YearsHolder, YearsStorage>()
        singleImpl<SocialAuthHolder, SocialAuthStorage>()
        singleImpl<MenuHolder, MenuStorage>()
        singleImpl<DownloadsHolder, DownloadsStorage>()
        singleImpl<DonationHolder, DonationStorage>()
        singleImpl<TeamsHolder, TeamsStorage>()
        singleImpl<RemoteFileHolder, RemoteFileStorage>()

        singleImpl<CookieHolder, CookiesStorage>()
        singleImpl<UserHolder, UserStorage>()
        singleImpl<AuthHolder, AuthStorage>()
        singleImpl<AuthTokenHolder, AuthTokenStorage>()
        singleImpl<UserViewsSyncHolder, UserViewsSyncStorage>()

        single<ApiConfigStorage>()
        single<AdsConfigStorage>()
    }

    internal class PreferencesProvider @Inject constructor(
        private val context: Context,
    ) : Provider<SharedPreferences> {
        override fun get(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    internal class DataPreferencesProvider @Inject constructor(
        @Suppress("unused") private val context: Context,
        private val preferencesProvider: PreferencesProvider,
    ) : Provider<SharedPreferences> {

        override fun get(): SharedPreferences {
            return context.getSharedPreferences("data_storage", Context.MODE_PRIVATE)
                ?: preferencesProvider.get()
        }
    }

    internal class SecureDataPreferencesProvider @Inject constructor(
        private val context: Context,
        @DataPreferences private val fallbackPreferences: SharedPreferences,
    ) : Provider<SharedPreferences> {

        override fun get(): SharedPreferences {
            return runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

                EncryptedSharedPreferences.create(
                    "data_storage_secure",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse { error ->
                Timber.w(error, "Falling back to plaintext prefs because encrypted prefs init failed.")
                fallbackPreferences
            }
        }
    }

    internal class CriticalSecureDataPreferencesProvider @Inject constructor(
        private val context: Context,
        @DataPreferences private val fallbackPreferences: SharedPreferences,
        private val criticalSecureStorageStatus: CriticalSecureStorageStatus,
    ) : Provider<SharedPreferences> {

        override fun get(): SharedPreferences {
            return runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

                EncryptedSharedPreferences.create(
                    "data_storage_secure",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse { error ->
                criticalSecureStorageStatus.markUnavailable(error)
                Timber.e(error, "Critical secure prefs init failed: token storage switched to fail-closed mode.")
                fallbackPreferences
            }
        }
    }
}
