package ru.radiationx.data.datasource.holders

import kotlinx.coroutines.flow.Flow

/**
 * Stores AniLiberty API auth token (usually Bearer token).
 *
 * Token is used by [ru.radiationx.data.datasource.remote.interceptors.AniLibertyAuthInterceptor]
 * to attach Authorization header for AniLiberty requests.
 */
interface AuthTokenHolder {
    fun observeToken(): Flow<String?>

    suspend fun getToken(): String?

    suspend fun saveToken(token: String)

    suspend fun deleteToken()
}
