package com.ms.videoeditor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream

@OptIn(UnstableApi::class)
fun exportTrimmedVideo(
    context: Context,
    source: Uri,
    startMs: Long,
    endMs: Long,
    onFinished: (Boolean, String) -> Unit
) {
    if (endMs <= startMs) {
        onFinished(false, "Select a valid trim range.")
        return
    }

    val outputFile = File(context.cacheDir, "ms_video_${System.currentTimeMillis()}.mp4")
    val clippedMediaItem = MediaItem.Builder()
        .setUri(source)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()
    val editedItem = EditedMediaItem.Builder(clippedMediaItem).build()
    startCompositionExport(
        context = context,
        composition = Composition.Builder(
            EditedMediaItemSequence.withAudioAndVideoFrom(listOf(editedItem))
        ).build(),
        outputFile = outputFile,
        successMessage = "Trimmed video saved inside MS Video Editor. It was not added to Gallery.",
        onFinished = onFinished
    )
}

/**
 * Exports the ordered source-video sequence as one composition.
 * startMs/endMs refer to the combined project timeline, not to a single source file.
 */
@OptIn(UnstableApi::class)
fun exportVideoSequence(
    context: Context,
    sources: List<Uri>,
    startMs: Long,
    endMs: Long,
    onFinished: (Boolean, String) -> Unit
) {
    if (sources.isEmpty() || endMs <= startMs) {
        onFinished(false, "No video clips or invalid export range.")
        return
    }

    val editedItems = mutableListOf<EditedMediaItem>()
    var timelineCursorMs = 0L

    try {
        for (source in sources) {
            val retriever = android.media.MediaMetadataRetriever()
            val sourceDurationMs = try {
                retriever.setDataSource(context, source)
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }

            if (sourceDurationMs <= 0L) continue
            val sourceTimelineStart = timelineCursorMs
            val sourceTimelineEnd = sourceTimelineStart + sourceDurationMs
            val overlapStart = maxOf(startMs.coerceAtLeast(0L), sourceTimelineStart)
            val overlapEnd = minOf(endMs, sourceTimelineEnd)

            if (overlapEnd > overlapStart) {
                val localStart = overlapStart - sourceTimelineStart
                val localEnd = overlapEnd - sourceTimelineStart
                val clippedItem = MediaItem.Builder()
                    .setUri(source)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(localStart)
                            .setEndPositionMs(localEnd)
                            .build()
                    )
                    .build()
                editedItems += EditedMediaItem.Builder(clippedItem).build()
            }
            timelineCursorMs = sourceTimelineEnd
        }
    } catch (error: Exception) {
        onFinished(false, error.message ?: "Could not read the selected video clips.")
        return
    }

    if (editedItems.isEmpty()) {
        onFinished(false, "The selected timeline range does not contain a readable video clip.")
        return
    }

    val outputFile = File(context.cacheDir, "ms_sequence_${System.currentTimeMillis()}.mp4")
    val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)
    val composition = Composition.Builder(sequence).build()
    startCompositionExport(
        context = context,
        composition = composition,
        outputFile = outputFile,
        successMessage = "Video sequence exported and saved inside MS Video Editor. It was not added to Gallery.",
        onFinished = onFinished
    )
}

@OptIn(UnstableApi::class)
private fun startCompositionExport(
    context: Context,
    composition: Composition,
    outputFile: File,
    successMessage: String,
    onFinished: (Boolean, String) -> Unit
) {
    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                try {
                    val outputDir = File(context.filesDir, "trimmed_videos").apply { mkdirs() }
                    val finalFile = File(outputDir, "MS_Video_${System.currentTimeMillis()}.mp4")
                    FileInputStream(outputFile).use { input ->
                        finalFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outputFile.delete()
                    onFinished(true, "$successMessage\nSaved file: ${finalFile.name}")
                } catch (error: Exception) {
                    outputFile.delete()
                    onFinished(false, error.message ?: "Could not save the exported video.")
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                outputFile.delete()
                onFinished(false, exportException.message ?: "Video export failed.")
            }
        })
        .build()

    try {
        transformer.start(composition, outputFile.absolutePath)
    } catch (error: Exception) {
        outputFile.delete()
        onFinished(false, error.message ?: "Could not start video export.")
    }
}
