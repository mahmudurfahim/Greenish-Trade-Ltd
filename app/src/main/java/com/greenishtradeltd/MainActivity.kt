package com.greenishtradeltd

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    // ====== URLs ======
    private val base = "https://www.greenishtradeltd.com"
    private val homeUrl = "$base/"
    private val loginUrl = "$base/login"
    private val signupUrl = "$base/signup"
    private val contactUrl = "$base/contact-us"
    private val servicesUrl = "$base/services"
    private val productsUrl = "$base/products" // Change this if the products page URL is different
    // ==================

    private data class Tab(val id: Int, val title: String, val icon: Int, val url: String)

    private val tHome by lazy { Tab(1, "হোম", R.drawable.ic_nav_home, homeUrl) }
    private val tLogin by lazy { Tab(2, "লগইন", R.drawable.ic_nav_login, loginUrl) }
    private val tSignup by lazy { Tab(3, "নিবন্ধন", R.drawable.ic_nav_signup, signupUrl) }
    private val tContact by lazy { Tab(4, "যোগাযোগ", R.drawable.ic_nav_mail, contactUrl) }
    private val tServices by lazy { Tab(5, "সেবাসমূহ", R.drawable.ic_nav_services, servicesUrl) }
    private val tProducts by lazy { Tab(6, "পণ্যসমূহ", R.drawable.ic_nav_bag, productsUrl) }

    // Navigation items for visitors who are not signed in
    private val guestTabs by lazy { listOf(tHome, tLogin, tSignup, tContact) }

    // Navigation items for signed-in users
    private val memberTabs by lazy { listOf(tHome, tServices, tProducts, tContact) }

    private lateinit var webView: WebView
    private lateinit var bottomNav: BottomNavigationView
    private var currentTabs: List<Tab> = emptyList()
    private var loggedIn: Boolean? = null
    private var ignoreSelect = false
    private var lastBackPress = 0L
    private val handler = Handler(Looper.getMainLooper())

    // Checks whether the user is signed in (returns true or false)
    private val loginCheckJs = """
        (function() {
            try {
                if (/^\/(login|signup)/.test(location.pathname)) return false;
                var ls = false;
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i).toLowerCase();
                    if (/token|auth|session/.test(k)) ls = true;
                }
                var hasLogout = !!document.querySelector(
                    'a[href*="logout" i], button[class*="logout" i], [onclick*="logout" i]');
                var txt = /logout|sign out|log out|লগআউট|লগ আউট/i.test(document.body.innerText);
                return ls || hasLogout || txt;
            } catch (e) { return false; }
        })();
    """.trimIndent()

    // Fits the page to the screen and hides the floating download button
    private val injectedJs = """
        (function() {
            var m = document.querySelector('meta[name=viewport]');
            if (!m) {
                m = document.createElement('meta');
                m.name = 'viewport';
                document.head.appendChild(m);
            }
            m.setAttribute('content',
                'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no');

            if (!document.getElementById('gt-fix-style')) {
                var st = document.createElement('style');
                st.id = 'gt-fix-style';
                st.textContent =
                    'html, body { max-width: 100%; overflow-x: hidden !important; }' +
                    'img, video, iframe, canvas, svg { max-width: 100%; height: auto; }' +
                    'pre, code { white-space: pre-wrap; word-break: break-word; }' +
                    'td, th { word-break: break-word; }';
                document.head.appendChild(st);
            }

            function fitTables() {
                document.querySelectorAll('table').forEach(function(t) {
                    t.style.zoom = '';
                    var left = t.getBoundingClientRect().left;
                    var avail = window.innerWidth - Math.max(left, 0) - 20;
                    var w = t.scrollWidth;
                    if (avail > 0 && w > avail) {
                        t.style.zoom = (avail / w).toFixed(3);
                    }
                });
            }

            function hideFloatingButton() {
                document.querySelectorAll('body *').forEach(function(el) {
                    var s = getComputedStyle(el);
                    if (s.position !== 'fixed') return;
                    var r = el.getBoundingClientRect();
                    if (r.width === 0 || r.width > 100 || r.height > 100) return;
                    if (window.innerWidth - r.right > 100) return;
                    if (window.innerHeight - r.bottom > 160) return;
                    var t = (el.className + ' ' + el.id + ' ' +
                             (el.getAttribute('aria-label') || '') + ' ' +
                             el.innerHTML).toString().toLowerCase();
                    if (/download|install|pwa|arrow-down|chevron-down/.test(t)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });
            }

            var timer = null;
            function runAll() { fitTables(); hideFloatingButton(); }
            function scheduleRun() {
                clearTimeout(timer);
                timer = setTimeout(runAll, 300);
            }
            runAll();
            setTimeout(runAll, 1500);
            window.addEventListener('resize', scheduleRun);
            if (document.body) {
                new MutationObserver(scheduleRun)
                    .observe(document.body, { childList: true, subtree: true });
            }
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        bottomNav = BottomNavigationView(this).apply {
            setBackgroundColor(Color.parseColor("#43873A"))
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.argb(190, 255, 255, 255))
            )
            itemTextColor = itemIconTintList
            itemActiveIndicatorColor = ColorStateList.valueOf(Color.argb(70, 255, 255, 255))
            elevation = 0f
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(webView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottomNav, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)

        // Keep content below the status bar and the nav above the system navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            bottomNav.setPadding(0, 0, 0, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Tapping a nav item loads its page in the WebView
        bottomNav.setOnItemSelectedListener { item ->
            if (!ignoreSelect) {
                currentTabs.firstOrNull { it.id == item.itemId }?.let { webView.loadUrl(it.url) }
            }
            true
        }
        bottomNav.setOnItemReselectedListener { item ->
            currentTabs.firstOrNull { it.id == item.itemId }?.let { webView.loadUrl(it.url) }
        }

        buildNav(false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val host = request.url.host
                // Keep the site inside the app
                if (host == "www.greenishtradeltd.com" || host == "greenishtradeltd.com") {
                    return false
                }
                // Open external links (tel, mailto, WhatsApp, other sites) outside the app
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {
                }
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                syncSelection(url)
                handler.postDelayed({ checkLogin() }, 1200)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(injectedJs, null)
                checkLogin()
                handler.postDelayed({ checkLogin() }, 1500)
                syncSelection(url)
            }
        }

        // Back goes to the previous web page; on the first page, press twice to exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPress < 2000) {
                        finish()
                    } else {
                        lastBackPress = now
                        Toast.makeText(
                            this@MainActivity,
                            "বের হতে আবার ব্যাক চাপুন",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl(homeUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun checkLogin() {
        webView.evaluateJavascript(loginCheckJs) { result ->
            val now = result == "true"
            if (loggedIn != now) {
                buildNav(now)
                syncSelection(webView.url)
            }
        }
    }

    private fun buildNav(isLoggedIn: Boolean) {
        loggedIn = isLoggedIn
        currentTabs = if (isLoggedIn) memberTabs else guestTabs
        ignoreSelect = true
        bottomNav.menu.clear()
        currentTabs.forEachIndexed { index, tab ->
            bottomNav.menu.add(0, tab.id, index, tab.title).setIcon(tab.icon)
        }
        ignoreSelect = false
    }

    // Highlights the nav item that matches the current URL
    private fun syncSelection(url: String?) {
        if (url == null) return
        val path = (Uri.parse(url).path ?: "").trimEnd('/')
        val id = when {
            path.isEmpty() -> tHome.id
            path.startsWith("/login") -> tLogin.id
            path.startsWith("/signup") -> tSignup.id
            path.startsWith("/contact-us") -> tContact.id
            path.startsWith("/services") -> tServices.id
            path.startsWith(Uri.parse(productsUrl).path ?: "/products") -> tProducts.id
            else -> return
        }
        if (bottomNav.menu.findItem(id) != null && bottomNav.selectedItemId != id) {
            ignoreSelect = true
            bottomNav.selectedItemId = id
            ignoreSelect = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}