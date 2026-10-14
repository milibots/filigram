package com.filigram.cinema

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdater(private val activity: MainActivity) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private var pendingApk: File? = null

    private val unknownSourcesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingApk?.takeIf { it.exists() }?.let(::installApk)
    }

    fun check(manual: Boolean = false) {
        activity.lifecycleScope.launch {
            if (manual) activity.showAppToast("در حال بررسی نسخه جدید…")
            val release = withContext(Dispatchers.IO) { latestRelease() }
            if (release == null) {
                if (manual) activity.showAppToast("بررسی بروزرسانی انجام نشد", autoDismissMs = 4000L)
                return@launch
            }
            if (!isNewer(release.version, BuildConfig.VERSION_NAME)) {
                if (manual) activity.showAppToast("آخرین نسخه نصب است: v${BuildConfig.VERSION_NAME}")
                return@launch
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle("نسخه ${release.version} آماده است")
                .setMessage("نسخه فعلی ${BuildConfig.VERSION_NAME} است. بروزرسانی از GitHub دانلود و سپس صفحه نصب باز می‌شود.")
                .setNegativeButton("بعداً", null)
                .setPositiveButton("دانلود و نصب") { _, _ -> download(release) }
                .show()
        }
    }

    private fun download(release: ReleaseInfo) {
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle("دانلود بروزرسانی")
            .setMessage("لطفاً صبر کنید…")
            .setCancelable(false)
            .create()
        progress.show()
        activity.lifecycleScope.launch {
            val apk = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(release.apkUrl).header("User-Agent", USER_AGENT).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val target = File(activity.cacheDir, "updates/filigram-${release.version}.apk")
                        target.parentFile?.mkdirs()
                        response.body?.byteStream()?.use { input -> target.outputStream().use(input::copyTo) }
                            ?: error("Empty download")
                        target
                    }
                }.getOrNull()
            }
            progress.dismiss()
            if (apk == null) {
                activity.showAppToast("دانلود بروزرسانی ناموفق بود", autoDismissMs = 4000L)
            } else {
                pendingApk = apk
                installApk(apk)
            }
        }
    }

    private fun installApk(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.showAppToast("برای نصب، اجازه «نصب برنامه ناشناس» را فعال کنید", autoDismissMs = 5000L)
            unknownSourcesLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun latestRelease(): ReleaseInfo? = runCatching {
        val request = Request.Builder().url(LATEST_RELEASE_API).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.optString("tag_name").trim().removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") == "app-universal-release.apk") {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (version.isBlank() || !apkUrl.startsWith("https://github.com/")) null else ReleaseInfo(version, apkUrl)
        }
    }.getOrNull()

    private fun isNewer(remote: String, local: String): Boolean {
        val a = remote.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = local.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private data class ReleaseInfo(val version: String, val apkUrl: String)

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/milibots/filigram/releases/latest"
        private const val USER_AGENT = "Filigram-Android-Updater"
    }
}
