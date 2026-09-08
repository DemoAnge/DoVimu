package com.example.dovimu.ui.quality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dovimu.data.QualityKind
import com.example.dovimu.data.QualityOption
import com.example.dovimu.ui.theme.DoVimuColors
import com.example.dovimu.viewmodel.DoVimuViewModel
import com.example.dovimu.viewmodel.UiState

@Composable
fun DownloadFlowScreen(state: UiState, viewModel: DoVimuViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoVimuColors.Bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::backDownloadStep) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = DoVimuColors.Text)
            }
            Text(
                if (state.choosingKind || state.inspecting) "¿Qué quieres descargar?" else
                    if (state.qualityKind == QualityKind.Video) "Calidad de video" else "Calidad de música",
                color = DoVimuColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.inspecting) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = DoVimuColors.Orange, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("Leyendo el enlace…", color = DoVimuColors.Mute)
            }
        } else {
        val media = state.media
        if (media != null) {
            Text(
                media.title,
                color = DoVimuColors.Text,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(media.uploader, media.durationLabel).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = DoVimuColors.Mute, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            if (state.choosingKind) {
                KindCard(
                    title = "Video",
                    subtitle = if (media.video.isEmpty()) "No hay video en este enlace" else "${media.video.size} calidades",
                    icon = Icons.Default.PlayArrow,
                    enabled = media.video.isNotEmpty(),
                    accent = DoVimuColors.Orange,
                    onClick = { viewModel.selectQualityKind(QualityKind.Video) },
                )
                Spacer(Modifier.height(12.dp))
                KindCard(
                    title = "Música",
                    subtitle = if (media.audio.isEmpty()) "No hay audio en este enlace" else "${media.audio.size} calidades (MP3 / original)",
                    icon = Icons.Default.MusicNote,
                    enabled = media.audio.isNotEmpty(),
                    accent = DoVimuColors.Audio,
                    onClick = { viewModel.selectQualityKind(QualityKind.Audio) },
                )
            } else {
                val qualities = if (state.qualityKind == QualityKind.Video) media.video else media.audio
                if (qualities.isEmpty()) {
                    Text("No hay calidades para esta opción.", color = DoVimuColors.Mute)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(qualities, key = { it.id }) { quality ->
                            QualityRow(quality) { viewModel.download(quality) }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        } else if (state.error != null) {
            Text(state.error, color = DoVimuColors.Danger, modifier = Modifier.padding(vertical = 16.dp))
        }
        }
    }
}

@Composable
private fun KindCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) accent.copy(alpha = 0.16f) else DoVimuColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) accent else DoVimuColors.Mute, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = if (enabled) DoVimuColors.Text else DoVimuColors.Mute, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(subtitle, color = DoVimuColors.Mute, fontSize = 14.sp)
        }
    }
}

@Composable
fun QualityRow(quality: QualityOption, onDownload: () -> Unit) {
    val accent = if (quality.kind == "audio") DoVimuColors.Audio else DoVimuColors.Orange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DoVimuColors.Surface)
            .clickable(onClick = onDownload)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (quality.kind == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = accent,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(quality.label, color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (!quality.note.isNullOrBlank()) {
                Text(quality.note, color = DoVimuColors.Mute, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!quality.filesizeLabel.isNullOrBlank()) {
                Text(quality.filesizeLabel, color = DoVimuColors.Text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            Icon(Icons.Default.Download, contentDescription = "Descargar", tint = accent)
        }
    }
}
