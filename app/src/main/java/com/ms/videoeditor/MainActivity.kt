package com.ms.videoeditor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private val Black = Color(0xFF050807)
private val Panel = Color(0xFF111816)
private val Panel2 = Color(0xFF19231F)
private val Green = Color(0xFF19F000)
private val Emerald = Color(0xFF00D889)
private val Gold = Color(0xFFFFD84A)
private val Muted = Color(0xFF9AA7A1)

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
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var activeTool by remember { mutableStateOf("Trim") }
    var page by remember { mutableStateOf("Home") }
    var dialog by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("ms_video_editor", android.content.Context.MODE_PRIVATE) }
    var recentProjects by remember {
        mutableStateOf(prefs.getStringSet("recent_projects", emptySet())!!.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() })
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
            recentProjects = (listOf(uri) + recentProjects.filter { it != uri }).take(30)
            prefs.edit().putStringSet("recent_projects", recentProjects.map { it.toString() }.toSet()).apply()
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            audioUri = uri
        }
    }
    fun startProject(tool: String) {
        activeTool = tool
        videoPicker.launch(arrayOf("video/*"))
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Emerald, secondary = Gold, background = Black, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            when {
                videoUri != null -> EditorScreen(
                    uri = videoUri!!,
                    audioUri = audioUri,
                    activeTool = activeTool,
                    onTool = { activeTool = it },
                    onBack = { videoUri = null; audioUri = null; page = "Home" },
                    onAddMedia = { videoPicker.launch(arrayOf("video/*")) },
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onExport = { startMs, endMs ->
                        exportTrimmedVideo(context, videoUri!!, startMs, endMs) { _, message ->
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
                    onProject = { videoUri = it; activeTool = "Trim" },
                    onSettings = { dialog = "Settings" },
                    onPremium = { dialog = "MS Premium" },
                    onPage = { page = it }
                )
                else -> SimplePage(
                    page = page, projects = recentProjects,
                    onBack = { page = "Home" },
                    onNew = { startProject("Trim") },
                    onProject = { videoUri = it },
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
    uri: Uri, audioUri: Uri?, activeTool: String, onTool: (String) -> Unit,
    onBack: () -> Unit, onAddMedia: () -> Unit, onPickAudio: () -> Unit,
    onExport: (Long, Long) -> Unit, onSplit: (Long, Long, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editTrayOpen by remember { mutableStateOf(false) }
    var clipSelected by remember(uri) { mutableStateOf(false) }
    var clipMuted by remember(uri) { mutableStateOf(false) }
    val durationMs = remember(uri) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            d
        } catch (_: Exception) { 0L }
    }
    val timelineFrames = remember(uri, durationMs) {
        val frames = mutableListOf<android.graphics.Bitmap>()
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            for (index in 0 until 7) {
                val timeUs = if (durationMs > 0L) (durationMs * index / 7) * 1000L else 0L
                retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frames.add(it) }
            }
            retriever.release()
        } catch (_: Exception) {}
        frames
    }
    var trimRange by remember(uri, durationMs) { mutableStateOf(0f..durationMs.toFloat()) }
    var splitPoint by remember(uri) { mutableFloatStateOf(durationMs / 2f) }
    var overlayText by remember(uri) { mutableStateOf("") }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var playing by remember(uri) { mutableStateOf(true) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(speed, player) { player.playbackParameters = PlaybackParameters(speed) }
    LaunchedEffect(playing, player) { player.playWhenReady = playing }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close project", tint = Color.White) }
            IconButton(onClick = { onTool("Search") }) { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onTool("AI UHD") }) { Text("AI UHD  ▾", color = Color.White, fontSize = 12.sp) }
            Button(onClick = { onExport(trimRange.start.toLong(), trimRange.endInclusive.toLong()) },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp)) { Text("Export", color = Black, fontSize = 13.sp) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            if (overlayText.isNotBlank()) {
                Text(overlayText, color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .28f), RoundedCornerShape(5.dp)).padding(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp).background(Panel), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { onTool("Fullscreen") }) { Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White) }
            IconButton(onClick = { playing = !playing }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Green, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = {
                if (durationMs > 1000L) {
                    val cut = player.currentPosition.coerceIn(1L, durationMs - 1L)
                    onSplit(0L, cut, durationMs)
                }
            }) { Icon(Icons.Default.ContentCut, contentDescription = "Split at playhead", tint = Color.White) }
            IconButton(onClick = { onTool("Keyframe") }) { Icon(Icons.Default.Diamond, contentDescription = "Keyframe", tint = Color.White) }
            IconButton(onClick = { player.seekTo((player.currentPosition - 1000L).coerceAtLeast(0L)) }) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White) }
            IconButton(onClick = { player.seekTo((player.currentPosition + 1000L).coerceAtMost(durationMs)) }) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).background(Panel, RoundedCornerShape(8.dp)).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(48.dp).fillMaxHeight().clickable {
                clipMuted = !clipMuted
                player.volume = if (clipMuted) 0f else 1f
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
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .then(if (clipSelected) Modifier.border(2.dp, Emerald, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable {
                            clipSelected = true
                            editTrayOpen = true
                            playing = false
                            onTool("Edit")
                        }
                ) {
                    if (timelineFrames.isNotEmpty()) {
                        timelineFrames.forEach { frame ->
                            Image(bitmap = frame.asImageBitmap(), contentDescription = "Video frame",
                                modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Panel2), contentAlignment = Alignment.Center) {
                            Text("VIDEO CLIP", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                IconButton(onClick = onAddMedia, modifier = Modifier.align(Alignment.CenterEnd).size(26.dp).background(Color.White, RoundedCornerShape(7.dp))) {
                    Icon(Icons.Default.Add, contentDescription = "Add video clip", tint = Black)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(32.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = "Audio track", tint = Muted, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(5.dp)).clickable { onPickAudio() }.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (audioUri == null) "+ Add audio" else "♪ ${audioUri.lastPathSegment ?: "Audio"}",
                    color = if (audioUri == null) Muted else Green, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(32.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Title, contentDescription = "Text track", tint = Muted, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(5.dp)).clickable { onTool("Text") }.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (overlayText.isBlank()) "+ Add text" else overlayText, color = if (overlayText.isBlank()) Muted else Green, fontSize = 11.sp, maxLines = 1)
            }
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
                if (durationMs > 1000L) {
                    Slider(value = splitPoint, onValueChange = { splitPoint = it }, valueRange = 1f..(durationMs - 1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Emerald))
                    Text("Split at ${formatTime(splitPoint.toLong())}", color = Green, fontSize = 12.sp)
                    Button(onClick = { onSplit(0L, splitPoint.toLong(), durationMs) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald), modifier = Modifier.fillMaxWidth()) {
                        Text("Export Both Parts", color = Black)
                    }
                }
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
                Button(onClick = onPickAudio, colors = ButtonDefaults.buttonColors(containerColor = Emerald), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MusicNote, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Choose Audio", color = Black)
                }
                Text(if (audioUri == null) "No audio selected" else "Audio file selected: ${audioUri.lastPathSegment ?: "audio"}", color = if (audioUri == null) Muted else Green, fontSize = 11.sp)
                Text("Adding and mixing this track into the final exported video is not connected yet.", color = Muted, fontSize = 11.sp)
            }
            else -> {
                Text("$activeTool controls are not implemented yet.", color = Muted, fontSize = 12.sp)
                Text("This button now opens its own tool panel; processing will be added in the next development stage.", color = Muted, fontSize = 11.sp)
            }
        }
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
            "Split", "Animations", "Effects", "Speed", "Delete",
            "AI video", "Crop", "Duplicate", "Replace", "Overlay",
            "Adjust", "Filters", "Retouch", "Video quality", "Remove BG",
            "AI remove", "AI expand", "AI remix", "AI extend", "Relight",
            "Opacity", "Motion blur", "Lip sync", "Transform", "Auto reframe",
            "Stabilize", "Camera tracking", "Isolate voice", "Enhance voice",
            "Freeze", "Reverse", "Mask", "Unlink"
        )
        // The same bottom toolbar switches between the main tools and the Edit tools.
        // This avoids rendering a duplicate tool strip.
        if (editTrayOpen) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(42.dp).height(68.dp).clickable { editTrayOpen = false }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to tools", tint = Color.White, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Back", color = Color.White, fontSize = 9.sp)
                }
                editTools.forEach { tool ->
                    // Match the individual pictograms shown above each tool name in the user's PDF.
                    val toolIcon = when (tool) {
                        "Split" -> Icons.Default.ContentCut
                        "Animations" -> Icons.Default.Movie
                        "Effects" -> Icons.Default.AutoAwesome
                        "Speed" -> Icons.Default.Speed
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
                        Modifier.width(62.dp).height(68.dp)
                            .clickable { onTool(tool) }.padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(toolIcon, contentDescription = tool, tint = if (activeTool == tool) Green else Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(tool, color = if (activeTool == tool) Green else Color.White, fontSize = 9.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
}
