package ru.radiationx.shared_app.imageloader.impls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import coil.ImageLoader
import coil.clear
import coil.dispose
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private var _okHttpClient: OkHttpClient? = null

    private var _imageLoader: ImageLoader? = null

    private val loaderMutex = Mutex()

    private fun getImageLoader(): ImageLoader {
        val result = runBlocking {
            loaderMutex.withLock {
                val actualOkHttpClient = apiClientWrapper.get()
                val okHttpClient = _okHttpClient
                val imageLoader = _imageLoader
                if (imageLoader == null || okHttpClient != actualOkHttpClient) {
                    _imageLoader?.shutdown()
                    val newImageLoader = createImageLoader(actualOkHttpClient)
                    _okHttpClient = actualOkHttpClient
                    _imageLoader = newImageLoader
                    newImageLoader
                } else {
                    imageLoader
                }
            }
        }
        return result
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

        imageView.load(normalizedUrl, getImageLoader()) {
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

    override suspend fun loadImageBitmap(context: Context, url: String?): Bitmap {
        val safeUrl = url?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Image url is null or blank")

        val request = ImageRequest.Builder(context)
            .diskCacheKey(safeUrl.toCacheKey())
            .memoryCacheKey(safeUrl.toCacheKey())
            .data(safeUrl)
            .build()

        val result = getImageLoader().execute(request)
        return (result.drawable as BitmapDrawable).bitmap
    }

    private var ImageView.successUrl: String?
        get() {
            return getTag(R.id.tag_image_loader_success_url) as String?
        }
        set(value) {
            setTag(R.id.tag_image_loader_success_url, value)
        }
}
