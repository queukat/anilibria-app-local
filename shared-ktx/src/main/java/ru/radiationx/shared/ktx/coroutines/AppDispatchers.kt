@file:Suppress("kotlin:S6310")

package ru.radiationx.shared.ktx.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

object AppDispatchers {
    val io: CoroutineDispatcher
        get() = Dispatchers.IO

    val default: CoroutineDispatcher
        get() = Dispatchers.Default

    val mainImmediate: MainCoroutineDispatcher
        get() = Dispatchers.Main.immediate
}
