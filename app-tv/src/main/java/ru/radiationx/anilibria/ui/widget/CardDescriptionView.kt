package ru.radiationx.anilibria.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import ru.radiationx.anilibria.R

class CardDescriptionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val subtitleView: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_card_description, this, true)
        titleView = findViewById(R.id.cardDescriptionTitle)
        subtitleView = findViewById(R.id.cardDescriptionSubtitle)
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setSubtitle(subtitle: CharSequence) {
        subtitleView.text = subtitle
    }

    fun isFilled(): Boolean {
        return titleView.text.isNotEmpty() || subtitleView.text.isNotEmpty()
    }
}
