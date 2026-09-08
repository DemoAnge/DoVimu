package com.example.dovimu.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dovimu.data.DownloadItem
import com.example.dovimu.data.prettySpeed
import com.example.dovimu.data.statusLabel
import com.example.dovimu.ui.components.EmptyState
import com.example.dovimu.ui.components.JobProgress
import com.example.dovimu.ui.components.MediaLine
import com.example.dovimu.ui.theme.DoVimuColors
import com.example.dovimu.viewmodel.DoVimuViewModel
import com.example.dovimu.viewmodel.UiState

@Composable
fun DownloadsScreen(state: UiState, viewModel: DoVimuViewModel) {
    if (state.jobs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Download,
            title = "Sin descargas",
            subtitle = "Cuando elijas una calidad, el progreso sale aquí.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.jobs, key = { it.jobId }) { item ->
            DownloadCard(item, viewModel)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, viewModel: DoVimuViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DoVimuColors.Surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                MediaLine(
                    title = item.title,
                    subtitle = listOfNotNull(item.artist, item.album, item.qualityLabel).joinToString(" · "),
                    thumbnail = item.thumbnail,
                    audio = item.kind == "audio",
                )
            }
            IconButton(onClick = { viewModel.cancelJob(item) }) {
                Icon(Icons.Default.Close, contentDescription = "Quitar", tint = DoVimuColors.Mute)
            }
        }
        Spacer(Modifier.height(10.dp))
        val color = when (item.status) {
            "failed", "save_failed" -> DoVimuColors.Danger
            "saved" -> DoVimuColors.Success
            else -> DoVimuColors.Orange
        }
        if (item.status !in setOf("saved", "failed", "cancelled", "save_failed")) {
            JobProgress(item.progress, color)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLabel(item.status),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            val extra = buildList {
                if (item.status in setOf("downloading", "saving") && item.progress > 0f) {
                    add("${item.progress.toInt()}%")
                }
                prettySpeed(item.speedBytes)?.let(::add)
            }.joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(extra, color = DoVimuColors.Mute, fontSize = 12.sp)
            }
        }
        if (!item.error.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(item.error, color = DoVimuColors.Danger, fontSize = 12.sp)
        }
        if (item.status == "saved" && item.localUri != null) {
            TextButton(onClick = { viewModel.openLocalFile(item) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = DoVimuColors.Orange)
                Spacer(Modifier.width(6.dp))
                Text("Abrir archivo", color = DoVimuColors.Orange)
            }
        }
        if (item.status == "save_failed") {
            TextButton(onClick = { viewModel.retrySave(item) }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = DoVimuColors.Orange)
                Spacer(Modifier.width(6.dp))
                Text("Reintentar guardado", color = DoVimuColors.Orange)
            }
        }
    }
}
