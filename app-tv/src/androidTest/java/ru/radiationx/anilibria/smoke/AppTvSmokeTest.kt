package ru.radiationx.anilibria.smoke

import android.content.Intent
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.radiationx.anilibria.screen.details.DetailFragment
import ru.radiationx.anilibria.screen.launcher.MainActivity
import ru.radiationx.anilibria.screen.mainpages.MainPagesFragment

@RunWith(AndroidJUnit4::class)
class AppTvSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun appLaunchesAndMainScreenRenders() {
        waitUntil("MainPagesFragment is not displayed") {
            hasFragment(MainPagesFragment::class.java)
        }
        waitUntil("Main header actions are not visible on launch") {
            hasTextVisible("Главная") && hasTextVisible("Поиск")
        }
    }

    @Test
    fun openCardFromMainListShowsDetailsScreen() {
        waitUntil("MainPagesFragment is not displayed") {
            hasFragment(MainPagesFragment::class.java)
        }
        waitForFakeCardTitle()

        device.pressDPadRight()
        device.pressDPadCenter()

        waitUntil("DetailFragment was not opened from list card") {
            hasFragment(DetailFragment::class.java)
        }
    }

    @Test
    fun watchingScreenKeepsSearchIconVisible() {
        waitUntil("Header 'Я смотрю' is not visible") {
            device.wait(Until.hasObject(By.text("Я смотрю")), 1_000)
        }
        val watchingHeader = device.findObject(By.text("Я смотрю"))
        if (watchingHeader == null) {
            fail("Cannot find 'Я смотрю' header")
        }
        watchingHeader.click()

        waitUntil("Header actions disappeared on 'Я смотрю'") {
            hasTextVisible("Я смотрю") && hasTextVisible("Поиск")
        }
    }

    private fun waitForFakeCardTitle() {
        waitUntil("Fake smoke card is not rendered") {
            device.wait(Until.hasObject(By.textContains("Smoke Release")), 1_000)
        }
    }

    private fun hasTextVisible(text: String): Boolean {
        return device.wait(Until.hasObject(By.text(text)), 1_000)
    }

    private fun hasFragment(fragmentClass: Class<out Fragment>): Boolean {
        var found = false
        scenario.onActivity { activity ->
            found = activity.supportFragmentManager.fragments.any { it.containsFragment(fragmentClass) }
        }
        return found
    }

    private fun Fragment.containsFragment(fragmentClass: Class<out Fragment>): Boolean {
        if (fragmentClass.isInstance(this)) {
            return true
        }
        return childFragmentManager.fragments.any { child -> child.containsFragment(fragmentClass) }
    }

    private fun waitUntil(
        failMessage: String,
        timeoutMs: Long = 15_000L,
        check: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (check()) {
                return
            }
            SystemClock.sleep(100)
        }
        assertTrue(failMessage, check())
    }
}
