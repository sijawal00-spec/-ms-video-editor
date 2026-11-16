package com.ms.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState

/**
 * Fixed track label with a horizontally draggable content viewport.
 * The content starts after the same 96dp leading area used by the main
 * video lane, then follows the editor's shared ScrollState.
 */
@Composable
internal fun LayerTrackRow(
    label: String,
    icon: ImageVector,
    contentWidthDp: Float,
    scrollState: ScrollState,
    selected: Boolean,
    onSelect: () -> Unit,
    onAdd: () -> Unit
) {
    val laneColor = if (selected) Color(0xFF123D30) else Color(0xFF111816)
    Row(
        Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.width(96.dp).fillMaxHeight()
                .background(if (selected) Color(0xFF192D25) else Color.Transparent)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) Color(0xFF00D889) else Color.Transparent
                )
                .clickable { onSelect() }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = "$label track",
                tint = if (selected) Color(0xFF00D889) else Color(0xFF9AA7A1),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (selected) Color(0xFF00D889) else Color(0xFFCDD5D1),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clipToBounds()
                .background(laneColor, RoundedCornerShape(5.dp))
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) Color(0xFF00D889) else Color.Transparent,
                    shape = RoundedCornerShape(5.dp)
                )
                .pointerInput(scrollState) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        scrollState.dispatchRawDelta(-dragAmount)
                    }
                }
                .clickable { onSelect() }
        ) {
            Box(
                Modifier.offset {
                    IntOffset(96.dp.roundToPx() - scrollState.value, 0)
                }.width(contentWidthDp.dp).fillMaxHeight()
            ) {
                Row(
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 9.dp)
                        .background(Color(0xFF19231F), RoundedCornerShape(7.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "+ Add $label",
                        color = if (selected) Color(0xFF00D889) else Color(0xFFB7C3BD),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
