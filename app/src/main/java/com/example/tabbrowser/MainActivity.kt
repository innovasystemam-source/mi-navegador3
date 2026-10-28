package com.example.tabbrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Cada modo define el User-Agent y el ancho de viewport (en px CSS) que ve la web. */
enum class Mode(val label: String, val viewportWidth: Int?) {
    MOBILE("Móvil", null),
    TABLET("Tablet", 800),
    DESKTOP("Escritorio", 1280)
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var modeBtn: Button

    private val homeUrl = "https://duckduckgo.com"
    private var mode = Mode.TABLET

    private val baseUa: String by lazy {
        // Quita marcadores de WebView para que los sitios lo traten como Chrome normal
        WebSettings.getDefaultUserAgent(this)
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
    }

    private fun userAgentFor(m: Mode): String = when (m) {
        Mode.MOBILE -> baseUa
        Mode.TABLET -> baseUa.replace(" Mobile", "")
        Mode.DESKTOP -> {
            val chrome = Regex("Chrome/[\\d.]+").find(baseUa)?.value ?: "Chrome/120.0.0.0"
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        modeBtn = findViewById(R.id.modeBtn)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        mode = Mode.values().getOrElse(prefs.getInt("mode", Mode.TABLET.ordinal)) { Mode.TABLET }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                if (!urlBar.hasFocus()) urlBar.setText(url)
            }
            override fun onPageCommitVisible(view: WebView, url: String) = forceViewport()
            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.INVISIBLE
                forceViewport()
                if (!urlBar.hasFocus()) urlBar.setText(url)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }
        }

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load(urlBar.text.toString())
                urlBar.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(urlBar.windowToken, 0)
                true
            } else false
        }

        findViewById<Button>(R.id.backBtn).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<Button>(R.id.forwardBtn).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<Button>(R.id.reloadBtn).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.homeBtn).setOnClickListener { load(homeUrl) }
        modeBtn.setOnClickListener { showModeMenu() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        applyMode(reload = false)
        load(intent?.data?.toString() ?: homeUrl)
    }

    private fun showModeMenu() {
        val menu = PopupMenu(this, modeBtn)
        Mode.values().forEach { menu.menu.add(0, it.ordinal, it.ordinal, it.label) }
        menu.setOnMenuItemClickListener { item ->
            mode = Mode.values()[item.itemId]
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("mode", mode.ordinal).apply()
            applyMode(reload = true)
            true
        }
        menu.show()
    }

    private fun applyMode(reload: Boolean) {
        webView.settings.userAgentString = userAgentFor(mode)
        webView.settings.loadWithOverviewMode = mode != Mode.MOBILE
        modeBtn.text = mode.label
        if (reload) webView.reload()
    }

    /** Fuerza el ancho de viewport del modo elegido (tablet/escritorio). */
    private fun forceViewport() {
        val w = mode.viewportWidth ?: return
        webView.evaluateJavascript(
            """
            (function(){
              var m=document.querySelector('meta[name=viewport]');
              if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
              m.content='width=$w, initial-scale=0.1, minimum-scale=0.1, maximum-scale=5, user-scalable=yes';
            })();
            """.trimIndent(), null
        )
    }

    private fun load(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            !text.contains(" ") && text.contains(".") -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(text)
        }
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
