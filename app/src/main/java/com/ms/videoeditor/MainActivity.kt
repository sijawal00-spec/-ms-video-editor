package com.ms.videoeditor

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val EditorBlack = Color(0xFF090D0C)
private val EditorPanel = Color(0xFF151D1A)
private val Emerald = Color(0xFF00D889)
private val Gold = Color(0xFFE6BD62)
private val Muted = Color(0xFF9AA7A1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 12)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 12)
        setContent { MSVideoEditor() }
    }
}

@Composable
private fun MSVideoEditor() {
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var activeTool by remember { mutableStateOf("Trim") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Emerald, secondary = Gold, background = EditorBlack, surface = EditorPanel)) {
        Surface(Modifier.fillMaxSize(), color = EditorBlack) {
            if (videoUri == null) {
                HomeScreen(onNewProject = { videoPicker.launch(arrayOf("video/*")) })
            } else {
                VideoEditorScreen(
                    uri = videoUri!!,
                    activeTool = activeTool,
                    onToolSelected = { activeTool = it },
                    onBack = { videoUri = null },
                    onAddMedia = { videoPicker.launch(arrayOf("video/*")) },
                    onExport = { Toast.makeText(context, "Export engine is the next implementation step.", Toast.LENGTH_LONG).show() }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(onNewProject: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MS VIDEO EDITOR", color = Color.White, fontSize = 21.sp)
                Text("Create something amazing", color = Gold, fontSize = 12.sp)
            }
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Gold)
        }
        Spacer(Modifier.height(28.dp))
        Text("Start editing", color = Color.White, fontSize = 27.sp)
        Text("Your next video starts with a new project.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onNewProject,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = EditorBlack)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Project", fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("QUICK TOOLS", color = Gold, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile("Video", Icons.Default.Movie, Modifier.weight(1f))
            HomeTile("Audio", Icons.Default.MusicNote, Modifier.weight(1f))
            HomeTile("Effects", Icons.Default.AutoAwesome, Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(EditorPanel, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Emerald, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text("No projects yet", color = Color.White, fontSize = 17.sp)
                Text("Create a project to begin editing.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HomeTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.background(EditorPanel, RoundedCornerShape(14.dp)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Emerald)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun VideoEditorScreen(
    uri: Uri,
    activeTool: String,
    onToolSelected: (String) -> Unit,
    onBack: () -> Unit,
    onAddMedia: () -> Unit,
    onExport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("New Project", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onExport) { Text("Export", color = Emerald) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> VideoView(ctx).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { player -> player.isLooping = true; start() }
                } },
                update = { view ->
                    if (view.tag != uri) {
                        view.tag = uri
                        view.setVideoURI(uri)
                        view.setOnPreparedListener { player -> player.isLooping = true; view.start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("00:00", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.PlayCircle, contentDescription = "Preview", tint = Emerald, modifier = Modifier.size(34.dp))
            Spacer(Modifier.weight(1f))
            Text("--:--", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("TIMELINE", color = Gold, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().background(EditorPanel, RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("V1", color = Emerald, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(52.dp).background(Color(0xFF24533F), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("VIDEO CLIP", color = Color.White, fontSize = 11.sp)
            }
            IconButton(onClick = onAddMedia) { Icon(Icons.Default.Add, contentDescription = "Add video", tint = Emerald) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Trim", "Split", "Audio", "Text", "Filters", "Effects", "Speed", "Crop").forEach { tool ->
                FilterChip(
                    selected = activeTool == tool,
                    onClick = { onToolSelected(tool) },
                    label = { Text(tool) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald, selectedLabelColor = EditorBlack, labelColor = Color.White)
                )
            }
        }
        Text("$activeTool selected", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text("Editing controls will be connected to the timeline engine in the next step.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
    }
}
