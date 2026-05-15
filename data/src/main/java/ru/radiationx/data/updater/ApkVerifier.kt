package ru.radiationx.data.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.withContext
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import timber.log.Timber

class ApkVerifier
    @Inject
    constructor(
        private val context: Context,
    ) {
        sealed interface Result {
            data object Success : Result

            data class Failure(val reason: String) : Result
        }

        suspend fun verify(
            apkFile: File,
            expectedSha256: String?,
        ): Result =
            withContext(AppDispatchers.io) {
                if (!apkFile.exists() || !apkFile.isFile) {
                    return@withContext Result.Failure("Файл обновления не найден.")
                }

                if (!hasMatchingAppSignature(apkFile)) {
                    return@withContext Result.Failure(
                        "Подпись файла обновления не совпадает с установленным приложением.",
                    )
                }

                if (!expectedSha256.isNullOrBlank()) {
                    if (!matchesSha256(apkFile, expectedSha256)) {
                        return@withContext Result.Failure("Контрольная сумма APK не совпадает.")
                    }
                } else {
                    Timber.w("Update APK checksum is not provided; only signature verification was applied.")
                }

                Result.Success
            }

        internal fun calculateSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = input.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return digest.digest().toHex()
        }

        internal fun matchesSha256(
            file: File,
            expectedSha256: String,
        ): Boolean {
            val expectedNormalized = expectedSha256.normalizeSha256() ?: return false
            val actual = calculateSha256(file)
            return actual.equals(expectedNormalized, ignoreCase = true)
        }

        private fun hasMatchingAppSignature(apkFile: File): Boolean {
            val packageManager = context.packageManager
            val installedInfo = getInstalledPackageInfo(packageManager) ?: return false
            val archiveInfo = getArchivePackageInfo(packageManager, apkFile) ?: return false

            if (archiveInfo.packageName != context.packageName) {
                return false
            }

            val installedDigests = extractSignatureDigests(installedInfo)
            val archiveDigests = extractSignatureDigests(archiveInfo)
            if (installedDigests.isEmpty() || archiveDigests.isEmpty()) {
                return false
            }
            return archiveDigests.any { it in installedDigests }
        }

        @Suppress("DEPRECATION")
        private fun getInstalledPackageInfo(packageManager: PackageManager): PackageInfo? {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                    )
                } else {
                    packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                }
            }.getOrNull()
        }

        @Suppress("DEPRECATION")
        private fun getArchivePackageInfo(
            packageManager: PackageManager,
            apkFile: File,
        ): PackageInfo? {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        }

        @Suppress("DEPRECATION")
        private fun extractSignatureDigests(packageInfo: PackageInfo): Set<String> {
            val signatures: Array<Signature> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
                } else {
                    packageInfo.signatures ?: emptyArray()
                }
            return signatures
                .map { signature ->
                    MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .toHex()
                }
                .toSet()
        }

        private fun ByteArray.toHex(): String {
            return joinToString(separator = "") { "%02x".format(it) }
        }

        private fun String?.normalizeSha256(): String? {
            return this
                ?.trim()
                ?.replace(":", "")
                ?.replace(" ", "")
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        }
    }
