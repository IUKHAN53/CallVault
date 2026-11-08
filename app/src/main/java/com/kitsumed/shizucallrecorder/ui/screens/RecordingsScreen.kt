/*
 * CallVault (a FORK of ShizuCallRecorder: https://github.com/kitsumed/ShizuCallRecorder)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.system.shareApp
import com.kitsumed.shizucallrecorder.ui.viewmodels.PlayerState
import com.kitsumed.shizucallrecorder.ui.viewmodels.Recording
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingFilter
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsViewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Home tab: searchable, day-grouped list of recordings with play, share, call back and (bulk) delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    val visible by viewModel.visible.collectAsState()
    val player by viewModel.player.collectAsState()

    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var pendingDelete by remember { mutableStateOf<List<Recording>?>(null) }
    val selecting = selected.isNotEmpty()

    // Person "folder" currently opened (see personKey); null = main list.
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (openPerson == null) visible else visible.filter { personKey(it) == openPerson }

    // Drop selections that no longer exist, and leave a person folder once all its recordings are gone.
    LaunchedEffect(ui.all) {
        selected = selected intersect ui.all.map { it.uri }.toSet()
        if (openPerson != null && ui.all.none { personKey(it) == openPerson }) openPerson = null
    }
    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && openPerson != null) { openPerson = null }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = shown.map { it.uri }.toSet() },
                onShare = { shareRecordings(context, ui.all.filter { it.uri in selected }) },
                onDelete = { pendingDelete = ui.all.filter { it.uri in selected } },
            )
        } else if (openPerson != null) {
            val personRecordings = ui.all.filter { personKey(it) == openPerson }
            PersonHeader(
                title = personRecordings.firstOrNull()?.title.orEmpty(),
                count = personRecordings.size,
                totalBytes = personRecordings.sumOf { it.sizeBytes },
                onBack = { openPerson = null },
            )
        } else {
            Header(
                count = ui.all.size,
                totalBytes = ui.all.sumOf { it.sizeBytes },
                onRefresh = viewModel::refresh,
            )
        }

        if (ui.all.isNotEmpty()) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.recordings_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.recordings_clear_search))
                    }
                },
                singleLine = true,
                shape = CircleShape,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterOption(R.string.recordings_filter_all, RecordingFilter.ALL, ui.filter, viewModel::setFilter)
                FilterOption(R.string.recordings_filter_incoming, RecordingFilter.INCOMING, ui.filter, viewModel::setFilter)
                FilterOption(R.string.recordings_filter_outgoing, RecordingFilter.OUTGOING, ui.filter, viewModel::setFilter)
            }
        }

        when {
            ui.loading -> Unit
            ui.folderMissing -> EmptyState(Icons.Default.FolderOff, R.string.recordings_no_folder, R.string.recordings_no_folder_hint)
            ui.all.isEmpty() -> EmptyState(Icons.Default.MicNone, R.string.recordings_empty, R.string.recordings_empty_hint)
            shown.isEmpty() -> EmptyState(Icons.Default.Search, R.string.recordings_no_match, null)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                val entries = if (openPerson == null) groupByPerson(shown) else shown.map { ListEntry.Single(it) }
                entries.groupBy { dayLabel(context, it.latestMs) }.forEach { (day, items) ->
                    stickyHeader(key = "h_$day") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(items, key = { it.key }) { entry ->
                        if (entry is ListEntry.Folder) {
                            val uris = entry.items.map { it.uri }.toSet()
                            FolderRow(
                                folder = entry,
                                isPlaying = player.isPlaying && player.recording?.uri in uris,
                                isSelected = selected.containsAll(uris),
                                onClick = { if (selecting) selected = selected.toggleAll(uris) else openPerson = entry.person },
                                onLongClick = { selected = selected.toggleAll(uris) },
                            )
                            return@items
                        }
                        val r = (entry as ListEntry.Single).recording
                        RecordingRow(
                            recording = r,
                            isCurrent = player.recording?.uri == r.uri,
                            isPlaying = player.recording?.uri == r.uri && player.isPlaying,
                            isSelected = r.uri in selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) selected = selected.toggle(r.uri) else viewModel.playOrToggle(r)
                            },
                            onLongClick = { selected = selected.toggle(r.uri) },
                            onShare = { shareRecordings(context, listOf(r)) },
                            onCall = r.number?.let { n -> { dial(context, n) } },
                            onDelete = { pendingDelete = listOf(r) },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { items ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(pluralStringResource(R.plurals.recordings_delete_title, items.size, items.size)) },
            text = { Text(stringResource(R.string.recordings_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(items)
                    selected = emptySet()
                    pendingDelete = null
                }) { Text(stringResource(R.string.recordings_action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(count: Int, totalBytes: Long, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recordings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (count > 0) Text(
                pluralStringResource(R.plurals.recordings_summary, count, count, Formatter.formatShortFileSize(context, totalBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { context.shareApp() }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.settings_share_app)) }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.recordings_refresh)) }
    }
}

@Composable
private fun PersonHeader(title: String, count: Int, totalBytes: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recordings_back)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.recordings_summary, count, count, Formatter.formatShortFileSize(context, totalBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A person with 2+ recordings, collapsed into one row; tapping opens their recordings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: ListEntry.Folder, isPlaying: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val latest = folder.items.first()
    Row(
        Modifier.fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when { isSelected -> Icons.Default.Check; isPlaying -> Icons.Default.GraphicEq; else -> Icons.Default.Folder },
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(latest.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val totalMs = folder.items.sumOf { it.durationMs ?: 0L }
            val details = listOfNotNull(
                pluralStringResource(R.plurals.recordings_count, folder.items.size, folder.items.size),
                totalMs.takeIf { it > 0 }?.let { formatDuration(it) },
                stringResource(R.string.recordings_last, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(latest.modifiedMs))),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            folder.items.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(android.R.string.cancel)) }
        Text(stringResource(R.string.recordings_selected, count), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = stringResource(android.R.string.selectAll)) }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.recordings_action_share)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.recordings_action_delete)) }
    }
}

@Composable
private fun FilterOption(label: Int, value: RecordingFilter, current: RecordingFilter, onSelect: (RecordingFilter) -> Unit) {
    FilterChip(selected = current == value, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    recording: Recording,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onCall: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val rowColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isCurrent -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.background
    }
    Row(
        Modifier.fillMaxWidth().background(rowColor).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val incoming = recording.direction == CallDirection.INCOMING
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else if (incoming) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    isSelected -> Icons.Default.Check
                    isPlaying -> Icons.Default.GraphicEq
                    recording.direction == null -> Icons.Default.MicNone
                    incoming -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMade
                },
                contentDescription = when (recording.direction) {
                    CallDirection.INCOMING -> stringResource(R.string.recordings_filter_incoming)
                    CallDirection.OUTGOING -> stringResource(R.string.recordings_filter_outgoing)
                    null -> null
                },
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else if (incoming) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recording.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                if (recording.contactName != null) recording.number else null,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(recording.modifiedMs)),
                recording.durationMs?.let { formatDuration(it) },
                Formatter.formatShortFileSize(context, recording.sizeBytes),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selecting) Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.recordings_more)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.recordings_action_share)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuOpen = false; onShare() })
                if (onCall != null) DropdownMenuItem(text = { Text(stringResource(R.string.recordings_action_call)) }, leadingIcon = { Icon(Icons.Default.Call, null) }, onClick = { menuOpen = false; onCall() })
                DropdownMenuItem(text = { Text(stringResource(R.string.recordings_action_delete)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: Int, hint: Int?) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Mini player ────────────────────────────────────────────────────────────────────────

/** Persistent player shown above the bottom navigation while a recording is loaded. */
@Composable
fun MiniPlayer(state: PlayerState, viewModel: RecordingsViewModel) {
    val recording = state.recording ?: return
    var dragPos by remember(recording.uri) { mutableStateOf<Float?>(null) }
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(recording.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DateUtils.formatDateTime(LocalContext.current, recording.modifiedMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::stopPlayback) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.player_close)) }
            }
            val duration = state.durationMs.coerceAtLeast(1).toFloat()
            Slider(
                value = (dragPos ?: state.positionMs.toFloat()).coerceIn(0f, duration),
                onValueChange = { dragPos = it },
                onValueChangeFinished = { dragPos?.let { viewModel.seekTo(it.toInt()) }; dragPos = null },
                valueRange = 0f..duration,
                modifier = Modifier.padding(end = 8.dp).height(28.dp),
            )
            Row(Modifier.padding(end = 8.dp)) {
                Text(formatDuration((dragPos ?: state.positionMs.toFloat()).toLong()), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs.toLong()), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::cycleSpeed, modifier = Modifier.width(64.dp)) {
                    Text(if (state.speed % 1f == 0f) "${state.speed.toInt()}x" else "${state.speed}x")
                }
                IconButton(onClick = { viewModel.skip(-10_000) }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_back_10))
                }
                FilledIconButton(onClick = { viewModel.playOrToggle(recording) }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                    )
                }
                IconButton(onClick = { viewModel.skip(10_000) }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_forward_10))
                }
                Spacer(Modifier.width(64.dp))
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────────────

private fun Set<Uri>.toggle(uri: Uri) = if (uri in this) this - uri else this + uri
private fun Set<Uri>.toggleAll(uris: Set<Uri>) = if (containsAll(uris)) this - uris else this + uris

/** Row model for the main list: a single recording, or a person's recordings collapsed into a folder. */
private sealed interface ListEntry {
    val key: String
    val latestMs: Long

    data class Single(val recording: Recording) : ListEntry {
        override val key get() = recording.uri.toString()
        override val latestMs get() = recording.modifiedMs
    }

    /** [items] are newest first. */
    data class Folder(val person: String, val items: List<Recording>) : ListEntry {
        override val key get() = "p_$person"
        override val latestMs get() = items.first().modifiedMs
    }
}

/**
 * Identifies the person behind a recording: the last 10 digits of the number, so `+923001234567` and `03001234567` match.
 * Recordings without a usable number (anonymous / unknown template) never group.
 */
internal fun personKey(r: Recording): String? =
    r.number?.filter(Char::isDigit)?.takeLast(10)?.takeIf { it.length >= 7 }

/** Collapses people with 2+ recordings into one folder entry, placed at their latest recording. Input must be newest first. */
private fun groupByPerson(list: List<Recording>): List<ListEntry> =
    list.groupBy(::personKey)
        .flatMap { (person, items) ->
            if (person != null && items.size > 1) listOf(ListEntry.Folder(person, items)) else items.map { ListEntry.Single(it) }
        }
        .sortedByDescending { it.latestMs }

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun dayLabel(context: Context, ms: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(day, today)) return context.getString(R.string.recordings_today)
    if (sameDay(day, (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) })) return context.getString(R.string.recordings_yesterday)
    return DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
}

private fun shareRecordings(context: Context, items: List<Recording>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.apply {
        type = "audio/*"
        // ClipData carries the read grant for every URI to the receiving app.
        clipData = ClipData.newRawUri(null, uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    } catch (_: ActivityNotFoundException) { }
}
