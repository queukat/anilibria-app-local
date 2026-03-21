package ru.radiationx.anilibria.screen

import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.media3.common.util.UnstableApi
import ru.radiationx.anilibria.screen.auth.AuthFragment
import ru.radiationx.anilibria.screen.config.ConfigFragment
import ru.radiationx.anilibria.screen.details.DetailFragment
import ru.radiationx.anilibria.screen.mainpages.MainPagesFragment
import ru.radiationx.anilibria.screen.player.PlayerFragment
import ru.radiationx.anilibria.screen.schedule.ScheduleFragment
import ru.radiationx.anilibria.screen.search.SearchFragment
import ru.radiationx.anilibria.screen.suggestions.SuggestionsFragment
import ru.radiationx.anilibria.screen.update.UpdateFragment
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import com.github.terrakok.cicerone.androidx.FragmentScreen
import java.util.UUID

class ConfigScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return ConfigFragment()
    }
}

class MainPagesScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return MainPagesFragment()
    }
}

class DetailsScreen(private val releaseId: ReleaseId) : FragmentScreen {
    override val screenKey: String = "details:${releaseId.id}:${UUID.randomUUID()}"

    override fun createFragment(factory: FragmentFactory): Fragment {
        return DetailFragment.newInstance(releaseId)
    }
}

class ScheduleScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return ScheduleFragment()
    }
}

class UpdateScreen
    : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return UpdateFragment()
    }
}

class SuggestionsScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return SuggestionsFragment()
    }
}

class SearchScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return SearchFragment()
    }
}

class AuthScreen : FragmentScreen {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AuthFragment()
    }
}

class PlayerScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : FragmentScreen {
    @OptIn(UnstableApi::class)
    override fun createFragment(factory: FragmentFactory): Fragment {
        return PlayerFragment.newInstance(releaseId, episodeId)
    }
}
