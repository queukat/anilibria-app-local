package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.shared_app.imageloader.loadImageBitmap

internal sealed interface TvAsyncImageState {
    data object Empty : TvAsyncImageState
    data object Loading : TvAsyncImageState
    data class Success(val bitmap: ImageBitmap) : TvAsyncImageState
    data class Error(val throwable: Throwable?) : TvAsyncImageState
}

@Composable
internal fun TvAsyncImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholderRes: Int? = null,
    errorRes: Int? = placeholderRes,
    onStateChanged: ((TvAsyncImageState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val normalizedUrl = imageUrl?.trim().takeIf { !it.isNullOrEmpty() }
    val state = produceState<TvAsyncImageState>(
        initialValue = if (normalizedUrl == null) {
            TvAsyncImageState.Empty
        } else {
            TvAsyncImageState.Loading
        },
        key1 = normalizedUrl,
        key2 = context,
    ) {
        if (normalizedUrl == null) {
            value = TvAsyncImageState.Empty
            return@produceState
        }

        value = TvAsyncImageState.Loading
        value = runCatching {
            withContext(Dispatchers.IO) {
                context.loadImageBitmap(normalizedUrl).asImageBitmap()
            }
        }.fold(
            onSuccess = { bitmap -> TvAsyncImageState.Success(bitmap) },
            onFailure = { error -> TvAsyncImageState.Error(error) },
        )
    }.value

    LaunchedEffect(state) {
        onStateChanged?.invoke(state)
    }

    Box(modifier = modifier) {
        when (state) {
            TvAsyncImageState.Empty,
            TvAsyncImageState.Loading,
            -> {
                if (placeholderRes != null) {
                    Image(
                        painter = painterResource(placeholderRes),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        alignment = alignment,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            is TvAsyncImageState.Error -> {
                if (errorRes != null) {
                    Image(
                        painter = painterResource(errorRes),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        alignment = alignment,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            is TvAsyncImageState.Success -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
