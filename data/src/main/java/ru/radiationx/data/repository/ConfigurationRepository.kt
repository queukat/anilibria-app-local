package ru.radiationx.data.repository

import com.stealthcopter.networktools.ping.PingOptions
import com.stealthcopter.networktools.ping.PingResult
import com.stealthcopter.networktools.ping.PingTools
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.address.ApiConfigData
import ru.radiationx.data.datasource.remote.api.ConfigurationApi
import ru.radiationx.data.datasource.storage.ApiConfigStorage
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class ConfigurationRepository
    @Inject
    constructor(
        private val configurationApi: ConfigurationApi,
        private val apiConfig: ApiConfig,
        private val apiConfigStorage: ApiConfigStorage,
    ) {
        private val pingRelay = MutableStateFlow<Map<String, PingResult>?>(null)

        suspend fun checkAvailable(apiUrl: String): Boolean =
            configurationApi.checkAvailable(apiUrl)

        suspend fun getConfiguration(): ApiConfigData =
            withContext(AppDispatchers.io) {
                configurationApi
                    .getConfiguration()
                    .also { apiConfigStorage.save(it) }
                    .toDomain()
                    .also { apiConfig.setConfig(it) }
            }

        suspend fun getPingHost(host: String): PingResult {
            return withContext(AppDispatchers.io) {
                withTimeout(15_000) {
                    PingTools.doNativePing(InetAddress.getByName(host), PingOptions())
                }.also {
                    val map =
                        if (pingRelay.value != null) {
                            pingRelay.value!!.toMutableMap()
                        } else {
                            mutableMapOf()
                        }
                    map[host] = it
                    pingRelay.value = map
                }
            }
        }
    }
