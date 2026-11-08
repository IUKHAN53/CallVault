/*
 * CallVault (a FORK of ShizuCallRecorder: https://github.com/kitsumed/ShizuCallRecorder)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 */

package com.kitsumed.shizucallrecorder.ui.viewmodels

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One audio file in the recording folder. Number/direction are parsed from the default `{date}_{direction}_{phone_number}` name. */
data class Recording(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val direction: CallDirection?,
    val number: String?,
    val contactName: String? = null,
    val durationMs: Long? = null,
) {
    val title: String get() = contactName ?: number ?: fileName.substringBeforeLast('.')
}

enum class RecordingFilter { ALL, INCOMING, OUTGOING }

data class RecordingsUiState(
    val loading: Boolean = true,
    val folderMissing: Boolean = false,
    val all: List<Recording> = emptyList(),
    val query: String = "",
    val filter: RecordingFilter = RecordingFilter.ALL,
)

data class PlayerState(
    val recording: Recording? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1f,
)

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = AppPreferences(appContext)

    private val _ui = MutableStateFlow(RecordingsUiState())
    val ui: StateFlow<RecordingsUiState> = _ui.asStateFlow()

    /** [RecordingsUiState.all] narrowed by search query and direction filter. */
    val visible: StateFlow<List<Recording>> = _ui.map { s ->
        val q = s.query.trim()
        s.all.filter { r ->
            (s.filter == RecordingFilter.ALL ||
                (s.filter == RecordingFilter.INCOMING && r.direction == CallDirection.INCOMING) ||
                (s.filter == RecordingFilter.OUTGOING && r.direction == CallDirection.OUTGOING)) &&
                (q.isEmpty() || r.title.contains(q, ignoreCase = true) || r.number?.contains(q) == true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val nameCache = HashMap<String, String?>()
    private val durationCache = HashMap<String, Long?>()
    private var loadJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    init { refresh() }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setFilter(f: RecordingFilter) = _ui.update { it.copy(filter = f) }
    fun consumeMessage() { _message.value = null }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val folder = preferences.getRecordingFolderUri()
            if (folder == null) {
                _ui.update { it.copy(loading = false, folderMissing = true, all = emptyList()) }
                return@launch
            }
            val listed = runCatching { listAudio(folder) }.getOrElse {
                AppLogger.w("Failed to list recordings folder.", it)
                _ui.update { s -> s.copy(loading = false, folderMissing = true, all = emptyList()) }
                return@launch
            }.sortedByDescending { it.modifiedMs }
                .map { r -> r.copy(contactName = r.number?.let(::contactNameFor), durationMs = durationCache[cacheKey(r)]) }

            _ui.update { it.copy(loading = false, folderMissing = false, all = listed) }

            // Durations need a file open each; fill them in after the list is already visible.
            var changed = false
            for (r in listed) {
                if (!isActive) return@launch
                val key = cacheKey(r)
                if (!durationCache.containsKey(key)) { durationCache[key] = readDurationMs(r.uri); changed = true }
            }
            if (changed) _ui.update { s -> s.copy(all = s.all.map { it.copy(durationMs = durationCache[cacheKey(it)]) }) }
        }
    }

    fun delete(items: Collection<Recording>) {
        viewModelScope.launch {
            if (items.any { it.uri == _player.value.recording?.uri }) stopPlayback()
            val failed = withContext(Dispatchers.IO) {
                items.count { r -> !runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, r.uri) }.getOrDefault(false) }
            }
            if (failed > 0) _message.value = "Could not delete $failed recording(s)"
            val gone = items.map { it.uri }.toSet()
            _ui.update { s -> s.copy(all = s.all.filterNot { it.uri in gone }) }
        }
    }

    // ── Player ──────────────────────────────────────────────────────────────────────────

    /** Plays [r], or toggles play/pause if it is already loaded. */
    fun playOrToggle(r: Recording) {
        val mp = mediaPlayer
        if (mp != null && _player.value.recording?.uri == r.uri) {
            if (mp.isPlaying) pause() else resume()
            return
        }
        stopPlayback()
        val speed = _player.value.speed
        _player.value = PlayerState(recording = r, speed = speed, durationMs = r.durationMs?.toInt() ?: 0)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setOnPreparedListener {
                _player.update { s -> s.copy(durationMs = it.duration) }
                resume()
            }
            setOnCompletionListener {
                tickerJob?.cancel()
                _player.update { s -> s.copy(isPlaying = false, positionMs = s.durationMs) }
            }
            setOnErrorListener { _, what, extra ->
                AppLogger.w("MediaPlayer error what=$what extra=$extra")
                _message.value = "Can't play this recording"
                stopPlayback()
                true
            }
            try {
                setDataSource(appContext, r.uri)
                prepareAsync()
            } catch (e: Exception) {
                AppLogger.w("Failed to open recording for playback.", e)
                _message.value = "Can't open this recording"
                stopPlayback()
            }
        }
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        tickerJob?.cancel()
        _player.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        val mp = mediaPlayer ?: return
        if (_player.value.positionMs >= _player.value.durationMs && _player.value.durationMs > 0) mp.seekTo(0)
        mp.start()
        // Setting PlaybackParams on a paused player would auto-start it, so speed is only applied while playing.
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(_player.value.speed) }
        _player.update { it.copy(isPlaying = true) }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                mediaPlayer?.let { p -> _player.update { it.copy(positionMs = p.currentPosition) } }
                delay(200)
            }
        }
    }

    fun seekTo(ms: Int) {
        val mp = mediaPlayer ?: return
        val target = ms.coerceIn(0, _player.value.durationMs.coerceAtLeast(0))
        mp.seekTo(target)
        _player.update { it.copy(positionMs = target) }
    }

    fun skip(deltaMs: Int) = seekTo(_player.value.positionMs + deltaMs)

    fun cycleSpeed() {
        val next = when (_player.value.speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _player.update { it.copy(speed = next) }
        mediaPlayer?.takeIf { it.isPlaying }?.let { mp -> runCatching { mp.playbackParams = mp.playbackParams.setSpeed(next) } }
    }

    fun stopPlayback() {
        tickerJob?.cancel()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        _player.update { PlayerState(speed = it.speed) }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────

    private fun cacheKey(r: Recording) = "${r.uri}|${r.modifiedMs}|${r.sizeBytes}"

    /** Walks the SAF tree with one query per folder (much faster than DocumentFile.listFiles per-file lookups). */
    private fun listAudio(tree: Uri): List<Recording> {
        val out = ArrayList<Recording>()
        val dirs = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        while (dirs.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirs.removeLast())
            appContext.contentResolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) { dirs.add(id); continue }
                    if (!mime.startsWith("audio/") && name.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) continue
                    out += parseRecording(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.getLong(3), c.getLong(4))
                }
            }
        }
        return out
    }

    private fun contactNameFor(number: String): String? =
        nameCache.getOrPut(number) { runCatching { EnrichedCallData.getContactName(appContext, number) }.getOrNull() }

    private fun readDurationMs(uri: Uri): Long? = runCatching {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(appContext, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull()

    companion object {
        private val AUDIO_EXTENSIONS = setOf("ogg", "opus", "m4a", "aac", "mp3", "wav", "amr", "3gp", "flac")
        private val DIRECTION_REGEX = Regex("""_(in|out)_""")
        private val NUMBER_REGEX = Regex("""\+?\d{3,15}""")

        /** Parses `{date}_{direction}_{phone_number}`-style names; unknown templates just fall back to the file name as title. */
        internal fun parseRecording(uri: Uri, name: String, size: Long, modified: Long): Recording {
            val dir = DIRECTION_REGEX.find(name)
            val direction = when (dir?.groupValues?.get(1)) { "in" -> CallDirection.INCOMING; "out" -> CallDirection.OUTGOING; else -> null }
            // Only look for the number after the direction token, so the date digits are never mistaken for it.
            val number = dir?.let { NUMBER_REGEX.find(name.substring(it.range.last + 1).substringBeforeLast('.'))?.value }
            return Recording(uri, name, size, modified, direction, number)
        }
    }
}
