package com.filigram.cinema

import android.content.Context
import android.content.Intent
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
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.button.MaterialButton
import com.filigram.cinema.databinding.BottomSheetAboutBinding
import com.filigram.cinema.databinding.BottomSheetEnginesDrawerBinding
import com.filigram.cinema.databinding.BottomSheetRadarBinding
import com.filigram.cinema.databinding.DialogAnnouncementsBinding
import com.filigram.cinema.databinding.DialogFavoritesBinding
import com.filigram.cinema.databinding.DialogInAppPlayerBinding
import com.filigram.cinema.databinding.DialogMovieDetailBinding
import com.filigram.cinema.databinding.DialogPlaylistDetailBinding
import com.filigram.cinema.databinding.DialogPlaylistsHubBinding
import com.filigram.cinema.databinding.ItemQualityRowBinding
import com.filigram.cinema.databinding.ItemRadarServiceRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
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

    // Active Engine: "movielix" (default), "rezflix", "almasmovie", "nextmovie"
    private var activeEngine = "movielix"
    private var activeExoPlayer: ExoPlayer? = null

    // -- Export/Import launchers (SAF) --
    private var pendingExportJson: String = ""
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    // Notification Permission Launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "مجوز اعلان فعال شد 🔔", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "برای دریافت اعلان قسمت‌های جدید به مجوز نوتیفیکیشن نیاز است", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Vitrin Pagination state
    private var vitrinPage = 1
    private var isVitrinLoadingMore = false
    private var hasMoreVitrin = true

    // Grid Pagination state
    private var gridPage = 1
    private var isGridLoadingMore = false
    private var hasMoreGrid = true
    private var currentGridType = 0 // 0 = movie, 1 = series, 2 = search
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register SAF launchers for export / import
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null && pendingExportJson.isNotEmpty()) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(pendingExportJson.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "فایل با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "خطا در ذخیره فایل: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pendingExportJson = ""
                }
            }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }

        // Dark status & navigation bar
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        // Smooth window transitions
        window.setWindowAnimations(R.style.Anim_Filigram_Window)

        // Load saved engine
        val prefs = getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        activeEngine = prefs.getString("active_engine", "movielix") ?: "movielix"

        FavoritesManager.init(this)
        SeriesSubscriptionManager.init(this)
        SeriesNotificationHelper.createNotificationChannel(this)
        SeriesUpdateWorker.schedulePeriodicCheck(this)
        ImageLoader.init(this)
        AppCacheManager.init(this)
        movielixApi = MovielixApi(this)

        setupAdapters()
        setupBottomNav()
        setupSearch()
        setupTopMenu()
        setupSwipeRefresh()
        setupLogsListener()
        setupPaginationScrollListeners()

        handleSeriesNotificationIntent(intent)

        // Animate root in
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()

        // Load initial home vitrin
        loadHomeData()

        // Fetch announcements and update unread badge
        refreshAnnouncementsBadge()
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

    private fun handleSeriesNotificationIntent(intent: Intent?) {
        if (intent == null) return
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
                type = 1, // Series
                slug = slug
            )
            binding.root.postDelayed({
                showMovieDetail(movieItem)
            }, 350)
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Animation helpers
    // ───────────────────────────────────────────────────────────────

    /** Apply full-screen slide-up enter / slide-down exit to a Dialog window */
    private fun Dialog.applyFullscreenAnimation() {
        window?.setWindowAnimations(R.style.Anim_Filigram_Dialog_Fullscreen)
    }

    /** Apply compact slide-up enter / slide-down exit to a Dialog window */
    private fun Dialog.applyCompactAnimation() {
        window?.setWindowAnimations(R.style.Anim_Filigram_Dialog_Compact)
    }

    /** Ensure fullscreen dialog top bar safely clears camera cutouts, notches, and status bars */
    private fun applyDialogStatusBarInsets(topBar: View) {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fallbackStatusHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else (38 * resources.displayMetrics.density).toInt()
        val safeTop = maxOf(fallbackStatusHeight, (40 * resources.displayMetrics.density).toInt())
        val initialPaddingBottom = topBar.paddingBottom
        val initialPaddingStart = topBar.paddingStart
        val initialPaddingEnd = topBar.paddingEnd
        val extraSpacer = (8 * resources.displayMetrics.density).toInt()

        // Set safe padding immediately so content is never drawn under the notch
        topBar.setPaddingRelative(initialPaddingStart, safeTop + extraSpacer, initialPaddingEnd, initialPaddingBottom)

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val targetTop = maxOf(cutoutInsets.top, safeTop) + extraSpacer
            v.setPaddingRelative(initialPaddingStart, targetTop, initialPaddingEnd, initialPaddingBottom)
            insets
        }
    }

    /** Fade a View in from 0 to 1 with optional offset-Y slide */
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

    /** Cross-fade between two visible containers */
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
        // Vitrin Adapter
        vitrinAdapter = VitrinSectionAdapter(mutableListOf()) { item ->
            showMovieDetail(item)
        }
        binding.rvVitrinSections.layoutManager = LinearLayoutManager(this)
        binding.rvVitrinSections.adapter = vitrinAdapter

        // Grid Adapter for Movies / Series / Search
        gridAdapter = MovieCardAdapter(mutableListOf()) { item ->
            showMovieDetail(item)
        }
        val gridLm = GridLayoutManager(this, 3)
        binding.rvGridMovies.layoutManager = gridLm
        binding.rvGridMovies.adapter = gridAdapter

        // Logs Adapter
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
        binding.btnTopNotifications.setOnClickListener {
            showAnnouncementsDialog()
        }
        binding.btnTopMenu.setOnClickListener {
            showEnginesDrawer()
        }
    }

    private fun setupPaginationScrollListeners() {
        // 1. Vitrin Infinite Scroll
        binding.homeScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            if (currentTab == R.id.nav_home && hasMoreVitrin && !isVitrinLoadingMore && activeEngine == "movielix") {
                val totalContentHeight = v.getChildAt(0)?.measuredHeight ?: 0
                val scrollViewHeight = v.measuredHeight
                if (scrollY >= (totalContentHeight - scrollViewHeight - 400)) {
                    loadMoreVitrin()
                }
            }
        })

        // 2. Grid Infinite Scroll
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

    private fun showHomeScreen() {
        currentTab = R.id.nav_home
        binding.topTitle.text = if (activeEngine == "movielix") "فیلیگرام" else "فیلیگرام (${getEngineName(activeEngine)})"
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
        binding.btnTopSearch.setOnClickListener {
            val isVisible = binding.searchContainer.isVisible
            binding.searchContainer.isVisible = !isVisible
            if (!isVisible) {
                binding.etSearch.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        val doSearch = {
            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                initSearch(query)
            }
        }

        binding.btnSubmitSearch.setOnClickListener { doSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
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
                } else {
                    val (banners, sections) = movielixApi.getVitrinSections(1)
                    withContext(Dispatchers.Main) {
                        binding.mainProgressBar.isVisible = false
                        binding.swipeRefresh.isRefreshing = false

                        if (banners.isNotEmpty()) {
                            binding.bannerViewPager.isVisible = true
                            binding.bannerViewPager.adapter = HeroBannerAdapter(banners) { bannerItem ->
                                showMovieDetail(bannerItem)
                            }
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
                    Toast.makeText(this@MainActivity, "خطا در اتصال به شبکه", Toast.LENGTH_SHORT).show()
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
                val results = when (activeEngine) {
                    "almasmovie" -> AlmasMovieApi.search(query, 1)
                    "rezflix" -> RezFlixApi.search(query, 1)
                    "nextmovie" -> NextMovieApi.search(query, 1)
                    else -> movielixApi.search(query, type = 2, page = 1)
                }

                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    gridAdapter.updateData(results)
                    if (results.isEmpty()) {
                        hasMoreGrid = false
                        Toast.makeText(this@MainActivity, "موردی یافت نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.gridProgressBar.isVisible = false
                    AppLogger.e("MainActivity", "خطا در جستجو: ${e.message}")
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
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val detailBinding = DialogMovieDetailBinding.inflate(layoutInflater)
        dialog.setContentView(detailBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(detailBinding.topBarDetail)

        // Animate content in after open
        detailBinding.root.post {
            detailBinding.detailTitle.animateIn(durationMs = 300, startDelayMs = 60)
            detailBinding.detailPoster.animateIn(durationMs = 380, startDelayMs = 0, fromY = 20f)
        }

        detailBinding.btnBackDetail.setOnClickListener {
            dialog.dismiss()
        }

        // Favorites / Like state
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
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }

        // Add to Playlist click listeners
        detailBinding.btnAddPlaylistDetail.setOnClickListener {
            showAddToPlaylistDialog(item)
        }

        detailBinding.btnActionAddToPlaylist.setOnClickListener {
            showAddToPlaylistDialog(item)
        }

        // Series Notification Subscription tracking
        var latestKnownSeason = 1
        var latestKnownEpisode = 0
        var latestKnownEpTitle: String? = null

        fun updateSeriesNotificationUI() {
            val isSubscribed = SeriesSubscriptionManager.isSubscribed(item.id)
            if (isSubscribed) {
                detailBinding.btnNotifySeriesDetail.setImageResource(R.drawable.ic_bell_gold)
                detailBinding.btnNotifySeriesDetail.imageTintList = null
                detailBinding.btnToggleSeriesNotification.text = "خاموش کردن"
                detailBinding.btnToggleSeriesNotification.backgroundTintList = ColorStateList.valueOf(0x33FF4444.toInt())
                detailBinding.btnToggleSeriesNotification.setTextColor(0xFFFF6666.toInt())
                detailBinding.btnToggleSeriesNotification.strokeColor = ColorStateList.valueOf(0xFFFF4444.toInt())
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
            val msg = if (isNowSub) "اعلان قسمت‌های جدید «${item.title}» فعال شد 🔔" else "اعلان «${item.title}» خاموش شد"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
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

        loadEpisodesForSeason(selectedSeason)
    }

    private fun loadMovieQualities(detailBinding: DialogMovieDetailBinding, movieId: Int) {
        detailBinding.detailLoading.isVisible = true
        detailBinding.qualityListContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val qualities = when (activeEngine) {
                    "almasmovie" -> AlmasMovieApi.getQualities(movieId)
                    "nextmovie" -> NextMovieApi.getQualities(movieId)
                    else -> movielixApi.getQualities(movieId)
                }
                withContext(Dispatchers.Main) {
                    detailBinding.detailLoading.isVisible = false
                    populateQualities(detailBinding, movieId, qualities, isSeries = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    detailBinding.detailLoading.isVisible = false
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
        action: Int, // 1: In-App Player, 2: Download Direct, 3: External Player
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
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl))
                    startActivity(browserIntent)
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
            2 -> "دریافت لینک دانلود مستقیم..."
            else -> "آماده‌سازی پخش با پلیر جانبی..."
        }
        Toast.makeText(this, notice, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val streamUrl = movielixApi.getStreamUrl(
                    movieId,
                    qualityId,
                    season = if (isSeries) season else -1,
                    episode = if (isSeries) episode else -1
                )
                withContext(Dispatchers.Main) {
                    if (!streamUrl.isNullOrEmpty()) {
                        when (action) {
                            1 -> playVideoInApp(mediaTitle, qualityLabel, streamUrl)
                            2 -> {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
                                startActivity(browserIntent)
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
                        Toast.makeText(this@MainActivity, "خطا در استخراج آدرس رسانه", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AppLogger.e("MainActivity", "خطا در استخراج آدرس: ${e.message}")
                    Toast.makeText(this@MainActivity, "خطا در برقراری ارتباط با سرور", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playVideoInApp(title: String, quality: String, streamUrl: String) {
        try {
            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.applyFullscreenAnimation()
            val playerBinding = DialogInAppPlayerBinding.inflate(LayoutInflater.from(this))
            dialog.setContentView(playerBinding.root)
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)

            playerBinding.txtPlayerTitle.text = title
            playerBinding.txtPlayerQuality.text = quality
            playerBinding.playerLoading.visibility = View.VISIBLE

            activeExoPlayer?.release()

            val player = ExoPlayer.Builder(this).build().apply {
                val mediaItem = MediaItem.fromUri(streamUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                playerBinding.playerLoading.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                playerBinding.playerLoading.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                playerBinding.playerLoading.visibility = View.GONE
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerBinding.playerLoading.visibility = View.GONE
                        AppLogger.e("InAppPlayer", "خطا در پخش استریم: ${error.message}")
                        Toast.makeText(this@MainActivity, "خطا در پخش آنلاین ویدیو", Toast.LENGTH_LONG).show()
                    }
                })
            }
            activeExoPlayer = player
            playerBinding.playerView.player = player

            playerBinding.btnClosePlayer.setOnClickListener {
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                player.stop()
                player.release()
                if (activeExoPlayer == player) {
                    activeExoPlayer = null
                }
            }

            dialog.show()
            AppLogger.i("InAppPlayer", "استریم ویدیو آغاز شد: $title")
        } catch (e: Exception) {
            AppLogger.e("InAppPlayer", "خطا در ایجاد پلیر: ${e.message}")
            Toast.makeText(this, "امکان پخش آنلاین در این دستگاه وجود ندارد: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEnginesDrawer() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val drawerBinding = BottomSheetEnginesDrawerBinding.inflate(layoutInflater)
        dialog.setContentView(drawerBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        applyDialogStatusBarInsets(drawerBinding.topBarEnginesDrawer)

        drawerBinding.tvCurrentEngineSub.text = "موتور فعال: ${getEngineName(activeEngine)}"

        drawerBinding.btnDrawerClose.setOnClickListener {
            dialog.dismiss()
        }

        when (activeEngine) {
            "movielix" -> drawerBinding.rbMovielix.isChecked = true
            "rezflix" -> drawerBinding.rbRezFlix.isChecked = true
            "almasmovie" -> drawerBinding.rbAlmasMovie.isChecked = true
            "nextmovie" -> drawerBinding.rbNextMovie.isChecked = true
        }

        drawerBinding.cardOpenRadar.setOnClickListener {
            dialog.dismiss()
            showSystemRadarDialog()
        }

        // Contact Support Link
        drawerBinding.cardDrawerTelegram.setOnClickListener {
            val tgUrl = "https://t.me/filigramapp"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl)))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "پشتیبانی در تلگرام: t.me/filigramapp", Toast.LENGTH_LONG).show()
            }
        }

        // Clear Cache
        val currentCacheSize = AppCacheManager.getCacheSizeBytes(this@MainActivity)
        drawerBinding.txtCacheSizeSub.text = "حجم کش ذخیره‌شده: ${AppCacheManager.formatSize(currentCacheSize)}"
        drawerBinding.cardClearCache.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("پاکسازی حافظه موقت")
                .setMessage("آیا مایل به حذف تمام داده‌های موقت، تصاویر کش‌شده و پاسخ‌های سرور هستید؟")
                .setPositiveButton("پاکسازی کش") { _, _ ->
                    AppCacheManager.clearAll(this@MainActivity)
                    drawerBinding.txtCacheSizeSub.text = "حجم کش ذخیره‌شده: ۰ مگابایت"
                    Toast.makeText(this@MainActivity, "حافظه موقت با موفقیت پاکسازی شد", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        drawerBinding.btnApplyEngine.setOnClickListener {
            val selected = when (drawerBinding.rgEngines.checkedRadioButtonId) {
                R.id.rbRezFlix -> "rezflix"
                R.id.rbAlmasMovie -> "almasmovie"
                R.id.rbNextMovie -> "nextmovie"
                else -> "movielix"
            }

            activeEngine = selected
            getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("active_engine", activeEngine)
                .apply()

            AppLogger.i("MainActivity", "موتور فعال به $selected تغییر یافت")
            Toast.makeText(this, "موتور فعال: ${getEngineName(activeEngine)}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            showHomeScreen()
            loadHomeData()
        }

        dialog.show()
    }

    private fun showSystemRadarDialog() {
        val dialog = BottomSheetDialog(this)
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
        "rezflix" -> "رزفلیکس (RezFlix)"
        "almasmovie" -> "الماس مووی (AlmasMovie)"
        "nextmovie" -> "نکست مووی (NextMovie)"
        else -> "موویلیکس (Movielix)"
    }

    private fun showPlaylistsHubDialog(initialTab: Int = 0) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val hubBinding = DialogPlaylistsHubBinding.inflate(layoutInflater)
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
                val favAdapter = MovieCardAdapter(favs.toMutableList()) { item ->
                    showMovieDetail(item)
                }
                hubBinding.rvFavoritesHub.layoutManager = GridLayoutManager(this@MainActivity, 3)
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

        val selectTab = { isFav: Boolean ->
            hubBinding.tabFavorites.isSelected = isFav
            hubBinding.tabPlaylists.isSelected = !isFav
            hubBinding.containerFavorites.isVisible = isFav
            hubBinding.containerPlaylists.isVisible = !isFav

            val mutedColor = ContextCompat.getColor(this@MainActivity, R.color.muted)

            if (isFav) {
                hubBinding.icTabFav.imageTintList = ColorStateList.valueOf(Color.WHITE)
                hubBinding.txtTabFav.setTextColor(Color.WHITE)
                hubBinding.badgeFavCount.setTextColor(Color.WHITE)
                hubBinding.badgeFavCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#35000000"))

                hubBinding.icTabPlaylists.imageTintList = ColorStateList.valueOf(mutedColor)
                hubBinding.txtTabPlaylists.setTextColor(mutedColor)
                hubBinding.badgePlaylistsCount.setTextColor(mutedColor)
                hubBinding.badgePlaylistsCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))
                loadFavorites()
            } else {
                hubBinding.icTabFav.imageTintList = ColorStateList.valueOf(mutedColor)
                hubBinding.txtTabFav.setTextColor(mutedColor)
                hubBinding.badgeFavCount.setTextColor(mutedColor)
                hubBinding.badgeFavCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FFFFFF"))

                hubBinding.icTabPlaylists.imageTintList = ColorStateList.valueOf(Color.WHITE)
                hubBinding.txtTabPlaylists.setTextColor(Color.WHITE)
                hubBinding.badgePlaylistsCount.setTextColor(Color.WHITE)
                hubBinding.badgePlaylistsCount.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#35000000"))
                loadPlaylists()
            }
        }

        hubBinding.tabFavorites.setOnClickListener { selectTab(true) }
        hubBinding.tabPlaylists.setOnClickListener { selectTab(false) }

        hubBinding.btnClearAllFavorites.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("حذف همه نشان‌ها")
                .setMessage("آیا از پاکسازی تمام فیلم‌ها و سریال‌های نشان‌شده اطمینان دارید؟")
                .setPositiveButton("حذف همه") { _, _ ->
                    FavoritesManager.clearAllFavorites(this@MainActivity)
                    loadFavorites()
                    Toast.makeText(this@MainActivity, "تمام نشان‌ها حذف شدند", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        hubBinding.btnClearAllPlaylists.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("حذف همه پلی‌لیست‌ها")
                .setMessage("آیا از حذف تمامی پلی‌لیست‌های اختصاصی خود اطمینان دارید؟ این عمل غیرقابل بازگشت است.")
                .setPositiveButton("حذف همه") { _, _ ->
                    PlaylistsManager.clearAllPlaylists(this@MainActivity)
                    loadPlaylists()
                    Toast.makeText(this@MainActivity, "تمامی پلی‌لیست‌ها حذف شدند", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // Export / Import — Favorites tab
        hubBinding.btnExportFavorites.setOnClickListener {
            exportCombinedBackup()
            Toast.makeText(this@MainActivity, "در حال آماده‌سازی فایل پشتیبان...", Toast.LENGTH_SHORT).show()
        }
        hubBinding.btnImportFavorites.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        // Export / Import — Playlists tab
        hubBinding.btnExportPlaylists.setOnClickListener {
            exportCombinedBackup()
            Toast.makeText(this@MainActivity, "در حال آماده‌سازی فایل پشتیبان...", Toast.LENGTH_SHORT).show()
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

        selectTab(initialTab == 0)

        dialog.show()
    }

    private fun showPlaylistDetailDialog(playlist: Playlist) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val plBinding = DialogPlaylistDetailBinding.inflate(layoutInflater)
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

        val movieAdapter = MovieCardAdapter(playlist.items.toMutableList()) { movie ->
            showMovieDetail(movie)
        }
        plBinding.rvPlaylistMovies.layoutManager = GridLayoutManager(this, 3)
        plBinding.rvPlaylistMovies.adapter = movieAdapter

        plBinding.btnPlaySequential.setOnClickListener {
            if (playlist.items.isNotEmpty()) {
                val firstMovie = playlist.items.first()
                Toast.makeText(this, "آغاز پخش ترتیبی پلی‌لیست: ${firstMovie.title}", Toast.LENGTH_SHORT).show()
                showMovieDetail(firstMovie)
            }
        }

        dialog.show()
    }

    private fun showFavoritesDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val favBinding = DialogFavoritesBinding.inflate(layoutInflater)
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
                val favAdapter = MovieCardAdapter(favs.toMutableList()) { item ->
                    showMovieDetail(item)
                }
                favBinding.rvFavorites.layoutManager = GridLayoutManager(this@MainActivity, 3)
                favBinding.rvFavorites.adapter = favAdapter
            }
        }

        favBinding.btnClearAllFavoritesStandalone.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("حذف همه نشان‌ها")
                .setMessage("آیا از پاکسازی تمام فیلم‌ها و سریال‌های نشان‌شده اطمینان دارید؟")
                .setPositiveButton("حذف همه") { _, _ ->
                    FavoritesManager.clearAllFavorites(this@MainActivity)
                    refreshList()
                    Toast.makeText(this@MainActivity, "تمام نشان‌ها حذف شدند", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        refreshList()
        dialog.show()
    }

    private fun showAboutDialog() {
        val dialog = BottomSheetDialog(this)
        val aboutBinding = BottomSheetAboutBinding.inflate(layoutInflater)
        dialog.setContentView(aboutBinding.root)

        aboutBinding.cardTelegramSupport.setOnClickListener {
            val tgUrl = "https://t.me/filigramapp"
            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl))
            try {
                startActivity(tgIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "کانال تلگرام: t.me/filigramapp", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    private fun showAddToPlaylistDialog(item: MovieItem) {
        val playlists = PlaylistsManager.getAllPlaylists(this@MainActivity)
        val dialog = Dialog(this@MainActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_glass)
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val headerText = TextView(this@MainActivity).apply {
            text = "افزودن به پلی‌لیست"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gold))
            textSize = 16f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad_bold)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        layout.addView(headerText)

        val subText = TextView(this@MainActivity).apply {
            text = "انتخاب پلی‌لیست برای «${item.title}»:"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted))
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad)
            val topMargin = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, topMargin)
        }
        layout.addView(subText)

        val scrollView = androidx.core.widget.NestedScrollView(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (200 * resources.displayMetrics.density).toInt()
            )
        }
        val listContainer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (pl in playlists) {
            val itemBtn = MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "${pl.title} (${pl.items.size} اثر)"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_playlist_vector)
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.gold))
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                setOnClickListener {
                    val added = PlaylistsManager.addItemToPlaylist(this@MainActivity, pl.id, item)
                    if (added) {
                        Toast.makeText(this@MainActivity, "«${item.title}» به پلی‌لیست «${pl.title}» افزوده شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "این اثر قبلاً در این پلی‌لیست قرار گرفته است", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
            listContainer.addView(itemBtn)
        }
        scrollView.addView(listContainer)
        layout.addView(scrollView)

        // Button to create a new custom playlist
        val btnCreatePl = MaterialButton(this@MainActivity).apply {
            text = "+ ایجاد پلی‌لیست جدید"
            setTextColor(Color.BLACK)
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.gold))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad_bold)
            cornerRadius = (20 * resources.displayMetrics.density).toInt()
            val mt = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, mt, 0, 0)
            }
            setOnClickListener {
                dialog.dismiss()
                showCreatePlaylistDialog(item)
            }
        }
        layout.addView(btnCreatePl)

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun showCreatePlaylistDialog(item: MovieItem? = null, onCreated: (() -> Unit)? = null) {
        val dialog = Dialog(this@MainActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_glass)
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val titleTv = TextView(this@MainActivity).apply {
            text = "ایجاد پلی‌لیست جدید"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gold))
            textSize = 15f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad_bold)
        }
        layout.addView(titleTv)

        val input = EditText(this@MainActivity).apply {
            hint = "نام پلی‌لیست (مثلاً: فیلم‌های آخر هفته)"
            setHintTextColor(Color.parseColor("#777777"))
            setTextColor(Color.WHITE)
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad)
            setBackgroundResource(R.drawable.bg_search_input)
            val p = (12 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            val mt = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, mt, 0, mt)
            }
        }
        layout.addView(input)

        val btnConfirm = MaterialButton(this@MainActivity).apply {
            text = if (item != null) "ایجاد و افزودن اثر" else "ایجاد پلی‌لیست"
            setTextColor(Color.BLACK)
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.gold))
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.estedad_bold)
            cornerRadius = (20 * resources.displayMetrics.density).toInt()
            setOnClickListener {
                val plName = input.text.toString().trim()
                if (plName.isNotEmpty()) {
                    val pl = PlaylistsManager.createPlaylist(this@MainActivity, plName, "پلی‌لیست اختصاصی کاربر")
                    if (item != null) {
                        PlaylistsManager.addItemToPlaylist(this@MainActivity, pl.id, item)
                        Toast.makeText(this@MainActivity, "پلی‌لیست «$plName» ایجاد و اثر به آن افزوده شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "پلی‌لیست «$plName» با موفقیت ایجاد شد", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                    onCreated?.invoke()
                } else {
                    Toast.makeText(this@MainActivity, "لطفاً نام پلی‌لیست را وارد کنید", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnConfirm)

        dialog.setContentView(layout)
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
            binding.txtNotificationBadge.isVisible = true
            binding.txtNotificationBadge.text = if (unreadCount > 9) "+9" else unreadCount.toString()
            if (!showDevLogs) {
                try {
                    val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_notifications)
                    badge.isVisible = true
                    badge.number = unreadCount
                    badge.backgroundColor = ContextCompat.getColor(this, R.color.gold)
                } catch (_: Exception) {}
            }
        } else {
            binding.txtNotificationBadge.isVisible = false
            try {
                binding.bottomNavigation.removeBadge(R.id.nav_notifications)
            } catch (_: Exception) {}
        }
    }

    private fun showAnnouncementsDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.applyFullscreenAnimation()
        val anBinding = DialogAnnouncementsBinding.inflate(layoutInflater)
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
            Toast.makeText(this@MainActivity, "تمامی اعلانات به عنوان خوانده شده علامت‌گذاری شدند", Toast.LENGTH_SHORT).show()
        }

        if (initialList.isEmpty()) {
            loadData()
        } else {
            updateHeaderBadge()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeExoPlayer?.release()
        activeExoPlayer = null
    }

    // ── Export / Import helpers ──────────────────────────────────────────────

    /** Call once from onCreate to register SAF launchers */
    fun registerDataLaunchers(
        onImportResult: (String) -> Unit,
        defaultExportName: String = "filigram_backup.json"
    ) {
        // (Unused overload kept for API clarity — actual launchers registered in onCreate)
    }

    /**
     * Export a JSON string to a user-chosen file via SAF.
     * [suggestedName] e.g. "filigram_likes_2026.json"
     */
    fun exportJsonToFile(json: String, suggestedName: String) {
        pendingExportJson = json
        exportLauncher.launch(suggestedName)
    }

    /**
     * Build and export a combined backup JSON:
     *   { "version": 1, "exported_at": "…", "favorites": […], "playlists": […] }
     */
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

    /** Import a combined backup JSON from a SAF-chosen file */
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

                // Import favorites
                val favsArr = if (root.has("favorites")) root.getJSONArray("favorites")
                              else JSONArray(jsonStr) // v1 compat: plain array of favorites
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

                // Import playlists (only in combined/v2 backup)
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
                    Toast.makeText(
                        this@MainActivity,
                        "وارد شد: $favImported نشان + $plImported پلی‌لیست",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "خطا در وارد کردن: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

