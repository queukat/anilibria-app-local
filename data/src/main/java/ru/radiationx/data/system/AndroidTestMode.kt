package ru.radiationx.data.system

object AndroidTestMode {
    const val SYSTEM_PROPERTY: String = "anilibria.android_test_mode"

    val enabled: Boolean
        get() = System.getProperty(SYSTEM_PROPERTY).toBoolean()
}
