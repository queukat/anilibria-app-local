package ru.radiationx.data.analytics.profile

import ru.radiationx.data.analytics.features.model.AnalyticsAppTheme

fun interface AnalyticsThemeProvider {
    fun getTheme(): AnalyticsAppTheme
}
