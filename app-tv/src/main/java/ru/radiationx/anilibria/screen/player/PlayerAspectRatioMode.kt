package ru.radiationx.anilibria.screen.player

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import ru.radiationx.anilibria.R

@OptIn(UnstableApi::class)
enum class PlayerAspectRatioMode(
    @StringRes val titleRes: Int,
    @StringRes val compactTitleRes: Int,
    val resizeMode: Int,
) {
    FIT(
        titleRes = R.string.player_aspect_ratio_fit,
        compactTitleRes = R.string.player_aspect_ratio_fit_short,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ),
    ZOOM(
        titleRes = R.string.player_aspect_ratio_zoom,
        compactTitleRes = R.string.player_aspect_ratio_zoom_short,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    ),
    FILL(
        titleRes = R.string.player_aspect_ratio_fill,
        compactTitleRes = R.string.player_aspect_ratio_fill_short,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL,
    ),
}
