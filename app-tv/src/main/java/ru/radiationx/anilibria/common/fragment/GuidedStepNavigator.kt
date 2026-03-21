package ru.radiationx.anilibria.common.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.github.terrakok.cicerone.Back
import com.github.terrakok.cicerone.BackTo
import com.github.terrakok.cicerone.Command
import com.github.terrakok.cicerone.Forward
import com.github.terrakok.cicerone.androidx.AppNavigator
import com.github.terrakok.cicerone.androidx.FragmentScreen

class GuidedStepNavigator(
    activity: FragmentActivity,
    containerId: Int,
    fragmentManager: FragmentManager = activity.supportFragmentManager,
) : AppNavigator(activity, containerId, fragmentManager) {

    private val guidedStack = ArrayDeque<String>()

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
            is BackTo -> {
                if (guidedStack.isNotEmpty()) {
                    guidedBackTo(command)
                } else {
                    super.applyCommand(command)
                }
            }
            is Back -> {
                if (guidedStack.isNotEmpty()) {
                    guidedBack()
                } else {
                    super.applyCommand(command)
                }
            }
            else -> super.applyCommand(command)
        }
    }

    private fun guidedForward(command: Forward) {
        if (command.screen is GuidedAppScreen) {
            val screen = command.screen as GuidedAppScreen
            showGuidedScreen(screen)
        } else {
            super.applyCommand(command)
        }
    }

    private fun guidedBackTo(command: BackTo) {
        val targetKey = command.screen?.screenKey
        when {
            targetKey == null -> clearGuidedStack()
            targetKey !in guidedStack -> clearGuidedStack()
            else -> {
                while (guidedStack.lastOrNull() != targetKey) {
                    guidedStack.removeLast()
                    fragmentManager.popBackStackImmediate()
                }
            }
        }
    }

    private fun clearGuidedStack() {
        while (guidedStack.isNotEmpty()) {
            guidedStack.removeLast()
            fragmentManager.popBackStackImmediate()
        }
    }

    private fun guidedBack() {
        if (guidedStack.isNotEmpty()) {
            fragmentManager.popBackStackImmediate()
            guidedStack.removeLast()
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
