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
 * Additional synchronized timeline lane. It shares the editor's horizontal
 * scroll offset and zoom width. Add actions open the corresponding editor tool.
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
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(scrollState) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    scrollState.dispatchRawDelta(-dragAmount)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(if (selected) Color(0xFF192D25) else Color.Transparent)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) Color(0xFF00D889) else Color.Transparent
                )
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = "$label track",
                tint = if (selected) Color(0xFF00D889) else Color(0xFF9AA7A1),
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = if (selected) Color(0xFF00D889) else Color(0xFFCDD5D1),
                fontSize = 8.sp,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .background(laneColor, RoundedCornerShape(5.dp))
                .clickable { onSelect() }
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) Color(0xFF00D889) else Color.Transparent,
                    shape = RoundedCornerShape(5.dp)
                )
        ) {
            Box(
                Modifier
                    .offset { IntOffset(-scrollState.value, 0) }
                    .width(contentWidthDp.dp)
                    .fillMaxHeight()
            ) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .background(Color(0xFF19231F), RoundedCornerShape(5.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "+ Add $label",
                        color = if (selected) Color(0xFF00D889) else Color(0xFF9AA7A1),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
