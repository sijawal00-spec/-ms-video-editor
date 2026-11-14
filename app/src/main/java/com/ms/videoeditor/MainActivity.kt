package com.ms.videoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EditorBlack = Color(0xFF090D0C)
private val Panel = Color(0xFF151D1A)
private val Emerald = Color(0xFF00C98D)
private val Gold = Color(0xFFE6BD62)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { EditorHome() } }
    }
}

@Composable
private fun EditorHome() {
    Surface(Modifier.fillMaxSize(), color = EditorBlack) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(Emerald, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = EditorBlack)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("MS VIDEO EDITOR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Create something amazing", color = Gold, fontSize = 12.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start editing", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Your next video starts with a new project.", color = Color(0xFF9AA7A1))
            }
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = EditorBlack)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  New project", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text("QUICK TOOLS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Modifier.weight(1f), "Trim") { Icon(Icons.Default.ContentCut, null, tint = Emerald) }
                ToolTile(Modifier.weight(1f), "Audio") { Icon(Icons.Default.MusicNote, null, tint = Emerald) }
                ToolTile(Modifier.weight(1f), "Text") { Icon(Icons.Default.TextFields, null, tint = Emerald) }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(20.dp)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No projects yet", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Create a project to begin editing.", color = Color(0xFF9AA7A1), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ToolTile(modifier: Modifier, title: String, icon: @Composable () -> Unit) {
    Column(
        modifier.background(Panel, RoundedCornerShape(16.dp)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
