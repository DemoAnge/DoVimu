package com.example.dovimu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.dovimu.ui.theme.DoVimuColors

@Composable
fun Thumb(
    url: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.PlayArrow,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DoVimuColors.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(icon, contentDescription = null, tint = DoVimuColors.Mute, modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoVimuColors.Danger.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DoVimuColors.Danger)
        Spacer(Modifier.width(8.dp))
        Text(message, color = DoVimuColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("OK", color = DoVimuColors.Orange) }
    }
}

@Composable
fun OfflineBanner(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoVimuColors.Orange.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = DoVimuColors.Orange)
        Spacer(Modifier.width(8.dp))
        Text(
            "Sin conexión a la API. Enciéndela y revisa la URL.",
            color = DoVimuColors.Text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSettings) { Text("Ajustes", color = DoVimuColors.Orange) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = DoVimuColors.Mute, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = DoVimuColors.Mute, fontSize = 14.sp)
    }
}

@Composable
fun MediaLine(title: String, subtitle: String, thumbnail: String?, audio: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Thumb(
            url = thumbnail,
            modifier = Modifier.size(width = 88.dp, height = 56.dp),
            icon = if (audio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = DoVimuColors.Text,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = DoVimuColors.Mute, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun JobProgress(progress: Float, color: Color = DoVimuColors.Orange) {
    LinearProgressIndicator(
        progress = { (progress / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp)),
        color = color,
        trackColor = DoVimuColors.Line,
    )
}
