package ru.radiationx.data.downloader

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import ru.radiationx.data.SimpleClient
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class RemoteFileRepository
    @Inject
    constructor(
        private val context: Context,
        @SimpleClient private val client: IClient,
        private val holder: RemoteFileHolder,
    ) {
        fun loadFile(
            url: String,
            bucket: RemoteFile.Bucket,
        ): Flow<RemoteFileLoadEvent> =
            flow {
                emit(RemoteFileLoadEvent.Progress(0))

                val existedFile = getDownloadedFile(url)
                if (existedFile != null) {
                    emit(RemoteFileLoadEvent.Progress(100))
                    emit(RemoteFileLoadEvent.Completed(existedFile))
                    return@flow
                }

                val loadingFileId = holder.get(url)?.id ?: holder.generateId()
                val loadingFile = getFileById(loadingFileId)
                try {
                    val response = client.getRaw(url, emptyMap())

                    val responseBody =
                        requireNotNull(response.body) {
                            "Response doesn't contain a body"
                        }
                    require(responseBody.contentLength() >= 0) {
                        "Response content length < 0 bytes"
                    }
                    loadingFile.createNewFile()
                    responseBody.copyToWithProgress(loadingFile).collect { progress ->
                        emit(RemoteFileLoadEvent.Progress(progress))
                    }
                    val saveData =
                        RemoteFileSaveData(
                            id = loadingFileId,
                            url = url,
                            bucket = bucket,
                            contentDisposition = response.header("Content-Disposition"),
                            contentType = response.header("Content-Type"),
                        )
                    val remoteFile = holder.put(saveData)
                    emit(RemoteFileLoadEvent.Completed(DownloadedFile(remoteFile, loadingFile)))
                } catch (ex: Exception) {
                    if (loadingFile.exists() && !loadingFile.delete()) {
                        ex.addSuppressed(
                            IllegalStateException("Unable to delete incomplete file: ${loadingFile.absolutePath}"),
                        )
                    }
                    throw ex
                }
            }.flowOn(AppDispatchers.io)

        private fun getCacheDir(): File {
            val file = File(context.cacheDir, "anilibria_remote")
            check(file.isDirectory || file.mkdirs()) {
                "Unable to create cache directory: ${file.absolutePath}"
            }
            return file
        }

        private fun getFileById(id: RemoteFileId): File {
            val fileName = id.id.toString()
            return File(getCacheDir(), fileName)
        }

        private suspend fun getDownloadedFile(url: String): DownloadedFile? {
            val remoteFile = holder.get(url) ?: return null
            val file = getFileById(remoteFile.id)
            return if (file.exists() && file.isFile) {
                DownloadedFile(remoteFile, file)
            } else {
                null
            }
        }
    }

sealed interface RemoteFileLoadEvent {
    data class Progress(val value: Int) : RemoteFileLoadEvent

    data class Completed(val file: DownloadedFile) : RemoteFileLoadEvent
}

private fun ResponseBody.copyToWithProgress(destinationFile: File): Flow<Int> {
    return byteStream().copyToWithProgress(destinationFile.outputStream(), contentLength())
}

private fun InputStream.copyToWithProgress(
    outputStream: OutputStream,
    length: Long,
): Flow<Int> =
    flow {
        emit(0)
        use { input ->
            outputStream.use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var progressBytes = 0L
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    progressBytes += bytes
                    bytes = input.read(buffer)
                    emit(((progressBytes * 100) / length).toInt())
                }
            }
        }
        emit(100)
    }
        .flowOn(AppDispatchers.io)
        .distinctUntilChanged()
