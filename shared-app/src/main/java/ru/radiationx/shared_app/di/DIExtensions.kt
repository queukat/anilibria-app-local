package ru.radiationx.shared_app.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import ru.radiationx.quill.QuillExtra
import ru.radiationx.quill.getViewModel

inline fun <reified T : ViewModel> Fragment.quillParentViewModel(
    noinline extraProvider: (() -> QuillExtra)? = null
): Lazy<T> = lazy {
    val parent = requireParentFragment()
    parent.getViewModel(T::class, extraProvider)
}
//// добавьте рядом с quillParentViewModel в том же пакете
//inline fun <reified T : ViewModel> Fragment.quillActivityViewModel(
//    noinline extraProvider: (() -> QuillExtra)? = null
//): Lazy<T> = lazy {
//    requireActivity().getViewModel(T::class, extraProvider)
//}

