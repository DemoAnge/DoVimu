package com.dovimu.ui.quality

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dovimu.data.PlaylistEntry
import com.dovimu.data.QualityKind
import com.dovimu.data.QualityOption
import com.dovimu.ui.components.MediaLine
import com.dovimu.ui.theme.DoVimuColors
import com.dovimu.viewmodel.DoVimuViewModel
import com.dovimu.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFlowSheet(state: UiState, viewModel: DoVimuViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeQualitySheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DoVimuColors.Surface,
    ) {
        DownloadFlowContent(state, viewModel)
    }
}

@Composable
private fun DownloadFlowContent(state: UiState, viewModel: DoVimuViewModel) {
    val media = state.media
    var expanded by remember { mutableStateOf<QualityKind?>(null) }
    var startingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error, state.qualitySheetOpen) {
        if (state.error != null) startingId = null
    }
    LaunchedEffect(media?.mediaId, media?.webpageUrl, state.inspecting) {
        startingId = null
        expanded = when {
            state.inspecting || media == null || media.isPlaylist -> null
            media.video.isNotEmpty() && media.audio.isEmpty() -> QualityKind.Video
            media.audio.isNotEmpty() && media.video.isEmpty() -> QualityKind.Audio
            else -> null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "¿Qué quieres descargar?",
                color = DoVimuColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::closeQualitySheet) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = DoVimuColors.Mute)
            }
        }
        if (state.error != null) {
            Text(
                state.error,
                color = DoVimuColors.Danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (state.inspecting) {
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = DoVimuColors.Orange,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Leyendo el enlace…", color = DoVimuColors.Mute)
            }
            return@Column
        }
        if (media == null) {
            Spacer(Modifier.height(8.dp))
            return@Column
        }
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
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (media.isPlaylist) {
                item {
                    Text(
                        "${media.entries.size} videos · toca uno para ver calidades",
                        color = DoVimuColors.Mute,
                        fontSize = 13.sp,
                    )
                }
                items(media.entries, key = { it.mediaId + (it.url ?: it.title) }) { entry ->
                    PlaylistChoiceRow(entry) { viewModel.inspectEntry(entry.url) }
                }
            } else {
                if (!media.ffmpegAvailable) {
                    item {
                        Text(
                            "FFmpeg no está en el servidor: verás menos calidades.",
                            color = DoVimuColors.Mute,
                            fontSize = 12.sp,
                        )
                    }
                }
                item {
                    KindHeader(
                        title = "Video",
                        subtitle = if (media.video.isEmpty()) {
                            "No hay video en este enlace"
                        } else {
                            "${media.video.size} calidades"
                        },
                        icon = Icons.Default.PlayArrow,
                        enabled = media.video.isNotEmpty(),
                        expanded = expanded == QualityKind.Video,
                        accent = DoVimuColors.Orange,
                        onClick = {
                            expanded = if (expanded == QualityKind.Video) null else QualityKind.Video
                        },
                    )
                }
                if (expanded == QualityKind.Video) {
                    items(media.video, key = { "v-${it.id}" }) { quality ->
                        QualityRow(
                            quality = quality,
                            enabled = startingId == null,
                            starting = startingId == quality.id,
                        ) {
                            startingId = quality.id
                            viewModel.download(quality)
                        }
                    }
                }
                item {
                    KindHeader(
                        title = "Música",
                        subtitle = if (media.audio.isEmpty()) {
                            "No hay audio en este enlace"
                        } else {
                            "${media.audio.size} calidades (MP3 / original)"
                        },
                        icon = Icons.Default.MusicNote,
                        enabled = media.audio.isNotEmpty(),
                        expanded = expanded == QualityKind.Audio,
                        accent = DoVimuColors.Audio,
                        onClick = {
                            expanded = if (expanded == QualityKind.Audio) null else QualityKind.Audio
                        },
                    )
                }
                if (expanded == QualityKind.Audio) {
                    items(media.audio, key = { "a-${it.id}" }) { quality ->
                        QualityRow(
                            quality = quality,
                            enabled = startingId == null,
                            starting = startingId == quality.id,
                        ) {
                            startingId = quality.id
                            viewModel.download(quality)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    expanded: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "kindChevron")
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) accent.copy(alpha = 0.16f) else DoVimuColors.Bg,
            disabledContainerColor = DoVimuColors.Bg,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) accent else DoVimuColors.Mute,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) DoVimuColors.Text else DoVimuColors.Mute,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(subtitle, color = DoVimuColors.Mute, fontSize = 13.sp)
            }
            if (enabled) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Ocultar calidades" else "Ver calidades",
                    tint = accent,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun PlaylistChoiceRow(entry: PlaylistEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DoVimuColors.Bg),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaLine(
                title = entry.title,
                subtitle = listOfNotNull(entry.uploader, entry.durationLabel).joinToString(" · "),
                thumbnail = entry.thumbnail,
            )
        }
    }
}

@Composable
fun QualityRow(
    quality: QualityOption,
    enabled: Boolean = true,
    starting: Boolean = false,
    onDownload: () -> Unit,
) {
    val accent = if (quality.kind == "audio") DoVimuColors.Audio else DoVimuColors.Orange
    Card(
        onClick = onDownload,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (quality.recommended) Modifier.border(1.5.dp, accent, RoundedCornerShape(16.dp))
                else Modifier,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quality.recommended) accent.copy(alpha = 0.10f) else DoVimuColors.Bg,
            disabledContainerColor = DoVimuColors.Bg,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
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
                if (starting) {
                    Text("Iniciando descarga…", color = accent, fontSize = 12.sp)
                } else if (!quality.note.isNullOrBlank()) {
                    Text(quality.note, color = DoVimuColors.Mute, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!quality.filesizeLabel.isNullOrBlank()) {
                    Text(quality.filesizeLabel, color = DoVimuColors.Text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = "Descargar", tint = accent)
                }
            }
        }
    }
}
