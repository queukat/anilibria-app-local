package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.screen.watching.WatchingPalette

@Composable
internal fun TvPageHeader(
    title: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 780.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle
                ?.takeIf { it.isNotBlank() }
                ?.let { text ->
                    Text(
                        text = text,
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                }
        }
        trailingContent?.let { content ->
            Box(
                contentAlignment = Alignment.TopEnd,
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun TvSectionHeader(
    title: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = title,
            color = palette.textColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(palette.textColor.copy(alpha = 0.08f))
        )
    }
}
