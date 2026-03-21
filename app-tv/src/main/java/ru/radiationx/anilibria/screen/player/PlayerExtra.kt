package ru.radiationx.anilibria.screen.player

import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.QuillExtra

data class PlayerExtra(
    val releaseId: ReleaseId,
    val episodeId: EpisodeId?,
) : QuillExtra
