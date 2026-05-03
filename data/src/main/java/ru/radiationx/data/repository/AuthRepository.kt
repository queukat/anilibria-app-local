package ru.radiationx.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import ru.radiationx.data.datasource.holders.AuthHolder
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.holders.SocialAuthHolder
import ru.radiationx.data.datasource.holders.UserHolder
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyDeviceId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyOtpCode
import ru.radiationx.data.datasource.remote.api.AuthApi
import ru.radiationx.data.datasource.remote.parsers.AuthParser
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.auth.OtpAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.data.entity.domain.auth.OtpNotAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpNotFoundException
import ru.radiationx.data.entity.domain.auth.SocialAuth
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.entity.mapper.extractTokenOrNull
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.data.interactors.UserViewsSyncInteractor
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by radiationx on 30.12.17.
 */
class AuthRepository
    @Inject
    constructor(
        /**
         * Legacy API (cookie-based auth).
         *
         * Пока в проекте ещё есть старые эндпоинты/репозитории, которым нужен PHPSESSID.
         */
        private val authApi: AuthApi,
        /**
         * AniLiberty v1 API (token-based auth).
         */
        private val aniLibertyApi: AniLibertyApi,
        private val authTokenHolder: AuthTokenHolder,
        private val userHolder: UserHolder,
        private val authHolder: AuthHolder,
        private val socialAuthHolder: SocialAuthHolder,
        private val apiConfig: ApiConfig,
        private val cookieHolder: CookieHolder,
        private val authParser: AuthParser,
        private val userViewsSyncInteractor: UserViewsSyncInteractor,
    ) {
        fun observeUser(): Flow<ProfileItem?> =
            combine(observeAuthState(), userHolder.observeUser()) { authState, profileItem ->
                profileItem?.takeIf { authState == AuthState.AUTH }
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)

        suspend fun getUser(): ProfileItem? {
            return withContext(Dispatchers.IO) {
                userHolder.getUser()?.takeIf {
                    getAuthState() == AuthState.AUTH
                }
            }
        }

        fun observeAuthState(): Flow<AuthState> =
            combine(
                cookieHolder.observeCookies(),
                authTokenHolder.observeToken(),
                authHolder.observeAuthSkipped(),
            ) { cookies, token, skipped ->
                computeAuthState(cookies, token, skipped)
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)

        suspend fun getAuthState(): AuthState {
            return withContext(Dispatchers.IO) {
                computeAuthState(
                    cookies = cookieHolder.getCookies(),
                    token = authTokenHolder.getToken(),
                    skipped = authHolder.getAuthSkipped(),
                )
            }
        }

        suspend fun setAuthSkipped(value: Boolean) {
            withContext(Dispatchers.IO) {
                authHolder.setAuthSkipped(value)
            }
        }

        suspend fun loadUser(): ProfileItem =
            withContext(Dispatchers.IO) {
                val profile = loadUserInternal()
                updateUser(profile)

                // Background only: do not block startup/profile UX on watch sync.
                userViewsSyncInteractor.scheduleSyncIfNeeded(reason = "loadUser")

                profile
            }

        private suspend fun loadUserInternal(): ProfileItem {
            val token = authTokenHolder.getToken()
            return if (!token.isNullOrBlank()) {
                // token-based profile
                runCatching {
                    aniLibertyApi.getMyProfile().toDomain()
                }.getOrElse { error ->
                    Timber.w(error, "AniLiberty: failed to load profile, fallback to legacy")
                    authApi.loadUser().toDomain(apiConfig)
                }
            } else {
                // legacy profile (cookie-based)
                authApi.loadUser().toDomain(apiConfig)
            }
        }

        suspend fun getOtpInfo(): OtpInfo =
            withContext(Dispatchers.IO) {
                val deviceId = AniLibertyDeviceId(authHolder.getDeviceId())

                runCatching {
                    aniLibertyApi.otpGet(deviceId).toDomain()
                }.getOrElse { error ->
                    throw authParser.checkOtpError(error)
                }
            }

        suspend fun acceptOtp(code: String) =
            withContext(Dispatchers.IO) {
                val otpCode =
                    code.trim().toIntOrNull()
                        ?: throw IllegalArgumentException("OTP code must be numeric")

                runCatching {
                    aniLibertyApi.otpAccept(AniLibertyOtpCode(otpCode))
                }.getOrElse { error ->
                    throw authParser.checkOtpError(error)
                }

                // best-effort: поддержка legacy accept (если где-то ещё используется старый API)
                coRunCatching { authApi.acceptOtp(code) }
                    .onFailure { error ->
                        if (isIgnorableLegacyOtpError(error)) {
                            Timber.d(error, "Legacy OTP accept skipped")
                        } else {
                            Timber.w(error, "Legacy OTP accept failed")
                        }
                    }
            }

        suspend fun signInOtp(code: String): ProfileItem =
            withContext(Dispatchers.IO) {
                val deviceIdRaw = authHolder.getDeviceId()
                val deviceId = AniLibertyDeviceId(deviceIdRaw)

                val otpCodeInt =
                    code.trim().toIntOrNull()
                        ?: throw IllegalArgumentException("OTP code must be numeric")

                val tokenResponse =
                    runCatching {
                        aniLibertyApi.otpLogin(
                            code = AniLibertyOtpCode(otpCodeInt),
                            deviceId = deviceId,
                        )
                    }.getOrElse { error ->
                        throw authParser.checkOtpError(error)
                    }

                val token =
                    tokenResponse.extractTokenOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("AniLiberty: empty auth token")

                authTokenHolder.saveToken(token)

                // best-effort: legacy cookie login (нужно для старых эндпоинтов)
                coRunCatching { authApi.signInOtp(code, deviceIdRaw) }
                    .onFailure { error ->
                        if (error is OtpNotFoundException) {
                            // Ожидаемо: legacy пул OTP может не знать коды нового API
                            Timber.d(error, "Legacy OTP sign-in skipped: otpNotFound")
                        } else {
                            Timber.w(error, "Legacy OTP sign-in failed; old API features may be unavailable")
                        }
                    }

                // load profile and cache
                val profile =
                    runCatching {
                        aniLibertyApi.getMyProfile().toDomain()
                    }.getOrElse { error ->
                        Timber.w(error, "AniLiberty: failed to load profile after OTP sign-in, fallback to legacy")
                        authApi.loadUser().toDomain(apiConfig)
                    }

                updateUser(profile)

                // Background only: do not block auth UX on watch sync.
                userViewsSyncInteractor.scheduleSyncIfNeeded(reason = "signInOtp")

                profile
            }

        suspend fun signIn(
            login: String,
            password: String,
            code2fa: String,
        ): ProfileItem =
            withContext(Dispatchers.IO) {
                val tokenResponse = aniLibertyApi.login(login = login, password = password, code2fa = code2fa)
                val token =
                    tokenResponse.extractTokenOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("AniLiberty: empty auth token")

                authTokenHolder.saveToken(token)

                // best-effort: legacy cookie login (нужно для старых эндпоинтов)
                coRunCatching { authApi.signIn(login, password, code2fa) }
                    .onFailure { Timber.w(it, "Legacy sign-in failed; old API features may be unavailable") }

                val profile =
                    runCatching {
                        aniLibertyApi.getMyProfile().toDomain()
                    }.getOrElse { error ->
                        Timber.w(error, "AniLiberty: failed to load profile after sign-in, fallback to legacy")
                        authApi.loadUser().toDomain(apiConfig)
                    }

                updateUser(profile)

                // Background only: do not block auth UX on watch sync.
                userViewsSyncInteractor.scheduleSyncIfNeeded(reason = "signIn")

                profile
            }

        suspend fun signOut() {
            withContext(Dispatchers.IO) {
                coRunCatching {
                    // token logout (v1)
                    aniLibertyApi.logout()
                }.onFailure {
                    Timber.w(it, "AniLiberty logout failed")
                }

                coRunCatching {
                    // legacy logout (cookie)
                    authApi.signOut()
                }.onFailure {
                    Timber.w(it, "Legacy logout failed")
                }

                cookieHolder.removeAuthCookie()
                authTokenHolder.deleteToken()
                userHolder.delete()
            }
        }

        fun observeSocialAuth(): Flow<List<SocialAuth>> =
            socialAuthHolder
                .observe()
                .flowOn(Dispatchers.IO)

        suspend fun loadSocialAuth(): List<SocialAuth> =
            withContext(Dispatchers.IO) {
                authApi
                    .loadSocialAuth()
                    .map { it.toDomain() }
                    .also { socialAuthHolder.save(it) }
            }

        suspend fun getSocialAuth(key: String): SocialAuth =
            withContext(Dispatchers.IO) {
                socialAuthHolder.get().first { it.key == key }
            }

        suspend fun signInSocial(
            resultUrl: String,
            item: SocialAuth,
        ): ProfileItem =
            withContext(Dispatchers.IO) {
                authApi
                    .signInSocial(resultUrl, item)
                    .toDomain(apiConfig)
                    .also { updateUser(it) }
            }

        private suspend fun updateUser(newUser: ProfileItem) {
            withContext(Dispatchers.IO) {
                userHolder.saveUser(newUser)
            }
        }

        private fun computeAuthState(
            cookies: Map<String, Cookie>,
            token: String?,
            skipped: Boolean,
        ): AuthState {
            val hasToken = !token.isNullOrBlank()
            val hasLegacyCookie = cookies[CookieHolder.PHPSESSID] != null

            return when {
                hasToken || hasLegacyCookie -> AuthState.AUTH
                skipped -> AuthState.AUTH_SKIPPED
                else -> AuthState.NO_AUTH
            }
        }

        private fun isIgnorableLegacyOtpError(error: Throwable): Boolean {
            return error is OtpNotFoundException ||
                error is OtpAcceptedException ||
                error is OtpNotAcceptedException
        }
    }
