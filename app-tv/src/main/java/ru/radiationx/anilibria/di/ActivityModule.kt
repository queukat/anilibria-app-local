package ru.radiationx.anilibria.di

import androidx.fragment.app.FragmentActivity
import ru.radiationx.anilibria.common.DetailDataConverter
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.quill.QuillModule
import ru.radiationx.shared_app.common.SystemUtils

class ActivityModule(
    private val activity: FragmentActivity,
) : QuillModule() {
    init {
        instance { SystemUtils(activity) }
        instance { GradientBackgroundManager(activity) }
        instance { DetailDataConverter() }
    }
}
