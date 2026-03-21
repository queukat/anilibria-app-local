package ru.radiationx.anilibria.common

import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.R
import ru.radiationx.shared.ktx.android.asSoftware
import ru.radiationx.shared.ktx.android.getCompatColor
import ru.radiationx.shared.ktx.coRunCatching
import ru.radiationx.shared_app.imageloader.loadImageBitmap
import timber.log.Timber
import java.util.LinkedHashMap
import javax.inject.Inject

data class GradientBackgroundState(
    @ColorInt val baseColor: Int,
    @ColorInt val foregroundColor: Int,
    val foregroundVisible: Boolean,
)

class GradientBackgroundManager @Inject constructor(
    private val activity: FragmentActivity,
) {

    private val defaultColor = activity.getCompatColor(R.color.dark_colorAccent)
    private val foregroundColor = activity.getCompatColor(R.color.dark_windowBackground)

    private val defaultColorSelector = { palette: Palette ->
        palette.getMutedColor(defaultColor)
    }

    private val defaultColorModifier = { color: Int -> color }

    private val _backgroundState = MutableStateFlow(
        GradientBackgroundState(
            baseColor = defaultColor,
            foregroundColor = foregroundColor,
            foregroundVisible = true,
        )
    )
    val backgroundState: StateFlow<GradientBackgroundState> = _backgroundState.asStateFlow()

    private var imageApplierJob: Job? = null
    private val urlColorMap = LinkedHashMap<String, Int>(
        MAX_COLOR_CACHE_SIZE,
        CACHE_LOAD_FACTOR,
        true,
    )

    fun clearGradient() {
        imageApplierJob?.cancel()
        _backgroundState.value = _backgroundState.value.copy(foregroundVisible = true)
    }

    fun applyDefault() {
        imageApplierJob?.cancel()
        updateBackgroundState(defaultColor, foregroundVisible = false)
    }

    fun applyImage(
        url: String,
        colorSelector: (Palette) -> Int? = defaultColorSelector,
        colorModifier: (Int) -> Int = defaultColorModifier,
    ) {
        val normalizedUrl = url.trim().takeIf { it.isNotEmpty() }
        if (normalizedUrl == null) {
            applyDefault()
            return
        }

        val cachedColor = urlColorMap[normalizedUrl]
        if (colorSelector == defaultColorSelector && cachedColor != null) {
            updateBackgroundState(colorModifier(cachedColor), foregroundVisible = false)
            return
        }

        imageApplierJob?.cancel()
        imageApplierJob = activity.lifecycleScope.launch {
            coRunCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    activity.loadImageBitmap(normalizedUrl)
                } ?: return@coRunCatching null
                withContext(Dispatchers.Default) {
                    bitmap.asSoftware {
                        Palette.Builder(it).generate()
                    }
                }
            }.onSuccess { palette ->
                if (palette == null) {
                    applyDefault()
                    return@onSuccess
                }
                if (colorSelector == defaultColorSelector) {
                    cacheDefaultColor(
                        normalizedUrl,
                        colorSelector(palette) ?: defaultColorSelector(palette)
                    )
                }
                applyPalette(palette, colorSelector, colorModifier)
            }.onFailure {
                Timber.e(it)
            }
        }
    }

    private fun applyPalette(
        palette: Palette,
        colorSelector: (Palette) -> Int? = defaultColorSelector,
        colorModifier: (Int) -> Int = defaultColorModifier,
    ) {
        val resolvedColor = colorSelector(palette) ?: defaultColorSelector(palette)
        updateBackgroundState(
            color = colorModifier(resolvedColor),
            foregroundVisible = false,
        )
    }

    private fun updateBackgroundState(
        @ColorInt color: Int,
        foregroundVisible: Boolean,
    ) {
        _backgroundState.value = GradientBackgroundState(
            baseColor = color,
            foregroundColor = foregroundColor,
            foregroundVisible = foregroundVisible,
        )
    }

    private fun cacheDefaultColor(url: String, @ColorInt color: Int) {
        urlColorMap[url] = color
        while (urlColorMap.size > MAX_COLOR_CACHE_SIZE) {
            val eldestKey = urlColorMap.entries.firstOrNull()?.key ?: break
            urlColorMap.remove(eldestKey)
        }
    }

    private companion object {
        const val MAX_COLOR_CACHE_SIZE = 48
        const val CACHE_LOAD_FACTOR = 0.75f
    }
}
