package com.example.dovimu.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class WebNavAction { Back, Forward, Reload }

@Composable
expect fun PlatformWebView(
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
    modifier: Modifier = Modifier,
)
