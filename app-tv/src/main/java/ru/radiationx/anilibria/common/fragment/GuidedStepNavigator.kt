package ru.radiationx.anilibria.common.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.github.terrakok.cicerone.Back
import com.github.terrakok.cicerone.BackTo
import com.github.terrakok.cicerone.Command
import com.github.terrakok.cicerone.Forward
import com.github.terrakok.cicerone.Replace
import com.github.terrakok.cicerone.androidx.AppNavigator
import com.github.terrakok.cicerone.androidx.FragmentScreen

class GuidedStepNavigator(
    activity: FragmentActivity,
    containerId: Int,
    fragmentManager: FragmentManager = activity.supportFragmentManager,
) : AppNavigator(activity, containerId, fragmentManager) {

    private val guidedStack = ArrayDeque<String>()

    private val backStack: List<FragmentManager.BackStackEntry>
        get() = (0 until fragmentManager.backStackEntryCount).map {
            fragmentManager.getBackStackEntryAt(
                it
            )
        }

    fun backStackById(id: Int): FragmentManager.BackStackEntry? = backStack.find { it.id == id }

    override fun setupFragmentTransaction(
        screen: FragmentScreen,
        fragmentTransaction: FragmentTransaction,
        currentFragment: Fragment?,
        nextFragment: Fragment
    ) {
        super.setupFragmentTransaction(screen, fragmentTransaction, currentFragment, nextFragment)
        fragmentTransaction.setReorderingAllowed(true)
    }

    override fun applyCommands(commands: Array<out Command>) {
        val onlyGuidedCommands = commands.all { (it as? Forward)?.screen is GuidedAppScreen }
        if (onlyGuidedCommands) {
            for (command in commands) {
                applyCommand(command)
            }
        } else {
            super.applyCommands(commands)
        }
    }

    override fun applyCommand(command: Command) {
        when (command) {
            is Forward -> guidedForward(command)
            is Replace -> guidedReplace(command)
            is BackTo -> guidedBackTo(command)
            is Back -> guidedBack()
        }
    }

    private fun guidedForward(command: Forward) {
        if (command.screen is GuidedAppScreen) {
            val screen = command.screen as GuidedAppScreen
            showGuidedScreen(screen)
        } else {
            forward(command)
        }
    }

    private fun guidedReplace(command: Replace) {
        if (command.screen is GuidedAppScreen) {
            val screen = command.screen as GuidedAppScreen
            if (guidedStack.isNotEmpty()) {
                fragmentManager.popBackStackImmediate()
                guidedStack.removeLast()
            }
            showGuidedScreen(screen)
        } else {
            replace(command)
        }
    }

    private fun guidedBackTo(command: BackTo) {
        if (guidedStack.isEmpty()) {
            backTo(command)
            return
        }

        val targetKey = command.screen?.screenKey
        if (targetKey == null) {
            while (guidedStack.isNotEmpty()) {
                guidedStack.removeLast()
                fragmentManager.popBackStackImmediate()
            }
            return
        }

        val targetIndex = guidedStack.indexOf(targetKey)
        if (targetIndex < 0) {
            while (guidedStack.isNotEmpty()) {
                guidedStack.removeLast()
                fragmentManager.popBackStackImmediate()
            }
            return
        }

        while (guidedStack.lastOrNull() != targetKey) {
            guidedStack.removeLast()
            fragmentManager.popBackStackImmediate()
        }
    }

    private fun guidedBack() {
        if (guidedStack.isNotEmpty()) {
            fragmentManager.popBackStackImmediate()
            guidedStack.removeLast()
        } else {
            back()
        }
    }

    private fun showGuidedScreen(screen: GuidedAppScreen) {
        val fragment = screen.createFragment(fragmentManager.fragmentFactory)
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment, screen.screenKey)
            .addToBackStack(screen.screenKey)
            .commit()
        guidedStack.add(screen.screenKey)
    }
}
