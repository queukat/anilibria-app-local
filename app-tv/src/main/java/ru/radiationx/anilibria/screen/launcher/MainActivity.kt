package ru.radiationx.anilibria.screen.launcher

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.terrakok.cicerone.NavigatorHolder
import com.github.terrakok.cicerone.androidx.AppNavigator
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.contentprovider.suggestions.SuggestionsContentProvider
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.installModules
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.anilibria.di.ActivityModule
import ru.radiationx.anilibria.di.AppModule
import ru.radiationx.anilibria.di.NavigationModule
import ru.radiationx.anilibria.di.SearchModule
import ru.radiationx.quill.inject

class MainActivity : FragmentActivity() {


    private val viewModel: AppLauncherViewModel by viewModel()
    private val navigator by lazy {
        AppNavigator(this, android.R.id.content)
    }

    private val navigatorHolder by inject<NavigatorHolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)

        installModules(
            ActivityModule(this),
            AppModule(this),
            NavigationModule(),
            SearchModule(),
        )

        super.onCreate(savedInstanceState)

        lifecycle.addObserver(viewModel)
        subscribeTo(viewModel.commands) { _ ->
            handleIntent(intent)
        }

        if (savedInstanceState == null) {
            viewModel.coldLaunch()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        navigatorHolder.setNavigator(navigator)
    }

    override fun onPause() {
        navigatorHolder.removeNavigator()
        super.onPause()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == SuggestionsContentProvider.INTENT_ACTION) {
            val uri = intent.data ?: return
            val id = uri.lastPathSegment?.toInt() ?: return
            viewModel.openRelease(ReleaseId(id))
        }
    }
}
