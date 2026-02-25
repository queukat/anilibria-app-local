@file:Suppress("DEPRECATION")

package ru.radiationx.data.di

import android.content.Context
import ru.radiationx.quill.QuillModule

class DataModule(context: Context) : QuillModule() {

    init {
        include(
            DataStorageModule(context),
            DataNetworkModule(context),
            DataRepositoryModule(),
            DataTvModule(),
            DataAnalyticsModule(),
        )
    }
}
