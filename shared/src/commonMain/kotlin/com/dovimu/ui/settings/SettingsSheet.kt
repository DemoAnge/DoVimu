package com.dovimu.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dovimu.ui.theme.DoVimuColors
import com.dovimu.viewmodel.DoVimuViewModel
import com.dovimu.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: UiState, viewModel: DoVimuViewModel) {
    ModalBottomSheet(
        onDismissRequest = { viewModel.openSettings(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DoVimuColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Servidor API", color = DoVimuColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Emulador: http://10.0.2.2:8000  ·  Teléfono: http://IP-DE-TU-PC:8000",
                color = DoVimuColors.Mute,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("URL base") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DoVimuColors.Orange,
                    unfocusedBorderColor = DoVimuColors.Line,
                    cursorColor = DoVimuColors.Orange,
                ),
            )
            Spacer(Modifier.height(10.dp))
            val status = when (state.apiOnline) {
                true -> "Conectado · v${state.apiVersion ?: "?"} · FFmpeg ${if (state.ffmpeg) "sí" else "no"}"
                false -> "Sin conexión"
                null -> "Sin comprobar"
            }
            Text(status, color = if (state.apiOnline == true) DoVimuColors.Success else DoVimuColors.Mute, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::saveBaseUrl,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DoVimuColors.Orange),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (state.pinging) "Probando…" else "Guardar y probar")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::useEmulatorUrl,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Usar URL del emulador", color = DoVimuColors.Text)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
