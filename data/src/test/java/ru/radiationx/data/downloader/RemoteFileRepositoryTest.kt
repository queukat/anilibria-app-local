package ru.radiationx.data.downloader

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse
import java.io.File
import java.util.UUID

class RemoteFileRepositoryTest {

    @Test
    fun loadFile_emitsProgressAndCompletionEvents() = runBlocking {
        val tempDir = createTempDirectory(prefix = "remote-file-test").toFile()
        val context = mockk<Context>()
        every { context.cacheDir } returns tempDir

        val holder = FakeRemoteFileHolder()
        val repository = RemoteFileRepository(
            context = context,
            client = FakeRawClient("payload"),
            holder = holder,
        )

        val events = repository
            .loadFile("https://example.org/app.apk", RemoteFile.Bucket.AppUpdates)
            .toList()

        val progressValues = events.filterIsInstance<RemoteFileLoadEvent.Progress>().map { it.value }
        val completed = events.filterIsInstance<RemoteFileLoadEvent.Completed>().lastOrNull()

        assertTrue(progressValues.isNotEmpty())
        assertEquals(0, progressValues.first())
        assertTrue(progressValues.contains(100))
        assertTrue(completed != null)
        assertTrue(completed!!.file.local.exists())
    }
}

private class FakeRemoteFileHolder : RemoteFileHolder {
    private val store = mutableMapOf<String, RemoteFile>()

    override fun generateId(): RemoteFileId = RemoteFileId(UUID.randomUUID())

    override suspend fun get(url: String): RemoteFile? = store[url]

    override suspend fun put(data: RemoteFileSaveData): RemoteFile {
        val remote = RemoteFile(
            id = data.id,
            url = data.url,
            bucket = data.bucket,
            name = "downloaded.file",
            mimeType = data.contentType ?: "application/octet-stream",
        )
        store[data.url] = remote
        return remote
    }
}

private class FakeRawClient(
    private val payload: String,
) : IClient {
    override suspend fun getRaw(url: String, args: Map<String, String>): Response {
        val request = Request.Builder().url(url).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .addHeader("Content-Type", "application/octet-stream")
            .body(payload.toResponseBody(null))
            .build()
    }

    override suspend fun postRaw(url: String, args: Map<String, String>): Response = error("Not used")

    override suspend fun get(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun post(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun put(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun delete(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun getFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun postFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun putFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun deleteFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
}
