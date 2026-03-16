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
    val groupSpacing: Dp,
    val primaryRowSpacing: Dp,
    val secondaryRowSpacing: Dp,
    val primaryButtons: List<PlayerControlButtonId>,
    val secondaryButtons: List<PlayerControlButtonId>,
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

internal object PlayerControlsLayoutPresets {
    private val transportIconButtonLayout = PlayerActionButtonLayout(
        minWidth = 48.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 8.dp,
        iconSize = 18.dp,
    )

    // Edit these presets and inspect the previews below when you want to compare
    // widths, spacing and button order without launching the TV app.
    val Runtime = PlayerControlsLayoutSpec(
        panelWidthFraction = 1f,
        progressHorizontalPadding = 22.dp,
        progressVerticalPadding = 16.dp,
        controlsHorizontalPadding = 22.dp,
        controlsBottomPadding = 18.dp,
        rowsTopPadding = 16.dp,
        groupSpacing = 20.dp,
        primaryRowSpacing = 12.dp,
        secondaryRowSpacing = 8.dp,
        primaryButtons = listOf(
            PlayerControlButtonId.Previous,
            PlayerControlButtonId.SeekBack,
            PlayerControlButtonId.PlayPause,
            PlayerControlButtonId.SeekForward,
            PlayerControlButtonId.Next,
        ),
        secondaryButtons = listOf(
            PlayerControlButtonId.Episodes,
            PlayerControlButtonId.Speed,
            PlayerControlButtonId.Quality,
            PlayerControlButtonId.AspectRatio,
        ),
        buttonLayouts = mapOf(
            PlayerControlButtonId.Previous to transportIconButtonLayout,
            PlayerControlButtonId.SeekBack to transportIconButtonLayout,
            PlayerControlButtonId.PlayPause to transportIconButtonLayout,
            PlayerControlButtonId.SeekForward to transportIconButtonLayout,
            PlayerControlButtonId.Next to transportIconButtonLayout,
            PlayerControlButtonId.Episodes to PlayerActionButtonLayout(
                minWidth = 92.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 9.dp,
                iconSize = 18.dp,
                textFontSize = 15.sp,
            ),
            PlayerControlButtonId.Speed to PlayerActionButtonLayout(
                minWidth = 88.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 9.dp,
                iconSize = 18.dp,
                textFontSize = 15.sp,
            ),
            PlayerControlButtonId.Quality to PlayerActionButtonLayout(
                minWidth = 86.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 9.dp,
                textFontSize = 15.sp,
            ),
            PlayerControlButtonId.AspectRatio to PlayerActionButtonLayout(
                minWidth = 108.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 9.dp,
                textFontSize = 15.sp,
            ),
        ),
    )

    val Compact = Runtime.copy(
        panelWidthFraction = 0.86f,
        groupSpacing = 16.dp,
        primaryRowSpacing = 10.dp,
        secondaryRowSpacing = 6.dp,
        buttonLayouts = Runtime.buttonLayouts +
            mapOf(
                PlayerControlButtonId.Episodes to Runtime.button(PlayerControlButtonId.Episodes).copy(
                    minWidth = 84.dp,
                    horizontalPadding = 8.dp,
                ),
                PlayerControlButtonId.Speed to Runtime.button(PlayerControlButtonId.Speed).copy(
                    minWidth = 82.dp,
                    horizontalPadding = 8.dp,
                ),
                PlayerControlButtonId.Quality to Runtime.button(PlayerControlButtonId.Quality).copy(
                    minWidth = 80.dp,
                    horizontalPadding = 8.dp,
                ),
                PlayerControlButtonId.AspectRatio to Runtime.button(PlayerControlButtonId.AspectRatio).copy(
                    minWidth = 96.dp,
                    horizontalPadding = 8.dp,
                ),
            ),
    )

    val Balanced = Runtime.copy(
        panelWidthFraction = 0.90f,
        groupSpacing = 24.dp,
        primaryRowSpacing = 14.dp,
        secondaryRowSpacing = 10.dp,
        secondaryButtons = listOf(
            PlayerControlButtonId.Episodes,
            PlayerControlButtonId.Quality,
            PlayerControlButtonId.Speed,
            PlayerControlButtonId.AspectRatio,
        ),
        buttonLayouts = Runtime.buttonLayouts +
            mapOf(
                PlayerControlButtonId.Episodes to Runtime.button(PlayerControlButtonId.Episodes).copy(
                    minWidth = 104.dp,
                ),
                PlayerControlButtonId.Speed to Runtime.button(PlayerControlButtonId.Speed).copy(
                    minWidth = 92.dp,
                ),
                PlayerControlButtonId.Quality to Runtime.button(PlayerControlButtonId.Quality).copy(
                    minWidth = 92.dp,
                ),
                PlayerControlButtonId.AspectRatio to Runtime.button(PlayerControlButtonId.AspectRatio).copy(
                    minWidth = 116.dp,
                ),
            ),
    )
}

internal object PlayerOverlayUiDefaults {
    val ControlsPanelShape = RoundedCornerShape(24.dp)
    val LoadingPanelShape = RoundedCornerShape(22.dp)
    val ProgressBarShape = RoundedCornerShape(percent = 50)
    val ActionButtonContentSpacing = 6.dp
    val ProgressSurfacePadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp)
    val CompactControlPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val PickerPanelPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    val LoadingPanelPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)
    val QuickActionsRowHorizontalPadding = 6.dp
    val QuickActionsRowSpacing = 12.dp
    val PickerSectionSpacing = 12.dp
    val PickerOptionSpacing = 10.dp
    val PickerIndicatorSpacing = 10.dp
    val ProgressContentSpacing = 6.dp
    val ProgressTrackHeight = 7.dp
    val ProgressBufferedTrackHeight = 5.dp
    val PickerMinWidth = 240.dp
    val PickerMaxWidth = 300.dp
    val PickerIndicatorSize = 10.dp
    val LoadingIndicatorSize = 22.dp
    val LoadingIndicatorStrokeWidth = 2.dp

    fun controlsPanelStyle(palette: WatchingPalette): PlayerPanelSurfaceStyle {
        return PlayerPanelSurfaceStyle(
            shape = ControlsPanelShape,
            backgroundColor = Color.Black.copy(alpha = 0.82f),
            borderColor = palette.textColor.copy(alpha = 0.14f),
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
            backgroundColor = palette.surfaceColor.copy(alpha = 0.88f),
            focusedBackgroundColor = palette.surfaceColor,
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
                backgroundColor = palette.chipColor.copy(alpha = 0.86f),
                focusedBackgroundColor = palette.chipColor,
                borderColor = palette.textColor.copy(alpha = 0.74f),
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
