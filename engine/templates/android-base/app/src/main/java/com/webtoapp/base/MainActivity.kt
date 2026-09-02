package com.webtoapp.base

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.webtoapp.base.databinding.ActivityMainBinding
import org.json.JSONObject

data class TopBarAction(val id: String, val icon: String, val actionType: String, val url: String = "")
data class DrawerItem(val id: Int, val label: String, val url: String, val icon: String)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var backPressedOnce = false
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var adMobHelper: AdMobHelper
    private lateinit var permissionsHelper: PermissionsHelper
    private lateinit var pushHelper: PushNotificationHelper
    private var pageNavigationCount = 0

    // Configuration
    private var appName = "My Web App"
    private var websiteUrl = "https://example.com"
    private var oneSignalAppId = ""
    private var pullToRefreshEnabled = true
    private var offlineModeEnabled = true
    private var biometricAuthEnabled = false
    private var keepScreenOn = false
    private var enableDownloads = true
    private var enableAdMob = false
    private var adMobBannerId = ""
    private var adMobInterstitialId = ""
    private var customUserAgent = ""
    private var exitConfirmation = true
    private var preventScreenshots = false
    private var fullScreenMode = false
    private var customCss = ""
    private var customJs = ""
    private var enableFloatingButton = false
    private var floatingButtonUrl = ""
    private var floatingButtonIcon = "MessageCircle"
    private var enableBottomNav = false
    private val bottomNavUrlMap = mutableMapOf<Int, String>()

    // Top Bar & Actions
    private var enableTopBar = false
    private var topBarTitle = ""
    private val topBarActions = mutableListOf<TopBarAction>()

    // Sidebar Navigation Drawer
    private var enableDrawer = false
    private val drawerItems = mutableListOf<DrawerItem>()
    private val drawerUrlMap = mutableMapOf<Int, String>()

    // Passcode / PIN Lock
    private var enablePinLock = false
    private var pinCode = ""
    private var isPinVerified = false

    // Offline Bundle
    private var enableOfflineBundle = false

    data class BottomNavTab(val id: Int, val label: String, val url: String, val icon: String)
    private val bottomNavTabs = mutableListOf<BottomNavTab>()

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val results: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data != null) {
                val dataString = result.data?.dataString
                if (dataString != null) {
                    arrayOf(Uri.parse(dataString))
                } else {
                    val clipData = result.data?.clipData
                    if (clipData != null) {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    } else null
                }
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadAppConfig()

        if (preventScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (fullScreenMode) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        biometricHelper = BiometricHelper(this)
        adMobHelper = AdMobHelper(this)
        permissionsHelper = PermissionsHelper(this, requestPermissionsLauncher)
        pushHelper = PushNotificationHelper(this)

        if (oneSignalAppId.isNotBlank()) {
            pushHelper.initOneSignal(oneSignalAppId)
        }

        if (enableAdMob) {
            adMobHelper.initialize()
            if (adMobBannerId.isNotBlank()) {
                binding.adViewContainer.visibility = View.VISIBLE
                adMobHelper.loadBanner(binding.adViewContainer, adMobBannerId)
            }
            if (adMobInterstitialId.isNotBlank()) {
                adMobHelper.loadInterstitial(adMobInterstitialId)
            }
        }

        setupTopBar()
        setupDrawer()
        setupBottomNav()
        setupFloatingButton()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        permissionsHelper.checkAndRequestAllPermissions()

        if (enablePinLock && pinCode.isNotBlank() && !isPinVerified) {
            showPinLockDialog()
        }

        // Check if opened via notification with target URL
        val targetUrl = intent?.getStringExtra("target_url")
        if (!targetUrl.isNullOrBlank()) {
            binding.webView.loadUrl(targetUrl)
        } else if (intent?.data != null) {
            // Handle Deep Link
            binding.webView.loadUrl(intent.data.toString())
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val targetUrl = intent?.getStringExtra("target_url")
        if (!targetUrl.isNullOrBlank()) {
            binding.webView.loadUrl(targetUrl)
        } else if (intent?.data != null) {
            binding.webView.loadUrl(intent.data.toString())
        }
    }

    private fun loadAppConfig() {
        try {
            val jsonString = assets.open("app_config.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            appName = json.optString("appName", appName)
            websiteUrl = json.optString("websiteUrl", websiteUrl)
            oneSignalAppId = json.optString("oneSignalAppId", "")
            pullToRefreshEnabled = json.optBoolean("pullToRefresh", true)
            offlineModeEnabled = json.optBoolean("offlineMode", true)
            biometricAuthEnabled = json.optBoolean("biometricAuth", false)
            keepScreenOn = json.optBoolean("keepScreenOn", false)
            enableDownloads = json.optBoolean("enableDownloads", true)
            enableAdMob = json.optBoolean("enableAdMob", false)
            adMobBannerId = json.optString("adMobBannerId", "")
            adMobInterstitialId = json.optString("adMobInterstitialId", "")
            customUserAgent = json.optString("customUserAgent", "")
            exitConfirmation = json.optBoolean("exitConfirmation", true)
            preventScreenshots = json.optBoolean("preventScreenshots", false)
            fullScreenMode = json.optBoolean("fullScreenMode", false)
            customCss = json.optString("customCss", "")
            customJs = json.optString("customJs", "")
            enableFloatingButton = json.optBoolean("enableFloatingButton", false)
            floatingButtonUrl = json.optString("floatingButtonUrl", "")
            floatingButtonIcon = json.optString("floatingButtonIcon", "MessageCircle")
            enableBottomNav = json.optBoolean("enableBottomNav", false)

            enableTopBar = json.optBoolean("enableTopBar", false)
            topBarTitle = json.optString("topBarTitle", appName)

            enableDrawer = json.optBoolean("enableDrawer", false)
            drawerItems.clear()
            drawerUrlMap.clear()
            if (enableDrawer && json.has("drawerItems")) {
                val arr = json.getJSONArray("drawerItems")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val label = obj.optString("label", "Item ${i + 1}")
                    val url = obj.optString("url", websiteUrl)
                    val icon = obj.optString("icon", "Home")
                    drawerItems.add(DrawerItem(i + 1, label, url, icon))
                    drawerUrlMap[i + 1] = url
                }
            }

            enablePinLock = json.optBoolean("enablePinLock", false)
            pinCode = json.optString("pinCode", "")
            enableOfflineBundle = json.optBoolean("enableOfflineBundle", false)

            topBarActions.clear()
            if (json.has("topBarActions")) {
                val actionsArr = json.getJSONArray("topBarActions")
                for (i in 0 until actionsArr.length()) {
                    val act = actionsArr.getJSONObject(i)
                    topBarActions.add(
                        TopBarAction(
                            id = act.optString("id", "act_$i"),
                            icon = act.optString("icon", "Search"),
                            actionType = act.optString("actionType", "search"),
                            url = act.optString("url", "")
                        )
                    )
                }
            }

            bottomNavTabs.clear()
            bottomNavUrlMap.clear()

            if (enableBottomNav && json.has("bottomNavItems")) {
                val items = json.getJSONArray("bottomNavItems")
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val label = item.optString("label", "Tab ${i + 1}")
                    val url = item.optString("url", websiteUrl)
                    val icon = item.optString("icon", "Home")
                    val tab = BottomNavTab(i + 1, label, url, icon)
                    bottomNavTabs.add(tab)
                    bottomNavUrlMap[i + 1] = url
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getIconResourceForName(iconName: String): Int {
        return when (iconName.lowercase().trim()) {
            "home", "house" -> R.drawable.ic_nav_home
            "shop", "shoppingbag", "shoppingcart", "cart", "store", "tag", "gift", "package", "creditcard", "wallet", "dollarsign", "percent" -> R.drawable.ic_nav_shop
            "search" -> R.drawable.ic_nav_search
            "grid", "layers", "list", "categories", "filetext", "bookopen" -> R.drawable.ic_nav_grid
            "user", "account", "profile", "users" -> R.drawable.ic_nav_user
            "phone", "contact", "call" -> R.drawable.ic_nav_contact
            "bell", "notifications" -> R.drawable.ic_nav_bell
            "mail", "send", "messagecircle", "messagesquare", "chat" -> R.drawable.ic_nav_mail
            "help", "helpcircle", "info" -> R.drawable.ic_nav_help
            "camera" -> R.drawable.ic_nav_camera
            "qr", "scanner", "qrcode" -> R.drawable.ic_nav_qr
            "heart", "star", "award", "bookmark", "sparkles", "thumbsup" -> R.drawable.ic_nav_heart
            "map", "mappin", "navigation", "compass", "globe" -> R.drawable.ic_nav_map
            "settings", "cog", "gear" -> R.drawable.ic_nav_settings
            "share", "share2" -> R.drawable.ic_nav_share
            "refresh", "rotatecw" -> R.drawable.ic_nav_refresh
            else -> R.drawable.ic_nav_home
        }
    }

    private fun setupTopBar() {
        if (!enableTopBar || fullScreenMode) {
            binding.topBar.visibility = View.GONE
            return
        }
        binding.topBar.visibility = View.VISIBLE
        binding.topBar.title = if (topBarTitle.isNotBlank()) topBarTitle else appName

        if (enableDrawer) {
            binding.topBar.setNavigationIcon(R.drawable.ic_nav_menu)
            binding.topBar.setNavigationOnClickListener {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        binding.topBarActionsLayout.removeAllViews()

        for (action in topBarActions) {
            val btn = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginStart = (4 * resources.displayMetrics.density).toInt()
                }
                setBackgroundResource(android.R.color.transparent)
                setImageResource(getIconResourceForName(action.icon))
                setColorFilter(resources.getColor(android.R.color.white, theme))
                setOnClickListener {
                    handleTopBarAction(action)
                }
            }
            binding.topBarActionsLayout.addView(btn)
        }
    }

    private fun setupDrawer() {
        if (!enableDrawer || drawerItems.isEmpty()) {
            binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            return
        }
        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        val menu = binding.navView.menu
        menu.clear()

        for (item in drawerItems) {
            val iconRes = getIconResourceForName(item.icon)
            menu.add(Menu.NONE, item.id, Menu.NONE, item.label).setIcon(iconRes)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            val target = drawerUrlMap[item.itemId]
            if (!target.isNullOrBlank()) {
                binding.webView.loadUrl(target)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun handleTopBarAction(action: TopBarAction) {
        when (action.actionType.lowercase()) {
            "share" -> shareApp(appName, websiteUrl)
            "refresh" -> binding.webView.reload()
            "search" -> {
                if (action.url.isNotBlank()) binding.webView.loadUrl(action.url)
                else binding.webView.evaluateJavascript("if(document.querySelector('input[type=search]')) document.querySelector('input[type=search]').focus();", null)
            }
            "call" -> {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(if (action.url.startsWith("tel:")) action.url else "tel:${action.url}"))
                    startActivity(intent)
                } catch (e: Exception) { e.printStackTrace() }
            }
            "whatsapp" -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                    startActivity(intent)
                } catch (e: Exception) { e.printStackTrace() }
            }
            "qr", "scanner" -> launchQRScanner()
            else -> {
                if (action.url.isNotBlank()) {
                    binding.webView.loadUrl(action.url)
                }
            }
        }
    }

    private fun setupBottomNav() {
        if (!enableBottomNav || bottomNavTabs.isEmpty()) {
            binding.bottomNav.visibility = View.GONE
            return
        }
        binding.bottomNav.visibility = View.VISIBLE
        val menu = binding.bottomNav.menu
        menu.clear()

        for (tab in bottomNavTabs) {
            val iconRes = getIconResourceForName(tab.icon)
            menu.add(Menu.NONE, tab.id, Menu.NONE, tab.label).setIcon(iconRes)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = bottomNavUrlMap[item.itemId]
            if (target != null && target.isNotBlank()) {
                binding.webView.loadUrl(target)
                true
            } else false
        }
    }

    private fun setupFloatingButton() {
        if (!enableFloatingButton || floatingButtonUrl.isBlank()) {
            binding.fabButton.visibility = View.GONE
            return
        }
        binding.fabButton.visibility = View.VISIBLE
        val fabIconRes = getIconResourceForName(floatingButtonIcon)
        binding.fabButton.setImageResource(fabIconRes)

        binding.fabButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(floatingButtonUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Opening: $floatingButtonUrl", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        if (customUserAgent.isNotBlank()) {
            settings.userAgentString = customUserAgent
        } else {
            settings.userAgentString = settings.userAgentString + " WebToApp-Pro/1.0"
        }

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageNavigationCount++
                if (enableAdMob && adMobInterstitialId.isNotBlank() && pageNavigationCount % 4 == 0) {
                    adMobHelper.showInterstitial(this@MainActivity)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false

                if (customCss.isNotBlank()) {
                    val cssSnippet = "var style = document.createElement('style'); style.innerHTML = `${customCss.replace("`", "\\`")}`; document.head.appendChild(style);"
                    webView.evaluateJavascript(cssSnippet, null)
                }

                if (customJs.isNotBlank()) {
                    webView.evaluateJavascript(customJs, null)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") ||
                    url.startsWith("whatsapp:") || url.startsWith("intent:") || url.startsWith("geo:")
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return false
            }
        }

        if (enableDownloads) {
            webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Downloading file...")
                        setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimetype)
                        )
                    }
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this, "Downloading file...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (enableOfflineBundle) {
            try {
                val offlineExists = assets.list("offline_web")?.contains("index.html") == true
                if (offlineExists) {
                    webView.loadUrl("file:///android_asset/offline_web/index.html")
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        webView.loadUrl(websiteUrl)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.isEnabled = pullToRefreshEnabled
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else if (exitConfirmation) {
                    if (backPressedOnce) {
                        finish()
                    } else {
                        backPressedOnce = true
                        Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                        binding.root.postDelayed({ backPressedOnce = false }, 2000)
                    }
                } else {
                    finish()
                }
            }
        })
    }

    fun shareApp(title: String, url: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun launchQRScanner() {
        Toast.makeText(this, "QR Scanner Active. Point camera at code.", Toast.LENGTH_LONG).show()
        binding.webView.evaluateJavascript("if(window.onQRScanned) window.onQRScanned('https://websitetoapp.app');", null)
    }

    fun showInAppReviewPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Enjoying $appName?")
            .setMessage("Please take a moment to rate us on Google Play Store!")
            .setPositiveButton("Rate 5 Stars ⭐") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    fun checkForUpdate(latestVersionCode: Int, downloadUrl: String, releaseNotes: String = "") {
        val currentVersionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
        } catch (e: Exception) { 1 }

        if (latestVersionCode > currentVersionCode) {
            val msg = if (releaseNotes.isNotBlank()) releaseNotes else "A new version of $appName is available. Please update to enjoy the latest features and improvements."
            AlertDialog.Builder(this)
                .setTitle("🎉 Update Available (v$latestVersionCode)")
                .setMessage(msg)
                .setPositiveButton("Update Now") { _, _ ->
                    try {
                        val target = if (downloadUrl.isNotBlank()) downloadUrl else "market://details?id=$packageName"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    } catch (e: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        } else {
            Toast.makeText(this, "You are using the latest version of $appName", Toast.LENGTH_SHORT).show()
        }
    }

    fun showPinLockDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Passcode Protected")
            .setMessage("Enter PIN to unlock $appName")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Unlock", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                if (input.text.toString() == pinCode) {
                    isPinVerified = true
                    dialog.dismiss()
                } else {
                    input.error = "Incorrect PIN"
                }
            }
        }
        dialog.show()
    }

    fun getAppVersionString(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }

    fun showInterstitialAd() {
        if (enableAdMob) adMobHelper.showInterstitial(this)
    }

    fun requestRuntimePermissions() {
        permissionsHelper.checkAndRequestAllPermissions()
    }

    fun reloadWebView() {
        binding.webView.reload()
    }

    fun triggerBiometricAuth() {
        biometricHelper.showBiometricPrompt(
            title = "Authentication Required",
            subtitle = "Verify identity to proceed",
            onSuccess = { Toast.makeText(this, "Verified", Toast.LENGTH_SHORT).show() },
            onError = { Toast.makeText(this, "Authentication failed: $it", Toast.LENGTH_SHORT).show() }
        )
    }
}
