package com.macrotracker.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.SubScreenHeader
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ConsoleViewModel

/*
 * The console: a login shell on the dashboard server, in ~, as on the web (the terminal
 * button in its top bar). It is the dashboard's own console page (site/console.html) in a
 * WebView, so the phone gets a real terminal (xterm.js: colours, full-screen programs,
 * a key bar with Esc, Tab, Ctrl and the arrows) and the same shells: a job started at the
 * desk can be picked up here, and the other way round. Shells live in the bridge, not in
 * the screen, so leaving this does not end them; Hang up does.
 */

private val ConsoleBackground = Color(0xFF101010)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConsoleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
    val base by viewModel.dashboardUrl.collectAsState()
    val haptics = rememberHaptics()
    var web by remember { mutableStateOf<WebView?>(null) }
    // Reconnect after the page's renderer died builds a fresh WebView.
    var generation by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    val host = remember(base) { Uri.parse(base).host.orEmpty() }

    BackHandler { onNavigateBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ConsoleBackground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SubScreenHeader(
            title = "Console",
            subtitle = if (host.isBlank()) "Set your dashboard server in Settings" else "fredde@${host.substringBefore('.')} · a shell in ~",
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                IconButton(
                    onClick = {
                        haptics.tick()
                        val view = web
                        if (view != null && !rendererGone) {
                            view.reload()
                        } else {
                            rendererGone = false
                            generation++
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(AppIcons.Refresh, "Reconnect", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            },
        )
        if (base.isBlank()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("The console runs on your dashboard server.", color = TextSecondary, fontSize = 14.sp)
            }
        } else if (rendererGone) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("The console stopped. Reconnect to open it again.", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            key(generation) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(ConsoleBackground.toArgb())
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            webViewClient = object : WebViewClient() {
                                // The console stays in the app; a link printed in the shell opens outside.
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url
                                    if (url.host == host && url.path?.startsWith("/console") == true) return false
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                    return true
                                }

                                // A WebView renderer that dies takes the app with it unless it is let go here.
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    view.visibility = View.GONE
                                    rendererGone = true
                                    return true
                                }
                            }
                            loadUrl("${base.trimEnd('/')}/console.html")
                            web = this
                        }
                    },
                    // Destroyed once it is off the screen, never while it is still attached and drawing.
                    onRelease = { view ->
                        if (web === view) web = null
                        view.destroy()
                    },
                )
            }
        }
    }
}
