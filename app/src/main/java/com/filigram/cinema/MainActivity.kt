package com.filigram.cinema

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ActivityMainBinding
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.app.PictureInPictureParams
import android.util.Rational
import com.google.android.material.button.MaterialButton
import com.filigram.cinema.databinding.BottomSheetAboutBinding
import com.filigram.cinema.databinding.BottomSheetAddToPlaylistBinding
import com.filigram.cinema.databinding.BottomSheetConfirmDialogBinding
import com.filigram.cinema.databinding.BottomSheetCreatePlaylistBinding
import com.filigram.cinema.databinding.BottomSheetEnginesDrawerBinding
import com.filigram.cinema.databinding.BottomSheetItemSelectorBinding
import com.filigram.cinema.databinding.BottomSheetJoinChannelBinding
import com.filigram.cinema.databinding.BottomSheetMovieQuickActionsBinding
import com.filigram.cinema.databinding.BottomSheetRadarBinding
import com.filigram.cinema.databinding.BottomSheetSearchDrawerBinding
import com.filigram.cinema.databinding.BottomSheetShareAppBinding
import com.filigram.cinema.databinding.DialogAnnouncementsBinding
import com.filigram.cinema.databinding.DialogBatchDownloadSelectorBinding
import com.filigram.cinema.databinding.DialogDownloadSettingsBinding
import com.filigram.cinema.databinding.DialogDownloadsHubBinding
import com.filigram.cinema.databinding.DialogFavoritesBinding
import com.filigram.cinema.databinding.DialogInAppPlayerBinding
import com.filigram.cinema.databinding.DialogMovieDetailBinding
import com.filigram.cinema.databinding.DialogPlaylistDetailBinding
import com.filigram.cinema.databinding.DialogPlaylistsHubBinding
import com.filigram.cinema.databinding.ItemQualityRowBinding
import com.filigram.cinema.databinding.ItemRadarServiceRowBinding
import com.filigram.cinema.databinding.ItemSearchHistoryChipBinding
import com.filigram.cinema.download.DownloadForegroundService
import com.filigram.cinema.download.DownloadManager
import com.filigram.cinema.download.DownloadStatus
import com.filigram.cinema.download.DownloadTask
import com.filigram.cinema.download.DownloadsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var movielixApi: MovielixApi

    private lateinit var vitrinAdapter: VitrinSectionAdapter
    private lateinit var gridAdapter: MovieCardAdapter
    private lateinit var logsAdapter: LogsAdapter

    private var currentTab = R.id.nav_home

    private var activeEngine = "movielix"
    private var activeExoPlayer: ExoPlayer? = null

    private var pendingExportJson: String = ""
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var storageFolderLauncher: ActivityResultLauncher<Uri?>
    private var onStorageFolderSelected: ((String) -> Unit)? = null
    private var activePlayerDialog: Dialog? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAppToast("مجوز اعلان فعال شد 🔔")
            checkAndSendWelcomeNotification()
        } else {
            showAppToast("برای دریافت اعلان قسمت‌های جدید به مجوز نوتیفیکیشن نیاز است", autoDismissMs = 4000L)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var vitrinPage = 1
    private var isVitrinLoadingMore = false
    private var hasMoreVitrin = true

    private var gridPage = 1
    private var isGridLoadingMore = false
    private var hasMoreGrid = true
    private var currentGridType = 0
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null && pendingExportJson.isNotEmpty()) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(pendingExportJson.toByteArray(Charsets.UTF_8))
                    }
                    showAppToast("فایل با موفقیت ذخیره شد")
                } catch (e: Exception) {
                    showAppToast("خطا در ذخیره فایل: ${e.message}", autoDismissMs = 4000L)
                } finally {
                    pendingExportJson = ""
                }
            }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }

        storageFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}

                val resolvedPath = uri.path?.let { path ->
                    if (path.contains(":")) {
                        val parts = path.split(":")
                        if (parts.size > 1) {
                            val segment = parts[1]
                            val extStorage = android.os.Environment.getExternalStorageDirectory()
                            java.io.File(extStorage, segment).absolutePath
                        } else uri.toString()
                    } else uri.toString()
                } ?: uri.toString()
                onStorageFolderSelected?.invoke(resolvedPath)
            }
        }

        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        window.setWindowAnimations(R.style.Anim_Filigram_Window)

        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        activeEngine = prefs.getString("active_engine", "movielix") ?: "movielix"

        FavoritesManager.init(this)
        HistoryManager.init(this)
        SeriesSubscriptionManager.init(this)
        SeriesNotificationHelper.createNotificationChannel(this)
        SeriesUpdateWorker.schedulePeriodicCheck(this)
        ImageLoader.init(this)
        AppCacheManager.init(this)
        movielixApi = MovielixApi(this)
        DownloadManager.init(this)
        PlaybackProgressManager.init(this)
        setupDownloadsBadge()

        setupAdapters()
        setupBottomNav()
        setupSearch()
        setupTopMenu()
        setupTopDownloads()
        setupSwipeRefresh()
        setupLogsListener()
        setupPaginationScrollListeners()

        handleSeriesNotificationIntent(intent)

        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()

        loadHomeData()

        refreshAnnouncementsBadge()
        checkNotificationPermission()
        checkAndSendWelcomeNotification()
        checkAppOpenCountAndPromptChannel()

        lifecycleScope.launch {
            RemoteConfigRepository.getConfigs(this@MainActivity)
            InstallationTracker.checkAndReportInstallation(applicationContext)
        }
    }

    private fun checkAppOpenCountAndPromptChannel() {
        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        val hasHandledInvite = prefs.getBoolean("channel_invite_dismissed_or_joined", false)
        if (hasHandledInvite) return

        val launchCount = prefs.getInt("app_launch_count", 0) + 1
        prefs.edit().putInt("app_launch_count", launchCount).apply()

        if (launchCount >= 5) {
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    showJoinChannelBottomSheet()
                }
            }, 1800)
        }
    }

    private fun showJoinChannelBottomSheet() {
        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        val dialog = createStyledBottomSheetDialog()
        val sheetBinding = BottomSheetJoinChannelBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.btnJoinChannelConfirm.setOnClickListener {
            prefs.edit().putBoolean("channel_invite_dismissed_or_joined", true).apply()
            dialog.dismiss()
            val tgUrl = "https://t.me/filigramapp"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl)))
            } catch (e: Exception) {
                showAppToast("کانال تلگرام: t.me/filigramapp", autoDismissMs = 4000L)
            }
        }

        sheetBinding.btnJoinChannelLater.setOnClickListener {
            // Do not permanently block if user selects later; reset counter to 0 so it prompts again after another 5 opens
            prefs.edit().putInt("app_launch_count", 0).apply()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSeriesNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            SeriesUpdateWorker.checkAndNotifyUpdates(applicationContext)
        }
    }

    private fun checkAndSendWelcomeNotification() {
        // Marking the welcome as sent before the user grants POST_NOTIFICATIONS would drop it forever.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        val hasSentWelcome = prefs.getBoolean("has_sent_welcome_notification", false)
        if (!hasSentWelcome) {
            prefs.edit().putBoolean("has_sent_welcome_notification", true).apply()
            binding.root.postDelayed({
                SeriesNotificationHelper.sendWelcomeNotification(this@MainActivity)
            }, 1200)
        }
    }

    private fun handleSeriesNotificationIntent(intent: Intent?) {
        if (intent == null) return
        if (handleWidgetIntent(intent)) return
        if (intent.action == "com.filigram.cinema.ACTION_OPEN_ANNOUNCEMENTS") {
            binding.root.postDelayed({
                showAnnouncementsDialog()
            }, 300)
            return
        }
        val seriesId = intent.getIntExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_ID, -1)
        if (seriesId != -1) {
            val title = intent.getStringExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_TITLE) ?: ""
            val image = intent.getStringExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_IMAGE) ?: ""
            val slug = intent.getStringExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_SLUG)
            val engine = intent.getStringExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_ENGINE)
            if (!engine.isNullOrEmpty() && engine != activeEngine) {
                activeEngine = engine
            }
            val movieItem = MovieItem(
                id = seriesId,
                title = title,
                image = image,
                type = 1,
                slug = slug
            )
            binding.root.postDelayed({
                showMovieDetail(movieItem)
            }, 350)
        }
    }

    private fun handleWidgetIntent(intent: Intent): Boolean {
        if (intent.action != com.filigram.cinema.widget.FiligramWidgetProvider.ACTION_OPEN_ITEM) return false

        val mode = intent.getStringExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_MODE)
        if (mode == com.filigram.cinema.widget.FiligramWidgetProvider.MODE_NEWS) {
            binding.root.postDelayed({ showAnnouncementsDialog() }, 300)
            return true
        }

        val itemId = intent.getIntExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_ITEM_ID, -1)
        if (itemId == -1) return false

        val engine = intent.getStringExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_ITEM_ENGINE)
        if (!engine.isNullOrEmpty() && engine != activeEngine) {
            activeEngine = engine
        }

        val movieItem = MovieItem(
            id = itemId,
            title = intent.getStringExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_ITEM_TITLE) ?: "",
            image = intent.getStringExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_ITEM_IMAGE) ?: "",
            type = intent.getIntExtra(com.filigram.cinema.widget.FiligramWidgetProvider.EXTRA_ITEM_TYPE, 0)
        )
        binding.root.postDelayed({ showMovieDetail(movieItem) }, 350)
        return true
    }

    private fun Dialog.applyFullscreenAnimation() {
        window?.setWindowAnimations(R.style.Anim_Filigram_Dialog_Fullscreen)
    }

    private fun Dialog.applyCompactAnimation() {
        window?.setWindowAnimations(R.style.Anim_Filigram_Dialog_Compact)
    }

    private fun applyDialogStatusBarInsets(topBar: View) {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fallbackStatusHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else (38 * resources.displayMetrics.density).toInt()
        val safeTop = maxOf(fallbackStatusHeight, (40 * resources.displayMetrics.density).toInt())
        val initialPaddingBottom = topBar.paddingBottom
        val initialPaddingStart = topBar.paddingStart
        val initialPaddingEnd = topBar.paddingEnd
        val extraSpacer = (8 * resources.displayMetrics.density).toInt()

        topBar.setPaddingRelative(initialPaddingStart, safeTop + extraSpacer, initialPaddingEnd, initialPaddingBottom)

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val targetTop = maxOf(cutoutInsets.top, safeTop) + extraSpacer
            v.setPaddingRelative(initialPaddingStart, targetTop, initialPaddingEnd, initialPaddingBottom)
            insets
        }
    }

    private fun View.animateIn(durationMs: Long = 260, startDelayMs: Long = 0, fromY: Float = 30f) {
        alpha = 0f
        translationY = fromY
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setStartDelay(startDelayMs)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun crossFade(hideView: View, showView: View, duration: Long = 220) {
        if (showView.isVisible) return
        showView.alpha = 0f
        showView.isVisible = true
        showView.animate().alpha(1f).setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
        if (hideView.isVisible) {
            hideView.animate().alpha(0f).setDuration(duration).setInterpolator(DecelerateInterpolator())
                .withEndAction { hideView.isVisible = false; hideView.alpha = 1f }.start()
        }
    }

    private fun setupAdapters() {

        vitrinAdapter = VitrinSectionAdapter(
            mutableListOf(),
            onItemClick = { item -> showMovieDetail(item) },
            onItemLongClick = { item -> showMediaQuickActionsBottomSheet(item) }
        )
        binding.rvVitrinSections.layoutManager = LinearLayoutManager(this)
        binding.rvVitrinSections.adapter = vitrinAdapter

        gridAdapter = MovieCardAdapter(
            mutableListOf(),
            onItemClick = { item -> showMovieDetail(item) },
            onItemLongClick = { item -> showMediaQuickActionsBottomSheet(item) }
        )
        val gridLm = GridLayoutManager(this, 2)
        binding.rvGridMovies.layoutManager = gridLm
        binding.rvGridMovies.adapter = gridAdapter

        logsAdapter = LogsAdapter()
        val logsLm = LinearLayoutManager(this)
        logsLm.stackFromEnd = true
        binding.rvLogs.layoutManager = logsLm
        binding.rvLogs.adapter = logsAdapter
        logsAdapter.setAllLogs(AppLogger.getAllLogs())

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clear()
            logsAdapter.clear()
        }
    }

    private fun setupTopMenu() {
        binding.btnTopMenu.setOnClickListener {
            showEnginesDrawer()
        }
    }

    private fun setupPaginationScrollListeners() {

        binding.homeScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            if (currentTab == R.id.nav_home && hasMoreVitrin && !isVitrinLoadingMore && activeEngine == "movielix") {
                val totalContentHeight = v.getChildAt(0)?.measuredHeight ?: 0
                val scrollViewHeight = v.measuredHeight
                if (scrollY >= (totalContentHeight - scrollViewHeight - 400)) {
                    loadMoreVitrin()
                }
            }
        })

        binding.rvGridMovies.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && hasMoreGrid && !isGridLoadingMore) {
                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val totalItemCount = lm.itemCount
                    val lastVisibleItem = lm.findLastVisibleItemPosition()
                    if (totalItemCount <= (lastVisibleItem + 6)) {
                        loadMoreGrid()
                    }
                }
            }
        })
    }

    private fun setupLogsListener() {
        AppLogger.addListener { entry ->
            runOnUiThread {
                logsAdapter.addLog(entry)
                if (binding.logsScreenContainer.isVisible) {
                    binding.rvLogs.scrollToPosition(logsAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun updateBottomNavVisibility() {
        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        val showDevLogs = prefs.getBoolean("show_dev_logs", false)
        val navItem = binding.bottomNavigation.menu.findItem(R.id.nav_notifications)
        if (navItem != null) {
            if (showDevLogs) {
                navItem.title = "لاگ‌ها"
                navItem.setIcon(android.R.drawable.ic_menu_info_details)
                try {
                    binding.bottomNavigation.removeBadge(R.id.nav_notifications)
                } catch (_: Exception) {}
            } else {
                navItem.title = "اعلان‌ها"
                navItem.setIcon(R.drawable.ic_bell_gold)
                refreshAnnouncementsBadge()
            }
        }
    }

    private fun setupBottomNav() {
        updateBottomNavVisibility()
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeScreen()
                    true
                }
                R.id.nav_movies -> {
                    showGridScreen("فیلم‌های برتر")
                    initGridData(type = 0)
                    true
                }
                R.id.nav_series -> {
                    showGridScreen("سریال‌های برتر")
                    initGridData(type = 1)
                    true
                }
                R.id.nav_playlists -> {
                    showPlaylistsHubDialog()
                    false
                }
                R.id.nav_notifications -> {
                    val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
                    val showDevLogs = prefs.getBoolean("show_dev_logs", false)
                    if (showDevLogs) {
                        showLogsScreen()
                        true
                    } else {
                        showAnnouncementsDialog()
                        false
                    }
                }
                else -> false
            }
        }
    }

    // ─── In-app notification (replaces all Toast.makeText) ───────────────────
    private var activeNotifSheet: BottomSheetDialog? = null

    private fun showAppToast(
        title: String,
        subtitle: String? = null,
        iconRes: Int = R.drawable.ic_star_gold,
        isLoading: Boolean = false,
        autoDismissMs: Long = 2500L
    ): BottomSheetDialog {
        activeNotifSheet?.dismiss()
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_notification, null)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = view.measuredHeight
        sheet.behavior.isDraggable = true

        view.findViewById<android.widget.ImageView>(R.id.notifIcon).setImageResource(iconRes)
        view.findViewById<android.widget.TextView>(R.id.notifTitle).text = title

        val subtitleView = view.findViewById<android.widget.TextView>(R.id.notifSubtitle)
        if (!subtitle.isNullOrEmpty()) {
            subtitleView.text = subtitle
            subtitleView.visibility = android.view.View.VISIBLE
        }

        val progressView = view.findViewById<android.widget.ProgressBar>(R.id.notifProgress)
        if (isLoading) progressView.visibility = android.view.View.VISIBLE

        sheet.show()
        activeNotifSheet = sheet

        if (!isLoading) {
            view.postDelayed({ if (sheet.isShowing) sheet.dismiss() }, autoDismissMs)
        }
        return sheet
    }

    private fun showHomeScreen() {
        currentTab = R.id.nav_home
        binding.topTitle.text = "فیلیگرام"
        animateTitleChange()
        if (!binding.swipeRefresh.isVisible) {
            binding.gridScreenContainer.animate().alpha(0f).setDuration(180).withEndAction {
                binding.gridScreenContainer.isVisible = false
                binding.logsScreenContainer.isVisible = false
                binding.swipeRefresh.alpha = 0f
                binding.swipeRefresh.isVisible = true
                binding.swipeRefresh.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            }.start()
        } else {
            binding.gridScreenContainer.isVisible = false
            binding.logsScreenContainer.isVisible = false
        }
    }

    private fun showGridScreen(title: String) {
        currentTab = if (title.contains("فیلم")) R.id.nav_movies else R.id.nav_series
        binding.topTitle.text = title
        animateTitleChange()
        val wasHidden = !binding.gridScreenContainer.isVisible
        binding.swipeRefresh.isVisible = false
        binding.logsScreenContainer.isVisible = false
        if (wasHidden) {
            binding.gridScreenContainer.alpha = 0f
            binding.gridScreenContainer.isVisible = true
            binding.gridScreenContainer.animate().alpha(1f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        } else {
            binding.gridScreenContainer.isVisible = true
        }
    }

    private fun showLogsScreen() {
        currentTab = R.id.nav_notifications
        binding.topTitle.text = "کنسول استریم لاگ‌ها"
        animateTitleChange()
        binding.swipeRefresh.isVisible = false
        binding.gridScreenContainer.isVisible = false
        if (!binding.logsScreenContainer.isVisible) {
            binding.logsScreenContainer.alpha = 0f
            binding.logsScreenContainer.isVisible = true
            binding.logsScreenContainer.animate().alpha(1f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        }
        binding.rvLogs.scrollToPosition(logsAdapter.itemCount - 1)
    }

    private fun animateTitleChange() {
        binding.topTitle.animate().alpha(0f).setDuration(100).withEndAction {
            binding.topTitle.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    private fun setupSearch() {
        binding.searchContainer.isVisible = false
        binding.btnTopSearch.setOnClickListener {
            showSearchBottomSheet()
        }
    }

    private fun showSearchBottomSheet() {
        val dialog = createStyledBottomSheetDialog()
        val searchBinding = BottomSheetSearchDrawerBinding.inflate(layoutInflater)
        dialog.setContentView(searchBinding.root)

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        searchBinding.tvSearchEngineSubtitle.text = "موتور فعال: ${getEngineName(activeEngine)}"

        searchBinding.btnSearchDrawerClose.setOnClickListener {
            dialog.dismiss()
        }

        val renderDrawerSearchHistory = {
            val queries = SearchHistoryManager.getQueries(this@MainActivity)
            if (queries.isEmpty()) {
                searchBinding.recentSearchesSectionDrawer.isVisible = false
            } else {
                searchBinding.recentSearchesSectionDrawer.isVisible = true
                searchBinding.layoutSearchChipsDrawer.removeAllViews()
                for (q in queries) {
                    val chipBinding = ItemSearchHistoryChipBinding.inflate(layoutInflater, searchBinding.layoutSearchChipsDrawer, false)
                    chipBinding.txtSearchQuery.text = q
                    chipBinding.chipRoot.setOnClickListener {
                        dialog.dismiss()
                        SearchHistoryManager.addQuery(this@MainActivity, q)
                        initSearch(q)
                    }
                    chipBinding.btnRemoveQuery.setOnClickListener {
                        SearchHistoryManager.removeQuery(this@MainActivity, q)
                        val updated = SearchHistoryManager.getQueries(this@MainActivity)
                        if (updated.isEmpty()) {
                            searchBinding.recentSearchesSectionDrawer.isVisible = false
                        } else {
                            searchBinding.layoutSearchChipsDrawer.removeView(chipBinding.root)
                        }
                    }
                    searchBinding.layoutSearchChipsDrawer.addView(chipBinding.root)
                }
            }
        }
        renderDrawerSearchHistory()

        searchBinding.btnClearSearchHistoryDrawer.setOnClickListener {
            SearchHistoryManager.clearQueries(this@MainActivity)
            searchBinding.recentSearchesSectionDrawer.isVisible = false
            searchBinding.layoutSearchChipsDrawer.removeAllViews()
        }

        searchBinding.etSearchDrawer.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchBinding.btnClearSearchDrawerText.isVisible = !s.isNullOrEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBinding.btnClearSearchDrawerText.setOnClickListener {
            searchBinding.etSearchDrawer.setText("")
        }

        val executeSearch = {
            val query = searchBinding.etSearchDrawer.text.toString().trim()
            if (query.isNotEmpty()) {
                SearchHistoryManager.addQuery(this@MainActivity, query)
                dialog.dismiss()
                initSearch(query)
            } else {
                showAppToast("لطفاً عبارت جستجو را وارد کنید")
            }
        }

        searchBinding.btnSubmitSearchDrawer.setOnClickListener {
            executeSearch()
        }

        searchBinding.etSearchDrawer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch()
                true
            } else {
                false
            }
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.elevation = 0f
            if (bottomSheet != null) {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
            searchBinding.etSearchDrawer.postDelayed({
                searchBinding.etSearchDrawer.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(searchBinding.etSearchDrawer, InputMethodManager.SHOW_IMPLICIT)
            }, 180)
        }

        dialog.show()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadHomeData()
        }
    }

    private fun loadHomeData() {
        vitrinPage = 1
        hasMoreVitrin = true
        binding.mainProgressBar.isVisible = true
        binding.vitrinLoadingMore.isVisible = false

        lifecycleScope.launch {
            try {
                if (activeEngine == "almasmovie") {
                    val homeSections = AlmasMovieApi.getHomeSections()
                    val finalSections = if (homeSections.isNotEmpty()) {
                        homeSections
                    } else {
                        val almasMovies = AlmasMovieApi.getRecent(1)
                        listOf(VitrinSection(1, "منتخب فیلم‌های الماس‌مووی (AlmasMovie)", almasMovies))
                    }
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false
                        binding.bannerViewPager.isVisible = false
                        vitrinAdapter.updateData(finalSections)
                    }
                } else if (activeEngine == "rezflix") {
                    val rezMovies = RezFlixApi.getMovies(1)
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false
                        binding.bannerViewPager.isVisible = false
                        val section = VitrinSection(1, "منتخب فیلم‌های رزفلیکس (RezFlix)", rezMovies)
                        vitrinAdapter.updateData(listOf(section))
                    }
                } else if (activeEngine == "nextmovie") {
                    val nextMovies = NextMovieApi.getRecent(1)
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false
                        binding.bannerViewPager.isVisible = false
                        val section = VitrinSection(1, "منتخب فیلم‌های نکست‌مووی (NextMovie)", nextMovies)
                        vitrinAdapter.updateData(listOf(section))
                    }
                } else if (activeEngine == "bj") {
                    val bjSections = BjApi.getHomeSections()
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false
                        binding.bannerViewPager.isVisible = false
                        vitrinAdapter.updateData(bjSections)
                    }
                } else {
                    val (banners, sections) = movielixApi.getVitrinSections(1)
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false

                        if (banners.isNotEmpty()) {
                            binding.bannerViewPager.isVisible = true
                            binding.bannerViewPager.adapter = HeroBannerAdapter(
                                banners,
                                onItemClick = { bannerItem -> showMovieDetail(bannerItem) },
                                onItemLongClick = { bannerItem -> showMediaQuickActionsBottomSheet(bannerItem) }
                            )
                        } else {
                            binding.bannerViewPager.isVisible = false
                        }

                        vitrinAdapter.updateData(sections)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.mainProgressBar.isVisible = false
                    binding.swipeRefresh.isRefreshing = false
                    AppLogger.e("MainActivity", "خطا در دریافت اطلاعات خانه: ${e.message}")
                    showAppToast("خطا در اتصال به شبکه")
                }
            }
        }
    }

    private fun loadMoreVitrin() {
        isVitrinLoadingMore = true
        binding.vitrinLoadingMore.isVisible = true
        val nextPage = vitrinPage + 1

        lifecycleScope.launch {
            try {
                if (activeEngine == "almasmovie") {
                    val more = AlmasMovieApi.getRecent(nextPage)
                    withContext(Dispatchers.Main) {
                        isVitrinLoadingMore = false
                        binding.vitrinLoadingMore.isVisible = false
                        if (more.isNotEmpty()) {
                            vitrinPage = nextPage
                            val section = VitrinSection(nextPage, "عناوین بیشتر الماس‌مووی (صفحه $nextPage)", more)
                            vitrinAdapter.appendData(listOf(section))
                        } else {
                            hasMoreVitrin = false
                        }
                    }
                } else if (activeEngine == "rezflix") {
                    val more = RezFlixApi.getMovies(nextPage)
                    withContext(Dispatchers.Main) {
                        isVitrinLoadingMore = false
                        binding.vitrinLoadingMore.isVisible = false
                        if (more.isNotEmpty()) {
                            vitrinPage = nextPage
                            val section = VitrinSection(nextPage, "عناوین بیشتر رزفلیکس (صفحه $nextPage)", more)
                            vitrinAdapter.appendData(listOf(section))
                        } else {
                            hasMoreVitrin = false
                        }
                    }
                } else if (activeEngine == "nextmovie") {
                    val more = NextMovieApi.getRecent(nextPage)
                    withContext(Dispatchers.Main) {
                        isVitrinLoadingMore = false
                        binding.vitrinLoadingMore.isVisible = false
                        if (more.isNotEmpty()) {
                            vitrinPage = nextPage
                            val section = VitrinSection(nextPage, "عناوین بیشتر نکست‌مووی (صفحه $nextPage)", more)
                            vitrinAdapter.appendData(listOf(section))
                        } else {
                            hasMoreVitrin = false
                        }
                    }
                } else if (activeEngine == "bj") {
                    val more = BjApi.getMovies(nextPage)
                    withContext(Dispatchers.Main) {
                        isVitrinLoadingMore = false
                        binding.vitrinLoadingMore.isVisible = false
                        if (more.isNotEmpty()) {
                            vitrinPage = nextPage
                            val section = VitrinSection(nextPage, "عناوین بیشتر BJ (صفحه $nextPage)", more)
                            vitrinAdapter.appendData(listOf(section))
                        } else {
                            hasMoreVitrin = false
                        }
                    }
                } else {
                    val (_, newSections) = movielixApi.getVitrinSections(nextPage)
                    withContext(Dispatchers.Main) {
                        isVitrinLoadingMore = false
                        binding.vitrinLoadingMore.isVisible = false
                        if (newSections.isNotEmpty()) {
                            vitrinPage = nextPage
                            vitrinAdapter.appendData(newSections)
                        } else {
                            hasMoreVitrin = false
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isVitrinLoadingMore = false
                    binding.vitrinLoadingMore.isVisible = false
                }
            }
        }
    }

    private fun initGridData(type: Int) {
        currentGridType = type
        currentSearchQuery = ""
        gridPage = 1
        hasMoreGrid = true
        binding.gridProgressBar.isVisible = true
        binding.gridLoadingMore.isVisible = false
        gridAdapter.updateData(emptyList())

        lifecycleScope.launch {
            try {
                val results = when (activeEngine) {
                    "almasmovie" -> AlmasMovieApi.getRecent(1)
                    "rezflix" -> RezFlixApi.getMovies(1)
                    "nextmovie" -> NextMovieApi.getRecent(1)
                    "bj" -> if (type == 1) BjApi.getSeries(1) else BjApi.getMovies(1)
                    else -> {
                        val keyword = if (type == 1) "سریال" else "2024"
                        movielixApi.search(keyword, type = type, page = 1)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    gridAdapter.updateData(results)
                    if (results.isEmpty()) {
                        hasMoreGrid = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    AppLogger.e("MainActivity", "خطا در بارگذاری بخش: ${e.message}")
                }
            }
        }
    }

    private suspend fun searchInEngine(engine: String, query: String, page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            when (engine) {
                "almasmovie" -> AlmasMovieApi.search(query, page)
                "rezflix" -> RezFlixApi.search(query, page)
                "nextmovie" -> NextMovieApi.search(query, page)
                "bj" -> BjApi.search(query, page)
                else -> movielixApi.search(query, type = 2, page = page)
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "خطا در جستجوی موتور $engine: ${e.message}")
            emptyList()
        }
    }

    private fun initSearch(query: String) {
        currentGridType = 2
        currentSearchQuery = query
        gridPage = 1
        hasMoreGrid = true
        showGridScreen("جستجو: $query")
        binding.gridProgressBar.isVisible = true
        binding.gridLoadingMore.isVisible = false
        gridAdapter.updateData(emptyList())

        lifecycleScope.launch {
            try {
                // 1. First search in active engine
                val primaryResults = searchInEngine(activeEngine, query, 1)
                if (primaryResults.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.gridProgressBar.isVisible = false
                        gridAdapter.updateData(primaryResults)
                    }
                    return@launch
                }

                // 2. If no result in activeEngine, automatically try ALL other engines!
                withContext(Dispatchers.Main) {
                    showAppToast("در ${getEngineName(activeEngine)} یافت نشد؛ در حال بررسی سایر موتورها...")
                }

                val allEngines = listOf("movielix", "rezflix", "almasmovie", "nextmovie", "bj")
                val fallbackEngines = allEngines.filter { it != activeEngine }

                // Query all fallback engines in parallel for high speed
                val fallbackDeferreds = fallbackEngines.map { eng ->
                    eng to async(Dispatchers.IO) { searchInEngine(eng, query, 1) }
                }

                var foundEngine: String? = null
                var foundResults: List<MovieItem> = emptyList()

                for ((eng, deferred) in fallbackDeferreds) {
                    val res = deferred.await()
                    if (foundResults.isEmpty() && res.isNotEmpty()) {
                        foundResults = res
                        foundEngine = eng
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    if (foundResults.isNotEmpty() && foundEngine != null) {
                        activeEngine = foundEngine
                        getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("active_engine", activeEngine)
                            .apply()

                        gridAdapter.updateData(foundResults)
                        showAppToast("نتیجه از موتور ${getEngineName(foundEngine)} بارگذاری شد 🎯")
                    } else {
                        hasMoreGrid = false
                        gridAdapter.updateData(emptyList())
                        showAppToast("نتیجه‌ای یافت نشد", iconRes = R.drawable.ic_close_vector)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    AppLogger.e("MainActivity", "خطا در فرآیند جستجو: ${e.message}")
                    showAppToast("خطا در جستجو: ${e.message}")
                }
            }
        }
    }

    private fun loadMoreGrid() {
        isGridLoadingMore = true
        binding.gridLoadingMore.isVisible = true
        val nextPage = gridPage + 1

        lifecycleScope.launch {
            try {
                val moreItems = when (activeEngine) {
                    "almasmovie" -> {
                        if (currentGridType == 2 && currentSearchQuery.isNotBlank()) {
                            AlmasMovieApi.search(currentSearchQuery, nextPage)
                        } else {
                            AlmasMovieApi.getRecent(nextPage)
                        }
                    }
                    "rezflix" -> {
                        if (currentGridType == 2 && currentSearchQuery.isNotBlank()) {
                            RezFlixApi.search(currentSearchQuery, nextPage)
                        } else {
                            RezFlixApi.getMovies(nextPage)
                        }
                    }
                    "nextmovie" -> {
                        if (currentGridType == 2 && currentSearchQuery.isNotBlank()) {
                            NextMovieApi.search(currentSearchQuery, nextPage)
                        } else {
                            NextMovieApi.getRecent(nextPage)
                        }
                    }
                    "bj" -> {
                        if (currentGridType == 2 && currentSearchQuery.isNotBlank()) {
                            BjApi.search(currentSearchQuery, nextPage)
                        } else if (currentGridType == 1) {
                            BjApi.getSeries(nextPage)
                        } else {
                            BjApi.getMovies(nextPage)
                        }
                    }
                    else -> {
                        val keyword = if (currentGridType == 2) currentSearchQuery else if (currentGridType == 1) "سریال" else "2024"
                        movielixApi.search(keyword, type = currentGridType, page = nextPage)
                    }
                }

                withContext(Dispatchers.Main) {
                    isGridLoadingMore = false
                    binding.gridLoadingMore.isVisible = false
                    if (moreItems.isNotEmpty()) {
                        gridPage = nextPage
                        gridAdapter.appendData(moreItems)
                    } else {
                        hasMoreGrid = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGridLoadingMore = false
                    binding.gridLoadingMore.isVisible = false
                }
            }
        }
    }

    private fun showMovieDetail(item: MovieItem) {
        HistoryManager.addVisit(this, item)
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val detailBinding = DialogMovieDetailBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(detailBinding.root)
        dialog.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.black)
            applyDialogBlurBehind(w)
        }
        applyDialogStatusBarInsets(detailBinding.topBarDetail)

        detailBinding.root.post {
            detailBinding.detailTitle.animateIn(durationMs = 300, startDelayMs = 60)
            detailBinding.detailPoster.animateIn(durationMs = 380, startDelayMs = 0, fromY = 20f)
        }

        detailBinding.btnBackDetail.setOnClickListener {
            dialog.dismiss()
        }

        val updateFavIcon = {
            val isFav = FavoritesManager.isFavorite(item.id)
            detailBinding.btnFavoriteDetail.setImageResource(
                if (isFav) R.drawable.ic_heart_filled_gold else R.drawable.ic_heart_outline_gold
            )
        }
        updateFavIcon()

        detailBinding.btnFavoriteDetail.setOnClickListener {
            val isNowFav = FavoritesManager.toggleFavorite(this@MainActivity, item)
            updateFavIcon()
            val msg = if (isNowFav) "«${item.title}» به لیست نشان‌شده‌ها اضافه شد" else "از لیست نشان‌شده‌ها حذف شد"
            showAppToast(msg, iconRes = R.drawable.ic_heart_outline_gold)
        }

        detailBinding.btnAddPlaylistDetail.setOnClickListener {
            showAddToPlaylistDialog(item)
        }

        detailBinding.btnActionAddToPlaylist.setOnClickListener {
            showAddToPlaylistDialog(item)
        }

        var latestKnownSeason = 1
        var latestKnownEpisode = 0
        var latestKnownEpTitle: String? = null

        fun updateSeriesNotificationUI() {
            val isSubscribed = SeriesSubscriptionManager.isSubscribed(item.id)
            if (isSubscribed) {
                detailBinding.btnNotifySeriesDetail.setImageResource(R.drawable.ic_bell_gold)
                detailBinding.btnNotifySeriesDetail.imageTintList = null
                detailBinding.btnToggleSeriesNotification.text = "خاموش کردن"
                detailBinding.btnToggleSeriesNotification.backgroundTintList = ColorStateList.valueOf(0x22FFFFFF.toInt())
                detailBinding.btnToggleSeriesNotification.setTextColor(0xFFCCCCCC.toInt())
                detailBinding.btnToggleSeriesNotification.strokeColor = ColorStateList.valueOf(0x55FFFFFF.toInt())
                detailBinding.tvSeriesNotificationStatus.text = "روشن — به محض انتشار قسمت جدید به شما خبر داده می‌شود"
                detailBinding.tvSeriesNotificationStatus.setTextColor(0xFF4CAF50.toInt())
            } else {
                detailBinding.btnNotifySeriesDetail.setImageResource(R.drawable.ic_bell_gold)
                detailBinding.btnNotifySeriesDetail.imageTintList = ColorStateList.valueOf(0x88FFFFFF.toInt())
                detailBinding.btnToggleSeriesNotification.text = "روشن کردن"
                detailBinding.btnToggleSeriesNotification.backgroundTintList = ColorStateList.valueOf(0x26D4AF37.toInt())
                detailBinding.btnToggleSeriesNotification.setTextColor(0xFFD4AF37.toInt())
                detailBinding.btnToggleSeriesNotification.strokeColor = ColorStateList.valueOf(0xFFD4AF37.toInt())
                detailBinding.tvSeriesNotificationStatus.text = "خاموش — به محض انتشار قسمت جدید باخبر شوید"
                detailBinding.tvSeriesNotificationStatus.setTextColor(0xFF888888.toInt())
            }
        }

        val onToggleSeriesNotification = {
            checkNotificationPermission()
            val isNowSub = SeriesSubscriptionManager.toggleSubscription(
                context = this@MainActivity,
                item = item,
                engine = activeEngine,
                season = latestKnownSeason,
                episode = latestKnownEpisode,
                epTitle = latestKnownEpTitle
            )
            updateSeriesNotificationUI()
            val msg = if (isNowSub) "اعلان قسمت‌های جدید «${item.title}» فعال شد" else "اعلان «${item.title}» خاموش شد"
            showAppToast(msg, iconRes = R.drawable.ic_bell_gold)
        }

        detailBinding.btnNotifySeriesDetail.setOnClickListener { onToggleSeriesNotification() }
        detailBinding.btnToggleSeriesNotification.setOnClickListener { onToggleSeriesNotification() }

        val isSeriesItem = item.type == 1
        if (isSeriesItem) {
            detailBinding.btnNotifySeriesDetail.isVisible = true
            updateSeriesNotificationUI()
        }

        detailBinding.detailTitle.text = item.title
        ImageLoader.load(item.image, detailBinding.detailPoster)

        detailBinding.detailLoading.isVisible = true
        detailBinding.seriesControlsContainer.isVisible = false
        detailBinding.qualityListContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val detail = when (activeEngine) {
                    "almasmovie" -> AlmasMovieApi.getDetails(item.id, if (item.type == 1) "tvshow" else "movie")
                    "rezflix" -> RezFlixApi.getDetails(item.id)
                    "nextmovie" -> NextMovieApi.getDetails(item.id)
                    "bj" -> BjApi.getDetails(item.id)
                    else -> movielixApi.getMovieDetails(item.id)
                }

                withContext(Dispatchers.Main) {
                    detailBinding.detailLoading.isVisible = false
                    if (detail != null) {
                        detailBinding.detailTitle.text = detail.title
                        detailBinding.detailPlot.text = detail.description ?: detail.descriptionAi ?: "خلاصه داستانی ثبت نشده است."
                        detailBinding.detailImdb.text = detail.imdbRate ?: "N/A"
                        detailBinding.detailYear.text = detail.year ?: ""
                        detailBinding.detailTypeBadge.text = if (detail.type == 1) "سریال" else "فیلم"

                        if (!detail.banner.isNullOrEmpty()) {
                            ImageLoader.load(detail.banner, detailBinding.detailPoster)
                        }

                        val isSeries = detail.type == 1 || item.type == 1
                        if (isSeries) {
                            detailBinding.btnNotifySeriesDetail.isVisible = true
                            updateSeriesNotificationUI()
                        }

                        if (detail.directQualities.isNotEmpty()) {
                            populateQualities(detailBinding, detail.id, detail.directQualities, isSeries = detail.type == 1)
                        } else if (detail.type == 1) {
                            setupSeriesView(detailBinding, detail) { season, episode, epTitle ->
                                if (season >= latestKnownSeason) {
                                    latestKnownSeason = season
                                    latestKnownEpisode = episode
                                    latestKnownEpTitle = epTitle
                                    if (SeriesSubscriptionManager.isSubscribed(item.id)) {
                                        SeriesSubscriptionManager.updateLastKnown(
                                            context = this@MainActivity,
                                            id = item.id,
                                            season = season,
                                            episode = episode,
                                            epTitle = epTitle
                                        )
                                    }
                                }
                            }
                        } else {
                            loadMovieQualities(detailBinding, detail.id)
                        }
                    } else {
                        loadMovieQualities(detailBinding, item.id)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    detailBinding.detailLoading.isVisible = false
                    AppLogger.e("MainActivity", "خطا در دریافت جزییات: ${e.message}")
                }
            }
        }

        dialog.show()
    }

    private fun setupSeriesView(
        detailBinding: DialogMovieDetailBinding,
        detail: MovieDetail,
        onEpisodesLoaded: ((season: Int, episode: Int, title: String?) -> Unit)? = null
    ) {
        detailBinding.seriesControlsContainer.isVisible = true
        val seasons = if (detail.seasons.isNotEmpty()) detail.seasons else listOf(SeasonItem(1, "فصل ۱"))

        var selectedSeason = seasons.first().season
        var selectedEpisode = 1

        val loadEpisodesForSeason = { seasonNum: Int ->
            detailBinding.detailLoading.isVisible = true
            detailBinding.qualityListContainer.removeAllViews()

            lifecycleScope.launch {
                try {
                    val episodes = when (activeEngine) {
                        "almasmovie" -> AlmasMovieApi.getEpisodes(detail.id, seasonNum)
                        "nextmovie" -> NextMovieApi.getEpisodes(detail.id, seasonNum)
                        "bj" -> BjApi.getEpisodes(detail.id, seasonNum)
                        else -> movielixApi.getEpisodes(detail.id, seasonNum)
                    }
                    withContext(Dispatchers.Main) {
                        detailBinding.detailLoading.isVisible = false
                        if (episodes.isNotEmpty()) {
                            selectedEpisode = episodes.first().episode

                            val maxEp = episodes.maxByOrNull { it.episode }
                            if (maxEp != null) {
                                onEpisodesLoaded?.invoke(seasonNum, maxEp.episode, maxEp.title)
                            }

                            val epTitles = episodes.map { it.title }
                            val epAdapter = ChipSelectorAdapter(epTitles, 0) { epIndex ->
                                val ep = episodes[epIndex]
                                selectedEpisode = ep.episode
                                populateQualities(detailBinding, detail.id, ep.qualities, isSeries = true, season = seasonNum, episode = selectedEpisode)
                            }
                            detailBinding.rvEpisodes.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                            detailBinding.rvEpisodes.adapter = epAdapter

                            populateQualities(detailBinding, detail.id, episodes.first().qualities, isSeries = true, season = seasonNum, episode = selectedEpisode)
                        } else {
                            detailBinding.qualityListContainer.removeAllViews()
                            val noEpView = android.widget.TextView(this@MainActivity).apply {
                                text = "قسمتی برای این فصل یافت نشد."
                                setTextColor(0xFF888888.toInt())
                                textSize = 12f
                            }
                            detailBinding.qualityListContainer.addView(noEpView)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        detailBinding.detailLoading.isVisible = false
                    }
                }
            }
        }

        val seasonTitles = seasons.map { it.title }
        val seasonAdapter = ChipSelectorAdapter(seasonTitles, 0) { sIndex ->
            selectedSeason = seasons[sIndex].season
            loadEpisodesForSeason(selectedSeason)
        }
        detailBinding.rvSeasons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        detailBinding.rvSeasons.adapter = seasonAdapter

        detailBinding.btnDownloadSeasonBatch.setOnClickListener {
            showBatchDownloadDialog(
                title = "دانلود تمام قسمت‌های ${seasons.find { it.season == selectedSeason }?.title ?: "فصل $selectedSeason"}",
                subtitle = "سریال «${detail.title}»",
                movieId = detail.id,
                isSeries = true,
                seasons = listOf(selectedSeason)
            )
        }

        detailBinding.btnDownloadSeriesBatch.setOnClickListener {
            showBatchDownloadDialog(
                title = "دانلود تمام فصل‌ها و قسمت‌ها",
                subtitle = "سریال «${detail.title}» (${seasons.size} فصل)",
                movieId = detail.id,
                isSeries = true,
                seasons = seasons.map { it.season }
            )
        }

        loadEpisodesForSeason(selectedSeason)
    }

    private fun loadMovieQualities(detailBinding: DialogMovieDetailBinding, movieId: Int) {
        detailBinding.detailLoading.isVisible = false
        detailBinding.qualityListContainer.removeAllViews()

        // Add 3 skeleton rows with pulsing alpha animation
        val skeletonViews = (0 until 3).map {
            val skeletonView = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_quality_skeleton, detailBinding.qualityListContainer, false)
            detailBinding.qualityListContainer.addView(skeletonView)
            skeletonView
        }

        // Pulse animation for shimmer effect
        val pulseAnimator = android.animation.ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 900
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                skeletonViews.forEach { it.alpha = alpha }
            }
            start()
        }

        lifecycleScope.launch {
            try {
                val qualities = when (activeEngine) {
                    "almasmovie" -> AlmasMovieApi.getQualities(movieId)
                    "nextmovie" -> NextMovieApi.getQualities(movieId)
                    "bj" -> BjApi.getQualities(movieId)
                    else -> movielixApi.getQualities(movieId)
                }
                withContext(Dispatchers.Main) {
                    pulseAnimator.cancel()
                    populateQualities(detailBinding, movieId, qualities, isSeries = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pulseAnimator.cancel()
                    detailBinding.qualityListContainer.removeAllViews()
                }
            }
        }
    }

    private fun populateQualities(
        detailBinding: DialogMovieDetailBinding,
        movieId: Int,
        qualities: List<QualityItem>,
        isSeries: Boolean,
        season: Int = -1,
        episode: Int = -1
    ) {
        detailBinding.qualityListContainer.removeAllViews()

        if (qualities.isNotEmpty()) {
            for (q in qualities) {
                val rowBinding = ItemQualityRowBinding.inflate(
                    LayoutInflater.from(this@MainActivity),
                    detailBinding.qualityListContainer,
                    false
                )
                val fullTitle = "${detailBinding.detailTitle.text} - ${q.title}"
                rowBinding.qualityTitle.text = "${q.title} (${q.type})"
                rowBinding.qualitySize.text = q.size

                rowBinding.btnStreamInApp.setOnClickListener {
                    resolveAndOpenLink(
                        movieId = movieId,
                        qualityId = q.id,
                        directUrl = q.directUrl,
                        action = 1,
                        mediaTitle = fullTitle,
                        qualityLabel = q.title,
                        isSeries = isSeries,
                        season = season,
                        episode = episode
                    )
                }

                rowBinding.btnDownloadDirect.setOnClickListener {
                    resolveAndOpenLink(
                        movieId = movieId,
                        qualityId = q.id,
                        directUrl = q.directUrl,
                        action = 2,
                        mediaTitle = fullTitle,
                        qualityLabel = q.title,
                        isSeries = isSeries,
                        season = season,
                        episode = episode
                    )
                }

                rowBinding.btnPlayExternal.setOnClickListener {
                    resolveAndOpenLink(
                        movieId = movieId,
                        qualityId = q.id,
                        directUrl = q.directUrl,
                        action = 3,
                        mediaTitle = fullTitle,
                        qualityLabel = q.title,
                        isSeries = isSeries,
                        season = season,
                        episode = episode
                    )
                }

                detailBinding.qualityListContainer.addView(rowBinding.root)
            }
        } else {
            val noQView = android.widget.TextView(this@MainActivity).apply {
                text = "کیفیتی برای این عنوان ثبت نشده است."
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            }
            detailBinding.qualityListContainer.addView(noQView)
        }
    }

    private fun resolveAndOpenLink(
        movieId: Int,
        qualityId: Int,
        directUrl: String? = null,
        action: Int,
        mediaTitle: String,
        qualityLabel: String,
        isSeries: Boolean = false,
        season: Int = -1,
        episode: Int = -1
    ) {
        if (!directUrl.isNullOrEmpty()) {
            when (action) {
                1 -> playVideoInApp(mediaTitle, qualityLabel, directUrl)
                2 -> {
                    startTurboDownload(
                        movieId = movieId,
                        mediaTitle = mediaTitle,
                        qualityLabel = qualityLabel,
                        url = directUrl,
                        isSeries = isSeries,
                        season = season,
                        episode = episode
                    )
                }
                3 -> {
                    val videoIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(directUrl), "video/*")
                    }
                    try {
                        startActivity(Intent.createChooser(videoIntent, "پخش با پلیر:"))
                    } catch (e: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)))
                    }
                }
            }
            return
        }

        val notice = when (action) {
            1 -> "آماده‌سازی پخش آنلاین در برنامه..."
            2 -> "آماده‌سازی لینک برای دانلود توربو..."
            else -> "آماده‌سازی پخش با پلیر جانبی..."
        }
        val loadingSheet = showAppToast(notice, isLoading = true)
        lifecycleScope.launch {
            try {
                val streamUrl = movielixApi.getStreamUrl(
                    movieId,
                    qualityId,
                    season = if (isSeries) season else -1,
                    episode = if (isSeries) episode else -1
                )
                withContext(Dispatchers.Main) {
                    loadingSheet.dismiss()
                    if (!streamUrl.isNullOrEmpty()) {
                        when (action) {
                            1 -> playVideoInApp(mediaTitle, qualityLabel, streamUrl)
                            2 -> {
                                startTurboDownload(
                                    movieId = movieId,
                                    mediaTitle = mediaTitle,
                                    qualityLabel = qualityLabel,
                                    url = streamUrl,
                                    isSeries = isSeries,
                                    season = season,
                                    episode = episode
                                )
                            }
                            3 -> {
                                val videoIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(streamUrl), "video/*")
                                }
                                try {
                                    startActivity(Intent.createChooser(videoIntent, "پخش با پلیر:"))
                                } catch (e: Exception) {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)))
                                }
                            }
                        }
                    } else {
                        showAppToast("خطا در استخراج آدرس رسانه")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingSheet.dismiss()
                    AppLogger.e("MainActivity", "خطا در استخراج آدرس: ${e.message}")
                    showAppToast("خطا در برقراری ارتباط با سرور")
                }
            }
        }
    }

    private fun playVideoInApp(
        title: String,
        quality: String,
        streamUrl: String,
        subtitlePath: String? = null,
        isOffline: Boolean = false
    ) {
        try {
            val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
            dialog.applyFullscreenAnimation()
            val playerBinding = DialogInAppPlayerBinding.inflate(dialog.layoutInflater)
            dialog.setContentView(playerBinding.root)
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)

            activePlayerDialog?.takeIf { it.isShowing }?.dismiss()
            activeExoPlayer?.release()
            activeExoPlayer = null

            activePlayerDialog = dialog
            playerBinding.txtPlayerTitle.text = title
            playerBinding.txtPlayerQuality.text = quality
            playerBinding.txtPlayerBadge.text = if (isOffline) "فایل دانلودشده" else "استریم آنلاین"
            playerBinding.playerLoading.visibility = View.VISIBLE

            var activeSubtitleUri: Uri? = null
            if (!subtitlePath.isNullOrEmpty()) {
                val subFile = java.io.File(subtitlePath)
                if (subFile.exists() && subFile.length() > 0) {
                    activeSubtitleUri = Uri.fromFile(subFile)
                }
            } else if (isOffline) {
                val assumedSub = java.io.File(streamUrl.replaceAfterLast('.', "srt"))
                if (assumedSub.exists() && assumedSub.length() > 0) {
                    activeSubtitleUri = Uri.fromFile(assumedSub)
                }
            }

            fun buildMediaItem(subUri: Uri?): MediaItem {
                val builder = MediaItem.Builder().setUri(streamUrl)
                if (subUri != null) {
                    val subMime = when {
                        subUri.toString().endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                        subUri.toString().endsWith(".ass", true) ||
                            subUri.toString().endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(subMime)
                        .setLanguage("fa")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    builder.setSubtitleConfigurations(listOf(subConfig))
                }
                return builder.build()
            }

            // Streaming hosts redirect between http and https and reject non-browser agents,
            // which the media3 defaults refuse and surface as a generic playback error.
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setKeepPostFor302Redirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

            val player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
            activeExoPlayer = player
            playerBinding.playerView.player = player

            val mediaItem = buildMediaItem(activeSubtitleUri)
            player.setMediaItem(mediaItem)

            val progressKey = if (isOffline) "file://$streamUrl" else streamUrl
            val savedPosition = PlaybackProgressManager.getProgress(this, progressKey)
            if (savedPosition > 3000L) {
                player.seekTo(savedPosition)
                showAppToast("ادامه پخش از ${PlaybackProgressManager.formatTime(savedPosition)}")
            }

            player.prepare()
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> playerBinding.playerLoading.visibility = View.VISIBLE
                        Player.STATE_READY -> playerBinding.playerLoading.visibility = View.GONE
                        Player.STATE_ENDED -> {
                            playerBinding.playerLoading.visibility = View.GONE
                            PlaybackProgressManager.clearProgress(this@MainActivity, progressKey)
                        }
                        Player.STATE_IDLE -> {}
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    playerBinding.playerLoading.visibility = View.GONE
                    AppLogger.e("InAppPlayer", "خطا در پخش: ${error.message}")
                    showAppToast("خطا در پخش مدیا", autoDismissMs = 4000L)
                }
            })

            // ── Speed control ──────────────────────────────────────────────
            val speedSteps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            val speedLabels = listOf("0.75×", "1×", "1.25×", "1.5×", "2×")
            var speedIndex = 1 // default 1×
            playerBinding.btnPlayerSpeed.setOnClickListener {
                speedIndex = (speedIndex + 1) % speedSteps.size
                val speed = speedSteps[speedIndex]
                player.setPlaybackSpeed(speed)
                playerBinding.btnPlayerSpeed.text = speedLabels[speedIndex]
                showAppToast("سرعت پخش: ${speedLabels[speedIndex]}")
            }

            // ── Rotate ─────────────────────────────────────────────────────
            playerBinding.btnPlayerRotate.setOnClickListener {
                requestedOrientation = when (requestedOrientation) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            // ── Subtitles ──────────────────────────────────────────────────
            playerBinding.btnPlayerSubtitles.setOnClickListener {
                val subOptions = listOf(
                    "خاموش (بدون زیرنویس)",
                    if (activeSubtitleUri != null) "زیرنویس فارسی هماهنگ (فعال)" else "زیرنویس فارسی (یافت نشد)"
                )
                showSelectionBottomSheet(
                    title = "انتخاب و مدیریت زیرنویس",
                    subtitle = "زیرنویس مورد نظر خود را برای این فیلم انتخاب کنید:",
                    options = subOptions
                ) { which ->
                    when (which) {
                        0 -> {
                            val pos = player.currentPosition; val p = player.playWhenReady
                            player.setMediaItem(buildMediaItem(null), pos); player.playWhenReady = p
                            showAppToast("زیرنویس غیرفعال شد")
                        }
                        1 -> {
                            if (activeSubtitleUri != null) {
                                val pos = player.currentPosition; val p = player.playWhenReady
                                player.setMediaItem(buildMediaItem(activeSubtitleUri), pos); player.playWhenReady = p
                                showAppToast("زیرنویس فارسی فعال شد")
                            } else showAppToast("فایل زیرنویس برای این ویدیو موجود نیست")
                        }
                    }
                }
            }

            // ── PiP ────────────────────────────────────────────────────────
            playerBinding.btnPlayerPip.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9)).build()
                        enterPictureInPictureMode(params)
                    } catch (e: Exception) {
                        showAppToast("امکان تصویر در تصویر وجود ندارد: ${e.message}")
                    }
                } else showAppToast("تصویر در تصویر نیازمند اندروید ۸ به بالا است")
            }

            // ── Close ──────────────────────────────────────────────────────
            playerBinding.btnClosePlayer.setOnClickListener { dialog.dismiss() }

            // ── Dismiss: save progress & restore orientation ───────────────
            dialog.setOnDismissListener {
                val currentPos = player.currentPosition
                val duration = player.duration
                if (currentPos > 0L) PlaybackProgressManager.saveProgress(this, progressKey, currentPos, duration)
                player.stop()
                player.release()
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (activeExoPlayer == player) activeExoPlayer = null
                if (activePlayerDialog == dialog) activePlayerDialog = null
            }

            dialog.show()
            AppLogger.i("InAppPlayer", "پلیر اجرا شد: $title")
        } catch (e: Exception) {
            AppLogger.e("InAppPlayer", "خطا در ایجاد پلیر: ${e.message}")
            showAppToast("امکان پخش در این دستگاه وجود ندارد: ${e.message}", autoDismissMs = 4000L)
        }
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    private fun toPersianDigits(str: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder()
        for (ch in str) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun showEnginesDrawer() {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val drawerBinding = BottomSheetEnginesDrawerBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(drawerBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(drawerBinding.topBarEnginesDrawer)

        drawerBinding.tvCurrentEngineSub.text = "موتور فعال: ${getEngineName(activeEngine)}"

        try {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            drawerBinding.tvDeviceInfoModel.text = "$manufacturer $model"

            val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS.joinToString(", ")
            } else {
                Build.CPU_ABI
            }
            drawerBinding.tvDeviceInfoCpu.text = abis.ifEmpty { "ARM / x86" }

            val androidVersion = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            drawerBinding.tvDeviceInfoAndroid.text = "اندروید $androidVersion (API $sdkInt)"

            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = pInfo.versionName ?: "1.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            drawerBinding.tvDeviceInfoAppVersion.text = "v$versionName (کد بیلد $versionCode)"

            val installTimeMs = when {
                pInfo.firstInstallTime > 946684800000L -> pInfo.firstInstallTime
                pInfo.lastUpdateTime > 946684800000L -> pInfo.lastUpdateTime
                else -> {
                    val apkFile = java.io.File(applicationInfo.sourceDir)
                    if (apkFile.exists() && apkFile.lastModified() > 946684800000L) {
                        apkFile.lastModified()
                    } else {
                        System.currentTimeMillis()
                    }
                }
            }

            val cal = java.util.Calendar.getInstance().apply { timeInMillis = installTimeMs }
            val gy = cal.get(java.util.Calendar.YEAR)
            val gm = cal.get(java.util.Calendar.MONTH) + 1
            val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val hour = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
            val minute = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.MINUTE))

            val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
            val persianMonthNames = arrayOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
            val monthName = persianMonthNames.getOrElse(jm) { "" }

            val formattedDate = "$jd $monthName $jy — $hour:$minute"
            drawerBinding.tvDeviceInfoInstallDate.text = toPersianDigits(formattedDate)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "خطا در استخراج اطلاعات دستگاه: ${e.message}")
        }

        drawerBinding.btnDrawerClose.setOnClickListener {
            dialog.dismiss()
        }

        when (activeEngine) {
            "movielix" -> drawerBinding.rbMovielix.isChecked = true
            "rezflix" -> drawerBinding.rbRezFlix.isChecked = true
            "almasmovie" -> drawerBinding.rbAlmasMovie.isChecked = true
            "nextmovie" -> drawerBinding.rbNextMovie.isChecked = true
            "bj" -> drawerBinding.rbBjEngine.isChecked = true
        }

        drawerBinding.cardOpenRadar.setOnClickListener {
            dialog.dismiss()
            showSystemRadarDialog()
        }

        drawerBinding.cardDrawerTelegram.setOnClickListener {
            val tgUrl = "https://t.me/filigramapp?direct"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl)))
            } catch (e: Exception) {
                showAppToast("پشتیبانی و گزارش باگ در تلگرام: t.me/filigramapp?direct", autoDismissMs = 4000L)
            }
        }

        val currentCacheSize = AppCacheManager.getCacheSizeBytes(this@MainActivity)
        drawerBinding.txtCacheSizeSub.text = "حجم کش ذخیره‌شده: ${AppCacheManager.formatSize(currentCacheSize)}"
        drawerBinding.cardClearCache.setOnClickListener {
            showConfirmBottomSheet(
                title = "پاکسازی حافظه موقت",
                message = "آیا مایل به حذف تمام داده‌های موقت، تصاویر کش‌شده و پاسخ‌های سرور هستید؟ این عمل فضای ذخیره‌سازی را آزاد می‌کند.",
                positiveText = "پاکسازی کش",
                isDestructive = true
            ) {
                AppCacheManager.clearAll(this@MainActivity)
                drawerBinding.txtCacheSizeSub.text = "حجم کش ذخیره‌شده: ۰ مگابایت"
                showAppToast("حافظه موقت با موفقیت پاکسازی شد")
            }
        }

        drawerBinding.btnApplyEngine.setOnClickListener {
            val selected = when (drawerBinding.rgEngines.checkedRadioButtonId) {
                R.id.rbRezFlix -> "rezflix"
                R.id.rbAlmasMovie -> "almasmovie"
                R.id.rbNextMovie -> "nextmovie"
                R.id.rbBjEngine -> "bj"
                else -> "movielix"
            }

            activeEngine = selected
            getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("active_engine", activeEngine)
                .apply()

            AppLogger.i("MainActivity", "موتور فعال به $selected تغییر یافت")
            showAppToast("موتور فعال: ${getEngineName(activeEngine)}")
            dialog.dismiss()

            showHomeScreen()
            loadHomeData()
        }

        val donationWalletAddress = "TZBA9oggSuLveqs98s85yiSGaUVKZmpuBp"
        val copyDonationAction = {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Filigram Donation Wallet", donationWalletAddress)
                clipboard.setPrimaryClip(clip)
                showAppToast("آدرس کیف پول ترون کپی شد 📋")
            } catch (e: Exception) {
                showAppToast("خطا در کپی آدرس: ${e.message}")
            }
        }
        drawerBinding.btnCopyDonationAddress.setOnClickListener { copyDonationAction() }
        drawerBinding.boxDonationAddress.setOnClickListener { copyDonationAction() }
        drawerBinding.tvDonationAddress.setOnClickListener { copyDonationAction() }

        drawerBinding.btnShareApp.setOnClickListener {
            dialog.dismiss()
            showShareAppSheet()
        }

        drawerBinding.btnReportBug.setOnClickListener {
            val bugUrl = "https://t.me/filigramapp?direct"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bugUrl)))
            } catch (e: Exception) {
                showAppToast("ارسال گزارش باگ: t.me/filigramapp?direct", autoDismissMs = 4000L)
            }
        }

        dialog.show()
    }

    private fun showSystemRadarDialog() {
        val dialog = createStyledBottomSheetDialog()
        val radarBinding = BottomSheetRadarBinding.inflate(layoutInflater)
        dialog.setContentView(radarBinding.root)

        val loadRadar = {
            radarBinding.radarLoading.isVisible = true
            radarBinding.radarServicesContainer.removeAllViews()
            radarBinding.overallStatusDot.setBackgroundColor(Color.parseColor("#FFD600"))
            radarBinding.overallStatusText.text = "در حال ارزیابی پینگ و وضعیت زیرساخت‌ها..."

            lifecycleScope.launch {
                val services = SystemRadar.checkAllEngines()
                withContext(Dispatchers.Main) {
                    radarBinding.radarLoading.isVisible = false
                    radarBinding.radarServicesContainer.removeAllViews()

                    val hasOutage = services.any { !it.isOperational && it.isCore }

                    if (hasOutage) {
                        radarBinding.overallStatusDot.setBackgroundColor(Color.parseColor("#FF3366"))
                        radarBinding.overallStatusText.text = "اختلال موقت در برخی سرورها مشاهده شد"
                    } else {
                        radarBinding.overallStatusDot.setBackgroundColor(Color.parseColor("#00E560"))
                        radarBinding.overallStatusText.text = "تمامی سرورها و اتصالات پایدار و فعال هستند"
                    }

                    for (s in services) {
                        val rowBinding = ItemRadarServiceRowBinding.inflate(
                            LayoutInflater.from(this@MainActivity),
                            radarBinding.radarServicesContainer,
                            false
                        )
                        rowBinding.rowServiceName.text = s.name
                        rowBinding.rowServiceDesc.text = s.description
                        rowBinding.rowLatency.text = "${s.latencyMs}ms"

                        if (s.isOperational) {
                            rowBinding.rowStatusDot.setBackgroundColor(Color.parseColor("#00E560"))
                            rowBinding.rowStatusBadge.text = "عملیاتی و فعال"
                            rowBinding.rowStatusBadge.setBackgroundColor(Color.parseColor("#00E560"))
                        } else {
                            rowBinding.rowStatusDot.setBackgroundColor(Color.parseColor("#FF3366"))
                            rowBinding.rowStatusBadge.text = "غیرفعال موقت"
                            rowBinding.rowStatusBadge.setBackgroundColor(Color.parseColor("#FFD600"))
                        }

                        radarBinding.radarServicesContainer.addView(rowBinding.root)
                    }
                }
            }
        }

        radarBinding.btnRefreshRadar.setOnClickListener {
            loadRadar()
        }

        loadRadar()
        dialog.show()
    }

    private fun getEngineName(key: String): String = when (key) {
        "rezflix" -> "سیاره نپتون"
        "almasmovie" -> "سیاره اورانوس"
        "nextmovie" -> "سیاره زحل"
        "bj" -> "سیاره مشتری"
        else -> "سیاره زهره"
    }

    private fun showPlaylistsHubDialog(initialTab: Int = 0) {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val hubBinding = DialogPlaylistsHubBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(hubBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(hubBinding.topBarPlaylistsHub)

        hubBinding.btnBackPlaylistsHub.setOnClickListener { dialog.dismiss() }

        val loadFavorites = {
            val favs = FavoritesManager.getFavorites(this@MainActivity)
            hubBinding.badgeFavCount.text = "${favs.size}"
            hubBinding.headerActionsFavorites.isVisible = favs.isNotEmpty()
            hubBinding.txtFavHeaderCount.text = "عناوین نشان‌شده (${favs.size} عنوان)"
            if (favs.isEmpty()) {
                hubBinding.emptyFavoritesContainer.isVisible = true
                hubBinding.rvFavoritesHub.isVisible = false
            } else {
                hubBinding.emptyFavoritesContainer.isVisible = false
                hubBinding.rvFavoritesHub.isVisible = true
                val favAdapter = MovieCardAdapter(
                    favs.toMutableList(),
                    onItemClick = { item -> showMovieDetail(item) },
                    onItemLongClick = { item -> showMediaQuickActionsBottomSheet(item) }
                )
                hubBinding.rvFavoritesHub.layoutManager = GridLayoutManager(this@MainActivity, 2)
                hubBinding.rvFavoritesHub.adapter = favAdapter
            }
        }

        val loadPlaylists = {
            val playlists = PlaylistsManager.getAllPlaylists(this@MainActivity)
            hubBinding.badgePlaylistsCount.text = "${playlists.size}"
            hubBinding.headerActionsPlaylists.isVisible = playlists.isNotEmpty()
            hubBinding.txtPlaylistsHeaderCount.text = "مجموعه‌های شخصی شما (${playlists.size} پلی‌لیست)"
            if (playlists.isEmpty()) {
                hubBinding.emptyPlaylistsContainer.isVisible = true
                hubBinding.rvPlaylistsHub.isVisible = false
            } else {
                hubBinding.emptyPlaylistsContainer.isVisible = false
                hubBinding.rvPlaylistsHub.isVisible = true
                val adapter = PlaylistsAdapter(playlists) { selectedPl ->
                    showPlaylistDetailDialog(selectedPl)
                }
                hubBinding.rvPlaylistsHub.layoutManager = LinearLayoutManager(this@MainActivity)
                hubBinding.rvPlaylistsHub.adapter = adapter
            }
        }

        val loadHistory = {
            val history = HistoryManager.getHistory(this@MainActivity)
            hubBinding.badgeHistoryCount.text = "${history.size}"
            hubBinding.headerActionsHistory.isVisible = history.isNotEmpty()
            hubBinding.txtHistoryHeaderCount.text = "عناوین مشاهده‌شده (${history.size} عنوان)"
            if (history.isEmpty()) {
                hubBinding.emptyHistoryContainer.isVisible = true
                hubBinding.rvHistoryHub.isVisible = false
            } else {
                hubBinding.emptyHistoryContainer.isVisible = false
                hubBinding.rvHistoryHub.isVisible = true
                val historyAdapter = MovieCardAdapter(
                    history.toMutableList(),
                    onItemClick = { item -> showMovieDetail(item) },
                    onItemLongClick = { item -> showMediaQuickActionsBottomSheet(item) }
                )
                hubBinding.rvHistoryHub.layoutManager = GridLayoutManager(this@MainActivity, 2)
                hubBinding.rvHistoryHub.adapter = historyAdapter
            }
        }

        val selectTab = { tabIndex: Int ->
            hubBinding.tabFavorites.isSelected = (tabIndex == 0)
            hubBinding.tabPlaylists.isSelected = (tabIndex == 1)
            hubBinding.tabHistory.isSelected = (tabIndex == 2)

            hubBinding.containerFavorites.isVisible = (tabIndex == 0)
            hubBinding.containerPlaylists.isVisible = (tabIndex == 1)
            hubBinding.containerHistory.isVisible = (tabIndex == 2)

            val mutedColor = ContextCompat.getColor(this@MainActivity, R.color.muted)

            if (tabIndex == 0) {
                hubBinding.icTabFav.imageTintList = ColorStateList.valueOf(Color.WHITE)
                hubBinding.txtTabFav.setTextColor(Color.WHITE)
                hubBinding.badgeFavCount.setTextColor(Color.WHITE)
                hubBinding.badgeFavCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#35000000"))
            } else {
                hubBinding.icTabFav.imageTintList = ColorStateList.valueOf(mutedColor)
                hubBinding.txtTabFav.setTextColor(mutedColor)
                hubBinding.badgeFavCount.setTextColor(mutedColor)
                hubBinding.badgeFavCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))
            }

            if (tabIndex == 1) {
                hubBinding.icTabPlaylists.imageTintList = ColorStateList.valueOf(Color.WHITE)
                hubBinding.txtTabPlaylists.setTextColor(Color.WHITE)
                hubBinding.badgePlaylistsCount.setTextColor(Color.WHITE)
                hubBinding.badgePlaylistsCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#35000000"))
            } else {
                hubBinding.icTabPlaylists.imageTintList = ColorStateList.valueOf(mutedColor)
                hubBinding.txtTabPlaylists.setTextColor(mutedColor)
                hubBinding.badgePlaylistsCount.setTextColor(mutedColor)
                hubBinding.badgePlaylistsCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))
            }

            if (tabIndex == 2) {
                hubBinding.icTabHistory.imageTintList = ColorStateList.valueOf(Color.WHITE)
                hubBinding.txtTabHistory.setTextColor(Color.WHITE)
                hubBinding.badgeHistoryCount.setTextColor(Color.WHITE)
                hubBinding.badgeHistoryCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#35000000"))
            } else {
                hubBinding.icTabHistory.imageTintList = ColorStateList.valueOf(mutedColor)
                hubBinding.txtTabHistory.setTextColor(mutedColor)
                hubBinding.badgeHistoryCount.setTextColor(mutedColor)
                hubBinding.badgeHistoryCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))
            }

            when (tabIndex) {
                0 -> loadFavorites()
                1 -> loadPlaylists()
                2 -> loadHistory()
            }
        }

        hubBinding.tabFavorites.setOnClickListener { selectTab(0) }
        hubBinding.tabPlaylists.setOnClickListener { selectTab(1) }
        hubBinding.tabHistory.setOnClickListener { selectTab(2) }

        hubBinding.btnClearAllFavorites.setOnClickListener {
            showConfirmBottomSheet(
                title = "حذف همه نشان‌ها",
                message = "آیا از پاکسازی تمام فیلم‌ها و سریال‌های نشان‌شده اطمینان دارید؟",
                positiveText = "حذف همه",
                isDestructive = true
            ) {
                FavoritesManager.clearAllFavorites(this@MainActivity)
                loadFavorites()
                showAppToast("تمام نشان‌ها حذف شدند")
            }
        }

        hubBinding.btnClearAllPlaylists.setOnClickListener {
            showConfirmBottomSheet(
                title = "حذف همه پلی‌لیست‌ها",
                message = "آیا از حذف تمامی پلی‌لیست‌های اختصاصی خود اطمینان دارید؟ این عمل غیرقابل بازگشت است.",
                positiveText = "حذف همه",
                isDestructive = true
            ) {
                PlaylistsManager.clearAllPlaylists(this@MainActivity)
                loadPlaylists()
                showAppToast("تمامی پلی‌لیست‌ها حذف شدند")
            }
        }

        hubBinding.btnClearAllHistory.setOnClickListener {
            showConfirmBottomSheet(
                title = "پاکسازی تاریخچه",
                message = "آیا از حذف تمام عناوین مشاهده‌شده در تاریخچه اطمینان دارید؟",
                positiveText = "پاکسازی",
                isDestructive = true
            ) {
                HistoryManager.clearAllHistory(this@MainActivity)
                loadHistory()
                showAppToast("تاریخچه با موفقیت پاک شد")
            }
        }

        hubBinding.btnExportFavorites.setOnClickListener {
            exportCombinedBackup()
            showAppToast("در حال آماده‌سازی فایل پشتیبان...")
        }
        hubBinding.btnImportFavorites.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        hubBinding.btnExportPlaylists.setOnClickListener {
            exportCombinedBackup()
            showAppToast("در حال آماده‌سازی فایل پشتیبان...")
        }
        hubBinding.btnImportPlaylists.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        hubBinding.btnCreateNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog(null) {
                loadPlaylists()
            }
        }
        hubBinding.btnEmptyCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog(null) {
                loadPlaylists()
            }
        }

        selectTab(initialTab)

        dialog.show()
    }

    private fun showPlaylistDetailDialog(playlist: Playlist) {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val plBinding = DialogPlaylistDetailBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(plBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(plBinding.topBarPlaylistDetail)

        plBinding.btnBackPlaylist.setOnClickListener { dialog.dismiss() }
        plBinding.txtTopPlaylistTitle.text = playlist.title
        plBinding.txtPlaylistTitle.text = playlist.title
        plBinding.txtPlaylistEnTitle.text = playlist.englishTitle
        plBinding.txtPlaylistDesc.text = playlist.description
        plBinding.txtItemsCount.text = "عناوین این مجموعه (${playlist.items.size} عنوان):"

        ImageLoader.load(playlist.cover, plBinding.imgPlaylistCover)

        val movieAdapter = MovieCardAdapter(
            playlist.items.toMutableList(),
            onItemClick = { movie -> showMovieDetail(movie) },
            onItemLongClick = { movie -> showMediaQuickActionsBottomSheet(movie) }
        )
        plBinding.rvPlaylistMovies.layoutManager = GridLayoutManager(this, 2)
        plBinding.rvPlaylistMovies.adapter = movieAdapter
        plBinding.btnPlaySequential.setOnClickListener {
            if (playlist.items.isNotEmpty()) {
                val firstMovie = playlist.items.first()
                showAppToast("آغاز پخش ترتیبی پلی‌لیست: ${firstMovie.title}")
                showMovieDetail(firstMovie)
            }
        }

        dialog.show()
    }

    private fun showFavoritesDialog() {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val favBinding = DialogFavoritesBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(favBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(favBinding.topBarFavorites)

        favBinding.btnBackFavorites.setOnClickListener { dialog.dismiss() }

        val refreshList = {
            val favs = FavoritesManager.getFavorites(this@MainActivity)
            favBinding.txtFavCountBadge.text = "${favs.size} عنوان"
            favBinding.btnClearAllFavoritesStandalone.isVisible = favs.isNotEmpty()
            if (favs.isEmpty()) {
                favBinding.emptyFavoritesContainer.isVisible = true
                favBinding.rvFavorites.isVisible = false
            } else {
                favBinding.emptyFavoritesContainer.isVisible = false
                favBinding.rvFavorites.isVisible = true
                val favAdapter = MovieCardAdapter(
                    favs.toMutableList(),
                    onItemClick = { item -> showMovieDetail(item) },
                    onItemLongClick = { item -> showMediaQuickActionsBottomSheet(item) }
                )
                favBinding.rvFavorites.layoutManager = GridLayoutManager(this@MainActivity, 2)
                favBinding.rvFavorites.adapter = favAdapter
            }
        }

        favBinding.btnClearAllFavoritesStandalone.setOnClickListener {
            showConfirmBottomSheet(
                title = "حذف همه نشان‌ها",
                message = "آیا از پاکسازی تمام فیلم‌ها و سریال‌های نشان‌شده اطمینان دارید؟",
                positiveText = "حذف همه",
                isDestructive = true
            ) {
                FavoritesManager.clearAllFavorites(this@MainActivity)
                refreshList()
                showAppToast("تمام نشان‌ها حذف شدند")
            }
        }

        refreshList()
        dialog.show()
    }

    private fun createStyledBottomSheetDialog(): BottomSheetDialog {
        val dialog = BottomSheetDialog(this, R.style.Theme_Filigram_BottomSheetDialog)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.elevation = 0f
            if (bottomSheet != null) {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
            }
        }
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes.blurBehindRadius = 40
                } catch (_: Exception) {}
            }
            w.setDimAmount(0.45f)
        }
        return dialog
    }

    private fun applyDialogBlurBehind(window: Window?) {
        window?.let { w ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes.blurBehindRadius = 40
                } catch (_: Exception) {}
            }
        }
    }

    private fun showAboutDialog() {
        val dialog = createStyledBottomSheetDialog()
        val aboutBinding = BottomSheetAboutBinding.inflate(layoutInflater)
        dialog.setContentView(aboutBinding.root)

        aboutBinding.cardTelegramSupport.setOnClickListener {
            val tgUrl = "https://t.me/filigramapp"
            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl))
            try {
                startActivity(tgIntent)
            } catch (e: Exception) {
                showAppToast("کانال تلگرام: t.me/filigramapp", autoDismissMs = 4000L)
            }
        }

        dialog.show()
    }

    private fun showShareAppSheet() {
        val dialog = createStyledBottomSheetDialog()
        val shareBinding = BottomSheetShareAppBinding.inflate(layoutInflater)
        dialog.setContentView(shareBinding.root)

        val telegramLink = "https://t.me/filigramapp"
        val apkLink = "https://github.com/milibots/filigram/releases/latest"

        shareBinding.cardShareTelegram.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "📽 فیلیگرام — اپ رایگان فیلم و سریال بدون محدودیت\n" +
                    "همین الان عضو کانال تلگرام ما شو و از جدیدترین آپدیت‌ها باخبر بمان!\n\n" +
                    "🔗 $telegramLink")
            }
            try {
                startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری فیلیگرام"))
            } catch (e: Exception) {
                showAppToast("کانال تلگرام: $telegramLink", autoDismissMs = 4000L)
            }
            dialog.dismiss()
        }

        shareBinding.cardShareApk.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "📽 فیلیگرام — دانلود رایگان\n" +
                    "اپ فیلم و سریال بدون محدودیت برای اندروید!\n\n" +
                    "⬇️ دانلود آخرین نسخه:\n$apkLink")
            }
            try {
                startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری فیلیگرام"))
            } catch (e: Exception) {
                showAppToast("لینک دانلود: $apkLink", autoDismissMs = 4000L)
            }
            dialog.dismiss()
        }

        shareBinding.btnShareDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showConfirmBottomSheet(
        title: String,
        message: String,
        positiveText: String = "تایید",
        negativeText: String = "انصراف",
        isDestructive: Boolean = false,
        iconRes: Int = R.drawable.ic_bell_gold,
        onConfirm: () -> Unit
    ) {
        val dialog = createStyledBottomSheetDialog()
        val confirmBinding = BottomSheetConfirmDialogBinding.inflate(layoutInflater)
        dialog.setContentView(confirmBinding.root)

        confirmBinding.tvConfirmTitle.text = title
        confirmBinding.tvConfirmTitle.setTextColor(Color.WHITE)
        confirmBinding.tvConfirmMessage.text = message
        confirmBinding.tvConfirmMessage.setTextColor(Color.parseColor("#CCCCCC"))
        confirmBinding.btnConfirmPositive.text = positiveText
        confirmBinding.btnConfirmPositive.setTextColor(Color.WHITE)
        confirmBinding.btnConfirmNegative.text = negativeText
        confirmBinding.btnConfirmNegative.setTextColor(Color.parseColor("#CCCCCC"))
        confirmBinding.ivConfirmIcon.setImageResource(iconRes)

        if (isDestructive) {
            val redBgColor = Color.parseColor("#E50914")
            confirmBinding.btnConfirmPositive.backgroundTintList = ColorStateList.valueOf(redBgColor)
            confirmBinding.btnConfirmPositive.setTextColor(Color.WHITE)
            confirmBinding.ivConfirmIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            val neutralBgColor = Color.parseColor("#222222")
            confirmBinding.btnConfirmPositive.backgroundTintList = ColorStateList.valueOf(neutralBgColor)
            confirmBinding.btnConfirmPositive.setTextColor(Color.WHITE)
            confirmBinding.ivConfirmIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }

        confirmBinding.btnConfirmNegative.setOnClickListener {
            dialog.dismiss()
        }
        confirmBinding.btnConfirmPositive.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }

    private fun showSelectionBottomSheet(
        title: String,
        subtitle: String? = null,
        options: List<String>,
        selectedIndex: Int = -1,
        onSelected: (Int) -> Unit
    ) {
        val dialog = createStyledBottomSheetDialog()
        val selectorBinding = BottomSheetItemSelectorBinding.inflate(layoutInflater)
        dialog.setContentView(selectorBinding.root)

        selectorBinding.tvSelectorTitle.text = title
        selectorBinding.tvSelectorTitle.setTextColor(Color.WHITE)
        if (!subtitle.isNullOrEmpty()) {
            selectorBinding.tvSelectorSubtitle.text = subtitle
            selectorBinding.tvSelectorSubtitle.setTextColor(Color.parseColor("#999999"))
            selectorBinding.tvSelectorSubtitle.isVisible = true
        }

        val density = resources.displayMetrics.density

        options.forEachIndexed { index, optionText ->
            val itemBtn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = optionText
                setTextColor(if (index == selectedIndex) Color.WHITE else Color.parseColor("#CCCCCC"))
                textSize = 13f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.yekan_bakh_regular)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                val isCurrent = (index == selectedIndex)
                if (isCurrent) {
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_star_gold)
                    iconTint = ColorStateList.valueOf(Color.WHITE)
                }
                val padH = (12 * density).toInt()
                val padV = (10 * density).toInt()
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    dialog.dismiss()
                    onSelected(index)
                }
            }
            selectorBinding.layoutSelectorOptions.addView(itemBtn)
        }

        dialog.show()
    }

    private fun showMediaQuickActionsBottomSheet(item: MovieItem) {
        val dialog = createStyledBottomSheetDialog()
        val quickBinding = BottomSheetMovieQuickActionsBinding.inflate(layoutInflater)
        dialog.setContentView(quickBinding.root)

        quickBinding.tvQuickTitle.text = item.title
        quickBinding.tvQuickTitle.setTextColor(Color.WHITE)
        ImageLoader.load(item.image, quickBinding.ivQuickPoster)

        quickBinding.tvQuickTypeBadge.text = if (item.type == 1) "سریال" else "فیلم"
        quickBinding.tvQuickTypeBadge.setTextColor(Color.WHITE)
        if (item.hasDub) {
            quickBinding.tvQuickTypeBadge.text = "دوبله"
        } else if (item.hasSub) {
            quickBinding.tvQuickTypeBadge.text = "زیرنویس"
        }

        val rating = item.rating?.takeIf { it.isNotBlank() && it != "null" }
        if (rating != null) {
            quickBinding.tvQuickRating.text = "★ $rating"
            quickBinding.tvQuickRating.setTextColor(Color.WHITE)
            quickBinding.tvQuickRating.isVisible = true
        } else {
            quickBinding.tvQuickRating.isVisible = false
        }

        val year = item.year?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
        if (year != null) {
            quickBinding.tvQuickYear.text = year
            quickBinding.tvQuickYear.setTextColor(Color.parseColor("#999999"))
            quickBinding.tvQuickYear.isVisible = true
        } else {
            quickBinding.tvQuickYear.isVisible = false
        }

        quickBinding.tvQuickGenre.text = item.genre?.takeIf { it.isNotBlank() } ?: (if (item.type == 1) "سریال" else "سینمایی")
        quickBinding.tvQuickGenre.setTextColor(Color.parseColor("#CCCCCC"))

        FavoritesManager.init(this)
        var isFav = FavoritesManager.isFavorite(item.id)
        val updateFavUi = {
            if (isFav) {
                quickBinding.ivActionLikeIcon.setImageResource(R.drawable.ic_heart_filled_gold)
                quickBinding.ivActionLikeIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                quickBinding.tvActionLikeTitle.text = "حذف از نشان‌ها (علاقه‌مندی‌ها)"
                quickBinding.tvActionLikeTitle.setTextColor(Color.WHITE)
                quickBinding.tvActionLikeSub.text = "این اثر در لیست علاقه‌مندی‌های شما قرار دارد"
                quickBinding.tvActionLikeSub.setTextColor(Color.parseColor("#999999"))
            } else {
                quickBinding.ivActionLikeIcon.setImageResource(R.drawable.ic_heart_outline_gold)
                quickBinding.ivActionLikeIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                quickBinding.tvActionLikeTitle.text = "نشان کردن (علاقه‌مندی‌ها)"
                quickBinding.tvActionLikeTitle.setTextColor(Color.WHITE)
                quickBinding.tvActionLikeSub.text = "دسترسی سریع در بخش علاقه‌مندی‌ها"
                quickBinding.tvActionLikeSub.setTextColor(Color.parseColor("#999999"))
            }
        }
        updateFavUi()

        quickBinding.btnActionLike.setOnClickListener {
            isFav = FavoritesManager.toggleFavorite(this, item)
            updateFavUi()
            val msg = if (isFav) "«${item.title}» به نشان‌ها افزوده شد" else "«${item.title}» از نشان‌ها حذف شد"
            showAppToast(msg, iconRes = R.drawable.ic_playlist_vector)
        }

        // 2. Add to Playlist Action
        quickBinding.btnActionAddToPlaylist.setOnClickListener {
            dialog.dismiss()
            showAddToPlaylistDialog(item)
        }

        // 3. Schedule / Episodes Alert Action
        val isSeries = (item.type == 1)
        var isSubscribed = SeriesSubscriptionManager.isSubscribed(item.id)
        val updateScheduleUi = {
            quickBinding.ivActionScheduleIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            if (isSubscribed) {
                quickBinding.tvActionScheduleTitle.text = if (isSeries) "لغو اعلان قسمت‌های جدید" else "لغو یادآوری انتشار"
                quickBinding.tvActionScheduleTitle.setTextColor(Color.WHITE)
                quickBinding.tvActionScheduleSub.text = "اعلان خودکار برای این اثر فعال است"
                quickBinding.tvActionScheduleSub.setTextColor(Color.parseColor("#999999"))
            } else {
                quickBinding.tvActionScheduleTitle.text = if (isSeries) "زمان‌بندی و اعلان قسمت‌های جدید" else "یادآوری انتشار و تماشا"
                quickBinding.tvActionScheduleTitle.setTextColor(Color.WHITE)
                quickBinding.tvActionScheduleSub.text = if (isSeries) "اطلاع‌رسانی خودکار به محض انتشار قسمت جدید" else "یادآوری زمان انتشار و کیفیت‌های تازه"
                quickBinding.tvActionScheduleSub.setTextColor(Color.parseColor("#999999"))
            }
        }
        updateScheduleUi()

        quickBinding.btnActionScheduleEpisode.setOnClickListener {
            isSubscribed = SeriesSubscriptionManager.toggleSubscription(this, item, activeEngine)
            updateScheduleUi()
            val msg = if (isSubscribed) {
                "اعلان قسمت‌های جدید «${item.title}» فعال شد"
            } else {
                "اعلان «${item.title}» غیرفعال شد"
            }
            showAppToast(msg, iconRes = R.drawable.ic_playlist_vector)
        }

        // 4. Download Action
        quickBinding.btnActionDownload.setOnClickListener {
            dialog.dismiss()
            showMovieDetail(item)
        }

        // 5. Details Action
        quickBinding.btnActionDetails.setOnClickListener {
            dialog.dismiss()
            showMovieDetail(item)
        }

        dialog.show()
    }

    private fun showAddToPlaylistDialog(item: MovieItem) {
        val playlists = PlaylistsManager.getAllPlaylists(this@MainActivity)
        val dialog = createStyledBottomSheetDialog()
        val sheetBinding = BottomSheetAddToPlaylistBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvAddToPlaylistTitle.setTextColor(Color.WHITE)
        sheetBinding.tvAddToPlaylistSubtitle.text = "انتخاب پلی‌لیست برای «${item.title}»:"
        sheetBinding.tvAddToPlaylistSubtitle.setTextColor(Color.parseColor("#CCCCCC"))

        val density = resources.displayMetrics.density

        if (playlists.isEmpty()) {
            sheetBinding.tvNoPlaylistsNotice.isVisible = true
            sheetBinding.tvNoPlaylistsNotice.setTextColor(Color.parseColor("#999999"))
        } else {
            sheetBinding.tvNoPlaylistsNotice.isVisible = false
            for (pl in playlists) {
                val itemBtn = MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = "${pl.title} (${pl.items.size} اثر)"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.yekan_bakh_regular)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_playlist_vector)
                    iconTint = ColorStateList.valueOf(Color.WHITE)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    val padH = (12 * density).toInt()
                    val padV = (10 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                    setOnClickListener {
                        val added = PlaylistsManager.addItemToPlaylist(this@MainActivity, pl.id, item)
                        if (added) {
                            showAppToast("«${item.title}» به پلی‌لیست «${pl.title}» افزوده شد")
                        } else {
                            showAppToast("این اثر قبلاً در این پلی‌لیست قرار گرفته است")
                        }
                        dialog.dismiss()
                    }
                }
                sheetBinding.layoutPlaylistsList.addView(itemBtn)
            }
        }

        sheetBinding.btnCreatePlaylistFromAdd.setTextColor(Color.BLACK)
        sheetBinding.btnCreatePlaylistFromAdd.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        sheetBinding.btnCreatePlaylistFromAdd.setOnClickListener {
            dialog.dismiss()
            showCreatePlaylistDialog(item)
        }

        dialog.show()
    }

    private fun showCreatePlaylistDialog(item: MovieItem? = null, onCreated: (() -> Unit)? = null) {
        val dialog = createStyledBottomSheetDialog()
        val sheetBinding = BottomSheetCreatePlaylistBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvCreatePlaylistTitle.setTextColor(Color.WHITE)
        sheetBinding.tvCreatePlaylistSubtitle.setTextColor(Color.parseColor("#CCCCCC"))
        sheetBinding.btnCancelCreatePlaylist.setTextColor(Color.parseColor("#CCCCCC"))
        sheetBinding.btnSubmitCreatePlaylist.setTextColor(Color.BLACK)
        sheetBinding.btnSubmitCreatePlaylist.backgroundTintList = ColorStateList.valueOf(Color.WHITE)

        if (item != null) {
            sheetBinding.btnSubmitCreatePlaylist.text = "ایجاد و افزودن اثر"
        }

        sheetBinding.btnCancelCreatePlaylist.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnSubmitCreatePlaylist.setOnClickListener {
            val plName = sheetBinding.etPlaylistNameInput.text.toString().trim()
            if (plName.isNotEmpty()) {
                val pl = PlaylistsManager.createPlaylist(this@MainActivity, plName, "پلی‌لیست اختصاصی کاربر")
                if (item != null) {
                    PlaylistsManager.addItemToPlaylist(this@MainActivity, pl.id, item)
                    showAppToast("پلی‌لیست «$plName» ایجاد و اثر به آن افزوده شد")
                } else {
                    showAppToast("پلی‌لیست «$plName» با موفقیت ایجاد شد")
                }
                dialog.dismiss()
                onCreated?.invoke()
            } else {
                showAppToast("لطفاً نام پلی‌لیست را وارد کنید")
            }
        }

        dialog.show()
    }

    private fun refreshAnnouncementsBadge() {
        lifecycleScope.launch {
            AnnouncementsManager.fetchAnnouncements(this@MainActivity)
            val unread = AnnouncementsManager.getUnreadCount(this@MainActivity)
            withContext(Dispatchers.Main) {
                updateNotificationBadge(unread)
            }
        }
    }

    private fun updateNotificationBadge(unreadCount: Int) {
        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        val showDevLogs = prefs.getBoolean("show_dev_logs", false)
        if (unreadCount > 0) {
            if (!showDevLogs) {
                try {
                    val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_notifications)
                    badge.isVisible = true
                    badge.number = unreadCount
                    badge.backgroundColor = ContextCompat.getColor(this, R.color.gold)
                } catch (_: Exception) {}
            }
        } else {
            try {
                binding.bottomNavigation.removeBadge(R.id.nav_notifications)
            } catch (_: Exception) {}
        }
    }

    private fun showAnnouncementsDialog() {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val anBinding = DialogAnnouncementsBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(anBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(anBinding.topBarAnnouncements)

        anBinding.btnBackAnnouncements.setOnClickListener { dialog.dismiss() }

        var adapter: AnnouncementsAdapter? = null

        val updateHeaderBadge = {
            val unread = AnnouncementsManager.getUnreadCount(this@MainActivity)
            updateNotificationBadge(unread)
            if (unread > 0) {
                anBinding.txtUnreadCountBadge.isVisible = true
                anBinding.txtUnreadCountBadge.text = "$unread جدید"
            } else {
                anBinding.txtUnreadCountBadge.isVisible = false
            }
        }

        val loadData = {
            anBinding.loadingAnnouncements.isVisible = true
            lifecycleScope.launch {
                val list = AnnouncementsManager.fetchAnnouncements(this@MainActivity)
                withContext(Dispatchers.Main) {
                    anBinding.loadingAnnouncements.isVisible = false
                    anBinding.swipeRefreshAnnouncements.isRefreshing = false
                    if (list.isEmpty()) {
                        anBinding.emptyAnnouncementsContainer.isVisible = true
                        anBinding.rvAnnouncements.isVisible = false
                    } else {
                        anBinding.emptyAnnouncementsContainer.isVisible = false
                        anBinding.rvAnnouncements.isVisible = true
                        adapter?.updateData(list)
                    }
                    updateHeaderBadge()
                }
            }
        }

        val initialList = AnnouncementsManager.getCachedAnnouncements().toMutableList()
        adapter = AnnouncementsAdapter(initialList) { clickedItem ->
            if (!clickedItem.isRead) {
                AnnouncementsManager.markAsRead(this@MainActivity, clickedItem.id)
                updateHeaderBadge()
            }
            showAnnouncementDetailBottomSheet(clickedItem)
        }

        anBinding.rvAnnouncements.layoutManager = LinearLayoutManager(this@MainActivity)
        anBinding.rvAnnouncements.adapter = adapter

        anBinding.swipeRefreshAnnouncements.setOnRefreshListener {
            loadData()
        }

        anBinding.btnMarkAllRead.setOnClickListener {
            AnnouncementsManager.markAllAsRead(this@MainActivity)
            adapter.updateData(AnnouncementsManager.getCachedAnnouncements())
            updateHeaderBadge()
            showAppToast("تمامی اعلانات به عنوان خوانده شده علامت‌گذاری شدند")
        }

        updateHeaderBadge()
        loadData()

        dialog.show()
    }

    private fun showAnnouncementDetailBottomSheet(announcement: Announcement) {
        val bottomSheet = createStyledBottomSheetDialog()
        val bsBinding = com.filigram.cinema.databinding.BottomSheetAnnouncementDetailBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bsBinding.root)

        bsBinding.tvAnnouncementDetailTitle.text = announcement.title
        bsBinding.tvAnnouncementDetailDate.text = announcement.getFormattedDate()
        bsBinding.tvAnnouncementDetailBody.text = announcement.text

        bsBinding.btnJoinTelegramChannel.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/filigramapp")))
            } catch (e: Exception) {
                showAppToast("کانال تلگرام: @filigramapp")
            }
        }

        bsBinding.btnContactSupport.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/kiorcode")))
            } catch (e: Exception) {
                showAppToast("آیدی پشتیبانی: @kiorcode")
            }
        }

        bottomSheet.show()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        activePlayerDialog?.window?.decorView?.findViewById<View>(R.id.playerTopBar)?.isVisible = !isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        activeExoPlayer?.release()
        activeExoPlayer = null
        activePlayerDialog = null
    }

    fun registerDataLaunchers(
        onImportResult: (String) -> Unit,
        defaultExportName: String = "filigram_backup.json"
    ) {

    }

    fun exportJsonToFile(json: String, suggestedName: String) {
        pendingExportJson = json
        exportLauncher.launch(suggestedName)
    }

    fun exportCombinedBackup() {
        val favs = FavoritesManager.getFavorites(this)
        val playlists = PlaylistsManager.getAllPlaylists(this)

        val favsArr = JSONArray()
        for (f in favs) {
            favsArr.put(JSONObject().apply {
                put("id", f.id)
                put("title", f.title)
                put("image", f.image)
                put("type", f.type)
                put("year", f.year ?: "")
                put("hasSub", f.hasSub)
                put("hasDub", f.hasDub)
                put("slug", f.slug ?: "")
            })
        }

        val plArr = JSONArray()
        for (pl in playlists) {
            val itemsArr = JSONArray()
            for (it in pl.items) {
                itemsArr.put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("image", it.image)
                    put("type", it.type)
                    put("year", it.year ?: "")
                    put("hasSub", it.hasSub)
                    put("hasDub", it.hasDub)
                    put("slug", it.slug ?: "")
                })
            }
            plArr.put(JSONObject().apply {
                put("id", pl.id)
                put("title", pl.title)
                put("englishTitle", pl.englishTitle)
                put("description", pl.description)
                put("cover", pl.cover)
                put("items", itemsArr)
            })
        }

        val root = JSONObject().apply {
            put("version", 2)
            put("app", "Filigram")
            put("exported_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            put("favorites_count", favs.size)
            put("playlists_count", playlists.size)
            put("favorites", favsArr)
            put("playlists", plArr)
        }

        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        exportJsonToFile(root.toString(2), "filigram_backup_$ts.json")
    }

    private fun doImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")
                val jsonStr = stream.bufferedReader().use { it.readText() }
                stream.close()

                val root = JSONObject(jsonStr)
                val version = root.optInt("version", 1)

                var favImported = 0
                var plImported = 0

                val favsArr = if (root.has("favorites")) root.getJSONArray("favorites")
                              else JSONArray(jsonStr)
                for (i in 0 until favsArr.length()) {
                    val obj = favsArr.getJSONObject(i)
                    val item = MovieItem(
                        id    = obj.optInt("id"),
                        title = obj.optString("title"),
                        image = obj.optString("image"),
                        type  = obj.optInt("type", 0),
                        year  = obj.optString("year").takeIf { it.isNotBlank() },
                        hasSub = obj.optBoolean("hasSub", false),
                        hasDub = obj.optBoolean("hasDub", false),
                        slug  = obj.optString("slug").takeIf { it.isNotBlank() }
                    )
                    if (item.id != 0) {
                        FavoritesManager.toggleFavorite(this@MainActivity, item)
                        favImported++
                    }
                }

                if (version >= 2 && root.has("playlists")) {
                    val plArr = root.getJSONArray("playlists")
                    for (i in 0 until plArr.length()) {
                        val obj = plArr.getJSONObject(i)
                        val title = obj.optString("title", "پلی‌لیست وارد شده")
                        val desc  = obj.optString("description", "")
                        val cover = obj.optString("cover", "")
                        val pl = PlaylistsManager.createPlaylist(this@MainActivity, title, desc)
                        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                        for (j in 0 until itemsArr.length()) {
                            val mObj = itemsArr.getJSONObject(j)
                            val movie = MovieItem(
                                id    = mObj.optInt("id"),
                                title = mObj.optString("title"),
                                image = mObj.optString("image"),
                                type  = mObj.optInt("type", 0),
                                year  = mObj.optString("year").takeIf { it.isNotBlank() },
                                hasSub = mObj.optBoolean("hasSub", false),
                                hasDub = mObj.optBoolean("hasDub", false),
                                slug  = mObj.optString("slug").takeIf { it.isNotBlank() }
                            )
                            if (movie.id != 0) {
                                PlaylistsManager.addItemToPlaylist(this@MainActivity, pl.id, movie)
                            }
                        }
                        plImported++
                    }
                }

                withContext(Dispatchers.Main) {
                    showAppToast("وارد شد: $favImported نشان + $plImported پلی‌لیست", autoDismissMs = 4000L)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showAppToast("خطا در وارد کردن: ${e.message}", autoDismissMs = 4000L)
                }
            }
        }
    }

    private fun setupTopDownloads() {
        binding.btnTopDownloads.setOnClickListener {
            showDownloadsHubDialog()
        }
    }

    private fun setupDownloadsBadge() {
        DownloadManager.addListener(object : DownloadManager.DownloadListener {
            override fun onTaskUpdated(task: DownloadTask) {
                runOnUiThread { updateTopDownloadsBadge() }
            }

            override fun onQueueChanged() {
                runOnUiThread { updateTopDownloadsBadge() }
            }

            override fun onTotalSpeedUpdated(totalBytesPerSec: Long, activeCount: Int) {
                runOnUiThread { updateTopDownloadsBadge() }
            }
        })
        updateTopDownloadsBadge()
    }

    private fun updateTopDownloadsBadge() {
        val activeCount = DownloadManager.getActiveDownloadsCount()
        if (activeCount > 0) {
            binding.badgeTopDownloadsCount.isVisible = true
            binding.badgeTopDownloadsCount.text = "$activeCount"
        } else {
            binding.badgeTopDownloadsCount.isVisible = false
        }
    }

    private fun startTurboDownload(
        movieId: Int,
        mediaTitle: String,
        qualityLabel: String,
        url: String,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ) {
        DownloadManager.enqueue(
            context = this,
            mediaId = movieId,
            title = mediaTitle,
            seriesTitle = if (isSeries) mediaTitle.substringBefore(" - ") else null,
            season = season,
            episode = episode,
            qualityLabel = qualityLabel,
            url = url
        )
        DownloadForegroundService.startService(this)
        showAppToast("«$mediaTitle» با سرعت توربو به صف دانلود اضافه شد ⚡")
        showDownloadsHubDialog()
    }

    private fun showDownloadsHubDialog() {
        val dialog = Dialog(this, R.style.Theme_Filigram_Dialog_Fullscreen)
        dialog.applyFullscreenAnimation()
        val dBinding = DialogDownloadsHubBinding.inflate(dialog.layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(dBinding.topBarDownloadsHub)

        dBinding.btnBackDownloadsHub.setOnClickListener { dialog.dismiss() }

        val downloadsAdapter = DownloadsAdapter(
            onPauseResumeClick = { task ->
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING) {
                    DownloadManager.pauseTask(this, task.id)
                } else {
                    DownloadManager.resumeTask(this, task.id)
                    DownloadForegroundService.startService(this)
                }
            },
            onCancelDeleteClick = { task ->
                showConfirmBottomSheet(
                    title = "حذف دانلود",
                    message = "آیا از حذف «${task.title}» و فایل دانلودشده اطمینان دارید؟ این فایل به طور کامل از حافظه پاک می‌شود.",
                    positiveText = "حذف فایل و دانلود",
                    isDestructive = true
                ) {
                    DownloadManager.cancelTask(this, task.id, deleteFile = true)
                }
            },
            onPlayDownloadedClick = { task ->
                playDownloadedVideo(task)
            }
        )

        dBinding.rvDownloads.layoutManager = LinearLayoutManager(this)
        dBinding.rvDownloads.adapter = downloadsAdapter

        val updateList = {
            val tasks = DownloadManager.getTasks()
            downloadsAdapter.submitList(tasks)
            dBinding.emptyDownloadsContainer.isVisible = tasks.isEmpty()
            dBinding.rvDownloads.isVisible = tasks.isNotEmpty()

            val speed = DownloadManager.getTotalSpeed()
            dBinding.tvTotalSpeedSummary.text = "سرعت کل: ${DownloadManager.formatSpeed(speed)}"
        }

        updateList()

        val listener = object : DownloadManager.DownloadListener {
            override fun onTaskUpdated(task: DownloadTask) {
                runOnUiThread { updateList() }
            }

            override fun onQueueChanged() {
                runOnUiThread { updateList() }
            }

            override fun onTotalSpeedUpdated(totalBytesPerSec: Long, activeCount: Int) {
                runOnUiThread {
                    dBinding.tvTotalSpeedSummary.text = "سرعت کل: ${DownloadManager.formatSpeed(totalBytesPerSec)} ($activeCount فعال)"
                }
            }
        }

        DownloadManager.addListener(listener)
        dialog.setOnDismissListener {
            DownloadManager.removeListener(listener)
        }

        dBinding.btnPauseAll.setOnClickListener {
            DownloadManager.pauseAll(this)
            showAppToast("همه دانلودها متوقف شدند")
        }

        dBinding.btnResumeAll.setOnClickListener {
            DownloadManager.resumeAll(this)
            DownloadForegroundService.startService(this)
            showAppToast("همه دانلودها از سر گرفته شدند")
        }

        dBinding.btnClearDownloads.setOnClickListener {
            DownloadManager.clearCompleted(this)
            showAppToast("دانلودهای پایان‌یافته پاکسازی شدند")
        }

        dBinding.btnDownloadSettings.setOnClickListener {
            showDownloadSettingsDialog {
                updateList()
            }
        }

        dialog.show()
    }

    private fun showDownloadSettingsDialog(onSaved: (() -> Unit)? = null) {
        val dialog = createStyledBottomSheetDialog()
        val sBinding = DialogDownloadSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(sBinding.root)

        sBinding.sliderConcurrent.value = DownloadManager.settings.maxConcurrentTasks.toFloat()
        sBinding.sliderThreads.value = DownloadManager.settings.threadsPerTask.toFloat()

        sBinding.tvLabelConcurrent.text = "تعداد دانلودهای همزمان: ${DownloadManager.settings.maxConcurrentTasks}"
        sBinding.tvLabelThreads.text = "تعداد تکه‌های هر فایل: ${DownloadManager.settings.threadsPerTask} تکه موازی"

        sBinding.sliderConcurrent.addOnChangeListener { _, value, _ ->
            sBinding.tvLabelConcurrent.text = "تعداد دانلودهای همزمان: ${value.toInt()}"
        }

        sBinding.sliderThreads.addOnChangeListener { _, value, _ ->
            sBinding.tvLabelThreads.text = "تعداد تکه‌های هر فایل: ${value.toInt()} تکه موازی"
        }

        sBinding.switchWifiOnly.isChecked = DownloadManager.settings.wifiOnly
        sBinding.switchNightScheduler.isChecked = DownloadManager.settings.nightSchedulerEnabled
        sBinding.switchAutoSubtitles.isChecked = DownloadManager.settings.autoDownloadSubtitles

        var selectedCustomPath = DownloadManager.settings.customStoragePath
        val updatePathDisplay = {
            sBinding.tvCurrentStoragePath.text = if (!selectedCustomPath.isNullOrEmpty()) {
                selectedCustomPath
            } else {
                "Download/Filigram (پیش‌فرض)"
            }
        }
        updatePathDisplay()

        sBinding.btnChangeStorageLocation.setOnClickListener {
            onStorageFolderSelected = { pickedPath ->
                selectedCustomPath = pickedPath
                updatePathDisplay()
                showAppToast("پوشه انتخاب شد")
            }
            try {
                storageFolderLauncher.launch(null)
            } catch (e: Exception) {
                showAppToast("امکان باز کردن انتخابگر پوشه وجود ندارد")
            }
        }

        sBinding.btnSaveDownloadSettings.setOnClickListener {
            val maxCon = sBinding.sliderConcurrent.value.toInt()
            val thr = sBinding.sliderThreads.value.toInt()
            val wifiOnly = sBinding.switchWifiOnly.isChecked
            val nightSched = sBinding.switchNightScheduler.isChecked
            val autoSub = sBinding.switchAutoSubtitles.isChecked

            DownloadManager.updateSettings(
                context = this,
                maxConcurrent = maxCon,
                threads = thr,
                wifiOnly = wifiOnly,
                nightScheduler = nightSched,
                nightStart = 2,
                nightEnd = 7,
                autoSubtitles = autoSub,
                customPath = selectedCustomPath
            )
            showAppToast("تنظیمات پیشرفته دانلود ذخیره شد")
            dialog.dismiss()
            onSaved?.invoke()
        }

        dialog.show()
    }

    private fun playDownloadedVideo(task: DownloadTask) {
        try {
            val file = java.io.File(task.filePath)
            if (!file.exists()) {
                showAppToast("فایل ویدیویی در حافظه یافت نشد")
                return
            }

            val options = listOf("پخش درون برنامه‌ای (با پشتیبانی از زیرنویس و PiP)", "پخش در پلیرهای خارجی (VLC, MX Player, ...)")
            showSelectionBottomSheet(
                title = task.title,
                subtitle = "نحوه پخش فایل آفلاین را انتخاب کنید:",
                options = options
            ) { which ->
                when (which) {
                    0 -> {
                        playVideoInApp(
                            title = task.title,
                            quality = "آفلاین (${task.qualityLabel})",
                            streamUrl = file.absolutePath,
                            subtitlePath = task.subtitlePath,
                            isOffline = true
                        )
                    }
                    1 -> {
                        try {
                            val uri = Uri.fromFile(file)
                            val videoIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(videoIntent, "پخش با پلیر خارجی:"))
                        } catch (e: Exception) {
                            showAppToast("خطا در فراخوانی پلیر خارجی: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            showAppToast("خطا در پخش فایل: ${e.message}")
        }
    }

    private fun showBatchDownloadDialog(
        title: String,
        subtitle: String,
        movieId: Int,
        isSeries: Boolean,
        seasons: List<Int>
    ) {
        val dialog = createStyledBottomSheetDialog()
        val bBinding = DialogBatchDownloadSelectorBinding.inflate(layoutInflater)
        dialog.setContentView(bBinding.root)

        bBinding.tvBatchTitle.text = title
        bBinding.tvBatchSubtitle.text = subtitle

        bBinding.btnStartBatchDownload.setOnClickListener {
            val preferredResolution = when (bBinding.rgBatchQualities.checkedRadioButtonId) {
                R.id.rbQuality1080 -> "1080"
                R.id.rbQuality480 -> "480"
                else -> "720"
            }

            bBinding.batchLoading.isVisible = true
            bBinding.btnStartBatchDownload.isEnabled = false

            lifecycleScope.launch {
                try {
                    val tasksToEnqueue = mutableListOf<DownloadTask>()
                    for (seasonNum in seasons) {
                        val episodes = when (activeEngine) {
                            "almasmovie" -> AlmasMovieApi.getEpisodes(movieId, seasonNum)
                            "nextmovie" -> NextMovieApi.getEpisodes(movieId, seasonNum)
                            "bj" -> BjApi.getEpisodes(movieId, seasonNum)
                            else -> movielixApi.getEpisodes(movieId, seasonNum)
                        }

                        for (ep in episodes) {
                            val chosenQuality = ep.qualities.find { it.type.contains(preferredResolution) || it.title.contains(preferredResolution) }
                                ?: ep.qualities.firstOrNull()

                            if (chosenQuality != null) {
                                val streamUrl = if (!chosenQuality.directUrl.isNullOrEmpty()) {
                                    chosenQuality.directUrl
                                } else {
                                    movielixApi.getStreamUrl(
                                        id = movieId,
                                        qualityId = chosenQuality.id,
                                        season = seasonNum,
                                        episode = ep.episode
                                    )
                                }

                                if (!streamUrl.isNullOrEmpty()) {
                                    val safeFileName = "${subtitle.replace("سریال «", "").replace("»", "")} S${seasonNum}E${ep.episode} - ${chosenQuality.title}.mp4"
                                        .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                    val targetFile = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                        "Filigram/$safeFileName"
                                    )
                                    targetFile.parentFile?.mkdirs()

                                    tasksToEnqueue.add(
                                        DownloadTask(
                                            id = java.util.UUID.randomUUID().toString(),
                                            mediaId = movieId,
                                            title = "${subtitle.replace("سریال «", "").replace("»", "")} S${seasonNum}E${ep.episode} - ${chosenQuality.title}",
                                            seriesTitle = subtitle,
                                            season = seasonNum,
                                            episode = ep.episode,
                                            qualityLabel = chosenQuality.title,
                                            url = streamUrl,
                                            filePath = targetFile.absolutePath,
                                            partsCount = DownloadManager.settings.threadsPerTask,
                                            status = DownloadStatus.QUEUED
                                        )
                                    )
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        bBinding.batchLoading.isVisible = false
                        dialog.dismiss()
                        if (tasksToEnqueue.isNotEmpty()) {
                            DownloadManager.enqueueBatch(this@MainActivity, tasksToEnqueue)
                            DownloadForegroundService.startService(this@MainActivity)
                            showAppToast("${tasksToEnqueue.size} قسمت به صف دانلود توربو اضافه شد", autoDismissMs = 3500L)
                            showDownloadsHubDialog()
                        } else {
                            showAppToast("لینکی برای دانلود قسمت‌ها پیدا نشد")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        bBinding.batchLoading.isVisible = false
                        bBinding.btnStartBatchDownload.isEnabled = true
                        showAppToast("خطا در پردازش قسمت‌ها: ${e.message}")
                    }
                }
            }
        }

        dialog.show()
    }
}


