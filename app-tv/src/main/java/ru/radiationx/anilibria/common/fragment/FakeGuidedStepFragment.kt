package ru.radiationx.anilibria.common.fragment

import android.os.Bundle
import androidx.fragment.app.FragmentTransaction
import androidx.leanback.app.GuidedStepSupportFragment
import ru.radiationx.quill.get
import ru.radiationx.shared.ktx.android.attachBackPressed

open class FakeGuidedStepFragment : GuidedStepSupportFragment() {

    protected open val handleBackWithRouter: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!handleBackWithRouter) return

        attachBackPressed {
            if (isEnabled) {
                get<GuidedRouter>().exit()
                isEnabled = false
            }
        }
    }

    fun fakeOnAddSharedElementTransition(
        transaction: FragmentTransaction,
        disappearingFragment: GuidedStepSupportFragment
    ) {
        onAddSharedElementTransition(transaction, disappearingFragment)
    }
}
