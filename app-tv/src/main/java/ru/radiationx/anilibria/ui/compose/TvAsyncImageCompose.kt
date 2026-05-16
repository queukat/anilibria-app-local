package ru.radiationx.anilibria.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.radiationx.anilibria.common.TvStartupTrace
import ru.radiationx.shared_app.imageloader.libriaImageLoader
import ru.radiationx.shared_app.imageloader.utils.toCacheKey

internal sealed interface TvAsyncImageState {
    data object Empty : TvAsyncImageState

    data object Loading : TvAsyncImageState

    data object Success : TvAsyncImageState

    data class Error(val throwable: Throwable?) : TvAsyncImageState
}

internal data class TvAsyncImageOptions(
    val contentDescription: String? = null,
    val contentScale: ContentScale = ContentScale.Crop,
    val alignment: Alignment = Alignment.Center,
    val placeholderRes: Int? = null,
    val errorRes: Int? = placeholderRes,
    val onStateChanged: ((TvAsyncImageState) -> Unit)? = null,
)

@Composable
internal fun TvAsyncImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    options: TvAsyncImageOptions = TvAsyncImageOptions(),
) {
    val context = LocalContext.current
    val normalizedUrl = imageUrl?.trim().takeIf { !it.isNullOrEmpty() }
    val imageLoader = remember(context) { context.libriaImageLoader() }
    val imageRequest =
        remember(normalizedUrl, context) {
            normalizedUrl?.let { safeUrl ->
                ImageRequest.Builder(context)
                    .data(safeUrl)
                    .diskCacheKey(safeUrl.toCacheKey())
                    .memoryCacheKey(safeUrl.toCacheKey())
                    .crossfade(false)
                    .build()
            }
        }
    val placeholderPainter = options.placeholderRes?.let { painterResource(it) }
    val errorPainter = options.errorRes?.let { painterResource(it) } ?: placeholderPainter
    var state by remember(normalizedUrl) {
        mutableStateOf(
            if (normalizedUrl == null) {
                TvAsyncImageState.Empty
            } else {
                TvAsyncImageState.Loading
            },
        )
    }

    LaunchedEffect(normalizedUrl) {
        state =
            if (normalizedUrl == null) {
                TvAsyncImageState.Empty
            } else {
                TvAsyncImageState.Loading
            }
    }

    LaunchedEffect(state) {
        options.onStateChanged?.invoke(state)
    }

    AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = options.contentDescription,
        contentScale = options.contentScale,
        alignment = options.alignment,
        placeholder = placeholderPainter,
        error = errorPainter,
        fallback = placeholderPainter,
        modifier = modifier,
        onLoading = {
            state = TvAsyncImageState.Loading
        },
        onSuccess = {
            state = TvAsyncImageState.Success
            TvStartupTrace.markOnce("first_poster_visible")
        },
        onError = { error ->
            state = TvAsyncImageState.Error(error.result.throwable)
        },
    )
}
