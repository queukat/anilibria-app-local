package ru.radiationx.data.datasource.remote

import okhttp3.Response

interface IClient {
    suspend fun get(
        url: String,
        args: Map<String, String>,
    ): String

    suspend fun post(
        url: String,
        args: Map<String, String>,
    ): String

    suspend fun put(
        url: String,
        args: Map<String, String>,
    ): String

    suspend fun delete(
        url: String,
        args: Map<String, String>,
    ): String

    suspend fun getFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse

    suspend fun postFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse

    suspend fun putFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse

    suspend fun deleteFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse

    suspend fun getRaw(
        url: String,
        args: Map<String, String>,
    ): Response

    suspend fun postRaw(
        url: String,
        args: Map<String, String>,
    ): Response

    // JSON body variants for AniLiberty v1
    suspend fun postJson(
        url: String,
        jsonBody: String,
    ): String {
        throw UnsupportedOperationException("postJson is not implemented")
    }

    suspend fun putJson(
        url: String,
        jsonBody: String,
    ): String {
        throw UnsupportedOperationException("putJson is not implemented")
    }

    suspend fun deleteJson(
        url: String,
        jsonBody: String,
    ): String {
        throw UnsupportedOperationException("deleteJson is not implemented")
    }
}
