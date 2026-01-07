package ru.radiationx.anilibria.ui.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.databinding.ViewSearchControlsBinding

class SearchTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.leanback.R.attr.browseTitleViewStyle
) : BrowseTitleView(context, attrs, defStyleAttr) {

    private val binding: ViewSearchControlsBinding
    private var mode: Mode = Mode.SEARCH

    enum class Mode {
        SEARCH,
        FAVORITES,
    }

    fun setMode(mode: Mode) {
        this.mode = mode

        val upTarget = when (mode) {
            Mode.SEARCH -> R.id.title_orb
            Mode.FAVORITES -> R.id.title_other
        }

        val controls = listOf(
            binding.searchTitleYear,
            binding.searchTitleSeason,
            binding.searchTitleGenre,
            binding.searchTitleSort,
            binding.searchTitleComplete,
        )
        controls.forEach { it.nextFocusUpId = upTarget }
        getControls().nextFocusUpId = upTarget
    }

    fun resetFiltersScroll() {
        getControls().post { getControls().scrollTo(0, 0) }
    }

    var year: String?
        get() = binding.searchTitleYear.getWonderText()
        set(value) = binding.searchTitleYear.setWonderText(value)

    var season: String?
        get() = binding.searchTitleSeason.getWonderText()
        set(value) = binding.searchTitleSeason.setWonderText(value)

    var genre: String?
        get() = binding.searchTitleGenre.getWonderText()
        set(value) = binding.searchTitleGenre.setWonderText(value)

    var sort: String?
        get() = binding.searchTitleSort.getWonderText()
        set(value) = binding.searchTitleSort.setWonderText(value)

    var onlyCompleted: String?
        get() = binding.searchTitleComplete.getWonderText()
        set(value) = binding.searchTitleComplete.setWonderText(value)

    init {
        binding = ViewSearchControlsBinding.inflate(LayoutInflater.from(context), getControls(), true)
        getControls().isVisible = true
        setMode(Mode.SEARCH)
    }

    fun setYearClickListener(listener: OnClickListener?) {
        binding.searchTitleYear.setOnClickListener(listener)
    }

    fun setSeasonClickListener(listener: OnClickListener?) {
        binding.searchTitleSeason.setOnClickListener(listener)
    }

    fun setGenreClickListener(listener: OnClickListener?) {
        binding.searchTitleGenre.setOnClickListener(listener)
    }

    fun setSortClickListener(listener: OnClickListener?) {
        binding.searchTitleSort.setOnClickListener(listener)
    }

    fun setOnlyCompletedClickListener(listener: OnClickListener?) {
        binding.searchTitleComplete.setOnClickListener(listener)
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (findFocus() == null && direction == View.FOCUS_UP) {
            // 1) сначала фильтры
            if (getControls().requestFocus()) return true

            // 2) если фильтры не взяли фокус, тогда верхние кнопки
            if (mode == Mode.FAVORITES && tryFocusTop()) return true
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }


    private fun isFocusInsideFilters(): Boolean =
        binding.searchTitleYear.hasFocus() ||
            binding.searchTitleSeason.hasFocus() ||
            binding.searchTitleGenre.hasFocus() ||
            binding.searchTitleSort.hasFocus() ||
            binding.searchTitleComplete.hasFocus()

    private fun tryFocusTop(): Boolean {
        val root = rootView

        // Prefer a visible/focusable "other" button. There may be multiple views
        // with the same id in the hierarchy (e.g. this SearchTitleView and parent toolbar).
        findFirstFocusableById(root, R.id.title_other)
            ?.let { if (it.requestFocus()) return true }

        findFirstFocusableById(root, R.id.title_orb)
            ?.let { if (it.requestFocus()) return true }

        val buttons: ViewGroup? = root.findViewById(R.id.title_buttons)
        if (buttons?.requestFocus() == true) return true

        return false
    }

    private fun findFirstFocusableById(root: View, id: Int): View? {
        var result: View? = null

        fun walk(v: View) {
            if (result != null) return

            if (v.id == id && v.isShown && v.isFocusable) {
                result = v
                return
            }

            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    walk(v.getChildAt(i))
                    if (result != null) return
                }
            }
        }

        walk(root)
        return result
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (isFocusInsideFilters()) {
                // Не глотаем UP, если реально не смогли перевести фокус
                if (tryFocusTop()) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun TextView.getWonderText(): String? = text?.toString()

    private fun TextView.setWonderText(text: String?) {
        this.text = text
        this.isVisible = text != null
    }


}
