package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.downloader.DownloadedFile
import ru.radiationx.data.downloader.RemoteFile
import ru.radiationx.data.downloader.RemoteFileLoadEvent
import ru.radiationx.data.downloader.RemoteFileRepository
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.data.repository.CheckerRepository
import ru.radiationx.data.updater.ApkVerifier
import javax.inject.Inject

interface TvUpdateUseCase {
    sealed interface ApkVerificationResult {
        data object Success : ApkVerificationResult

        data class Failure(val reason: String) : ApkVerificationResult
    }

    suspend fun checkUpdate(force: Boolean): UpdateData?

    fun downloadUpdate(url: String): Flow<RemoteFileLoadEvent>

    suspend fun verifyApk(
        file: DownloadedFile,
        expectedSha256: String?,
    ): ApkVerificationResult
}

class TvUpdateUseCaseImpl
    @Inject
    constructor(
        private val checkerRepository: CheckerRepository,
        private val remoteFileRepository: RemoteFileRepository,
        private val apkVerifier: ApkVerifier,
    ) : TvUpdateUseCase {
        override suspend fun checkUpdate(force: Boolean): UpdateData? {
            return checkerRepository.checkUpdate(force)
        }

        override fun downloadUpdate(url: String): Flow<RemoteFileLoadEvent> {
            return remoteFileRepository.loadFile(url, RemoteFile.Bucket.AppUpdates)
        }

        override suspend fun verifyApk(
            file: DownloadedFile,
            expectedSha256: String?,
        ): TvUpdateUseCase.ApkVerificationResult {
            return when (
                val result =
                    apkVerifier.verify(
                        apkFile = file.local,
                        expectedSha256 = expectedSha256,
                    )
            ) {
                ApkVerifier.Result.Success -> TvUpdateUseCase.ApkVerificationResult.Success
                is ApkVerifier.Result.Failure -> TvUpdateUseCase.ApkVerificationResult.Failure(result.reason)
            }
        }
    }
