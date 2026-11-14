package com.ms.videoeditor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
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

    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                try {
                    val outputDir = File(context.filesDir, "trimmed_videos").apply { mkdirs() }
                    val finalFile = File(outputDir, "MS_Video_${System.currentTimeMillis()}.mp4")
                    FileInputStream(outputFile).use { input ->
                        finalFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outputFile.delete()
                    onFinished(true, "Trimmed video saved inside MS Video Editor. It was not added to Gallery.")
                } catch (error: Exception) {
                    outputFile.delete()
                    onFinished(false, error.message ?: "Could not save the exported video.")
                }
            }

            override fun onError(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                outputFile.delete()
                onFinished(false, exportException.message ?: "Video export failed.")
            }
        })
        .build()

    try {
        transformer.start(editedItem, outputFile.absolutePath)
    } catch (error: Exception) {
        outputFile.delete()
        onFinished(false, error.message ?: "Could not start video export.")
    }
}
