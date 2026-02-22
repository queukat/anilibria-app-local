package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.downloader.RemoteFile
import ru.radiationx.data.downloader.RemoteFileLoadEvent
import ru.radiationx.data.downloader.RemoteFileRepository
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.data.repository.CheckerRepository
import javax.inject.Inject

interface TvUpdateUseCase {
    suspend fun checkUpdate(force: Boolean): UpdateData?
    fun downloadUpdate(url: String): Flow<RemoteFileLoadEvent>
}

class TvUpdateUseCaseImpl @Inject constructor(
    private val checkerRepository: CheckerRepository,
    private val remoteFileRepository: RemoteFileRepository,
) : TvUpdateUseCase {
    override suspend fun checkUpdate(force: Boolean): UpdateData? {
        return checkerRepository.checkUpdate(force)
    }

    override fun downloadUpdate(url: String): Flow<RemoteFileLoadEvent> {
        return remoteFileRepository.loadFile(url, RemoteFile.Bucket.AppUpdates)
    }
}
