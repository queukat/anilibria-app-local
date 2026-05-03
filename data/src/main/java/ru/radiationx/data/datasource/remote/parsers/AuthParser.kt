package ru.radiationx.data.datasource.remote.parsers

import org.json.JSONObject
import ru.radiationx.data.datasource.remote.ApiError
import ru.radiationx.data.entity.domain.auth.EmptyFieldException
import ru.radiationx.data.entity.domain.auth.InvalidUserException
import ru.radiationx.data.entity.domain.auth.OtpAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpNotAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpNotFoundException
import ru.radiationx.data.entity.domain.auth.Wrong2FaCodeException
import ru.radiationx.data.entity.domain.auth.WrongPasswordException
import ru.radiationx.data.entity.domain.auth.WrongUserAgentException
import ru.radiationx.data.system.HttpException
import ru.radiationx.shared.ktx.android.nullString
import javax.inject.Inject

/**
 * Created by radiationx on 31.12.17.
 */
class AuthParser
    @Inject
    constructor() {
        fun checkOtpError(error: Throwable): Throwable =
            when (error) {
                is ApiError -> mapOtpApiError(error)
                is HttpException -> mapOtpHttpError(error)
                else -> error
            }

        fun authResult(responseText: String): String {
            val responseJson = JSONObject(responseText)
            val error = responseJson.nullString("err")
            val message = responseJson.nullString("mes")
            val key = responseJson.nullString("key")
            if (error != "ok" && key != "authorized") {
                val apiError = ApiError(400, message ?: key, null)
                throw when (key) {
                    // ignore
                    // "authorized" -> AlreadyAuthorizedException(apiError)
                    "empty" -> EmptyFieldException(apiError)
                    "wrongUserAgent" -> WrongUserAgentException(apiError)
                    "invalidUser" -> InvalidUserException(apiError)
                    "wrong2FA" -> Wrong2FaCodeException(apiError)
                    "wrongPasswd" -> WrongPasswordException(apiError)
                    else -> apiError
                }
            }
            return message.orEmpty()
        }

        private fun mapOtpApiError(error: ApiError): Throwable =
            when (error.description) {
                "otpNotFound" -> OtpNotFoundException(error.message.orEmpty())
                "otpAccepted" -> OtpAcceptedException(error.message.orEmpty())
                "otpNotAccepted" -> OtpNotAcceptedException(error.message.orEmpty())
                else -> error
            }

        /**
         * AniLiberty v1 может отдавать ошибки через HTTP-коды (4xx/5xx),
         * поэтому дополнительно пытаемся извлечь `description`/`message` из тела ответа.
         *
         * Формат тела может меняться, поэтому парсинг максимально «мягкий»:
         *  - `{ "description": "otpNotFound", "message": "..." }`
         *  - `{ "error": "otpNotFound", "message": "..." }`
         *  - вложенный объект `error`
         */
        private fun mapOtpHttpError(error: HttpException): Throwable {
            val bodyText = runCatching { error.response.body?.string().orEmpty() }.getOrDefault("")
            val parsed = parseOtpErrorBody(bodyText)

            val description = parsed.description
            val message =
                parsed.message
                    ?: error.message
                    ?: "HTTP ${error.code}"

            if (!description.isNullOrBlank()) {
                return when (description) {
                    "otpNotFound" -> OtpNotFoundException(message)
                    "otpAccepted" -> OtpAcceptedException(message)
                    "otpNotAccepted" -> OtpNotAcceptedException(message)
                    else -> error
                }
            }

            // Fallback по HTTP-коду, если в теле не удалось распознать описание.
            return when (error.code) {
                404 -> OtpNotFoundException(message)
                409 -> OtpAcceptedException(message)
                400, 401, 403 -> OtpNotAcceptedException(message)
                else -> error
            }
        }

        private data class ParsedOtpError(
            val description: String?,
            val message: String?,
        )

        private fun parseOtpErrorBody(bodyText: String): ParsedOtpError {
            val text = bodyText.trim()
            if (text.isEmpty() || !text.startsWith("{")) {
                return ParsedOtpError(description = null, message = null)
            }

            return runCatching {
                val json = JSONObject(text)

                val directDescription =
                    json.optString("description").takeIf { it.isNotBlank() }
                        ?: json.optString("error").takeIf { it.isNotBlank() }
                        ?: json.optString("key").takeIf { it.isNotBlank() }

                val directMessage =
                    json.optString("message").takeIf { it.isNotBlank() }
                        ?: json.optString("mes").takeIf { it.isNotBlank() }
                        ?: json.optString("detail").takeIf { it.isNotBlank() }

                // try nested error object
                val nestedErrorObj = json.optJSONObject("error")
                val nestedDescription =
                    nestedErrorObj?.optString("description")?.takeIf { it.isNotBlank() }
                        ?: nestedErrorObj?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: nestedErrorObj?.optString("key")?.takeIf { it.isNotBlank() }

                val nestedMessage =
                    nestedErrorObj?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: nestedErrorObj?.optString("mes")?.takeIf { it.isNotBlank() }
                        ?: nestedErrorObj?.optString("detail")?.takeIf { it.isNotBlank() }

                ParsedOtpError(
                    description = directDescription ?: nestedDescription,
                    message = directMessage ?: nestedMessage,
                )
            }.getOrElse {
                ParsedOtpError(description = null, message = null)
            }
        }
    }
