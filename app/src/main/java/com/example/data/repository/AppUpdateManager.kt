package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.AppConfig
import com.example.utils.NetworkUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AppUpdateInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val isMandatory: Boolean = false,
    val publishedAt: Long = System.currentTimeMillis()
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult()
    object NoInternet : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateManager {
    fun getCurrentVersionInfo(context: Context): Pair<Long, String> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(code, pInfo.versionName ?: "1.0.0")
        } catch (e: Exception) {
            Pair(1L, "1.0.0")
        }
    }

    /**
     * Checks for updates and returns a detailed UpdateCheckResult.
     */
    suspend fun checkForUpdateResult(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isInternetAvailable(context)) {
            return@withContext UpdateCheckResult.NoInternet
        }

        try {
            val (currentCode, currentVersionName) = getCurrentVersionInfo(context)
            val firestore = FirebaseFirestore.getInstance()

            val fetchedDoc = withTimeoutOrNull(10000L) {
                val docRef = firestore.collection(AppConfig.CONFIG_COLLECTION)
                    .document(AppConfig.CONFIG_DOCUMENT)

                var doc = try {
                    docRef.get(Source.SERVER).await()
                } catch (e: Exception) {
                    try {
                        docRef.get().await()
                    } catch (e2: Exception) {
                        null
                    }
                }

                if (doc == null || !doc.exists()) {
                    try {
                        val globalDoc = firestore.collection("config").document("global").get(Source.SERVER).await()
                        if (globalDoc != null && globalDoc.exists()) {
                            doc = globalDoc
                        }
                    } catch (_: Exception) {}
                }
                doc
            }

            if (fetchedDoc == null || !fetchedDoc.exists()) {
                val isAr = java.util.Locale.getDefault().language == "ar"
                return@withContext UpdateCheckResult.Error(
                    if (isAr) "تعذر الاتصال بخادم التحديثات، يرجى المحاولة لاحقاً"
                    else "Unable to reach update server, please try again later"
                )
            }

            val config = AppConfig.fromDocument(fetchedDoc)

            val isBelowMin = currentCode < config.minVersionCode
            val isUpdateAvailable = config.latestVersionCode > currentCode && config.apkUrl.isNotBlank()
            val isMandatory = isBelowMin || (isUpdateAvailable && config.forceUpdate)

            if (isUpdateAvailable || isBelowMin) {
                val info = AppUpdateInfo(
                    versionCode = config.latestVersionCode,
                    versionName = if (config.latestVersionName.isNotBlank()) config.latestVersionName else "v${config.latestVersionCode}",
                    releaseNotes = config.releaseNotes,
                    downloadUrl = config.apkUrl,
                    isMandatory = isMandatory,
                    publishedAt = config.updatedAt
                )
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = currentVersionName,
                    latestVersion = if (config.latestVersionName.isNotBlank()) config.latestVersionName else currentVersionName
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to connect to update server")
        }
    }

    /**
     * Checks for updates strictly from /config/app matching the Firebase Contract.
     * Backwards-compatible with callers expecting AppUpdateInfo?
     */
    suspend fun checkForUpdate(context: Context): AppUpdateInfo? {
        return when (val result = checkForUpdateResult(context)) {
            is UpdateCheckResult.UpdateAvailable -> result.info
            else -> null
        }
    }
}
