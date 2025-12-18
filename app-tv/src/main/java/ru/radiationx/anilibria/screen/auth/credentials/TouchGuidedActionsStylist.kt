package ru.radiationx.anilibria.screen.auth.credentials

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionEditText
import androidx.leanback.widget.GuidedActionsStylist

class TouchGuidedActionsStylist : GuidedActionsStylist() {

    override fun onBindViewHolder(vh: ViewHolder, action: GuidedAction) {
        super.onBindViewHolder(vh, action)

        // важно для телефона
        vh.itemView.isFocusableInTouchMode = true
        vh.itemView.isFocusable = true

        val edit = vh.itemView.findViewById<View>(androidx.leanback.R.id.guidedactions_item_description)
        if (edit is GuidedActionEditText) {
            edit.isFocusableInTouchMode = true
            edit.isFocusable = true

            edit.setOnClickListener {
                edit.requestFocus()
                edit.post {
                    val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}
