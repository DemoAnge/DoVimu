package com.example.dovimu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dovimu.shared.generated.resources.Res
import dovimu.shared.generated.resources.dovimu
import org.jetbrains.compose.resources.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dovimu.data.MainTab
import com.example.dovimu.platform.AppErrors
import com.example.dovimu.platform.createPlatformServices
import com.example.dovimu.ui.browser.BrowserScreen
import com.example.dovimu.ui.components.ErrorBanner
import com.example.dovimu.ui.downloads.DownloadsScreen
import com.example.dovimu.ui.search.SearchScreen
import com.example.dovimu.ui.settings.SettingsSheet
import com.example.dovimu.ui.theme.DoVimuColors
import com.example.dovimu.ui.theme.DoVimuTheme
import com.example.dovimu.viewmodel.DoVimuViewModel
import com.example.dovimu.viewmodel.UiState

@Composable
@Preview
fun App() {
    DoVimuTheme {
        val viewModel: DoVimuViewModel = viewModel {
            DoVimuViewModel(createPlatformServices())
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        DoVimuApp(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoVimuApp(state: UiState, viewModel: DoVimuViewModel) {
    var lastCrash by remember { mutableStateOf(AppErrors.consumeFatal()) }
    lastCrash?.let { (message, detail) ->
        AlertDialog(
            onDismissRequest = { lastCrash = null },
            title = { Text("DoVimu se cerró") },
            text = {
                Text(
                    "La última vez la app se cerró por este error:\n\n$message\n\n${detail.take(500)}",
                )
            },
            confirmButton = {
                TextButton(onClick = { lastCrash = null }) { Text("Entendido") }
            },
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DoVimuColors.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(Res.drawable.dovimu),
                            contentDescription = "DoVimu",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Do", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Vimu", color = DoVimuColors.Orange, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.apiOnline) {
                                        true -> DoVimuColors.Success
                                        false -> DoVimuColors.Danger
                                        null -> DoVimuColors.Mute
                                    },
                                ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = DoVimuColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DoVimuColors.Bg),
            )
        },
        bottomBar = {
            ShortNavigationBar(containerColor = DoVimuColors.Surface) {
                NavItem(state.tab == MainTab.Home, Icons.Default.Home, "Inicio") {
                    viewModel.selectTab(MainTab.Home)
                }
                NavItem(state.tab == MainTab.Search, Icons.Default.Search, "Buscar") {
                    viewModel.selectTab(MainTab.Search)
                }
                NavItem(state.tab == MainTab.Downloads, Icons.Default.Download, "Descargas") {
                    viewModel.selectTab(MainTab.Downloads)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                MainTab.Home -> BrowserScreen(state, viewModel)
                MainTab.Search -> SearchScreen(state, viewModel)
                MainTab.Downloads -> DownloadsScreen(state, viewModel)
            }
            if (state.error != null && !state.qualitySheetOpen) {
                ErrorBanner(
                    message = state.error,
                    onDismiss = viewModel::dismissError,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (state.settingsOpen) {
        SettingsSheet(state, viewModel)
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ShortNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIconColor = DoVimuColors.Orange,
            selectedTextColor = DoVimuColors.Orange,
            selectedIndicatorColor = DoVimuColors.Orange.copy(alpha = 0.16f),
            unselectedIconColor = DoVimuColors.Mute,
            unselectedTextColor = DoVimuColors.Mute,
        ),
    )
}
