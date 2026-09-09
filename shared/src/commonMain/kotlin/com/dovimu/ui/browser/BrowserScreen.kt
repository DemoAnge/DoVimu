package com.dovimu.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dovimu.data.QUICK_SITES
import com.dovimu.data.QuickSite
import com.dovimu.platform.PlatformWebView
import com.dovimu.ui.theme.DoVimuColors
import com.dovimu.viewmodel.DoVimuViewModel
import com.dovimu.viewmodel.UiState

@Composable
fun BrowserScreen(state: UiState, viewModel: DoVimuViewModel) {
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    val addressValue = if (editing) state.browserInput else state.browserCurrentUrl.ifBlank { state.browserInput }
    val showStart = state.browserHome

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = addressValue,
                onValueChange = {
                    editing = true
                    viewModel.onBrowserInput(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("YouTube, TikTok o un enlace") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        editing = false
                        focus.clearFocus()
                        viewModel.submitAddress()
                    },
                ),
                trailingIcon = {
                    if (addressValue.isNotBlank()) {
                        IconButton(onClick = {
                            editing = true
                            viewModel.onBrowserInput("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DoVimuColors.Orange,
                    unfocusedBorderColor = DoVimuColors.Mute.copy(alpha = 0.45f),
                    focusedContainerColor = DoVimuColors.Surface,
                    unfocusedContainerColor = DoVimuColors.Surface,
                    cursorColor = DoVimuColors.Orange,
                    unfocusedPlaceholderColor = DoVimuColors.Mute,
                ),
            )
            BrowserToolbar(state, viewModel) {
                editing = false
                focus.clearFocus()
            }
            if (state.browserProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { state.browserProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = DoVimuColors.Orange,
                    trackColor = DoVimuColors.Line,
                )
            }
            Box(Modifier.fillMaxSize()) {
                var webCreated by remember { mutableStateOf(false) }
                LaunchedEffect(showStart) {
                    if (!showStart) webCreated = true
                }
                if (webCreated) {
                    PlatformWebView(
                        loadUrl = state.loadUrl,
                        loadId = state.loadId,
                        navigation = state.webNav,
                        onNavigationHandled = viewModel::onWebNavHandled,
                        onUrlChange = {
                            viewModel.onBrowserUrlChange(it, updateInput = !editing)
                        },
                        onTitleChange = viewModel::onBrowserTitle,
                        onProgress = viewModel::onBrowserProgress,
                        onCanGoBack = viewModel::onCanGoBack,
                        onCanGoForward = viewModel::onCanGoForward,
                        canGoBack = state.canGoBack && !showStart,
                        extractId = state.extractId,
                        onExtractedUrl = viewModel::onExtractedUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (showStart) {
                    StartPage(viewModel)
                }
            }
        }
        if (!showStart && !state.qualitySheetOpen) {
            FloatingActionButton(
                onClick = viewModel::downloadCurrentPage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp),
                containerColor = DoVimuColors.Orange,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Download, contentDescription = "Descargar video en reproducción")
            }
        }
    }
}

@Composable
private fun BrowserToolbar(state: UiState, viewModel: DoVimuViewModel, onNavigate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { viewModel.browserBack(); onNavigate() },
            enabled = state.canGoBack || !state.browserHome,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = if (state.canGoBack || !state.browserHome) DoVimuColors.Text else DoVimuColors.Mute,
            )
        }
        IconButton(onClick = viewModel::browserForward, enabled = state.canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Adelante",
                tint = if (state.canGoForward) DoVimuColors.Text else DoVimuColors.Mute,
            )
        }
        IconButton(onClick = {
            onNavigate()
            viewModel.goBrowserHome()
        }) {
            Icon(Icons.Default.Home, contentDescription = "Inicio", tint = DoVimuColors.Text)
        }
        IconButton(onClick = viewModel::reloadPage, enabled = !state.browserHome) {
            Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = DoVimuColors.Text)
        }
        if (state.braveInstalled) {
            IconButton(onClick = viewModel::openInBrave) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "Abrir en Brave", tint = DoVimuColors.Orange)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StartPage(viewModel: DoVimuViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoVimuColors.Bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("Navega y descarga", color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Escribe youtube para ir a la web oficial, o elige un sitio.",
            color = DoVimuColors.Mute,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(QUICK_SITES, key = { it.name }) { site ->
                SiteTile(site) { viewModel.navigateTo(site.url) }
            }
        }
    }
}

@Composable
private fun SiteTile(site: QuickSite, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(site.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                site.name.take(1),
                color = if (site.name == "X") Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(site.name, color = DoVimuColors.Text, fontSize = 12.sp, maxLines = 1)
    }
}
