package com.aegispay.android.ui.docs

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aegispay.android.BuildConfig
import com.aegispay.android.ui.theme.AegisColor

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(
    onNavigateUp: () -> Unit,
    section:      String = "",
) {
    val docsUrl = remember(section) {
        val base = BuildConfig.WEB_BASE_URL.trimEnd('/')
        if (section.isNotBlank()) "$base/docs/$section" else "$base/docs"
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }
    var webView   by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text  = "Developer Docs",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text  = "Architecture · Flows · Patterns",
                            style = MaterialTheme.typography.bodySmall,
                            color = AegisColor.TextMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hasError  = false
                        isLoading = true
                        webView?.reload()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Background),
            )
        },
        containerColor = AegisColor.Background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (hasError) {
                // Error state
                Column(
                    modifier              = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.Center,
                ) {
                    Text(
                        text  = "📄",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "Couldn't load the docs",
                        style     = MaterialTheme.typography.titleMedium,
                        color     = AegisColor.Text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "Check your connection and try again.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = AegisColor.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            hasError  = false
                            isLoading = true
                            webView?.reload()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                    ) {
                        Text("Retry")
                    }
                }
            } else {
                // WebView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).also { wv ->
                            webView = wv
                            wv.settings.apply {
                                javaScriptEnabled       = true
                                domStorageEnabled        = true
                                loadWithOverviewMode     = true
                                useWideViewPort          = true
                                setSupportZoom(false)
                            }
                            wv.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        hasError  = true
                                    }
                                }
                            }
                            wv.loadUrl(docsUrl)
                        }
                    },
                )

                // Loading overlay
                AnimatedVisibility(
                    visible = isLoading,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(
                                color        = AegisColor.Primary,
                                strokeWidth  = 2.dp,
                                modifier     = Modifier.size(36.dp),
                            )
                            Text(
                                text  = "Loading docs…",
                                style = MaterialTheme.typography.bodySmall,
                                color = AegisColor.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
