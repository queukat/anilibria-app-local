package ru.radiationx.anilibria.screen

import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.media3.common.util.UnstableApi
import ru.radiationx.anilibria.common.fragment.GuidedAppScreen
import ru.radiationx.anilibria.screen.auth.credentials.AuthCredentialsGuidedFragment
import ru.radiationx.anilibria.screen.auth.main.AuthGuidedFragment
import ru.radiationx.anilibria.screen.auth.otp.AuthOtpGuidedFragment
import ru.radiationx.anilibria.screen.config.ConfigFragment
import ru.radiationx.anilibria.screen.details.DetailFragment
import ru.radiationx.anilibria.screen.details.description.DetailDescriptionGuidedFragment
import ru.radiationx.anilibria.screen.details.other.DetailOtherGuidedFragment
import ru.radiationx.anilibria.screen.mainpages.MainPagesFragment
import ru.radiationx.anilibria.screen.player.PlayerFragment
import ru.radiationx.anilibria.screen.player.end_episode.EndEpisodeGuidedFragment
import ru.radiationx.anilibria.screen.player.end_season.EndSeasonGuidedFragment
import ru.radiationx.anilibria.screen.player.episodes.PlayerEpisodesGuidedFragment
import ru.radiationx.anilibria.screen.player.putIds
import ru.radiationx.anilibria.screen.player.quality.PlayerQualityGuidedFragment
import ru.radiationx.anilibria.screen.player.speed.PlayerSpeedGuidedFragment
import ru.radiationx.anilibria.screen.schedule.ScheduleFragment
import ru.radiationx.anilibria.screen.search.SearchFragment
import ru.radiationx.anilibria.screen.suggestions.SuggestionsFragment
import ru.radiationx.anilibria.screen.update.UpdateFragment
import ru.radiationx.anilibria.screen.update.source.UpdateSourceGuidedFragment
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

class DetailOtherGuidedScreen(private val releaseId: ReleaseId) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return DetailOtherGuidedFragment.newInstance(releaseId)
    }
}

class DetailDescriptionScreen(
    private val title: String,
    private val message: String,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return DetailDescriptionGuidedFragment.newInstance(title, message)
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

class UpdateSourceScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return UpdateSourceGuidedFragment()
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

class AuthGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AuthGuidedFragment()
    }
}

class AuthCredentialsGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AuthCredentialsGuidedFragment()
    }
}

class AuthOtpGuidedScreen : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return AuthOtpGuidedFragment()
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

class PlayerQualityGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return PlayerQualityGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerSpeedGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return PlayerSpeedGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEpisodesGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return PlayerEpisodesGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEndEpisodeGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return EndEpisodeGuidedFragment().putIds(releaseId, episodeId)
    }
}

class PlayerEndSeasonGuidedScreen(
    private val releaseId: ReleaseId,
    private val episodeId: EpisodeId?,
) : GuidedAppScreen() {
    override fun createFragment(factory: FragmentFactory): Fragment {
        return EndSeasonGuidedFragment().putIds(releaseId, episodeId)
    }
}
