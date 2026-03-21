package ru.radiationx.anilibria.screen.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.screen.watching.WatchingPalette

internal data class PlayerActionButtonLayout(
    val minWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp = 24.dp,
    val textFontSize: TextUnit = 16.sp,
)

internal data class PlayerControlsLayoutSpec(
    val panelWidthFraction: Float,
    val progressHorizontalPadding: Dp,
    val progressVerticalPadding: Dp,
    val controlsHorizontalPadding: Dp,
    val controlsBottomPadding: Dp,
    val rowsTopPadding: Dp,
    val rowsVerticalSpacing: Dp,
    val primaryRowSpacing: Dp,
    val secondaryRowSpacing: Dp,
    val buttonLayouts: Map<PlayerControlButtonId, PlayerActionButtonLayout>,
) {
    fun button(id: PlayerControlButtonId): PlayerActionButtonLayout = buttonLayouts.getValue(id)
}

internal data class PlayerFocusableSurfaceColors(
    val backgroundColor: Color,
    val focusedBackgroundColor: Color,
    val borderColor: Color,
)

internal data class PlayerPanelSurfaceStyle(
    val shape: Shape,
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp = 1.dp,
)

internal enum class PlayerControlButtonId {
    Previous,
    SeekBack,
    PlayPause,
    SeekForward,
    Next,
    Episodes,
    Speed,
    Quality,
    AspectRatio,
}

private val transportIconButtonLayout = PlayerActionButtonLayout(
    minWidth = 64.dp,
    horizontalPadding = 12.dp,
    verticalPadding = 11.dp,
    iconSize = 18.dp,
)

internal val PlayerControlsRuntimeLayout = PlayerControlsLayoutSpec(
    panelWidthFraction = 1f,
    progressHorizontalPadding = 0.dp,
    progressVerticalPadding = 0.dp,
    controlsHorizontalPadding = 20.dp,
    controlsBottomPadding = 20.dp,
    rowsTopPadding = 18.dp,
    rowsVerticalSpacing = 16.dp,
    primaryRowSpacing = 10.dp,
    secondaryRowSpacing = 12.dp,
    buttonLayouts = mapOf(
        PlayerControlButtonId.Previous to transportIconButtonLayout,
        PlayerControlButtonId.SeekBack to transportIconButtonLayout,
        PlayerControlButtonId.PlayPause to PlayerActionButtonLayout(
            minWidth = 72.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 11.dp,
            iconSize = 20.dp,
        ),
        PlayerControlButtonId.SeekForward to transportIconButtonLayout,
        PlayerControlButtonId.Next to transportIconButtonLayout,
        PlayerControlButtonId.Episodes to PlayerActionButtonLayout(
            minWidth = 88.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 11.dp,
            iconSize = 18.dp,
            textFontSize = 15.sp,
        ),
        PlayerControlButtonId.Speed to PlayerActionButtonLayout(
            minWidth = 88.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 11.dp,
            iconSize = 18.dp,
            textFontSize = 15.sp,
        ),
        PlayerControlButtonId.Quality to PlayerActionButtonLayout(
            minWidth = 88.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 11.dp,
            textFontSize = 15.sp,
        ),
        PlayerControlButtonId.AspectRatio to PlayerActionButtonLayout(
            minWidth = 100.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 11.dp,
            textFontSize = 15.sp,
        ),
    ),
)

internal object PlayerOverlayUiDefaults {
    val ControlsPanelShape = RoundedCornerShape(18.dp)
    val ProgressSurfaceShape = RoundedCornerShape(12.dp)
    val LoadingPanelShape = RoundedCornerShape(22.dp)
    val ProgressBarShape = RoundedCornerShape(percent = 50)
    val ActionButtonContentSpacing = 8.dp
    val ProgressSurfacePadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    val CompactControlPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
    val PickerPanelPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    val LoadingPanelPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)
    val QuickActionsRowHorizontalPadding = 6.dp
    val QuickActionsRowSpacing = 12.dp
    val PickerSectionSpacing = 12.dp
    val PickerOptionSpacing = 10.dp
    val PickerIndicatorSpacing = 10.dp
    val ProgressContentSpacing = 12.dp
    val ProgressTrackHeight = 6.dp
    val ProgressBufferedTrackHeight = 6.dp
    val PickerMinWidth = 264.dp
    val PickerMaxWidth = 340.dp
    val PickerIndicatorSize = 12.dp
    val ProgressTimeWidth = 78.dp
    val LoadingIndicatorSize = 22.dp
    val LoadingIndicatorStrokeWidth = 2.dp
    const val PlayerFocusScale = 1f
    val PlayerFocusShadowElevation = 0.dp

    fun controlsPanelStyle(palette: WatchingPalette): PlayerPanelSurfaceStyle {
        return PlayerPanelSurfaceStyle(
            shape = ControlsPanelShape,
            backgroundColor = Color.Black.copy(alpha = 0.86f),
            borderColor = palette.textColor.copy(alpha = 0.12f),
        )
    }

    fun loadingPanelStyle(palette: WatchingPalette): PlayerPanelSurfaceStyle {
        return PlayerPanelSurfaceStyle(
            shape = LoadingPanelShape,
            backgroundColor = Color.Black.copy(alpha = 0.68f),
            borderColor = palette.textColor.copy(alpha = 0.12f),
        )
    }

    fun pickerPanelStyle(palette: WatchingPalette): PlayerPanelSurfaceStyle {
        return PlayerPanelSurfaceStyle(
            shape = ControlsPanelShape,
            backgroundColor = palette.surfaceColor.copy(alpha = 0.96f),
            borderColor = palette.textColor.copy(alpha = 0.08f),
        )
    }

    fun progressSurfaceColors(palette: WatchingPalette): PlayerFocusableSurfaceColors {
        return PlayerFocusableSurfaceColors(
            backgroundColor = Color.White.copy(alpha = 0.03f),
            focusedBackgroundColor = Color.White.copy(alpha = 0.08f),
            borderColor = palette.accentColor.copy(alpha = 0.92f),
        )
    }

    fun actionButtonColors(
        palette: WatchingPalette,
        emphasized: Boolean,
    ): PlayerFocusableSurfaceColors {
        return if (emphasized) {
            PlayerFocusableSurfaceColors(
                backgroundColor = palette.accentColor.copy(alpha = 0.18f),
                focusedBackgroundColor = palette.accentColor.copy(alpha = 0.30f),
                borderColor = palette.accentColor.copy(alpha = 0.96f),
            )
        } else {
            PlayerFocusableSurfaceColors(
                backgroundColor = Color.White.copy(alpha = 0.06f),
                focusedBackgroundColor = Color.White.copy(alpha = 0.12f),
                borderColor = palette.textColor.copy(alpha = 0.78f),
            )
        }
    }

    fun quickActionColors(
        palette: WatchingPalette,
        emphasized: Boolean,
    ): PlayerFocusableSurfaceColors {
        return if (emphasized) {
            PlayerFocusableSurfaceColors(
                backgroundColor = palette.accentColor.copy(alpha = 0.20f),
                focusedBackgroundColor = palette.accentColor.copy(alpha = 0.30f),
                borderColor = palette.accentColor.copy(alpha = 0.96f),
            )
        } else {
            PlayerFocusableSurfaceColors(
                backgroundColor = palette.surfaceColor.copy(alpha = 0.90f),
                focusedBackgroundColor = palette.surfaceColor,
                borderColor = palette.textColor.copy(alpha = 0.76f),
            )
        }
    }

    fun pickerOptionColors(
        palette: WatchingPalette,
        selected: Boolean,
    ): PlayerFocusableSurfaceColors {
        return if (selected) {
            PlayerFocusableSurfaceColors(
                backgroundColor = palette.accentColor.copy(alpha = 0.14f),
                focusedBackgroundColor = palette.accentColor.copy(alpha = 0.22f),
                borderColor = palette.accentColor.copy(alpha = 0.92f),
            )
        } else {
            PlayerFocusableSurfaceColors(
                backgroundColor = palette.surfaceColor.copy(alpha = 0.68f),
                focusedBackgroundColor = palette.surfaceColor,
                borderColor = palette.textColor.copy(alpha = 0.76f),
            )
        }
    }

    fun progressTrackColor(): Color = Color.White.copy(alpha = 0.10f)

    fun progressBufferedTrackColor(): Color = Color.White.copy(alpha = 0.18f)

    fun progressFillBrush(palette: WatchingPalette): Brush {
        return Brush.horizontalGradient(
            colors = listOf(
                palette.accentColor.copy(alpha = 0.82f),
                palette.textColor.copy(alpha = 0.94f),
            ),
        )
    }

    fun pickerIndicatorBorderColor(
        palette: WatchingPalette,
        selected: Boolean,
    ): Color {
        return if (selected) {
            palette.accentColor
        } else {
            palette.textColor.copy(alpha = 0.22f)
        }
    }
}
