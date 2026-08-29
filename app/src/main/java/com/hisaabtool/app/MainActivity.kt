package com.hisaabtool.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var splashScreen: LinearLayout
    private lateinit var noInternetLayout: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnShare: ImageButton
    private lateinit var btnHome: Button
    private lateinit var btnYouTube: Button
    private lateinit var btnRate: Button
    private lateinit var adView: AdView
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK) {
                uploadMessage?.onReceiveValue(result.data?.data?.let { arrayOf(it) })
            } else {
                uploadMessage?.onReceiveValue(null)
            }
        } catch (e: Exception) {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // AdMob Initialize
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {}

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        splashScreen = findViewById(R.id.splashScreen)
        noInternetLayout = findViewById(R.id.noInternetLayout)
        btnRetry = findViewById(R.id.btnRetry)
        btnShare = findViewById(R.id.btnShare)
        btnHome = findViewById(R.id.btnHome)
        btnYouTube = findViewById(R.id.btnYouTube)
        btnRate = findViewById(R.id.btnRate)
        adView = findViewById(R.id.adView)

        // Load Ad
        try {
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } catch (e: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            try { splashScreen.visibility = View.GONE } catch (e: Exception) {}
        }, 2000)

        setupWebView()
        
        val intentData: Uri? = intent.data
        if (intentData != null) {
            webView.loadUrl(intentData.toString())
        } else {
            loadWebsite()
        }

        swipeRefresh.setOnRefreshListener { webView.reload() }
        btnRetry.setOnClickListener { loadWebsite() }
        
        btnShare.setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, "HisaabTool के कैलकुलेटर्स का उपयोग करें: ${webView.url}")
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } catch (e: Exception) {}
        }

        btnHome.setOnClickListener { webView.loadUrl("https://hisaabtool.blogspot.com/") }
        btnYouTube.setOnClickListener { webView.loadUrl("https://youtube.com/@rahul-t9j8b") }
        btnRate.setOnClickListener { showRatingDialog() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("HisaabTool")
                            .setMessage("क्या आप ऐप बंद करना चाहते हैं?")
                            .setPositiveButton("हाँ") { _, _ -> finish() }
                            .setNegativeButton("नहीं", null)
                            .show()
                    }
                } catch (e: Exception) { finish() }
            }
        })
    }

    private fun showRatingDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle("HisaabTool को रेट करें")
                .setMessage("क्या आपको हमारा ऐप और हमारे YouTube वीडियो पसंद आ रहे हैं? कृपया हमें 5-स्टार रेटिंग दें!")
                .setPositiveButton("अभी रेट करें") { _, _ ->
                    Toast.makeText(this, "धन्यवाद! ऐप के प्ले स्टोर पर आने के बाद यह रेटिंग पेज खोलेगा।", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("बाद में", null)
                .show()
        } catch (e: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (!isNetworkAvailable()) {
                    webView.visibility = View.GONE
                    noInternetLayout.visibility = View.VISIBLE
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("intent://") || url.startsWith("whatsapp://") || url.startsWith("tel:") || url.startsWith("mailto:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                try {
                    if (newProgress == 100) progressBar.visibility = View.GONE
                    else {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    }
                } catch (e: Exception) {}
            }
            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                try {
                    uploadMessage?.onReceiveValue(null)
                    uploadMessage = filePathCallback
                    fileChooserLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    })
                } catch (e: Exception) {}
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("Downloading file...")
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
                
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Downloading File...", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadWebsite() {
        swipeRefresh.isRefreshing = false
        if (isNetworkAvailable()) {
            noInternetLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            if (webView.url == null) webView.loadUrl("https://hisaabtool.blogspot.com/")
            else webView.reload()
        } else {
            webView.visibility = View.GONE
            noInternetLayout.visibility = View.VISIBLE
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true 
        }
    }
}
