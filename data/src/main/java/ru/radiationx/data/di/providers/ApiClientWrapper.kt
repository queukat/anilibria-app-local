package ru.radiationx.data.di.providers

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.data.datasource.remote.address.ApiConfigChanger
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.data.system.ClientWrapper
import javax.inject.Inject

class ApiClientWrapper @Inject constructor(
    private val provider: ApiOkHttpProvider,
    configChanger: ApiConfigChanger,
    applicationScope: ApplicationCoroutineScope,
) : ClientWrapper(provider) {

    init {
        configChanger
            .observeConfigChanges()
            .onEach {
                set(provider.get())
            }
            .launchIn(applicationScope)
    }

}
