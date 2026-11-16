package com.ms.videoeditor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures

private val Black = Color(0xFF050807)
private val Panel = Color(0xFF111816)
private val Panel2 = Color(0xFF19231F)
private val Green = Color(0xFF19F000)
private val Emerald = Color(0xFF00D889)
private val Gold = Color(0xFFFFD84A)
private val Muted = Color(0xFF9AA7A1)

private data class TimelineClip(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)
}

private data class AudioTimelineClip(
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val timelineStartMs: Long
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(1L)
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

private fun seekGlobalPosition(player: ExoPlayer, positionMs: Long, durations: List<Long>) {
    var remaining = positionMs.coerceAtLeast(0L)
    for (index in durations.indices) {
        val itemDuration = durations[index].coerceAtLeast(1L)
        if (remaining < itemDuration || index == durations.lastIndex) {
            player.seekTo(index, remaining.coerceIn(0L, itemDuration))
            return
        }
        remaining -= itemDuration
    }
    if (durations.isEmpty()) player.seekTo(positionMs.coerceAtLeast(0L))
}

private fun globalPlaybackPosition(player: ExoPlayer, durations: List<Long>): Long {
    val currentIndex = player.currentMediaItemIndex
    if (currentIndex < 0) return 0L
    return durations.take(currentIndex).sum() + player.currentPosition
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 8, 7)
        window.navigationBarColor = android.graphics.Color.rgb(5, 8, 7)
        setContent { MSVideoEditorApp() }
    }
}

@Composable
private fun MSVideoEditorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("ms_video_editor", android.content.Context.MODE_PRIVATE) }
    // Keep the active editing session when Android recreates the Activity after the document picker returns.
    var videoUri by remember {
        mutableStateOf(prefs.getString("active_video_uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() })
    }
    var videoUris by remember {
        mutableStateOf<List<Uri>>(
            prefs.getString("active_video_uris", null)
                ?.split("\n")
                ?.filter { value -> value.isNotBlank() }
                ?.mapNotNull { value -> runCatching { Uri.parse(value) }.getOrNull() }
                ?: emptyList<Uri>()
        )
    }
    var audioUri by remember {
        mutableStateOf(prefs.getString("active_audio_uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() })
    }
    var activeTool by remember { mutableStateOf("Trim") }
    var page by remember { mutableStateOf("Home") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var recentProjects by remember {
        mutableStateOf<List<Uri>>(
            prefs.getStringSet("recent_projects", emptySet())
                .orEmpty()
                .mapNotNull { value -> runCatching { Uri.parse(value) }.getOrNull() }
        )
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
            videoUris = listOf(uri)
            prefs.edit().putString("active_video_uri", uri.toString())
                .putString("active_video_uris", uri.toString())
                .putString("project_clips:${uri}", uri.toString()).apply()
            recentProjects = (listOf(uri) + recentProjects.filter { it != uri }).take(30)
            prefs.edit().putStringSet("recent_projects", recentProjects.map { it.toString() }.toSet()).apply()
        }
    }
    val multiVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val creatingProject = videoUri == null
            uris.forEach { selected ->
                try { context.contentResolver.takePersistableUriPermission(selected, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
            val merged: List<Uri> = (videoUris + uris).distinct()
            videoUris = merged
            if (videoUri == null) videoUri = merged.firstOrNull()
            // One gallery selection can add many full-length clips to the same project.
            val activeUri: Uri = videoUri ?: merged.first()
            val serializedUris: String = merged.joinToString(separator = "\n") { item: Uri -> item.toString() }
            prefs.edit().putString("active_video_uri", activeUri.toString())
                .putString("active_video_uris", serializedUris)
                .putString("project_clips:${activeUri}", serializedUris).apply()
            // Only the first gallery import creates a Home project. Later imports append clips.
            if (creatingProject) {
                recentProjects = (listOf(activeUri) + recentProjects.filter { it != activeUri }).take(30)
                prefs.edit().putStringSet("recent_projects", recentProjects.map { it.toString() }.toSet()).apply()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            audioUri = uri
            prefs.edit().putString("active_audio_uri", uri.toString()).apply()
        }
    }
    fun startProject(tool: String) {
        activeTool = tool
        multiVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    fun openSavedProject(projectUri: Uri) {
        val savedClips = prefs.getString("project_clips:${projectUri}", null)
            ?.split("\n")
            ?.filter { value -> value.isNotBlank() }
            ?.mapNotNull { value -> runCatching { Uri.parse(value) }.getOrNull() }
            .orEmpty()
        videoUri = projectUri
        videoUris = savedClips.ifEmpty { listOf(projectUri) }
        audioUri = null
        activeTool = "Trim"
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Emerald, secondary = Gold, background = Black, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            when {
                videoUri != null -> EditorScreen(
                    uri = videoUri!!,
                    videoUris = videoUris.ifEmpty { listOf(videoUri!!) },
                    audioUri = audioUri,
                    activeTool = activeTool,
                    onTool = { activeTool = it },
                    onBack = {
                        videoUri = null; videoUris = emptyList(); audioUri = null; page = "Home"
                        prefs.edit().remove("active_video_uri").remove("active_video_uris").remove("active_audio_uri").apply()
                    },
                    onAddMedia = { multiVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onAudioExtracted = { extracted ->
                        audioUri = extracted
                        prefs.edit().putString("active_audio_uri", extracted.toString()).apply()
                    },
                    onAudioRemoved = {
                        audioUri = null
                        prefs.edit().remove("active_audio_uri").apply()
                    },
                    onExport = { startMs, endMs ->
                        exportVideoSequence(context, videoUris.ifEmpty { listOf(videoUri!!) }, startMs, endMs) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onSplit = { startMs, splitMs, endMs ->
                        if (splitMs > startMs && endMs > splitMs) {
                            exportTrimmedVideo(context, videoUri!!, startMs, splitMs) { _, message ->
                                Toast.makeText(context, "Part 1: $message", Toast.LENGTH_LONG).show()
                            }
                            exportTrimmedVideo(context, videoUri!!, splitMs, endMs) { _, message ->
                                Toast.makeText(context, "Part 2: $message", Toast.LENGTH_LONG).show()
                            }
                        } else Toast.makeText(context, "Move the split point away from the ends.", Toast.LENGTH_SHORT).show()
                    }
                )
                page == "Home" -> HomeScreen(
                    projects = recentProjects,
                    onNew = { startProject("Trim") },
                    onTool = { startProject(it) },
                    onProject = { openSavedProject(it) },
                    onSettings = { dialog = "Settings" },
                    onPremium = { dialog = "MS Premium" },
                    onPage = { page = it }
                )
                else -> SimplePage(
                    page = page, projects = recentProjects,
                    onBack = { page = "Home" },
                    onNew = { startProject("Trim") },
                    onProject = { openSavedProject(it) },
                    onInfo = { dialog = it }
                )
            }
            if (dialog != null) {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    containerColor = Panel2,
                    titleContentColor = Color.White,
                    textContentColor = Muted,
                    title = { Text(dialog!!) },
                    text = { Text(if (dialog == "Settings") "MS Video Editor settings" else "Premium options will be added in a later version.") },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK", color = Emerald) } }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    projects: List<Uri>, onNew: () -> Unit, onTool: (String) -> Unit,
    onProject: (Uri) -> Unit, onSettings: () -> Unit, onPremium: () -> Unit,
    onPage: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp)) {
        Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MS", color = Green, fontSize = 25.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Video Editor", color = Color.White, fontSize = 20.sp)
                }
                Text("EDIT  •  CREATE  •  SHARE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
            IconButton(onClick = onPremium) { Icon(Icons.Default.WorkspacePremium, contentDescription = "Premium", tint = Gold) }
        }
        Box(Modifier.fillMaxWidth().height(142.dp).background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF22F000), Color(0xFF087A16), Color(0xFF031B0B))),
            RoundedCornerShape(18.dp)
        ).clickable { onNew() }, contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(5.dp))
                    Text("New Project", color = Color.White, fontSize = 20.sp)
                    Text("Start editing your video", color = Color.White.copy(alpha = .82f), fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(17.dp))
        val tools = listOf(
            Triple("Trim", Icons.Default.ContentCut, "Trim"),
            Triple("Merge", Icons.Default.Collections, "Merge"),
            Triple("Effects", Icons.Default.AutoAwesome, "Effects"),
            Triple("Filters", Icons.Default.Tune, "Filters"),
            Triple("Transitions", Icons.Default.CompareArrows, "Transitions"),
            Triple("Music", Icons.Default.MusicNote, "Audio"),
            Triple("Text", Icons.Default.Title, "Text"),
            Triple("Speed", Icons.Default.Speed, "Speed"),
            Triple("Export", Icons.Default.IosShare, "Trim")
        )
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            tools.chunked(3).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    line.forEach { item -> ToolTile(item.first, item.second, Modifier.weight(1f)) { onTool(item.third) } }
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Projects", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onPage("Projects") }) { Text("View All  ›", color = Green) }
        }
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Muted, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("No projects yet", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects) { uri ->
                    Column(Modifier.width(112.dp).clickable { onProject(uri) }) {
                        Box(Modifier.fillMaxWidth().height(104.dp).background(Panel2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Open project", tint = Color.White, modifier = Modifier.size(35.dp))
                        }
                        Text("Video project", color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem("Home", Icons.Default.Home, true) { onPage("Home") }
            NavItem("Projects", Icons.Default.FolderOpen, false) { onPage("Projects") }
            NavItem("Templates", Icons.Default.Dashboard, false) { onPage("Templates") }
            NavItem("Profile", Icons.Default.Person, false) { onPage("Profile") }
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(91.dp).background(Panel, RoundedCornerShape(13.dp)).clickable { onClick() }.padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = label, tint = Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = if (selected) Green else Muted)
        Text(label, color = if (selected) Green else Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SimplePage(page: String, projects: List<Uri>, onBack: () -> Unit, onNew: () -> Unit, onProject: (Uri) -> Unit, onInfo: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text(page, color = Color.White, fontSize = 20.sp)
        }
        when (page) {
            "Projects" -> {
                if (projects.isEmpty()) Text("No projects yet. Create a new project to get started.", color = Muted)
                projects.forEach { uri -> Text("▶  Video project", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onProject(uri) }.padding(16.dp)) }
                Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text("New Project", color = Black) }
            }
            "Templates" -> {
                Text("Templates are not available yet.", color = Muted)
                Button(onClick = { onInfo("Templates") }, colors = ButtonDefaults.buttonColors(containerColor = Panel2)) { Text("Templates info") }
            }
            else -> {
                Icon(Icons.Default.Person, contentDescription = null, tint = Green, modifier = Modifier.size(50.dp))
                Text("MS Video Editor Profile", color = Color.White, fontSize = 18.sp)
                Text("Profile options are not available yet.", color = Muted)
            }
        }
    }
}

@Composable
private fun EditorScreen(
    uri: Uri, videoUris: List<Uri>, audioUri: Uri?, activeTool: String, onTool: (String) -> Unit,
    onBack: () -> Unit, onAddMedia: () -> Unit, onPickAudio: () -> Unit, onAudioExtracted: (Uri) -> Unit, onAudioRemoved: () -> Unit,
    onExport: (Long, Long) -> Unit, onSplit: (Long, Long, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExtractPicker by remember { mutableStateOf(false) }
    var extractCandidates by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedExtractVideo by remember { mutableIntStateOf(-1) }
    var extractingAudio by remember { mutableStateOf(false) }
    val extractVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { picked ->
        extractCandidates = picked
        selectedExtractVideo = if (picked.isNotEmpty()) 0 else -1
        showExtractPicker = picked.isNotEmpty()
    }
    val extractScope = rememberCoroutineScope()
    var editTrayOpen by remember { mutableStateOf(false) }
    var clipSelected by remember(uri) { mutableStateOf(false) }
    var selectedTrack by remember(uri) { mutableStateOf("video") }
    var selectedLayerTrack by remember(uri) { mutableStateOf("pip") }
    var clipMuted by remember(uri) { mutableStateOf(false) }
    var audioVolume by remember(audioUri) { mutableFloatStateOf(1f) }
    var videoVolume by remember(uri) { mutableFloatStateOf(1f) }
    var previewZoom by remember(uri) { mutableFloatStateOf(1f) }
    // Step 1: selected preview object transform and its visible selection frame.
    var previewObjectSelected by remember(uri) { mutableStateOf(true) }
    var objectScale by remember(uri) { mutableFloatStateOf(1f) }
    var objectTranslationX by remember(uri) { mutableFloatStateOf(0f) }
    var objectTranslationY by remember(uri) { mutableFloatStateOf(0f) }
    val sourceDurations = remember(videoUris) {
        videoUris.map { source ->
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, source)
                val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                d
            } catch (_: Exception) { 0L }
        }
    }
    val durationMs = sourceDurations.sum()
    val audioDurationMs = remember(audioUri) {
        if (audioUri == null) 0L else try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, audioUri)
            val result = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            result
        } catch (_: Exception) { 0L }
    }
    var audioClips by remember(audioUri, audioDurationMs) {
        mutableStateOf(
            if (audioUri != null && audioDurationMs > 0L) {
                listOf(AudioTimelineClip(0L, audioDurationMs, 0L))
            } else emptyList()
        )
    }
    var selectedAudioClipIndex by remember(audioUri) { mutableIntStateOf(0) }
    val audioTrackEndMs = audioClips.maxOfOrNull { it.timelineEndMs } ?: 0L
    val timelineDurationMs = maxOf(durationMs, audioTrackEndMs).coerceAtLeast(1L)
    // One shared zoom scale controls the video, ruler, Mute/Cover, Audio and Text tracks.
    var timelineZoom by remember(uri) { mutableFloatStateOf(1f) }
    val baseTimelineContentWidthDp = (timelineDurationMs / 1000f * 22f).coerceAtLeast(120f)
    val timelineContentWidthDp = baseTimelineContentWidthDp * timelineZoom
    val timelineContentWidthPx = timelineContentWidthDp * androidx.compose.ui.platform.LocalDensity.current.density
    val timelineScrollState = rememberScrollState()
    val timelineVerticalScrollState = rememberScrollState()
    var timelineClips by remember(videoUris, sourceDurations) {
        var cursor = 0L
        mutableStateOf(sourceDurations.mapNotNull { itemDuration ->
            if (itemDuration <= 0L) null else {
                val clip = TimelineClip(cursor, cursor + itemDuration)
                cursor += itemDuration
                clip
            }
        })
    }
    var selectedClipIndex by remember(uri) { mutableIntStateOf(0) }
    var selectedTransitionCutMs by remember(uri) { mutableLongStateOf(-1L) }
    var transitionSelections by remember(uri) { mutableStateOf(mapOf<Long, String>()) }
    val timelineFrames = remember(videoUris, sourceDurations) {
        val frames = mutableListOf<android.graphics.Bitmap>()
        videoUris.forEach { source ->
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, source)
                for (index in 0 until 4) {
                    val sourceDuration = sourceDurations.getOrNull(videoUris.indexOf(source)) ?: 0L
                    val timeUs = if (sourceDuration > 0L) (sourceDuration * index / 4) * 1000L else 0L
                    retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frames.add(it) }
                }
                retriever.release()
            } catch (_: Exception) {}
        }
        frames
    }
    var trimRange by remember(uri, durationMs) { mutableStateOf(0f..durationMs.toFloat()) }
    var splitPoint by remember(uri) { mutableFloatStateOf(durationMs / 2f) }
    var overlayText by remember(uri) { mutableStateOf("") }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var playing by remember(uri) { mutableStateOf(true) }
    var playheadMs by remember(uri) { mutableLongStateOf(0L) }
    val currentPlayheadMs by rememberUpdatedState(playheadMs)
    var seekRevision by remember(uri) { mutableIntStateOf(0) }
    val player = remember(videoUris) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(videoUris.map { MediaItem.fromUri(it) })
            // Let the preview and separate music player mix without audio-focus interruptions.
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            prepare()
            playWhenReady = true
        }
    }
    // Play the added/extracted audio as a separate synchronized track.
    val audioPlayer = remember(audioUri) {
        audioUri?.let { source ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(source))
                // Do not request audio focus: the video player must keep rendering while
                // this independent music/voice track is mixed into the preview.
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                volume = audioVolume
                prepare()
            }
        }
    }
    DisposableEffect(player, audioPlayer) {
        onDispose {
            player.release()
            audioPlayer?.release()
        }
    }
    // Keep the separate audio player locked to the shared project timeline.
    // The video clock leads while video is rendering; audio takes over after video ends.
    LaunchedEffect(player, audioPlayer, playing, audioClips, audioVolume) {
        while (true) {
            if (playing) {
                val videoPosition = globalPlaybackPosition(player, sourceDurations)
                val videoIsRunning = player.isPlaying
                val videoHasEnded = player.playbackState == androidx.media3.common.Player.STATE_ENDED
                val timelinePosition = if (videoIsRunning) videoPosition else currentPlayheadMs
                val activeAudioClip = audioClips.firstOrNull {
                    timelinePosition >= it.timelineStartMs && timelinePosition < it.timelineEndMs
                }

                if (audioPlayer != null && activeAudioClip != null) {
                    if (videoIsRunning || videoHasEnded) {
                        val desiredAudioPosition = activeAudioClip.sourceStartMs +
                            (timelinePosition - activeAudioClip.timelineStartMs).coerceAtLeast(0L)
                        val drift = kotlin.math.abs(audioPlayer.currentPosition - desiredAudioPosition)
                        // Large drift correction only: repeated small seeks cause audible restarts.
                        if (drift > 650L) {
                            audioPlayer.seekTo(desiredAudioPosition.coerceIn(activeAudioClip.sourceStartMs, activeAudioClip.sourceEndMs))
                        }
                        audioPlayer.volume = audioVolume.coerceIn(0f, 1f)
                        audioPlayer.playWhenReady = true
                    } else {
                        // Hold music until the video is actually rendering, not merely requested.
                        audioPlayer.playWhenReady = false
                    }
                } else {
                    audioPlayer?.playWhenReady = false
                }

                val position = when {
                    videoIsRunning -> videoPosition
                    videoHasEnded && audioPlayer?.isPlaying == true && activeAudioClip != null ->
                        activeAudioClip.timelineStartMs +
                            (audioPlayer.currentPosition - activeAudioClip.sourceStartMs).coerceAtLeast(0L)
                    else -> currentPlayheadMs
                }
                playheadMs = position.coerceIn(0L, timelineDurationMs)
            } else {
                audioPlayer?.playWhenReady = false
            }
            delay(40)
        }
    }
    // Explicit seeks update both players once; normal playback is synchronized by the clock above.
    LaunchedEffect(audioPlayer, seekRevision, audioClips) {
        val clip = audioClips.firstOrNull {
            playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs
        }
        if (clip != null) {
            val sourcePosition = clip.sourceStartMs + (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
            audioPlayer?.seekTo(sourcePosition.coerceIn(clip.sourceStartMs, clip.sourceEndMs))
        } else {
            audioPlayer?.playWhenReady = false
        }
    }
    LaunchedEffect(audioPlayer, audioVolume) {
        audioPlayer?.volume = audioVolume.coerceIn(0f, 1f)
    }
    LaunchedEffect(player, videoVolume, clipMuted) {
        player.volume = if (clipMuted) 0f else videoVolume.coerceIn(0f, 1f)
    }
    val splitAtPlayhead: () -> Unit = {
        val position = playheadMs.coerceIn(0L, durationMs)
        val index = timelineClips.indexOfFirst { position > it.startMs + 250L && position < it.endMs - 250L }
        val clip = timelineClips.getOrNull(index)
        if (clip == null) {
            Toast.makeText(context, "Move the playhead inside the selected clip, then Split.", Toast.LENGTH_SHORT).show()
        } else {
            val updated = timelineClips.toMutableList()
            updated.removeAt(index)
            // Keep the left segment before the right segment on the timeline.
            updated.add(index, TimelineClip(clip.startMs, position))
            updated.add(index + 1, TimelineClip(position, clip.endMs))
            timelineClips = updated
            selectedClipIndex = index + 1
            seekGlobalPosition(player, position.coerceAtMost(durationMs), sourceDurations)
            playheadMs = position.coerceIn(0L, timelineDurationMs)
            seekRevision++
            playing = false
            clipSelected = true
            editTrayOpen = true
            onTool("Edit")
        }
    }
    LaunchedEffect(speed, player) { player.playbackParameters = PlaybackParameters(speed) }
    LaunchedEffect(playing, player) { player.playWhenReady = playing }
    val deleteSelectedAudioClip: () -> Unit = {
        val index = selectedAudioClipIndex.coerceIn(0, (audioClips.size - 1).coerceAtLeast(0))
        val clip = audioClips.getOrNull(index)
        if (clip != null) {
            val remaining = audioClips.filterIndexed { itemIndex, _ -> itemIndex != index }
                .map { item -> if (item.timelineStartMs > clip.timelineStartMs) item.copy(timelineStartMs = (item.timelineStartMs - clip.durationMs).coerceAtLeast(0L)) else item }
            if (remaining.isEmpty()) {
                onAudioRemoved()
            } else {
                audioClips = remaining
                selectedAudioClipIndex = (index - 1).coerceIn(0, remaining.lastIndex)
                seekRevision++
            }
        }
    }
    val splitAudioAtPlayhead: () -> Unit = {
        val index = audioClips.indexOfFirst {
            playheadMs > it.timelineStartMs + 250L && playheadMs < it.timelineEndMs - 250L
        }
        val clip = audioClips.getOrNull(index)
        if (clip == null) {
            Toast.makeText(context, "Audio ke andar playhead rakhein, phir Split karein.", Toast.LENGTH_SHORT).show()
        } else {
            val sourceCut = clip.sourceStartMs + (playheadMs - clip.timelineStartMs)
            val updated = audioClips.toMutableList()
            updated.removeAt(index)
            updated.add(index, AudioTimelineClip(sourceCut, clip.sourceEndMs, playheadMs))
            updated.add(index, AudioTimelineClip(clip.sourceStartMs, sourceCut, clip.timelineStartMs))
            audioClips = updated
            selectedAudioClipIndex = index + 1
            selectedTrack = "audio"
            playing = false
            onTool("Audio Split")
            seekRevision++
        }
    }
    // Keep the active playhead visible: during playback the timeline content slides
    // underneath the centered playhead, matching the reference editor workflow.
    val timelineViewportWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 116.dp).toPx()
    }
    LaunchedEffect(playing, playheadMs, timelineDurationMs) {
        if (playing && timelineDurationMs > 0L) {
            val playheadPx = playheadMs.toFloat() / timelineDurationMs.toFloat() * timelineContentWidthPx
            val targetScroll = (playheadPx - timelineViewportWidthPx / 2f)
                .toInt().coerceIn(0, timelineScrollState.maxValue)
            timelineScrollState.scrollTo(targetScroll)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        val editorTools = listOf(
            "Edit" to Icons.Default.ContentCut,
            "Audio" to Icons.Default.MusicNote,
            "Text" to Icons.Default.Title,
            "Effects" to Icons.Default.AutoAwesome,
            "Overlay" to Icons.Default.Layers,
            "Captions" to Icons.Default.ClosedCaption,
            "Filters" to Icons.Default.FilterVintage,
            "Adjust" to Icons.Default.Tune,
            "Stickers" to Icons.Default.EmojiEmotions,
            "Generate media" to Icons.Default.AutoFixHigh,
            "AI avatar" to Icons.Default.Face,
            "Aspect ratio" to Icons.Default.Crop,
            "Background" to Icons.Default.Wallpaper
        )
        val editTools = listOf(
            "Split", "Animations", "Effects", "Speed", "Volume", "Delete",
            "AI video", "Crop", "Duplicate", "Replace", "Overlay",
            "Adjust", "Filters", "Retouch", "Video quality", "Remove BG",
            "AI remove", "AI expand", "AI remix", "AI extend", "Relight",
            "Opacity", "Motion blur", "Lip sync", "Transform", "Auto reframe",
            "Stabilize", "Camera tracking", "Isolate voice", "Enhance voice",
            "Freeze", "Reverse", "Mask", "Unlink"
        )
        // Keep the editor header and preview fixed; only the timeline lanes scroll vertically.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close project", tint = Color.White) }
            IconButton(onClick = { onTool("Search") }) { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onTool("AI UHD") }) { Text("AI UHD  ▾", color = Color.White, fontSize = 12.sp) }
            Button(onClick = { onExport(trimRange.start.toLong(), trimRange.endInclusive.toLong()) },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp)) { Text("Export", color = Black, fontSize = 13.sp) }
        }
        val previewTransition = transitionSelections.entries.firstOrNull {
            kotlin.math.abs(playheadMs - it.key) <= 500L && (it.value == "Zoom" || it.value == "Slide")
        }
        val transitionProgress = previewTransition?.let {
            ((playheadMs - it.key + 500L).toFloat() / 1000f).coerceIn(0f, 1f)
        } ?: 0f
        // Keep the video preview at a stable visible height; a weighted preview can
        // collapse to zero when the timeline/tool panels use the remaining column space.
        Box(Modifier.fillMaxWidth().height(280.dp).background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    previewZoom = (previewZoom * zoomChange).coerceIn(1f, 4f)
                }
            }, contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            }, update = { view ->
                if (view.player !== player) view.player = player
            }, modifier = Modifier.fillMaxSize().graphicsLayer {
                    val pulse = if (transitionProgress < .5f) transitionProgress * 2f else (1f - transitionProgress) * 2f
                    val transitionZoom = if (previewTransition?.value == "Zoom") 1f + pulse * .22f else 1f
                    scaleX = previewZoom * transitionZoom * objectScale
                    scaleY = previewZoom * transitionZoom * objectScale
                    translationX = objectTranslationX
                    translationY = objectTranslationY
                    if (previewTransition?.value == "Slide") {
                        translationX += (if (transitionProgress < .5f) 1f else -1f) *
                            (1f - pulse) * size.width * .18f
                    }
                })
            if (overlayText.isNotBlank()) {
                Text(overlayText, color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .28f), RoundedCornerShape(5.dp)).padding(8.dp))
            }
            if (previewObjectSelected) {
                // White selection frame. Drag inside it to move; drag the lower-right
                // handle with one finger to resize the selected preview object.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 240.dp, height = 135.dp)
                        .graphicsLayer {
                            scaleX = objectScale
                            scaleY = objectScale
                            translationX = objectTranslationX
                            translationY = objectTranslationY
                        }
                        .border(1.5.dp, Color.White, RoundedCornerShape(1.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                objectTranslationX += dragAmount.x
                                objectTranslationY += dragAmount.y
                            }
                        }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 9.dp, y = 9.dp)
                            .size(22.dp)
                            .background(Color.White, RoundedCornerShape(3.dp))
                            .pointerInput(objectScale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val delta = (dragAmount.x + dragAmount.y) / 260f
                                    objectScale = (objectScale + delta).coerceIn(0.2f, 4f)
                                }
                            }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp).background(Panel), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { onTool("Fullscreen") }) { Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White) }
            IconButton(onClick = { playing = !playing }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Green, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = splitAtPlayhead) {
                Icon(Icons.Default.ContentCut, contentDescription = "Split at playhead", tint = Color.White)
            }
            IconButton(onClick = { onTool("Keyframe") }) { Icon(Icons.Default.Diamond, contentDescription = "Keyframe", tint = Color.White) }
            IconButton(onClick = {
                val position = (playheadMs - 1000L).coerceAtLeast(0L)
                seekGlobalPosition(player, position.coerceAtMost(durationMs), sourceDurations)
                playheadMs = position
                seekRevision++
            }) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White) }
            IconButton(onClick = {
                val position = (playheadMs + 1000L).coerceAtMost(timelineDurationMs)
                seekGlobalPosition(player, position.coerceAtMost(durationMs), sourceDurations)
                playheadMs = position
                seekRevision++
            }) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.White) }
        }
        // All timeline lanes, including their left labels, move together vertically.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(timelineVerticalScrollState)) {
        Row(Modifier.fillMaxWidth().height(28.dp).padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(playheadMs), color = Green, fontSize = 10.sp, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("/", color = Muted, fontSize = 9.sp)
            Text(formatTime(timelineDurationMs), color = Muted, fontSize = 10.sp, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                val rulerWidth = timelineContentWidthDp.dp
                val stepMs = when {
                    timelineDurationMs <= 20_000L -> 1_000L
                    timelineDurationMs <= 60_000L -> 5_000L
                    timelineDurationMs <= 180_000L -> 10_000L
                    timelineDurationMs <= 600_000L -> 30_000L
                    else -> 60_000L
                }
                val tickCount = (timelineDurationMs / stepMs).toInt().coerceAtMost(120)
                Box(Modifier.offset { IntOffset(-timelineScrollState.value, 0) }.width(rulerWidth).fillMaxHeight()) {
                    for (tick in 0..tickCount) {
                        val timeMs = tick * stepMs
                        val fraction = if (timelineDurationMs > 0L) timeMs.toFloat() / timelineDurationMs.toFloat() else 0f
                        Box(
                            Modifier.align(Alignment.TopStart)
                                .offset(x = rulerWidth * fraction)
                                .width(1.dp).height(if (tick % 2 == 0) 9.dp else 5.dp)
                                .background(Muted.copy(alpha = .8f))
                        )
                        if (tick % 2 == 0) {
                            Text(
                                formatTime(timeMs),
                                color = Muted,
                                fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .offset(x = (rulerWidth * fraction) - 12.dp)
                            )
                        }
                    }
                    val currentFraction = if (timelineDurationMs > 0L) (playheadMs.toFloat() / timelineDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .offset(x = rulerWidth * currentFraction)
                            .width(2.dp).height(10.dp)
                            .background(Green)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp)
                .background(Panel, RoundedCornerShape(8.dp)).padding(2.dp)
                // Pinch directly on the timeline strip; scale is shared by every track.
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                            playing = false
                            timelineZoom = (timelineZoom * zoomChange).coerceIn(0.15f, 4f)
                        }
                    }
                }
                .horizontalScroll(timelineScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(48.dp).fillMaxHeight().clickable {
                val muteNow = !clipMuted
                clipMuted = muteNow
                player.volume = if (muteNow) 0f else 1f
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (clipMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = "Mute clip",
                    tint = if (clipMuted) Green else Color.White, modifier = Modifier.size(18.dp))
                Text(if (clipMuted) "Muted" else "Mute\nclip", color = Color.White, fontSize = 8.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Column(Modifier.width(48.dp).fillMaxHeight().clickable { onTool("Cover") },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (timelineFrames.isNotEmpty()) {
                    Image(bitmap = timelineFrames.first().asImageBitmap(), contentDescription = "Video cover",
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(36.dp).background(Panel2, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = "Cover", tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cover", color = Color.White, fontSize = 8.sp)
                    Icon(Icons.Default.Edit, contentDescription = "Edit cover", tint = Green, modifier = Modifier.size(10.dp))
                }
            }
            BoxWithConstraints(
                Modifier.width(timelineContentWidthDp.dp).fillMaxHeight()
                    .pointerInput(durationMs, timelineClips, timelineScrollState.value) {
                        detectTapGestures { tap ->
                            val contentX = (tap.x + timelineScrollState.value).coerceIn(0f, timelineContentWidthPx)
                            val position = if (timelineContentWidthPx > 0f) (timelineDurationMs * (contentX / timelineContentWidthPx)).toLong().coerceAtMost(timelineDurationMs) else 0L
                            playheadMs = position
                            seekGlobalPosition(player, position.coerceAtMost(durationMs), sourceDurations)
                            seekRevision++
                            playing = false
                            selectedClipIndex = timelineClips.indexOfFirst { position >= it.startMs && position < it.endMs }.coerceAtLeast(0)
                        }
                    }
            ) {
                val timelineWidth = timelineContentWidthDp.dp
                Box(Modifier.width(timelineWidth).fillMaxHeight()
                    .background(Panel2, RoundedCornerShape(5.dp))) {
                    Row(Modifier.width(timelineWidth * (durationMs.toFloat() / timelineDurationMs.toFloat()).coerceIn(0f, 1f)).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    timelineClips.forEachIndexed { index, segment ->
                        val segmentFrames = timelineFrames.filterIndexed { frameIndex, _ ->
                            val frameTime = if (timelineFrames.size <= 1) 0L else durationMs * frameIndex / (timelineFrames.size - 1)
                            frameTime >= segment.startMs && (frameTime < segment.endMs || index == timelineClips.lastIndex && frameTime <= segment.endMs)
                        }
                        Box(
                            Modifier.weight(segment.durationMs.toFloat()).fillMaxHeight().padding(end = 2.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .then(if (selectedClipIndex == index) Modifier.border(2.dp, Emerald, RoundedCornerShape(5.dp)) else Modifier)
                                .clickable {
                                    selectedTrack = "video"
                                    selectedClipIndex = index
                                    clipSelected = true
                                    seekGlobalPosition(player, segment.startMs.coerceAtMost(durationMs), sourceDurations)
                                    playheadMs = segment.startMs
                                    seekRevision++
                                    playing = false
                                    editTrayOpen = true
                                    onTool("Edit")
                                }
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                val visibleFrames = if (segmentFrames.isEmpty()) timelineFrames.take(1) else segmentFrames
                                visibleFrames.forEach { frame ->
                                    Image(bitmap = frame.asImageBitmap(), contentDescription = "Video clip segment",
                                        modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                }
                            }
                            if (timelineClips.size > 1) {
                                Text(" ${index + 1}", color = Color.White, fontSize = 8.sp,
                                    modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                    }
                    val playheadFraction = if (timelineDurationMs > 0L) (playheadMs.toFloat() / timelineDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .offset(x = (timelineWidth * playheadFraction) - 1.dp)
                            .width(2.dp).fillMaxHeight()
                            .background(Color.White)
                    )
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .offset(x = (timelineWidth * playheadFraction) - 5.dp)
                            .size(width = 10.dp, height = 7.dp)
                            .background(Emerald, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    )
                    timelineClips.drop(1).forEach { cut ->
                        val cutFraction = if (durationMs > 0L) cut.startMs.toFloat() / durationMs.toFloat() else 0f
                        val transitionName = transitionSelections[cut.startMs]
                        Box(
                            Modifier.align(Alignment.CenterStart)
                                .offset(x = (timelineWidth * cutFraction) - 11.dp)
                                .size(width = 22.dp, height = 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (transitionName == null) Panel2 else Emerald)
                                .border(1.dp, if (transitionName == null) Green else Color.White, RoundedCornerShape(6.dp))
                                .clickable {
                                    selectedTransitionCutMs = cut.startMs
                                    onTool("Transition")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (transitionName == null) "+" else "✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = onAddMedia, modifier = Modifier.align(Alignment.CenterEnd).size(26.dp).background(Color.White, RoundedCornerShape(7.dp))) {
                    Icon(Icons.Default.Add, contentDescription = "Add video clip", tint = Black)
                }
            }
        }
        // Audio label and clip area move together with the shared timeline scroll.
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(top = 2.dp)
                .horizontalScroll(timelineScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = "Audio track", tint = Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(48.dp))
            Box(Modifier.width(timelineContentWidthDp.dp).fillMaxHeight()
                .background(Panel2, RoundedCornerShape(5.dp)).clipToBounds()) {
                if (audioUri == null) {
                    Text("+ Add audio", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterStart).clickable { onPickAudio() }.padding(horizontal = 8.dp))
                } else {
                    Box(Modifier.width(timelineContentWidthDp.dp).fillMaxHeight()) {
                        audioClips.forEachIndexed { index, clip ->
                            val left = timelineContentWidthDp * (clip.timelineStartMs.toFloat() / timelineDurationMs.toFloat())
                            val clipWidth = (timelineContentWidthDp * (clip.durationMs.toFloat() / timelineDurationMs.toFloat())).coerceAtLeast(20f)
                            Box(
                                Modifier.offset(x = left.dp).width(clipWidth.dp).fillMaxHeight()
                                    .background(if (selectedTrack == "audio" && selectedAudioClipIndex == index) Emerald.copy(alpha = .25f) else Panel2, RoundedCornerShape(5.dp))
                                    .border(1.dp, if (selectedTrack == "audio" && selectedAudioClipIndex == index) Emerald else Color(0xFF267C62), RoundedCornerShape(5.dp))
                                    .clickable {
                                        selectedTrack = "audio"
                                        selectedAudioClipIndex = index
                                        editTrayOpen = false
                                        onTool("Audio clip")
                                    }.padding(horizontal = 5.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                    repeat(22) { bar ->
                                        val h = (5 + ((bar * 7 + index * 11) % 15)).dp
                                        Box(Modifier.width(2.dp).height(h).background(Emerald.copy(alpha = .85f), RoundedCornerShape(2.dp)))
                                    }
                                }
                                Text(
                                    "♪ ${formatTime(clip.durationMs)}",
                                    color = Color.White, fontSize = 8.sp, maxLines = 1,
                                    modifier = Modifier.align(Alignment.CenterStart).background(Panel2.copy(alpha = .72f))
                                )
                            }
                        }
                    }
                }
            }
        }
        // Text label and content move together with the shared timeline scroll.
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(top = 2.dp)
                .horizontalScroll(timelineScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Title, contentDescription = "Text track", tint = Muted, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.width(timelineContentWidthDp.dp).fillMaxHeight().clipToBounds()) {
                Box(Modifier.fillMaxSize()
                    .background(Panel, RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(if (overlayText.isBlank()) "+ Add text" else overlayText,
                        color = if (overlayText.isBlank()) Muted else Green, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        // Additional synchronized layers share the ruler width, zoom and scroll.
        LayerTrackRow(
            label = "PIP",
            icon = Icons.Default.Layers,
            contentWidthDp = timelineContentWidthDp,
            scrollState = timelineScrollState,
            selected = selectedLayerTrack == "pip",
            onSelect = { selectedLayerTrack = "pip"; onTool("Overlay") },
            onAdd = { selectedLayerTrack = "pip"; onTool("Overlay") }
        )
        LayerTrackRow(
            label = "Stickers",
            icon = Icons.Default.EmojiEmotions,
            contentWidthDp = timelineContentWidthDp,
            scrollState = timelineScrollState,
            selected = selectedLayerTrack == "stickers",
            onSelect = { selectedLayerTrack = "stickers"; onTool("Stickers") },
            onAdd = { selectedLayerTrack = "stickers"; onTool("Stickers") }
        )
        LayerTrackRow(
            label = "Effects",
            icon = Icons.Default.AutoAwesome,
            contentWidthDp = timelineContentWidthDp,
            scrollState = timelineScrollState,
            selected = selectedLayerTrack == "effects",
            onSelect = { selectedLayerTrack = "effects"; onTool("Effects") },
            onAdd = { selectedLayerTrack = "effects"; onTool("Effects") }
        )
        LayerTrackRow(
            label = "Audio FX",
            icon = Icons.Default.GraphicEq,
            contentWidthDp = timelineContentWidthDp,
            scrollState = timelineScrollState,
            selected = selectedLayerTrack == "audio_fx",
            onSelect = { selectedLayerTrack = "audio_fx"; onTool("Audio") },
            onAdd = { selectedLayerTrack = "audio_fx"; onTool("Audio") }
        )
        LayerTrackRow(
            label = "Adjust",
            icon = Icons.Default.Tune,
            contentWidthDp = timelineContentWidthDp,
            scrollState = timelineScrollState,
            selected = selectedLayerTrack == "adjust",
            onSelect = { selectedLayerTrack = "adjust"; onTool("Adjust") },
            onAdd = { selectedLayerTrack = "adjust"; onTool("Adjust") }
        )
        }
        when (activeTool) {
            "Trim" -> {
                if (durationMs > 0L) {
                    RangeSlider(value = trimRange, onValueChange = { trimRange = it }, valueRange = 0f..durationMs.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(trimRange.start.toLong()), color = Green, fontSize = 11.sp)
                        Text(formatTime(trimRange.endInclusive.toLong()), color = Muted, fontSize = 11.sp)
                    }
                    Button(onClick = { onExport(trimRange.start.toLong(), trimRange.endInclusive.toLong()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald), modifier = Modifier.fillMaxWidth()) {
                        Text("Export Trimmed Video", color = Black)
                    }
                } else Text("Could not read video duration.", color = Muted)
            }
            "Split" -> {
                Text("Move the video playhead to the cut point, then tap Split.", color = Muted, fontSize = 11.sp)
                Button(onClick = splitAtPlayhead,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Split at playhead", color = Black)
                }
            }
            "Transition" -> {
                Text("Add transition between the two video clips", color = Color.White, fontSize = 12.sp)
                Text("Cut point: ${formatTime(selectedTransitionCutMs.coerceAtLeast(0L))}", color = Green, fontSize = 10.sp)
                val transitionOptions = listOf("None", "Fade", "Dissolve", "Zoom", "Slide", "Wipe", "Spin")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    transitionOptions.forEach { option ->
                        val selected = transitionSelections[selectedTransitionCutMs] == option || (option == "None" && transitionSelections[selectedTransitionCutMs] == null)
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Emerald.copy(alpha = .22f) else Panel2)
                                .border(1.dp, if (selected) Green else Panel2, RoundedCornerShape(8.dp))
                                .clickable {
                                    transitionSelections = if (option == "None") transitionSelections - selectedTransitionCutMs
                                        else transitionSelections + (selectedTransitionCutMs to option)
                                }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(36.dp).background(if (selected) Emerald else Panel, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Text(if (option == "None") "×" else "✦", color = Color.White, fontSize = 19.sp)
                            }
                            Text(option, color = if (selected) Green else Color.White, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text("Tap the + box between clips to add or change a transition.", color = Muted, fontSize = 10.sp)
            }
            "Video Volume" -> {
                Text("Video volume", color = Color.White, fontSize = 12.sp)
                Slider(value = videoVolume, onValueChange = {
                    videoVolume = it.coerceIn(0f, 1f)
                    player.volume = if (clipMuted) 0f else videoVolume
                }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald))
                Text("${(videoVolume * 100).toInt()}%", color = Green, fontSize = 11.sp)
            }
            "Speed" -> {
                Slider(value = speed, onValueChange = { speed = it }, valueRange = .25f..2f,
                    colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald))
                Text("Preview speed: ${"%.2f".format(speed)}x", color = Green, fontSize = 12.sp)
                Text("Speed currently changes preview playback only; exported speed rendering is not connected yet.", color = Muted, fontSize = 11.sp)
            }
            "Text" -> {
                OutlinedTextField(value = overlayText, onValueChange = { overlayText = it }, label = { Text("Text on video") },
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                Text("Text preview overlay. Burning text into the exported video is still being developed.", color = Muted, fontSize = 11.sp)
            }
            "Audio" -> {
                // Audio actions are provided in the bottom Audio tray.
            }
            else -> { /* Unfinished-tool notices are intentionally hidden. */ }
        }
        // The same bottom toolbar switches between the main tools and the Edit tools.
        // This avoids rendering a duplicate tool strip.
        if (selectedTrack == "audio" && audioUri != null && (activeTool == "Audio clip" || activeTool.startsWith("Audio "))) {
            val audioTools = listOf("Split", "Trim", "Fade", "Volume", "Audio effects", "Delete")
            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(52.dp).height(76.dp).clickable { onTool("Trim") }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        Text("Back", color = Color.White, fontSize = 9.sp)
                    }
                    audioTools.forEach { tool ->
                        val toolIcon = when (tool) {
                            "Split" -> Icons.Default.ContentCut
                            "Trim" -> Icons.Default.Tune
                            "Fade" -> Icons.Default.GraphicEq
                            "Volume" -> Icons.Default.VolumeUp
                            "Audio effects" -> Icons.Default.AutoAwesome
                            else -> Icons.Default.Delete
                        }
                        Column(Modifier.width(78.dp).height(76.dp).clickable {
                            when (tool) {
                                "Delete" -> { deleteSelectedAudioClip(); onTool("Audio clip") }
                                "Split", "Trim", "Fade", "Volume", "Audio effects" -> onTool("Audio $tool")
                            }
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(toolIcon, contentDescription = tool, tint = if (activeTool == "Audio $tool") Emerald else Color.White, modifier = Modifier.size(26.dp))
                            Text(tool, color = if (activeTool == "Audio $tool") Emerald else Color.White, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                when (activeTool) {
                    "Audio Volume" -> {
                        Text("Audio volume", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = audioVolume,
                            onValueChange = {
                                audioVolume = it
                                audioPlayer?.volume = it.coerceIn(0f, 1f)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald)
                        )
                        Text("${(audioVolume * 100).toInt()}%", color = Emerald, fontSize = 11.sp)
                    }
                    "Audio Fade" -> { Text("Fade in / Fade out", color = Color.White, fontSize = 12.sp); var fade by remember { mutableFloatStateOf(0f) }; Slider(value = fade, onValueChange = { fade = it }, valueRange = 0f..10f); Text("${fade.toInt()} seconds", color = Emerald, fontSize = 11.sp) }
                    "Audio Audio effects" -> { Text("Audio effects", color = Color.White, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Echo", "Reverb", "Bass", "Voice").forEach { Text(it, color = Color.White, modifier = Modifier.background(Panel2, RoundedCornerShape(8.dp)).padding(8.dp)) } } }
                    "Audio Split" -> {
                        Text("Playhead par audio ko do hisson mein cut karein.", color = Muted, fontSize = 11.sp)
                        Button(onClick = splitAudioAtPlayhead,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentCut, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Split audio at playhead", color = Black)
                        }
                    }
                    "Audio Trim" -> {
                        val clip = audioClips.getOrNull(selectedAudioClipIndex)
                        if (clip != null) {
                            Text("Audio start / end trim", color = Color.White, fontSize = 12.sp)
                            RangeSlider(
                                value = clip.sourceStartMs.toFloat()..clip.sourceEndMs.toFloat(),
                                onValueChange = { range ->
                                    val updated = audioClips.toMutableList()
                                    updated[selectedAudioClipIndex] = clip.copy(
                                        sourceStartMs = range.start.toLong().coerceAtLeast(0L),
                                        sourceEndMs = range.endInclusive.toLong().coerceAtMost(audioDurationMs)
                                    )
                                    audioClips = updated
                                    seekRevision++
                                },
                                valueRange = 0f..audioDurationMs.coerceAtLeast(1L).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("In: ${formatTime(clip.sourceStartMs)}", color = Green, fontSize = 10.sp)
                                Text("Out: ${formatTime(clip.sourceEndMs)}", color = Green, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        } else if (editTrayOpen) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(52.dp).height(82.dp).clickable { editTrayOpen = false }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to tools", tint = Color.White, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(5.dp))
                    Text("Back", color = Color.White, fontSize = 10.sp)
                }
                editTools.forEach { tool ->
                    // Match the individual pictograms shown above each tool name in the user's PDF.
                    val toolIcon = when (tool) {
                        "Split" -> Icons.Default.ContentCut
                        "Animations" -> Icons.Default.Movie
                        "Effects" -> Icons.Default.AutoAwesome
                        "Speed" -> Icons.Default.Speed
                        "Volume" -> Icons.Default.VolumeUp
                        "Delete" -> Icons.Default.Delete
                        "AI video" -> Icons.Default.VideoLibrary
                        "Crop" -> Icons.Default.Crop
                        "Duplicate" -> Icons.Default.ContentCopy
                        "Replace" -> Icons.Default.SwapHoriz
                        "Overlay" -> Icons.Default.Layers
                        "Adjust" -> Icons.Default.Tune
                        "Filters" -> Icons.Default.FilterVintage
                        "Retouch" -> Icons.Default.FaceRetouchingNatural
                        "Video quality" -> Icons.Default.HighQuality
                        "Remove BG" -> Icons.Default.PersonRemove
                        "AI remove" -> Icons.Default.AutoFixHigh
                        "AI expand" -> Icons.Default.OpenInFull
                        "AI remix" -> Icons.Default.AutoAwesome
                        "AI extend" -> Icons.Default.AddPhotoAlternate
                        "Relight" -> Icons.Default.LightMode
                        "Opacity" -> Icons.Default.Opacity
                        "Motion blur" -> Icons.Default.BlurOn
                        "Lip sync" -> Icons.Default.RecordVoiceOver
                        "Transform" -> Icons.Default.Transform
                        "Auto reframe" -> Icons.Default.CropRotate
                        "Stabilize" -> Icons.Default.Videocam
                        "Camera tracking" -> Icons.Default.CenterFocusStrong
                        "Isolate voice" -> Icons.Default.GraphicEq
                        "Enhance voice" -> Icons.Default.Mic
                        "Freeze" -> Icons.Default.Pause
                        "Reverse" -> Icons.Default.Replay
                        "Mask" -> Icons.Default.VisibilityOff
                        "Unlink" -> Icons.Default.LinkOff
                        else -> Icons.Default.Tune
                    }
                    Column(
                        Modifier.width(78.dp).height(82.dp)
                            .clickable {
                                selectedTrack = "video"
                                onTool(if (tool == "Volume") "Video Volume" else tool)
                            }.padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(toolIcon, contentDescription = tool, tint = if (activeTool == tool) Green else Color.White, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(tool, color = if (activeTool == tool) Green else Color.White, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        } else if (activeTool == "Audio") {
            // CapCut-style Audio tray: this is the only area changed for the audio-menu update.
            val audioTools = listOf(
                Triple("Extract", Icons.Default.FolderOpen, "extract"),
                Triple("Sounds", Icons.Default.MusicNote, "sounds"),
                Triple("Sound FX", Icons.Default.GraphicEq, "effects"),
                Triple("Record", Icons.Default.Mic, "record"),
                Triple("Text to audio", Icons.Default.Title, "text"),
                Triple("Brand music", Icons.Default.Album, "brand"),
                Triple("Copyright", Icons.Default.Shield, "copyright")
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(48.dp).height(68.dp).clickable { onTool("Trim") }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to tools", tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Back", color = Color.White, fontSize = 9.sp)
                }
                audioTools.forEach { (label, icon, action) ->
                    Column(
                        Modifier.width(72.dp).height(68.dp)
                            .clickable {
                                when (action) {
                                    "sounds", "effects", "brand" -> onPickAudio()
                                    "extract" -> extractVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    "record" -> Toast.makeText(context, "Voice recording is not connected yet.", Toast.LENGTH_SHORT).show()
                                    "text" -> Toast.makeText(context, "Text to audio is not connected yet.", Toast.LENGTH_SHORT).show()
                                    "copyright" -> Toast.makeText(context, "Copyright checking is not connected yet.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                editorTools.forEach { (tool, icon) ->
                    Column(
                        Modifier.width(66.dp).height(68.dp)
                            .background(if (activeTool == tool) Panel2 else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                if (tool == "Edit") {
                                    editTrayOpen = true
                                    onTool("Edit")
                                } else {
                                    onTool(tool)
                                }
                            }.padding(horizontal = 3.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = tool, tint = if (activeTool == tool) Green else Color.White, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(tool, color = if (activeTool == tool) Green else Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showExtractPicker) {
        Dialog(onDismissRequest = { if (!extractingAudio) showExtractPicker = false }) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (!extractingAudio) showExtractPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close gallery", tint = Color.White)
                        }
                        Text("Albums", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("${extractCandidates.size} videos", color = Muted, fontSize = 10.sp)
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(extractCandidates) { index, candidate ->
                            val chosen = selectedExtractVideo == index
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (chosen) Emerald.copy(alpha = .18f) else Panel2, RoundedCornerShape(8.dp))
                                    .border(if (chosen) 1.dp else 0.dp, if (chosen) Emerald else Panel2, RoundedCornerShape(8.dp))
                                    .clickable { selectedExtractVideo = index }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null,
                                    tint = if (chosen) Emerald else Color.White, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.lastPathSegment?.substringAfterLast('/') ?: "Video ${index + 1}",
                                        color = Color.White, maxLines = 1)
                                    Text("Select video to extract its audio", color = Muted, fontSize = 10.sp)
                                }
                                Icon(if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (chosen) "Selected" else "Select",
                                    tint = if (chosen) Emerald else Muted)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val source = extractCandidates.getOrNull(selectedExtractVideo)
                            if (source != null && !extractingAudio) {
                                extractingAudio = true
                                extractScope.launch {
                                    val result = withContext(Dispatchers.IO) { extractAudioTrack(context, source) }
                                    extractingAudio = false
                                    if (result != null) {
                                        onAudioExtracted(result)
                                        showExtractPicker = false
                                        Toast.makeText(context, "Audio extracted and added to timeline.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Selected video mein audio track nahi mila ya extract nahi ho saka.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = selectedExtractVideo >= 0 && !extractingAudio,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, disabledContainerColor = Panel2),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        if (extractingAudio) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Extract and add audio", color = Black)
                        }
                    }
                }
            }
        }
    }
}

private fun extractAudioTrack(context: android.content.Context, source: Uri): Uri? {
    var extractor: android.media.MediaExtractor? = null
    var muxer: android.media.MediaMuxer? = null
    var muxerStarted = false
    var outputFile: java.io.File? = null
    return try {
        extractor = android.media.MediaExtractor()
        extractor.setDataSource(context, source, null)
        var sourceAudioTrack = -1
        for (index in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(index)
            val mime = trackFormat.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                sourceAudioTrack = index
                break
            }
        }
        if (sourceAudioTrack < 0) return null
        val outputDirectory = java.io.File(context.filesDir, "extracted_audio").apply { mkdirs() }
        outputFile = java.io.File(outputDirectory, "audio_${System.currentTimeMillis()}.m4a")
        muxer = android.media.MediaMuxer(
            outputFile.absolutePath,
            android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        extractor.selectTrack(sourceAudioTrack)
        muxer.addTrack(extractor.getTrackFormat(sourceAudioTrack))
        muxer.start()
        muxerStarted = true
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(0, buffer, info)
            extractor.advance()
        }
        muxer.stop()
        muxerStarted = false
        Uri.fromFile(outputFile)
    } catch (_: Exception) {
        outputFile?.delete()
        null
    } finally {
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { extractor?.release() }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
}