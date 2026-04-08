package ru.radiationx.anilibria

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CompletableDeferred
import ru.radiationx.anilibria.common.TvStartupTrace
import ru.mintrocket.lib.mintpermissions.ext.initMintPermissions
import ru.mintrocket.lib.mintpermissions.flows.ext.initMintPermissionsFlow
import ru.radiationx.anilibria.di.AppModule
import ru.radiationx.data.di.DataModule
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.migration.MigrationDataSource
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.data.system.AndroidTestMode
import ru.radiationx.quill.Quill
import ru.radiationx.shared_app.imageloader.LibriaImageLoader
import timber.log.Timber

class App : Application() {

    companion object {
        /**
         * Завершается (`complete`) сразу после полной инициализации
         * `Application.onCreate()`.
         * Любой ContentProvider может вызвать
         * `App.appInitialized.await()` вместо «крутилки»‐StateFlow.
         */
        val appInitialized = CompletableDeferred<Unit>()
    }

    override fun onCreate() {
        super.onCreate()
        TvStartupTrace.markOnce("app_on_create_start")

        if (!AndroidTestMode.enabled) {
            initYandexAppMetrica()
        }

        if (isMainProcess()) {
            initInMainProcess()
        }

        // сигнал «приложение полностью готово»
        appInitialized.complete(Unit)
    }

    private fun initYandexAppMetrica() {
        val config = AppMetricaConfig
            .newConfigBuilder("48d49aa0-6aad-407e-a738-717a6c77d603")
            .build()
        AppMetrica.activate(applicationContext, config)
        AppMetrica.enableActivityAutoTracking(this)
    }

    private fun initInMainProcess() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())   // единственный plant
        }
        initDependencies()
        initMintPermissions()
        initMintPermissionsFlow()
    }

    private fun initDependencies() {
        val rootScope = Quill.getRootScope()
        rootScope.installModules(
            AppModule(this),
            DataModule(this)
        )
        TvStartupTrace.markOnce("app_dependencies_installed")
        Handler(Looper.getMainLooper()).post {
            runCatching {
                rootScope.get(MigrationDataSource::class).update()
            }.onFailure {
                Timber.e(it, "Migration pipeline failed after startup.")
            }
        }
        Handler(Looper.getMainLooper()).post {
            runCatching {
                rootScope.get(LibriaImageLoader::class)
            }.onFailure {
                Timber.w(it, "Image loader warmup failed.")
            }
        }
        if (AndroidTestMode.enabled) {
            rootScope.get(ApiConfig::class).needConfig = false
        }
    }

    /**
     * Проверяем, тот ли это главный процесс.
     * На API 28+ используем `Application.getProcessName()`;
     * на старых версиях — fall‑back через `ActivityManager`.
     */
    private fun isMainProcess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageName == Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val mgr = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            mgr.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName == packageName
        }

    override fun onTerminate() {
        runCatching {
            Quill.getRootScope().get(ApplicationCoroutineScope::class).shutdown()
        }
        super.onTerminate()
    }
}
