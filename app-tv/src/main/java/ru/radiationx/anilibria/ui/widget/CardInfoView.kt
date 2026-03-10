package ru.radiationx.anilibria.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import dev.androidbroadcast.vbpd.viewBinding
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.databinding.ViewCardInfoBinding

class CardInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by viewBinding<ViewCardInfoBinding>(attachToRoot = true)

    init {
        setBackgroundResource(R.color.dark_colorPrimary)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setInfo(
        title: String,
        subtitle: String,
    ) {
        binding.infoTitle.text = title
        binding.infoSubtitle.text = subtitle
    }
}
