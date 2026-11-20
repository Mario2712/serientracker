package io.github.mario2712.serientracker.ui.tmdbsync

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mario2712.serientracker.data.remote.TmdbSearchResult
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import io.github.mario2712.serientracker.ui.components.Poster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSeriesScreen(
    onDone: () -> Unit,
    viewModel: MatchSeriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title.ifEmpty { "Zuordnen" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = { TextButton(onClick = { viewModel.skip(onDone) }) { Text("Überspringen") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Bei TMDB suchen") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.search() }) { Icon(Icons.Filled.Search, contentDescription = "Suchen") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (viewModel.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.results, key = { it.id }) { result ->
                    CandidateRow(result, enabled = !viewModel.busy) { viewModel.pick(result, onDone) }
                }
            }
        }
    }
}

@Composable
fun CandidateRow(result: TmdbSearchResult, enabled: Boolean = true, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(12.dp)) {
            Poster(result.posterUrl, Modifier.width(56.dp).height(84.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(result.name, style = MaterialTheme.typography.titleSmall)
                if (result.originalName.isNotBlank() && result.originalName != result.name) {
                    Text(result.originalName, style = MaterialTheme.typography.bodySmall)
                }
                result.year?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                result.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
