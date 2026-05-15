package ru.radiationx.anilibria.screen.update

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.downloader.LocalFile
import ru.radiationx.data.downloader.RemoteFileLoadEvent
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.data.interactors.tv.TvUpdateUseCase
import ru.radiationx.shared.ktx.coRunCatching
import ru.radiationx.shared_app.common.SystemUtils
import timber.log.Timber
import javax.inject.Inject

class UpdateViewModel
    @Inject
    constructor(
        private val tvUpdateUseCase: TvUpdateUseCase,
        private val systemUtils: SystemUtils,
    ) : LifecycleViewModel() {
        private var downloadJob: Job? = null

        private val _updateData = MutableStateFlow<UpdateData?>(null)
        val updateData: StateFlow<UpdateData?> = _updateData.asStateFlow()
        private val _progressState = MutableStateFlow(false)
        val progressState: StateFlow<Boolean> = _progressState.asStateFlow()
        private val _downloadProgressShowState = MutableStateFlow(false)
        val downloadProgressShowState: StateFlow<Boolean> = _downloadProgressShowState.asStateFlow()
        private val _downloadProgressData = MutableStateFlow(0)
        val downloadProgressData: StateFlow<Int> = _downloadProgressData.asStateFlow()
        private val _sourceChooserVisible = MutableStateFlow(false)
        val sourceChooserVisible: StateFlow<Boolean> = _sourceChooserVisible.asStateFlow()
        private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

        init {
            viewModelScope.launch {
                _progressState.value = true
                coRunCatching {
                    tvUpdateUseCase.checkUpdate(false)
                }.onSuccess { update ->
                    _updateData.value = update
                }.onFailure {
                    Timber.e(it)
                }
                _progressState.value = false
            }
        }

        fun onActionClick() {
            if (_downloadProgressShowState.value) {
                cancelDownloadClick()
            } else {
                downloadClick()
            }
        }

        private fun downloadClick() {
            val data = _updateData.value ?: return
            if (data.links.size > 1) {
                _sourceChooserVisible.value = true
            } else {
                val link = data.links.firstOrNull() ?: return
                handleSelectedLink(link)
            }
        }

        private fun cancelDownloadClick() {
            downloadJob?.cancel()
            downloadJob = null
            _downloadProgressShowState.value = false
            _downloadProgressData.value = 0
        }

        fun onSourceSelected(index: Int) {
            val link = _updateData.value?.links?.getOrNull(index) ?: return
            _sourceChooserVisible.value = false
            handleSelectedLink(link)
        }

        fun dismissSourceChooser() {
            _sourceChooserVisible.value = false
        }

        private fun handleSelectedLink(link: UpdateData.UpdateLink) {
            when (link.type) {
                UpdateData.LinkType.FILE -> startDownload(link.url)
                UpdateData.LinkType.SITE -> systemUtils.externalLink(link.url)
            }
        }

        private fun startDownload(url: String) {
            if (downloadJob?.isActive == true) {
                return
            }
            _sourceChooserVisible.value = false
            _downloadProgressData.value = 0
            val expectedSha256 =
                _updateData.value
                    ?.links
                    ?.firstOrNull { it.url == url }
                    ?.sha256
            downloadJob =
                viewModelScope.launch {
                    _downloadProgressShowState.value = true
                    try {
                        coRunCatching {
                            tvUpdateUseCase.downloadUpdate(url).collect { event ->
                                when (event) {
                                    is RemoteFileLoadEvent.Progress -> {
                                        _downloadProgressData.value = event.value
                                    }

                                    is RemoteFileLoadEvent.Completed -> {
                                        when (val verification = tvUpdateUseCase.verifyApk(event.file, expectedSha256)) {
                                            TvUpdateUseCase.ApkVerificationResult.Success -> {
                                                systemUtils.openLocalFile(
                                                    LocalFile(
                                                        file = event.file.local,
                                                        name = event.file.remote.name,
                                                        mimeType = event.file.remote.mimeType,
                                                    ),
                                                )
                                            }

                                            is TvUpdateUseCase.ApkVerificationResult.Failure -> {
                                                _errorMessages.tryEmit(verification.reason)
                                                return@collect
                                            }
                                        }
                                    }
                                }
                            }
                        }.onSuccess {
                            Unit
                        }.onFailure {
                            Timber.e(it)
                            _errorMessages.tryEmit("Не удалось загрузить обновление.")
                        }
                    } finally {
                        _downloadProgressShowState.value = false
                    }
                }
        }
    }
