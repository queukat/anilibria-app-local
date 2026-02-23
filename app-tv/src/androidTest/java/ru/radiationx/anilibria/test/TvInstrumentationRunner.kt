package ru.radiationx.anilibria.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import ru.radiationx.data.system.AndroidTestMode

class TvInstrumentationRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        System.setProperty(AndroidTestMode.SYSTEM_PROPERTY, "true")
        return super.newApplication(cl, className, context)
    }
}
