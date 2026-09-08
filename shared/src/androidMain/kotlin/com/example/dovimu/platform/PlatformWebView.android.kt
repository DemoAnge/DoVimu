package com.example.dovimu.platform

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(
    loadUrl: String,
    loadId: Long,
    navigation: WebNavAction?,
    onNavigationHandled: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onCanGoBack: (Boolean) -> Unit,
    onCanGoForward: (Boolean) -> Unit,
    canGoBack: Boolean,
    extractId: Long,
    onExtractedUrl: (String) -> Unit,
    modifier: Modifier,
) {
    val lastLoadId = remember { longArrayOf(-1L) }
    val lastExtractId = remember { longArrayOf(-1L) }
    val webViewRef = remember { arrayOfNulls<WebView>(1) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val urlCallback = remember { arrayOf(onUrlChange) }
    val extractCallback = remember { arrayOf(onExtractedUrl) }
    urlCallback[0] = onUrlChange
    extractCallback[0] = onExtractedUrl

    AndroidView(
        modifier = modifier,
        factory = { context ->
            try {
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    val ua = settings.userAgentString.orEmpty()
                    if (ua.isNotBlank()) {
                        settings.userAgentString = ua.replace("; wv", "").replace("Version/4.0 ", "")
                    }
                    runCatching {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }.onFailure(CrashReporter::recordNonFatal)
                    addJavascriptInterface(
                        DovimuJsBridge(mainHandler) { url ->
                            runCatching { urlCallback[0](url) }.onFailure(CrashReporter::recordNonFatal)
                        },
                        "Dovimu",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val scheme = request.url?.scheme?.lowercase()
                            return !(scheme == "http" || scheme == "https")
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            runCatching {
                                url?.let(onUrlChange)
                                onCanGoBack(view.canGoBack())
                                onCanGoForward(view.canGoForward())
                            }.onFailure(CrashReporter::recordNonFatal)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            runCatching {
                                url?.let(onUrlChange)
                                view.title?.let(onTitleChange)
                                onCanGoBack(view.canGoBack())
                                onCanGoForward(view.canGoForward())
                                view.evaluateJavascript(PLAYER_HOOK_JS, null)
                            }.onFailure(CrashReporter::recordNonFatal)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            runCatching {
                                url?.let(onUrlChange)
                                onCanGoBack(view.canGoBack())
                                onCanGoForward(view.canGoForward())
                                view.evaluateJavascript(PLAYER_HOOK_JS, null)
                            }.onFailure(CrashReporter::recordNonFatal)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            runCatching { onProgress(newProgress) }
                        }

                        override fun onReceivedTitle(view: WebView, title: String?) {
                            runCatching { title?.let(onTitleChange) }
                        }
                    }
                    webViewRef[0] = this
                    CrashReporter.install(context)
                }
            } catch (error: Throwable) {
                CrashReporter.recordNonFatal(error)
                TextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    gravity = Gravity.CENTER
                    setPadding(48, 48, 48, 48)
                    setTextColor(Color.parseColor("#F5F5F7"))
                    text = "No se pudo iniciar el navegador:\n${error.toUserMessage()}"
                }
            }
        },
        update = { view ->
            val web = view as? WebView ?: return@AndroidView
            webViewRef[0] = web
            runCatching {
                if (lastLoadId[0] != loadId && loadUrl.isNotBlank()) {
                    lastLoadId[0] = loadId
                    web.loadUrl(loadUrl)
                }
                when (navigation) {
                    WebNavAction.Back -> if (web.canGoBack()) web.goBack()
                    WebNavAction.Forward -> if (web.canGoForward()) web.goForward()
                    WebNavAction.Reload -> web.reload()
                    null -> Unit
                }
                if (navigation != null) onNavigationHandled()
                if (extractId > 0 && lastExtractId[0] != extractId) {
                    lastExtractId[0] = extractId
                    web.evaluateJavascript(EXTRACT_VIDEO_JS) { raw ->
                        runCatching {
                            extractCallback[0](parseJsString(raw) ?: web.url.orEmpty())
                        }.onFailure(CrashReporter::recordNonFatal)
                    }
                }
            }.onFailure(CrashReporter::recordNonFatal)
        },
    )

    BackHandler(enabled = canGoBack) {
        webViewRef[0]?.goBack()
    }
}

private class DovimuJsBridge(
    private val handler: Handler,
    private val onVideo: (String) -> Unit,
) {
    @JavascriptInterface
    fun videoUrl(url: String) {
        if (url.isBlank()) return
        handler.post { onVideo(url) }
    }
}

private fun parseJsString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
        ?: raw.trim('"').replace("\\/", "/")
}

private const val EXTRACT_VIDEO_JS = """
(function(){
  function youtubeId() {
    try {
      var v = new URLSearchParams(location.search).get('v');
      if (v && v.length >= 11) return v;
    } catch (e) {}
    var path = location.pathname || '';
    var m = path.match(/\/(shorts|embed|live|v)\/([a-zA-Z0-9_-]{6,})/);
    if (m) return m[2];
    try {
      if (window.ytInitialPlayerResponse && ytInitialPlayerResponse.videoDetails && ytInitialPlayerResponse.videoDetails.videoId)
        return ytInitialPlayerResponse.videoDetails.videoId;
    } catch (e) {}
    try {
      var el = document.querySelector('[video-id], [videoid], ytd-watch-flexy, ytm-player-microformat-renderer');
      if (el) {
        var id = el.getAttribute('video-id') || el.getAttribute('videoid');
        if (id) return id;
      }
    } catch (e) {}
    try {
      if (window.ytplayer && ytplayer.config && ytplayer.config.args)
        return ytplayer.config.args.video_id || ytplayer.config.args.videoId;
    } catch (e) {}
    try {
      var html = document.documentElement.innerHTML || '';
      var mm = html.match(/"videoId":"([a-zA-Z0-9_-]{11})"/);
      if (mm) return mm[1];
    } catch (e) {}
    try {
      var c = document.querySelector('link[rel="canonical"]');
      if (c && c.href) {
        var u = new URL(c.href);
        var cv = u.searchParams.get('v');
        if (cv) return cv;
        var sm = u.pathname.match(/\/shorts\/([a-zA-Z0-9_-]+)/);
        if (sm) return sm[1];
      }
    } catch (e) {}
    return null;
  }
  var id = youtubeId();
  if (id) return 'https://www.youtube.com/watch?v=' + id;
  try {
    if (/tiktok\.com/.test(location.host) && /\/video\//.test(location.pathname))
      return location.href.split('?')[0];
  } catch (e) {}
  return location.href;
})();
"""

private const val PLAYER_HOOK_JS = """
(function(){
  function youtubeId() {
    try {
      var v = new URLSearchParams(location.search).get('v');
      if (v && v.length >= 11) return v;
    } catch (e) {}
    var path = location.pathname || '';
    var m = path.match(/\/(shorts|embed|live|v)\/([a-zA-Z0-9_-]{6,})/);
    if (m) return m[2];
    try {
      if (window.ytInitialPlayerResponse && ytInitialPlayerResponse.videoDetails && ytInitialPlayerResponse.videoDetails.videoId)
        return ytInitialPlayerResponse.videoDetails.videoId;
    } catch (e) {}
    try {
      var el = document.querySelector('[video-id], [videoid], ytd-watch-flexy');
      if (el) {
        var id = el.getAttribute('video-id') || el.getAttribute('videoid');
        if (id) return id;
      }
    } catch (e) {}
    try {
      var html = document.documentElement.innerHTML || '';
      var mm = html.match(/"videoId":"([a-zA-Z0-9_-]{11})"/);
      if (mm) return mm[1];
    } catch (e) {}
    return null;
  }
  function currentUrl() {
    var id = youtubeId();
    if (id) return 'https://www.youtube.com/watch?v=' + id;
    try {
      if (/tiktok\.com/.test(location.host) && /\/video\//.test(location.pathname))
        return location.href.split('?')[0];
    } catch (e) {}
    return location.href;
  }
  function skipAds() {
    var sels = [
      '.ytp-ad-skip-button',
      '.ytp-ad-skip-button-modern',
      '.ytp-skip-ad-button',
      '.ytp-ad-skip-button-container button',
      '.ytp-ad-skip-button-slot button',
      '.ytp-ad-overlay-close-button',
      'button.ytp-ad-skip-button-modern',
      '.ytm-skip'
    ];
    sels.forEach(function(s){
      document.querySelectorAll(s).forEach(function(b){ try { b.click(); } catch(e){} });
    });
    document.querySelectorAll('button, .ytp-ad-skip-button-text, .ytp-skip-ad-button__text').forEach(function(b){
      var t = (b.innerText || b.getAttribute('aria-label') || '').toLowerCase();
      if (t.indexOf('skip') >= 0 || t.indexOf('omitir') >= 0 || t.indexOf('saltar') >= 0) {
        try { b.click(); } catch(e){}
      }
    });
  }
  if (!window.__dovimuHooked) {
    window.__dovimuHooked = true;
    setInterval(function(){
      skipAds();
      try {
        var u = currentUrl();
        if (u && window.Dovimu) Dovimu.videoUrl(u);
      } catch(e){}
    }, 700);
  }
  skipAds();
  try {
    var u = currentUrl();
    if (u && window.Dovimu) Dovimu.videoUrl(u);
  } catch(e){}
})();
"""
