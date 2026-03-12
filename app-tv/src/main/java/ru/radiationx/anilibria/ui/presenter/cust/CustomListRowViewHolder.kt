package ru.radiationx.anilibria.ui.presenter.cust

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ListRowView
import ru.radiationx.anilibria.R
import androidx.compose.ui.geometry.Offset

class CustomListRowViewHolder(
    rootView: ListRowView,
    gridView: HorizontalGridView,
    presenter: ListRowPresenter,
    onRequestRailFocus: (() -> Boolean)? = null,
) : ListRowPresenter.ViewHolder(rootView, gridView, presenter) {

    private var descriptionTitle by mutableStateOf("")
    private var descriptionSubtitle by mutableStateOf("")
    private val descriptionView = ComposeView(rootView.context).apply {
        isVisible = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RowDescription(
                title = descriptionTitle,
                subtitle = descriptionSubtitle,
            )
        }
    }

    init {
        rootView.addView(
            descriptionView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (onRequestRailFocus != null) {
            gridView.setOnKeyInterceptListener { event ->
                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                ) {
                    val selectedPosition = gridView.selectedPosition
                    if (selectedPosition >= 0 && !gridView.hasPreviousViewInSameRow(selectedPosition)) {
                        return@setOnKeyInterceptListener onRequestRailFocus.invoke()
                    }
                }
                false
            }
        }
    }

    fun setDescription(title: CharSequence, subtitle: CharSequence) {
        descriptionTitle = title.toString()
        descriptionSubtitle = subtitle.toString()
    }

    fun setExpanded(expanded: Boolean) {
        descriptionView.isVisible = expanded && isSelected
    }

    fun setSelected(selected: Boolean) {
        descriptionView.isVisible = selected && isExpanded
    }
}

@Composable
private fun RowDescription(
    title: String,
    subtitle: String,
) {
    val titleColor = colorResource(R.color.dark_textDefault)
    val subtitleColor = colorResource(R.color.dark_textSecond)
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.72f),
        offset = Offset.Zero,
        blurRadius = 4f,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = textShadow),
            )
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = textShadow),
            )
        }
    }
}
