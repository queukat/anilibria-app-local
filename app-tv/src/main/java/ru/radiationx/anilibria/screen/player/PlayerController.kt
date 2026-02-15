package ru.radiationx.anilibria.screen.player

import kotlinx.coroutines.flow.MutableStateFlow
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.shared.ktx.EventFlow
import javax.inject.Inject

class PlayerController @Inject constructor() {

    val data = MutableStateFlow<List<Release>?>(null)

    val selectEpisodeRelay = EventFlow<EpisodeId>()

    /**
     * true, когда открыт экран плеера и есть активная [PlayerViewModel],
     * которая умеет реагировать на [selectEpisodeRelay].
     *
     * Нужен для guided-экранов (список серий/конец серии/конец сезона),
     * чтобы понимать: переключать серию в текущем плеере или открывать новый [PlayerScreen]
     * (например, когда список серий открыт из Details).
     */
    @Volatile
    var isPlayerActive: Boolean = false
        private set

    fun bindPlayer() {
        isPlayerActive = true
    }

    fun unbindPlayer() {
        isPlayerActive = false
        reset()
    }

    fun reset() {
        data.value = null
    }
}
