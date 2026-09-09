package com.dovimu.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dovimu.data.SearchResult
import com.dovimu.ui.components.EmptyState
import com.dovimu.ui.components.MediaLine
import com.dovimu.ui.theme.DoVimuColors
import com.dovimu.viewmodel.DoVimuViewModel
import com.dovimu.viewmodel.UiState

@Composable
fun SearchScreen(state: UiState, viewModel: DoVimuViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar canciones o videos") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DoVimuColors.Orange)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
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
        if (!state.searching && state.searchResults.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Busca en YouTube",
                    subtitle = "Escribe un tema y pulsa buscar en el teclado.",
                )
            }
        }
        items(state.searchResults, key = { it.mediaId + (it.url ?: "") }) { result ->
            SearchRow(result) { viewModel.openSearchResult(result) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SearchRow(result: SearchResult, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoVimuColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        MediaLine(
            title = result.title,
            subtitle = listOfNotNull(result.uploader, result.durationLabel).joinToString(" · "),
            thumbnail = result.thumbnail,
        )
    }
}
