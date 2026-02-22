package ru.radiationx.data.datasource.remote.address

import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.storage.ApiConfigStorage
import ru.radiationx.data.system.ApplicationCoroutineScope

class ApiConfigTest {

    @Test
    fun setConfig_withEmptyAddresses_doesNotCrash_andClearsPossibleIps() {
        val storage = mockk<ApiConfigStorage>(relaxed = true)
        coEvery { storage.getActive() } returns null
        coEvery { storage.get() } returns null

        val apiConfig = ApiConfig(
            configChanger = mockk(relaxed = true),
            apiConfigStorage = storage,
            applicationScope = ApplicationCoroutineScope(),
        )

        apiConfig.setConfig(ApiConfigData(addresses = emptyList()))

        assertTrue(apiConfig.getAddresses().isEmpty())
        assertTrue(apiConfig.getPossibleIps().isEmpty())
    }
}
