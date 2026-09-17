package com.filigram.cinema

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Handles tracking and reporting new app installations to both Cloudflare Worker
 * and ArvanCloud edge servers with hardware, CPU, and device metadata.
 */
object InstallationTracker {

    private const val TAG = "InstallationTracker"

    // Endpoints
    private const val CF_WORKER_URL = "https://filigramv1.miladjobs22.workers.dev"
    private const val ARVAN_EDGE_URL = "https://filmapi1.milaadfarzian-tnljt.arvanedge.ir"

    // Paths: requested path with fallback for typo tolerance
    private const val PRIMARY_PATH = "/new_installiotn"
    private const val FALLBACK_PATH = "/new_installation"

    private const val PREFS_NAME = "filigram_installation_tracker"
    private const val KEY_REPORTED = "installation_reported_v1"
    private const val KEY_DEVICE_UUID = "device_unique_uuid"
    private const val KEY_FIRST_SEEN = "first_seen_timestamp"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .writeTimeout(7, TimeUnit.SECONDS)
        .build()

    data class ServerTarget(
        val name: String,
        val baseUrl: String,
        val healthCheckPath: String = "/api/config"
    )

    private val SERVER_TARGETS = listOf(
        ServerTarget("Cloudflare Worker", CF_WORKER_URL),
        ServerTarget("ArvanCloud Edge", ARVAN_EDGE_URL)
    )

    /**
     * Checks if this device installation has been reported.
     * If not:
     * 1. Checks which server (Cloudflare or ArvanCloud) is currently reachable and available.
     * 2. Sends the new installation notification through the available server(s).
     * 3. Marks as reported once delivery is confirmed.
     */
    suspend fun checkAndReportInstallation(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REPORTED, false)) {
            AppLogger.d(TAG, "اطلاعات نصب این دستگاه قبلاً ارسال شده است.")
            return@withContext
        }

        try {
            val payload = buildInstallationPayload(context)
            val jsonString = payload.toString()
            AppLogger.i(TAG, "آماده‌سازی ارسال نوتیفیکیشن نصب جدید: $jsonString")

            var delivered = false

            // Step 1: Detect available servers
            for (target in SERVER_TARGETS) {
                AppLogger.i(TAG, "بررسی وضعیت دسترسی سرور ${target.name}...")
                val isAvailable = isServerReachable(target)
                if (isAvailable) {
                    AppLogger.s(TAG, "سرور ${target.name} در دسترس است. در حال ارسال نوتیفیکیشن نصب...")
                    val success = sendToEndpoint(target.baseUrl, jsonString, target.name)
                    if (success) {
                        delivered = true
                        AppLogger.s(TAG, "نوتیفیکیشن نصب با موفقیت از طریق ${target.name} ارسال شد.")
                        break
                    } else {
                        AppLogger.w(TAG, "ارسال از طریق ${target.name} ناموفق بود. بررسی سرور بعدی...")
                    }
                } else {
                    AppLogger.w(TAG, "سرور ${target.name} در دسترس نیست یا مسدود می‌باشد.")
                }
            }

            // Step 2: Fallback attempt to both endpoints if availability check didn't result in delivery
            if (!delivered) {
                AppLogger.i(TAG, "تلاش نهایی ارسال همزمان به کلیه سرورها...")
                delivered = coroutineScope {
                    val cfDeferred = async { sendToEndpoint(CF_WORKER_URL, jsonString, "Cloudflare Worker") }
                    val arvanDeferred = async { sendToEndpoint(ARVAN_EDGE_URL, jsonString, "ArvanCloud Edge") }
                    val results = awaitAll(cfDeferred, arvanDeferred)
                    results.any { it }
                }
            }

            if (delivered) {
                prefs.edit().putBoolean(KEY_REPORTED, true).apply()
                AppLogger.s(TAG, "گزارش نصب جدید با موفقیت به سرور ثبت و تایید شد.")
            } else {
                AppLogger.w(TAG, "ارسال نوتیفیکیشن نصب در این نوبت ناموفق بود. در اجرای بعدی مجدداً تلاش خواهد شد.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در فرآیند ثبت نصب جدید: ${e.message}")
        }
    }

    /**
     * Checks if a server target is reachable with a quick timeout.
     */
    private fun isServerReachable(target: ServerTarget): Boolean {
        return try {
            val request = Request.Builder()
                .url("${target.baseUrl}${target.healthCheckPath}")
                .header("User-Agent", "Filigram-Android-Client/${BuildConfig.VERSION_NAME}")
                .header("Accept", "*/*")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                // Any response from the host (including 200, 404, etc.) proves connectivity
                val reachable = response.isSuccessful || response.code < 500
                if (reachable) {
                    AppLogger.d(TAG, "سرور ${target.name} پاسخ داد: HTTP ${response.code}")
                }
                reachable
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "عدم برقراری ارتباط با ${target.name}: ${e.message}")
            false
        }
    }

    /**
     * Sends the JSON payload to the specified base URL.
     * Tries the primary path (/new_installiotn) first; if 404, tries fallback (/new_installation).
     */
    private fun sendToEndpoint(baseUrl: String, jsonBody: String, serverName: String): Boolean {
        // 1. Try primary path
        val primarySuccess = executePost("$baseUrl$PRIMARY_PATH", jsonBody, serverName)
        if (primarySuccess) return true

        // 2. Try fallback path if primary failed or was 404
        val fallbackSuccess = executePost("$baseUrl$FALLBACK_PATH", jsonBody, serverName)
        return fallbackSuccess
    }

    private fun executePost(fullUrl: String, jsonBody: String, serverName: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(fullUrl)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .header("User-Agent", "Filigram-Android-Client/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppLogger.s(TAG, "ارسال به $serverName ($fullUrl) موفق بود: کد ${response.code}")
                    true
                } else {
                    AppLogger.w(TAG, "پاسخ از $serverName ($fullUrl): کد ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "عدم برقراری ارتباط با $serverName ($fullUrl): ${e.message}")
            false
        }
    }

    /**
     * Constructs a comprehensive, structured JSON payload containing date, hardware ID,
     * device name, CPU details, and OS specifications.
     */
    fun buildInstallationPayload(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Ensure persistent UUID across launches
        var deviceUuid = prefs.getString(KEY_DEVICE_UUID, null)
        if (deviceUuid.isNullOrBlank()) {
            deviceUuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, deviceUuid).apply()
        }

        val nowMs = System.currentTimeMillis()
        var firstSeenMs = prefs.getLong(KEY_FIRST_SEEN, 0L)
        if (firstSeenMs == 0L) {
            firstSeenMs = nowMs
            prefs.edit().putLong(KEY_FIRST_SEEN, firstSeenMs).apply()
        }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val installDateIso = isoFormat.format(Date(nowMs))

        // Hardware ID & Android ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        // Device Brand & Model formatting
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }} $model".trim()
        }

        // CPU & Architecture
        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        val primaryAbi = supportedAbis.firstOrNull() ?: "unknown"
        val cpuCores = Runtime.getRuntime().availableProcessors()

        // Display Metrics
        val displayMetrics = context.resources.displayMetrics
        val screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        val densityDpi = displayMetrics.densityDpi

        // Package & App Details
        var packageFirstInstall = 0L
        var packageLastUpdate = 0L
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageFirstInstall = pInfo.firstInstallTime
            packageLastUpdate = pInfo.lastUpdateTime
        } catch (_: Exception) {}

        return JSONObject().apply {
            put("event", "new_installation")
            put("installed_at", installDateIso)
            put("timestamp_ms", nowMs)
            put("timezone", TimeZone.getDefault().id)

            // Hardware details
            put("hardware", JSONObject().apply {
                put("hardware_id", androidId)
                put("android_id", androidId)
                put("device_uuid", deviceUuid)
                put("board", Build.BOARD.orEmpty())
                put("hardware", Build.HARDWARE.orEmpty())
                put("fingerprint", Build.FINGERPRINT.orEmpty())
            })

            // Mobile name & branding
            put("mobile", JSONObject().apply {
                put("name", deviceName)
                put("manufacturer", manufacturer)
                put("brand", brand)
                put("model", model)
                put("product", Build.PRODUCT.orEmpty())
                put("device", Build.DEVICE.orEmpty())
            })

            // CPU details
            put("cpu", JSONObject().apply {
                put("architecture", primaryAbi)
                put("supported_abis", JSONArray(supportedAbis))
                put("supported_64_bit_abis", JSONArray(Build.SUPPORTED_64_BIT_ABIS?.toList() ?: emptyList<String>()))
                put("supported_32_bit_abis", JSONArray(Build.SUPPORTED_32_BIT_ABIS?.toList() ?: emptyList<String>()))
                put("cores_count", cpuCores)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    put("soc_model", Build.SOC_MODEL.orEmpty())
                    put("soc_manufacturer", Build.SOC_MANUFACTURER.orEmpty())
                }
            })

            // Android OS details
            put("os", JSONObject().apply {
                put("android_version", Build.VERSION.RELEASE.orEmpty())
                put("sdk_int", Build.VERSION.SDK_INT)
                put("security_patch", Build.VERSION.SECURITY_PATCH.orEmpty())
            })

            // App details
            put("app", JSONObject().apply {
                put("app_name", "Filigram Cinema")
                put("package_name", context.packageName)
                put("version_name", BuildConfig.VERSION_NAME)
                put("version_code", BuildConfig.VERSION_CODE)
                put("first_install_time", packageFirstInstall)
                put("last_update_time", packageLastUpdate)
            })

            // Screen & Display
            put("display", JSONObject().apply {
                put("resolution", screenResolution)
                put("density_dpi", densityDpi)
            })
        }
    }
}
