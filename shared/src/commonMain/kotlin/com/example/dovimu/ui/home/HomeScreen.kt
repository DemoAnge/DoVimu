package com.example.dovimu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dovimu.data.InspectResponse
import com.example.dovimu.data.PlaylistEntry
import com.example.dovimu.data.QualityKind
import com.example.dovimu.data.QualityOption
import com.example.dovimu.data.sourceLabel
import com.example.dovimu.ui.components.EmptyState
import com.example.dovimu.ui.components.ErrorBanner
import com.example.dovimu.ui.components.MediaLine
import com.example.dovimu.ui.components.OfflineBanner
import com.example.dovimu.ui.components.Thumb
import com.example.dovimu.ui.theme.DoVimuColors
import com.example.dovimu.viewmodel.DoVimuViewModel
import com.example.dovimu.viewmodel.UiState

@Composable
fun HomeScreen(state: UiState, viewModel: DoVimuViewModel) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Descarga video o música", color = DoVimuColors.Mute, fontSize = 14.sp)
        }
        item {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Pega un enlace de YouTube, TikTok…") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let(viewModel::pasteUrl) }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Pegar")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DoVimuColors.Orange,
                    unfocusedBorderColor = DoVimuColors.Line,
                    focusedContainerColor = DoVimuColors.Surface,
                    unfocusedContainerColor = DoVimuColors.Surface,
                    cursorColor = DoVimuColors.Orange,
                ),
            )
        }
        item {
            Button(
                onClick = { viewModel.inspect() },
                enabled = !state.inspecting && state.url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DoVimuColors.Orange,
                    disabledContainerColor = DoVimuColors.Orange.copy(alpha = 0.4f),
                ),
            ) {
                if (state.inspecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Analizando…")
                } else {
                    Text("Analizar enlace", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        if (state.apiOnline == false) {
            item { OfflineBanner { viewModel.openSettings(true) } }
        }
        if (state.error != null && state.tab == com.example.dovimu.data.MainTab.Home) {
            item { ErrorBanner(state.error, viewModel::dismissError) }
        }
        val media = state.media
        if (media == null && !state.inspecting) {
            item {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Empieza con un enlace",
                    subtitle = "Pega la URL y elige calidad, como en SnapTube.",
                )
            }
        }
        if (media != null && media.isPlaylist) {
            item { PlaylistHeader(media) }
            items(media.entries, key = { it.mediaId + (it.url ?: "") }) { entry ->
                PlaylistRow(entry) { viewModel.inspectEntry(entry.url) }
            }
        } else if (media != null) {
            item { MediaHero(media) }
            if (!media.ffmpegAvailable) {
                item {
                    Text(
                        "FFmpeg no está en el servidor: verás menos calidades (suele quedar 360p y audio original).",
                        color = DoVimuColors.Mute,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                KindToggle(
                    kind = state.qualityKind,
                    videoCount = media.video.size,
                    audioCount = media.audio.size,
                    onSelect = viewModel::selectQualityKind,
                )
            }
            val qualities = if (state.qualityKind == QualityKind.Video) media.video else media.audio
            items(qualities, key = { it.id }) { quality ->
                QualityRow(quality) { viewModel.download(quality) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MediaHero(media: InspectResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DoVimuColors.Surface),
    ) {
        Box {
            Thumb(
                url = media.thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
            if (media.durationLabel != null) {
                Text(
                    media.durationLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                sourceLabel(media.source),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DoVimuColors.Orange)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                media.title,
                color = DoVimuColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(media.uploader, media.durationLabel).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, color = DoVimuColors.Mute, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun KindToggle(
    kind: QualityKind,
    videoCount: Int,
    audioCount: Int,
    onSelect: (QualityKind) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (videoCount > 0) {
            KindChip("Video", videoCount, kind == QualityKind.Video, Icons.Default.PlayArrow) {
                onSelect(QualityKind.Video)
            }
        }
        if (audioCount > 0) {
            KindChip("Música", audioCount, kind == QualityKind.Audio, Icons.Default.MusicNote) {
                onSelect(QualityKind.Audio)
            }
        }
    }
}

@Composable
private fun KindChip(label: String, count: Int, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label ($count)") },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = DoVimuColors.Orange,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = DoVimuColors.Surface2,
            labelColor = DoVimuColors.Text,
        ),
    )
}

@Composable
private fun QualityRow(quality: QualityOption, onDownload: () -> Unit) {
    val accent = if (quality.kind == "audio") DoVimuColors.Audio else DoVimuColors.Orange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (quality.recommended) accent.copy(alpha = 0.10f) else DoVimuColors.Surface)
            .then(
                if (quality.recommended) Modifier.border(1.5.dp, accent, RoundedCornerShape(16.dp))
                else Modifier,
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quality.label, color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (quality.recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mejor",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
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

@Composable
private fun PlaylistHeader(media: InspectResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DoVimuColors.Surface)
            .padding(14.dp),
    ) {
        Text("Lista de reproducción", color = DoVimuColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(media.title, color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("${media.entries.size} videos · toca uno para ver calidades", color = DoVimuColors.Mute, fontSize = 13.sp)
    }
}

@Composable
private fun PlaylistRow(entry: PlaylistEntry, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DoVimuColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        MediaLine(
            title = entry.title,
            subtitle = listOfNotNull(entry.uploader, entry.durationLabel).joinToString(" · "),
            thumbnail = entry.thumbnail,
        )
    }
}
