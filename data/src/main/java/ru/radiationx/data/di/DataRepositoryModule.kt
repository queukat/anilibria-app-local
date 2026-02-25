package ru.radiationx.data.di

import ru.radiationx.data.ads.AdsConfigRepository
import ru.radiationx.data.downloader.RemoteFileRepository
import ru.radiationx.data.interactors.HistoryRuntimeCache
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.ReleaseUpdateMiddleware
import ru.radiationx.data.interactors.UserViewsSyncInteractor
import ru.radiationx.data.migration.MigrationExecutor
import ru.radiationx.data.migration.MigrationExecutorImpl
import ru.radiationx.data.player.PlayerCacheDataSourceProvider
import ru.radiationx.data.player.PlayerDataSourceProvider
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.CheckerRepository
import ru.radiationx.data.repository.ConfigurationRepository
import ru.radiationx.data.repository.DonationRepository
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.FeedRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.MenuRepository
import ru.radiationx.data.repository.PageRepository
import ru.radiationx.data.repository.ReleaseRepository
import ru.radiationx.data.repository.ScheduleRepository
import ru.radiationx.data.repository.SearchRepository
import ru.radiationx.data.repository.TeamsRepository
import ru.radiationx.data.repository.UserViewsRepository
import ru.radiationx.data.repository.YoutubeRepository
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.quill.QuillModule

class DataRepositoryModule : QuillModule() {

    init {
        single<AuthRepository>()
        single<ReleaseRepository>()
        single<ConfigurationRepository>()
        single<SearchRepository>()
        single<PageRepository>()
        single<CheckerRepository>()
        single<HistoryRepository>()
        single<FavoriteRepository>()
        single<YoutubeRepository>()
        single<ScheduleRepository>()
        single<FeedRepository>()
        single<MenuRepository>()
        single<DonationRepository>()
        single<TeamsRepository>()
        single<RemoteFileRepository>()
        single<AdsConfigRepository>()

        single<ReleaseUpdateMiddleware>()
        single<ReleaseInteractor>()
        single<ApplicationCoroutineScope>()
        single<HistoryRuntimeCache>()
        single<UserViewsRepository>()
        single<UserViewsSyncInteractor>()

        single<PlayerDataSourceProvider>()
        single<PlayerCacheDataSourceProvider>()
        singleImpl<MigrationExecutor, MigrationExecutorImpl>()
    }
}
