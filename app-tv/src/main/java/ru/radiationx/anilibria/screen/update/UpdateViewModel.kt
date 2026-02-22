package ru.radiationx.anilibria.screen.update

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.UpdateSourceScreen
import ru.radiationx.data.downloader.RemoteFileLoadEvent
import ru.radiationx.data.downloader.toLocalFile
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.data.interactors.tv.TvUpdateUseCase
import ru.radiationx.shared.ktx.coRunCatching
import ru.radiationx.shared_app.common.SystemUtils
import timber.log.Timber
import javax.inject.Inject

class UpdateViewModel @Inject constructor(
    private val tvUpdateUseCase: TvUpdateUseCase,
    private val guidedRouter: GuidedRouter,
    private val updateController: UpdateController,
    private val systemUtils: SystemUtils,
) : LifecycleViewModel() {

    private var downloadJob: Job? = null

    val updateData = MutableStateFlow<UpdateData?>(null)
    val progressState = MutableStateFlow(false)
    val downloadProgressShowState = MutableStateFlow(false)
    val downloadProgressData = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            progressState.value = true
            coRunCatching {
                tvUpdateUseCase.checkUpdate(false)
            }.onSuccess { update ->
                updateData.value = update
            }.onFailure {
                Timber.e(it)
            }
            progressState.value = false
        }
        updateController
            .downloadAction
            .onEach {
                startDownload(it.url)
            }
            .launchIn(viewModelScope)
    }

    fun onActionClick() {
        if (downloadProgressShowState.value) {
            cancelDownloadClick()
        } else {
            downloadClick()
        }
    }

    private fun downloadClick() {
        val data = updateData.value ?: return
        if (data.links.size > 1) {
            guidedRouter.open(UpdateSourceScreen())
        } else {
            val link = data.links.firstOrNull() ?: return
            startDownload(link.url)
        }
    }

    private fun cancelDownloadClick() {
        downloadJob?.cancel()
        downloadJob = null
        downloadProgressShowState.value = false
    }

    private fun startDownload(url: String) {
        if (downloadJob?.isActive == true) {
            return
        }
        downloadJob = viewModelScope.launch {
            downloadProgressShowState.value = true
            coRunCatching {
                tvUpdateUseCase.downloadUpdate(url).collect { event ->
                    when (event) {
                        is RemoteFileLoadEvent.Progress -> {
                            downloadProgressData.value = event.value
                        }

                        is RemoteFileLoadEvent.Completed -> {
                            systemUtils.openLocalFile(event.file.toLocalFile())
                        }
                    }
                }
            }.onSuccess {
                Unit
            }.onFailure {
                Timber.e(it)
            }
            downloadProgressShowState.value = false
        }
    }

}
