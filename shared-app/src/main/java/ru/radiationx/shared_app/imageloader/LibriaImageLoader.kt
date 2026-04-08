package ru.radiationx.shared_app.imageloader

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import coil.ImageLoader

interface LibriaImageLoader {

    fun showImage(imageView: ImageView, url: String?, config: ImageLoaderScopeConfig)

    fun imageLoader(): ImageLoader

    suspend fun loadImageBitmap(
        context: Context,
        url: String?,
        widthPx: Int? = null,
        heightPx: Int? = null,
    ): Bitmap
}
