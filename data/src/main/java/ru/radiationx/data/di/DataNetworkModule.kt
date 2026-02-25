package ru.radiationx.data.di

import android.content.Context
import com.squareup.moshi.Moshi
import okhttp3.ConnectionSpec
import ru.radiationx.data.ApiClient
import ru.radiationx.data.MainClient
import ru.radiationx.data.R
import ru.radiationx.data.SimpleClient
import ru.radiationx.data.ads.AdsConfigApi
import ru.radiationx.data.datasource.remote.IApiUtils
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.address.ApiConfigChanger
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyTupleAdapterFactory
import ru.radiationx.data.datasource.remote.api.AuthApi
import ru.radiationx.data.datasource.remote.api.CheckerApi
import ru.radiationx.data.datasource.remote.api.ConfigurationApi
import ru.radiationx.data.datasource.remote.api.DonationApi
import ru.radiationx.data.datasource.remote.api.FavoriteApi
import ru.radiationx.data.datasource.remote.api.FeedApi
import ru.radiationx.data.datasource.remote.api.MenuApi
import ru.radiationx.data.datasource.remote.api.PageApi
import ru.radiationx.data.datasource.remote.api.ReleaseApi
import ru.radiationx.data.datasource.remote.api.ScheduleApi
import ru.radiationx.data.datasource.remote.api.SearchApi
import ru.radiationx.data.datasource.remote.api.TeamsApi
import ru.radiationx.data.datasource.remote.api.YoutubeApi
import ru.radiationx.data.datasource.remote.interceptors.AniLibertyAuthInterceptor
import ru.radiationx.data.datasource.remote.interceptors.UnauthorizedInterceptor
import ru.radiationx.data.datasource.remote.parsers.AuthParser
import ru.radiationx.data.datasource.remote.parsers.PagesParser
import ru.radiationx.data.di.providers.ApiClientWrapper
import ru.radiationx.data.di.providers.ApiNetworkClient
import ru.radiationx.data.di.providers.ApiOkHttpProvider
import ru.radiationx.data.di.providers.MainClientWrapper
import ru.radiationx.data.di.providers.MainNetworkClient
import ru.radiationx.data.di.providers.MainOkHttpProvider
import ru.radiationx.data.di.providers.PlayerOkHttpProvider
import ru.radiationx.data.di.providers.SimpleClientWrapper
import ru.radiationx.data.di.providers.SimpleNetworkClient
import ru.radiationx.data.di.providers.SimpleOkHttpProvider
import ru.radiationx.data.sslcompat.SslCompat
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.data.system.AppCookieJar
import ru.radiationx.quill.QuillModule

class DataNetworkModule(context: Context) : QuillModule() {

    init {
        instance<SslCompat> {
            val rawCertResources = listOf(
                R.raw.gsr4,
                R.raw.gtsr1,
                R.raw.gtsr2,
                R.raw.gtsr3,
                R.raw.gtsr4,
                R.raw.isrg_root_x1,
                R.raw.isrg_root_x2,
            )
            val connectionSpecs = listOf(
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT,
            )
            SslCompat(context, rawCertResources, connectionSpecs)
        }

        instance<Moshi> {
            Moshi.Builder()
                .add(AniLibertyTupleAdapterFactory)
                .build()
        }

        single<ApiConfigChanger>()
        single<AniLibertyApi>()

        single<AppCookieJar>()
        single<AniLibertyAuthInterceptor>()
        single<UnauthorizedInterceptor>()
        single<ApiConfig>()

        single<PlayerOkHttpProvider>()
        single<SimpleOkHttpProvider>()
        single<MainOkHttpProvider>()
        single<ApiOkHttpProvider>()

        single<SimpleClientWrapper>()
        single<MainClientWrapper>()
        single<ApiClientWrapper>()

        singleImpl<IClient, SimpleNetworkClient>(SimpleClient::class)
        singleImpl<IClient, MainNetworkClient>(MainClient::class)
        singleImpl<IClient, ApiNetworkClient>(ApiClient::class)

        singleImpl<IApiUtils, ApiUtils>()

        single<AuthParser>()
        single<PagesParser>()

        single<AuthApi>()
        single<CheckerApi>()
        single<ConfigurationApi>()
        single<FavoriteApi>()
        single<ReleaseApi>()
        single<SearchApi>()
        single<PageApi>()
        single<YoutubeApi>()
        single<ScheduleApi>()
        single<FeedApi>()
        single<MenuApi>()
        single<DonationApi>()
        single<TeamsApi>()
        single<AdsConfigApi>()
    }
}
