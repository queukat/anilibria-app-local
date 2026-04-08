package ru.radiationx.shared_app.imageloader.impls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.clear
import coil.dispose
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import okhttp3.OkHttpClient
import ru.radiationx.data.di.providers.ApiClientWrapper
import ru.radiationx.shared_app.R
import ru.radiationx.shared_app.imageloader.ImageLoaderScopeConfig
import ru.radiationx.shared_app.imageloader.LibriaImageLoader
import ru.radiationx.shared_app.imageloader.utils.toCacheKey
import javax.inject.Inject

class CoilLibriaImageLoaderImpl @Inject constructor(
    private val context: Context,
    private val apiClientWrapper: ApiClientWrapper,
) : LibriaImageLoader {

    private val loaderLock = Any()

    private var loaderState: LoaderState? = null

    init {
        // Best-effort warmup to avoid doing first heavy init inside UI draw path.
        ensureImageLoader()
    }

    fun warmup() {
        ensureImageLoader()
    }

    private fun ensureImageLoader(): ImageLoader {
        val actualOkHttpClient = apiClientWrapper.get()
        synchronized(loaderLock) {
            val currentState = loaderState
            if (currentState != null && currentState.okHttpClient == actualOkHttpClient) {
                return currentState.imageLoader
            }

            currentState?.imageLoader?.shutdown()
            val newImageLoader = createImageLoader(actualOkHttpClient)
            loaderState = LoaderState(
                okHttpClient = actualOkHttpClient,
                imageLoader = newImageLoader,
            )
            return newImageLoader
        }
    }

    private fun createImageLoader(okHttpClient: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .build()
    }

    override fun showImage(imageView: ImageView, url: String?, config: ImageLoaderScopeConfig) {
        val normalizedUrl = url?.takeIf { it.isNotBlank() }

        // Ключевой фикс: если пришёл null/blank — обязаны сбросить successUrl и очистить ImageView,
        // иначе потом возможен ранний return на "старом successUrl".
        if (normalizedUrl == null) {
            config.onStart?.invoke()

            imageView.successUrl = null
            imageView.dispose()
            // отменяем возможную текущую загрузку
            imageView.setImageDrawable(null)

            config.onComplete?.invoke()
            return
        }

        if (imageView.successUrl == normalizedUrl) {
            return
        }

        imageView.load(normalizedUrl, ensureImageLoader()) {
            diskCacheKey(normalizedUrl.toCacheKey())
            memoryCacheKey(normalizedUrl.toCacheKey())
            listener(
                onStart = {
                    config.onStart?.invoke()
                },
                onCancel = {
                    config.onCancel?.invoke()
                    config.onComplete?.invoke()
                },
                onError = { _: ImageRequest, errorResult: ErrorResult ->
                    config.onError?.invoke(errorResult.throwable)
                    config.onComplete?.invoke()
                },
                onSuccess = { _: ImageRequest, successResult: SuccessResult ->
                    imageView.successUrl = normalizedUrl
                    val bitmap = (successResult.drawable as BitmapDrawable).bitmap
                    config.onSuccess?.invoke(bitmap)
                    config.onComplete?.invoke()
                }
            )
        }
    }

    override fun imageLoader(): ImageLoader {
        return ensureImageLoader()
    }

    override suspend fun loadImageBitmap(
        context: Context,
        url: String?,
        widthPx: Int?,
        heightPx: Int?,
    ): Bitmap {
        val safeUrl = url?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Image url is null or blank")

        val requestBuilder = ImageRequest.Builder(context)
            .diskCacheKey(safeUrl.toCacheKey())
            .memoryCacheKey(safeUrl.toCacheKey())
            .data(safeUrl)
            .allowHardware(false)

        if (widthPx != null && heightPx != null) {
            requestBuilder
                .size(widthPx, heightPx)
                .precision(Precision.INEXACT)
        }

        val result = ensureImageLoader().execute(requestBuilder.build())
        val drawable = requireNotNull(result.drawable) {
            "Image request completed without drawable for $safeUrl"
        }
        return (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
    }

    private var ImageView.successUrl: String?
        get() {
            return getTag(R.id.tag_image_loader_success_url) as String?
        }
        set(value) {
            setTag(R.id.tag_image_loader_success_url, value)
        }

    private data class LoaderState(
        val okHttpClient: OkHttpClient,
        val imageLoader: ImageLoader,
    )
}
