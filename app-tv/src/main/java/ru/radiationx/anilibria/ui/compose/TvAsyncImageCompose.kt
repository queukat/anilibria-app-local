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
    val imageLoader = remember(context) { context.libriaImageLoader() }
    val imageRequest = remember(normalizedUrl, context) {
        normalizedUrl?.let { safeUrl ->
            ImageRequest.Builder(context)
                .data(safeUrl)
                .diskCacheKey(safeUrl.toCacheKey())
                .memoryCacheKey(safeUrl.toCacheKey())
                .crossfade(false)
                .build()
        }
    }
    val placeholderPainter = placeholderRes?.let { painterResource(it) }
    val errorPainter = errorRes?.let { painterResource(it) } ?: placeholderPainter
    var state by remember(normalizedUrl) {
        mutableStateOf(
            if (normalizedUrl == null) {
                TvAsyncImageState.Empty
            } else {
                TvAsyncImageState.Loading
            }
        )
    }

    LaunchedEffect(normalizedUrl) {
        state = if (normalizedUrl == null) {
            TvAsyncImageState.Empty
        } else {
            TvAsyncImageState.Loading
        }
    }

    LaunchedEffect(state) {
        onStateChanged?.invoke(state)
    }

    AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
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
