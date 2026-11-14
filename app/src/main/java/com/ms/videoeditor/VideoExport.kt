package com.ms.videoeditor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream

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
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "MS_Video_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MS Video Editor")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val savedUri = context.contentResolver.insert(collection, values)
                        ?: throw IllegalStateException("Could not create a video in Gallery.")
                    context.contentResolver.openOutputStream(savedUri)?.use { output ->
                        FileInputStream(outputFile).use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not write the exported video.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                        context.contentResolver.update(savedUri, ready, null, null)
                    }
                    outputFile.delete()
                    onFinished(true, "Trimmed video saved to Gallery > Movies > MS Video Editor.")
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
