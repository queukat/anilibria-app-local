package ru.radiationx.anilibria.ui.presenter

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.ui.widget.CardInfoView

class InfoCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardHeight = parent.context.resources.getDimension(R.dimen.card_height).toInt()
        val cardReleaseWidth =
            parent.context.resources.getDimension(R.dimen.card_release_width).toInt()

        val infoView = CardInfoView(parent.context)
        infoView.layoutParams = ViewGroup.LayoutParams(cardReleaseWidth, cardHeight)
        return ViewHolder(infoView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        item ?: return
        item as InfoCard
        val infoView = viewHolder.view as CardInfoView
        infoView.setInfo(item.title, item.subtitle)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // No-op.
    }
}
