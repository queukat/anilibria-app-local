package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import ru.radiationx.shared_app.imageloader.loadImageBitmap

@Composable
internal fun ReleasePosterImage(
    imageUrl: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = imageUrl,
    ) {
        value =
            if (imageUrl.isBlank()) {
                null
            } else {
                runCatching {
                    withContext(AppDispatchers.io) {
                        context.loadImageBitmap(imageUrl).asImageBitmap()
                    }
                }.getOrNull()
            }
    }

    Box(
        modifier =
            modifier
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
