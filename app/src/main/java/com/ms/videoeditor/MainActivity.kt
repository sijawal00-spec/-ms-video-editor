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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var activeTool by remember { mutableStateOf("Trim") }
    var page by remember { mutableStateOf("Home") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var recentProjects by remember { mutableStateOf(listOf<Uri>()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
            recentProjects = (listOf(uri) + recentProjects.filter { it != uri }).take(8)
        }
    }
    fun startProject(tool: String) {
        activeTool = tool
        picker.launch(arrayOf("video/*"))
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Emerald, secondary = Gold, background = Black, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            when {
                videoUri != null -> EditorScreen(
                    uri = videoUri!!,
                    activeTool = activeTool,
                    onTool = { activeTool = it; dialog = it },
                    onBack = { videoUri = null; page = "Home" },
                    onAddMedia = { picker.launch(arrayOf("video/*")) },
                    onExport = { startMs, endMs ->\n                        exportTrimmedVideo(context, videoUri!!, startMs, endMs) { success, message ->\n                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()\n                        }\n                    }
                )
                page == "Home" -> HomeScreen(
                    projects = recentProjects,
                    onNew = { startProject("Trim") },
                    onTool = { startProject(it) },
                    onProject = { videoUri = it },
                    onSettings = { dialog = "Settings" },
                    onPremium = { dialog = "MS Premium" },
                    onPage = { page = it }
                )
                else -> SimplePage(
                    page = page,
                    projects = recentProjects,
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
                    text = {
                        Text(when (dialog) {
                            "Export" -> "Export settings are being connected to the video rendering engine. This build does not yet create an exported video."
                            "Settings" -> "MS Video Editor settings"
                            "MS Premium" -> "Premium tools and subscriptions will be added later."
                            else -> "$dialog tool selected. Select a video clip to use this editing workspace. Full processing for this tool is still under development."
                        })
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK", color = Emerald) } }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    projects: List<Uri>,
    onNew: () -> Unit,
    onTool: (String) -> Unit,
    onProject: (Uri) -> Unit,
    onSettings: () -> Unit,
    onPremium: () -> Unit,
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
        Box(
            Modifier.fillMaxWidth().height(142.dp).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF22F000), Color(0xFF087A16), Color(0xFF031B0B))),
                RoundedCornerShape(18.dp)
            ).clickable { onNew() },
            contentAlignment = Alignment.Center
        ) {
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
            Triple("Music", Icons.Default.MusicNote, "Music"),
            Triple("Text", Icons.Default.Title, "Text"),
            Triple("Speed", Icons.Default.Speed, "Speed"),
            Triple("Export", Icons.Default.IosShare, "Export")
        )
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            tools.chunked(3).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    line.forEach { item ->
                        ToolTile(item.first, item.second, Modifier.weight(1f)) { onTool(item.third) }
                    }
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
    Column(
        modifier.height(91.dp).background(Panel, RoundedCornerShape(13.dp)).clickable { onClick() }.padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                if (projects.isEmpty()) Text("No saved projects yet. Create a new project to get started.", color = Muted)
                projects.forEach { uri -> Text("▶  Video project", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onProject(uri) }.padding(16.dp)) }
                Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text("New Project", color = Black) }
            }
            "Templates" -> {
                Text("Templates", color = Green, fontSize = 18.sp)
                Text("Video templates are coming soon.", color = Muted)
                Button(onClick = { onInfo("Templates") }, colors = ButtonDefaults.buttonColors(containerColor = Panel2)) { Text("Explore templates") }
            }
            else -> {
                Icon(Icons.Default.Person, contentDescription = null, tint = Green, modifier = Modifier.size(50.dp))
                Text("MS Video Editor Profile", color = Color.White, fontSize = 18.sp)
                Text("Profile and account options will be added in a future update.", color = Muted)
            }
        }
    }
}

@Composable
private fun EditorScreen(
    uri: Uri,
    activeTool: String,
    onTool: (String) -> Unit,
    onBack: () -> Unit,
    onAddMedia: () -> Unit,
    onExport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("New Project", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = { onExport(trimRange.start.toLong(), trimRange.endInclusive.toLong()) }) { Text("Export", color = Green) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx -> VideoView(ctx).apply {
                setVideoURI(uri)
                setOnPreparedListener { player -> player.isLooping = true; start() }
            } }, update = { view ->
                if (view.tag != uri) {
                    view.tag = uri
                    view.setVideoURI(uri)
                    view.setOnPreparedListener { player -> player.isLooping = true; view.start() }
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("00:00", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.PlayCircle, contentDescription = "Preview", tint = Green, modifier = Modifier.size(36.dp))
            Spacer(Modifier.weight(1f))
            Text("--:--", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("TIMELINE", color = Gold, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("V1", color = Green, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(50.dp).background(Color(0xFF24533F), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("VIDEO CLIP", color = Color.White, fontSize = 11.sp)
            }
            IconButton(onClick = onAddMedia) { Icon(Icons.Default.Add, contentDescription = "Add media", tint = Green) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Trim", "Split", "Audio", "Text", "Filters", "Effects", "Speed", "Crop", "Transitions", "Merge").forEach { tool ->
                FilterChip(selected = activeTool == tool, onClick = { onTool(tool) }, label = { Text(tool) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald, selectedLabelColor = Black, labelColor = Color.White))
            }
        }
        Text("$activeTool selected", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text("Choose a tool to open its controls. Processing and final export are being implemented.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
    }
}
\n\nprivate fun formatTime(milliseconds: Long): String {\n    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)\n    return String.format("%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)\n}\n