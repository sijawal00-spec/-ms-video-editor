package com.ms.videoeditor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToLong
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import kotlinx.coroutines.delay


private data class EditorClip(val uri: Uri, val sourceStartMs: Long, val sourceEndMs: Long, val sourceDurationMs: Long = sourceEndMs)

private fun readVideoDuration(context: android.content.Context, uri: Uri): Long {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        duration
    }.getOrDefault(0L).coerceAtLeast(1L)
}

private val Bg = Color(0xFF050807)
private val SurfaceDark = Color(0xFF101916)
private val TrackDark = Color(0xFF1A2520)
private val Neon = Color(0xFF18E889)
private val Gold = Color(0xFFFFD45A)
private val Soft = Color(0xFF9AA9A1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Neon, background = Bg, surface = SurfaceDark)) { MSVideoEditorApp() } }
    }
}

@Composable
private fun MSVideoEditorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var clips by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("New project") }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { picked ->
        if (picked.isNotEmpty()) {
            clips = picked
            editorOpen = true
            projectName = "Untitled project"
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked -> audioUri = picked }
    if (editorOpen && clips.isNotEmpty()) {
        NewEditorScreen(
            clips = clips,
            audioUri = audioUri,
            onClipsChanged = { clips = it },
            onBack = { editorOpen = false },
            onAddVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            onAddAudio = { audioPicker.launch("audio/*") },
            onExport = { Toast.makeText(context, "Export module will be connected after timeline editing is completed.", Toast.LENGTH_LONG).show() }
        )
    } else {
        HomeScreen(
            projectName = projectName,
            onNewProject = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            onOpenRecent = { if (clips.isNotEmpty()) editorOpen = true }
        )
    }
}

@Composable
private fun HomeScreen(projectName: String, onNewProject: () -> Unit, onOpenRecent: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Neon), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Bg, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MS VIDEO EDITOR", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("Create • Edit • Export", color = Soft, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text("Create your next video", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Edit clips on a professional multi-track timeline.", color = Soft, fontSize = 13.sp)
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp))
                .background(SurfaceDark).border(1.dp, Neon.copy(alpha = .45f), RoundedCornerShape(22.dp))
                .clickable { onNewProject() }.padding(20.dp)
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Neon), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Bg, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(13.dp))
                Text("New project", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Select one or more videos", color = Soft, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent projects", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("See all", color = Neon, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceDark)
                .clickable { onOpenRecent() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(TrackDark), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Neon, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(projectName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Your latest editing session", color = Soft, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Soft)
        }
        Spacer(Modifier.weight(1f))
        Text("MSH • Video tools", color = Soft, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun NewEditorScreen(
    clips: List<Uri>,
    audioUri: Uri?,
    onClipsChanged: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    onAddVideo: () -> Unit,
    onAddAudio: () -> Unit,
    onExport: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var timelineClips by remember(clips) { mutableStateOf(clips.map { uri -> val duration = readVideoDuration(context, uri); EditorClip(uri, 0L, duration, duration) }) }
    val durations = remember(timelineClips) { timelineClips.map { (it.sourceEndMs - it.sourceStartMs).coerceAtLeast(1L) } }
    val totalDuration = durations.sum().coerceAtLeast(1L)
    val player = remember(timelineClips) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(timelineClips.map { clip -> MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.sourceStartMs).setEndPositionMs(clip.sourceEndMs).build()).build() })
            prepare()
            playWhenReady = false
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playhead by remember { mutableLongStateOf(0L) }
    var selectedClip by remember { mutableIntStateOf(0) }
    var selectedTool by remember { mutableStateOf("Edit") }
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trimHandler = rememberUpdatedState<(Int, Boolean, Float) -> Unit>(newValue = { index, trimLeft, dragPx ->
        val current = timelineClips.getOrNull(index)
        if (current != null) {
            val deltaMs = (dragPx / density.density / 48f * 1000f).roundToLong()
            val minimumDuration = 500L
            val updated = if (trimLeft) {
                current.copy(sourceStartMs = (current.sourceStartMs + deltaMs).coerceIn(0L, current.sourceEndMs - minimumDuration))
            } else {
                current.copy(sourceEndMs = (current.sourceEndMs + deltaMs).coerceIn(current.sourceStartMs + minimumDuration, current.sourceDurationMs))
            }
            if (updated != current) {
                timelineClips = timelineClips.toMutableList().also { it[index] = updated }
                if (index == selectedClip) {
                    isPlaying = false
                    player.seekTo(index, 0L)
                }
            }
        }
    })
    val timelineWidth = (totalDuration / 1000f * 48f).coerceAtLeast(420f)
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, isPlaying, durations) {
        while (true) {
            if (isPlaying) {
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                playhead = durations.take(index).sum() + player.currentPosition
                if (player.playbackState == Player.STATE_ENDED) isPlaying = false
            }
            delay(50)
        }
    }
    LaunchedEffect(isPlaying) { player.playWhenReady = isPlaying }
    LaunchedEffect(player, selectedClip) { if (selectedClip in timelineClips.indices) player.seekTo(selectedClip, 0L) }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("MS Video Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Project • ${timelineClips.size} clips", color = Soft, fontSize = 10.sp)
            }
            TextButton(onClick = onExport) { Text("Export", color = Neon, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                update = { if (it.player !== player) it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Text("${formatMs(playhead)}", color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth().height(48.dp).background(SurfaceDark, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { Toast.makeText(context, "Undo history will be added with edit operations.", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White) }
            IconButton(onClick = { Toast.makeText(context, "Redo history will be added with edit operations.", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.White) }
            IconButton(onClick = {
                isPlaying = !isPlaying
                if (isPlaying && player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play", tint = Neon, modifier = Modifier.size(30.dp)) }
            IconButton(onClick = { selectedTool = "Fullscreen"; Toast.makeText(context, "Fullscreen preview", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatMs(playhead), color = Neon, fontSize = 10.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
            Row(Modifier.weight(1f).horizontalScroll(scroll).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(timelineWidth.dp).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val step = 48.dp.toPx() * 5
                        var x = 0f
                        while (x <= size.width) {
                            drawLine(Soft.copy(alpha = .8f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, if (x % (step * 2) < 1f) size.height else size.height * .55f), 1.dp.toPx())
                            x += step
                        }
                    }
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                        for (second in 0..(totalDuration / 5000L).toInt().coerceAtMost(100)) {
                            Text(formatMs(second * 5000L), color = Soft, fontSize = 8.sp, modifier = Modifier.width(240.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("VIDEO", color = Soft, fontSize = 9.sp, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
            Row(Modifier.weight(1f).horizontalScroll(scroll).height(70.dp), verticalAlignment = Alignment.CenterVertically) {
                timelineClips.forEachIndexed { index, clip ->
                    val thumb = remember(clip.uri) { loadFrame(context, clip.uri) }
                    Box(
                        Modifier.width((timelineWidth * (durations[index].coerceAtLeast(1L).toFloat() / totalDuration)).coerceAtLeast(100f).dp)
                            .fillMaxHeight().padding(end = 3.dp).clip(RoundedCornerShape(7.dp))
                            .background(TrackDark)
                            .border(if (selectedClip == index) 2.dp else 1.dp, if (selectedClip == index) Color.White else Neon.copy(alpha = .45f), RoundedCornerShape(7.dp))
                            .clickable {
                                selectedClip = index
                                val start = durations.take(index).sum()
                                playhead = start
                                var remaining = start
                                var target = 0
                                while (target < durations.size - 1 && remaining >= durations[target]) { remaining -= durations[target]; target++ }
                                player.seekTo(target, remaining)
                                isPlaying = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) Image(thumb.asImageBitmap(), contentDescription = "Clip thumbnail", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Text("Clip ${index + 1}", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .6f)).padding(4.dp))
                        if (selectedClip == index) {
                            Box(
                                Modifier.align(Alignment.CenterStart).width(14.dp).fillMaxHeight()
                                    .background(Gold.copy(alpha = .95f))
                                    .pointerInput(index) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            trimHandler.value(index, true, dragAmount.x)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Box(Modifier.width(3.dp).height(24.dp).background(Bg, RoundedCornerShape(2.dp))) }
                            Box(
                                Modifier.align(Alignment.CenterEnd).width(14.dp).fillMaxHeight()
                                    .background(Gold.copy(alpha = .95f))
                                    .pointerInput(index) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            trimHandler.value(index, false, dragAmount.x)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Box(Modifier.width(3.dp).height(24.dp).background(Bg, RoundedCornerShape(2.dp))) }
                        }
                    }
                }
                Box(Modifier.width(42.dp).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(TrackDark).clickable { onAddVideo() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add clip", tint = Neon)
                }
            }
        }
        TrackRow("AUDIO", if (audioUri == null) "+ Add audio" else "Audio track added", Icons.Default.MusicNote, onAddAudio)
        TrackRow("TEXT", "+ Add text", Icons.Default.Title) { selectedTool = "Text"; Toast.makeText(context, "Text overlay controls are next in the editing tools.", Toast.LENGTH_SHORT).show() }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().height(78.dp).background(Color(0xFF07150F), RoundedCornerShape(13.dp)).border(1.dp, Neon.copy(alpha = .55f), RoundedCornerShape(13.dp)).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            val tools = listOf(
                "Split" to Icons.Default.ContentCut, "Delete" to Icons.Default.Delete, "Speed" to Icons.Default.Speed,
                "Volume" to Icons.Default.VolumeUp, "Crop" to Icons.Default.Crop, "Effects" to Icons.Default.AutoAwesome,
                "Filters" to Icons.Default.FilterVintage, "Overlay" to Icons.Default.Layers, "More" to Icons.Default.MoreHoriz
            )
            tools.forEach { (name, icon) ->
                Column(Modifier.width(72.dp).fillMaxHeight().clickable {
                    selectedTool = name
                    when (name) {
                        "Split" -> { val clipStart = durations.take(selectedClip).sum(); val clip = timelineClips.getOrNull(selectedClip); val splitOffset = playhead - clipStart; if (clip == null || splitOffset <= 0L || splitOffset >= durations[selectedClip]) { Toast.makeText(context, "Move the playhead inside the selected clip to split it.", Toast.LENGTH_SHORT).show() } else { val sourceSplit = clip.sourceStartMs + splitOffset; val next = timelineClips.toMutableList().also { it[selectedClip] = clip.copy(sourceEndMs = sourceSplit); it.add(selectedClip + 1, clip.copy(sourceStartMs = sourceSplit)) }; timelineClips = next; selectedClip += 1; isPlaying = false; Toast.makeText(context, "Clip split into two timeline segments.", Toast.LENGTH_SHORT).show() } }
                        "Delete" -> {
                            if (timelineClips.size <= 1) {
                                Toast.makeText(context, "At least one video clip must remain in the project.", Toast.LENGTH_SHORT).show()
                            } else {
                                val next = timelineClips.toMutableList().also { it.removeAt(selectedClip.coerceIn(0, it.lastIndex)) }
                                val nextSelected = (selectedClip - 1).coerceAtLeast(0).coerceAtMost(next.lastIndex)
                                timelineClips = next
                                selectedClip = nextSelected
                                playhead = durations.take(nextSelected).sum()
                                isPlaying = false
                                Toast.makeText(context, "Selected clip deleted from timeline.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> Toast.makeText(context, "$name tool selected.", Toast.LENGTH_SHORT).show()
                    }
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, contentDescription = name, tint = if (selectedTool == name) Neon else Color.White, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = Color.White, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun TrackRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = Soft, modifier = Modifier.width(24.dp).size(17.dp))
        Text(label, color = Soft, fontSize = 9.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp).clip(RoundedCornerShape(5.dp)).background(TrackDark).clickable { onClick() }.padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
            Text(value, color = if (value.startsWith("+")) Soft else Neon, fontSize = 10.sp)
        }
    }
}

private fun loadFrame(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    }.getOrNull()
}

private fun formatMs(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
