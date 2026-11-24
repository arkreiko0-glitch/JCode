package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.BrowserRunTarget
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BuiltInBrowserPane(
    url: String,
    reloadNonce: Int,
    runTarget: BrowserRunTarget,
    consoleLogs: List<String>,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onSelectRunTarget: (BrowserRunTarget) -> Unit,
    onOpenInChrome: (String) -> Unit,
    onConsoleLog: (String) -> Unit,
    onClearConsole: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var urlDraft by remember(url) { mutableStateOf(url) }
    var desktopMode by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewRecreateNonce by remember { mutableIntStateOf(0) }
    var useSoftwareLayer by remember { mutableStateOf(false) }
    var lastLoadedKey by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(url, reloadNonce, webViewRef, webViewRecreateNonce) {
        val currentKey = url to reloadNonce
        val view = webViewRef
        if (view != null && url.isNotBlank() && lastLoadedKey != currentKey) {
            lastLoadedKey = currentKey
            view.loadUrl(url)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.panelBg)
            .border(0.5.dp, palette.border)
    ) {
        // Browser Header + Run Target Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.tabBarBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "PREVIEW",
                color = palette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            // Target Selector: Built-in vs Chrome
            BrowserRunTarget.entries.forEach { target ->
                val selected = target == runTarget
                Surface(
                    color = if (selected) palette.accent else palette.surfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clickable { onSelectRunTarget(target) }
                        .testTag("browser_target_${target.name.lowercase()}")
                ) {
                    Text(
                        text = if (target == BrowserRunTarget.BUILTIN_BROWSER) "Built-in" else "Chrome",
                        color = if (selected) palette.onAccent else palette.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(
                color = palette.surfaceElevated,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .clickable { onOpenInChrome(urlDraft.ifBlank { url }) }
                    .testTag("open_in_chrome_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open in Chrome", tint = palette.accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chrome", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Browser", tint = palette.textMuted, modifier = Modifier.size(16.dp))
            }
        }

        // URL Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.sidebarBg)
                .border(0.5.dp, palette.border)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webViewRef?.goBack() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { webViewRef?.goForward() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = palette.textMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = {
                webViewRef?.reload()
                onReload()
            }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.editorBg)
                    .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onNavigate(urlDraft) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browser_url_input")
                )
            }

            IconButton(
                onClick = {
                    desktopMode = !desktopMode
                    webViewRef?.settings?.apply {
                        useWideViewPort = desktopMode
                        loadWithOverviewMode = desktopMode
                        userAgentString = if (desktopMode) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
                        } else null
                    }
                    webViewRef?.reload()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (desktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                    contentDescription = "Toggle Desktop/Mobile Viewport",
                    tint = if (desktopMode) palette.accent else palette.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = { showConsole = !showConsole }, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Toggle Browser DevTools Console",
                    tint = if (showConsole) palette.accent else palette.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Live WebView Surface
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(webViewRecreateNonce) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            if (useSoftwareLayer) {
                                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                                settings.safeBrowsingEnabled = false
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                    if (!loadedUrl.isNullOrBlank()) {
                                        urlDraft = loadedUrl
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    onConsoleLog("[WARN] WebView renderer exited; recovering cleanly...")
                                    if (webViewRef == view) {
                                        webViewRef = null
                                    }
                                    useSoftwareLayer = true
                                    lastLoadedKey = null
                                    webViewRecreateNonce += 1
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    if (consoleMessage != null) {
                                        onConsoleLog("[${consoleMessage.messageLevel()}] L${consoleMessage.lineNumber()}: ${consoleMessage.message()}")
                                    }
                                    return true
                                }
                            }
                            webViewRef = this
                            if (url.isNotBlank()) {
                                lastLoadedKey = url to reloadNonce
                                loadUrl(url)
                            }
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    onRelease = { view ->
                        if (webViewRef == view) {
                            webViewRef = null
                        }
                        view.stopLoading()
                        view.removeAllViews()
                        view.destroy()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Collapsible DevTools Console Drawer
        AnimatedVisibility(visible = showConsole) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(palette.terminalBg)
                    .border(0.5.dp, palette.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.panelBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DEVTOOLS CONSOLE (${consoleLogs.size})", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Browser Console",
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClearConsole() }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(consoleLogs) { log ->
                        val color = if (log.startsWith("[ERROR]")) palette.errorColor else palette.terminalFg
                        Text(log, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = color)
                    }
                }
            }
        }
    }
}
