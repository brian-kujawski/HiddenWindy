package com.hiddenwindy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiddenwindy.data.RulesBlock
import com.hiddenwindy.data.RulesRepository
import com.hiddenwindy.data.RulesSearchHit
import com.hiddenwindy.data.RulesSection

/**
 * Full-rulebook reference modal. Presents the bundled Hide & Seek rules
 * (`assets/rules.json`) with drill-down table-of-contents navigation and unified
 * keyword search across every section.
 *
 * Navigation model: [path] is the current drill-down stack of sections. Empty
 * means the home screen (search field + top-level table of contents). Tapping a
 * section pushes it; the back button pops. A non-empty search query replaces the
 * home table of contents with a flat results list.
 */
@Composable
fun RulesModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { RulesRepository(context) }
    val document = remember { repository.load() }

    val path: SnapshotStateList<RulesSection> = remember { emptyList<RulesSection>().toMutableStateList() }
    var query by remember { mutableStateOf("") }

    val current: RulesSection? = path.lastOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (current != null) {
                        IconButton(onClick = { path.removeAt(path.lastIndex) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                    Text(
                        text = current?.title ?: "Rules",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (current == null) {
                    HomeScreen(
                        sections = document.sections,
                        query = query,
                        onQueryChange = { query = it },
                        searchResults = { repository.search(query) },
                        attribution = document.attribution,
                        onOpenSection = { path.add(it) },
                        onOpenHit = { hit ->
                            query = ""
                            path.add(hit.section)
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SectionScreen(
                        section = current,
                        onOpenSection = { path.add(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    sections: List<RulesSection>,
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: () -> List<RulesSearchHit>,
    attribution: String,
    onOpenSection: (RulesSection) -> Unit,
    onOpenHit: (RulesSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            placeholder = { Text("Search the rules") },
        )

        Spacer(Modifier.height(8.dp))

        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            val results = searchResults()
            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matches for \u201c$trimmed\u201d",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results) { hit ->
                        SearchResultRow(hit = hit, query = trimmed, onClick = { onOpenHit(hit) })
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(sections) { index, section ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                    SectionRow(section = section, onClick = { onOpenSection(section) })
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionScreen(
    section: RulesSection,
    onOpenSection: (RulesSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(section.blocks) { block ->
            BlockView(block)
        }
        if (section.children.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SECTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(section.children) { child ->
                SectionRow(section = child, onClick = { onOpenSection(child) })
            }
        }
    }
}

@Composable
private fun SectionRow(section: RulesSection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (section.fromExpansion) {
            ExpansionTag()
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(hit: RulesSearchHit, query: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(
            text = hit.breadcrumb,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = highlight(hit.snippet, query),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BlockView(block: RulesBlock) {
    when (block) {
        is RulesBlock.Heading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.text,
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (block.fromExpansion) {
                    ExpansionTag(modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        is RulesBlock.Paragraph -> {
            Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is RulesBlock.Bullets -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEach { item ->
                    Row {
                        Text("\u2022  ", style = MaterialTheme.typography.bodyMedium)
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpansionTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = "EXPANSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Builds an annotated string with case-insensitive occurrences of [query] bolded. */
private fun highlight(text: String, query: String) = buildAnnotatedString {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var start = 0
    while (true) {
        val idx = lowerText.indexOf(lowerQuery, start)
        if (idx < 0) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}
