package com.ms.videoeditor.timeline

/**
 * UI-independent project model for the MS Video Editor timeline.
 * All time values are milliseconds on the project timeline unless named source*.
 */
enum class EditorTrackKind {
    MAIN_VIDEO, PIP_VIDEO, AUDIO, AUDIO_EFFECT, TEXT, STICKER, EFFECT, FILTER_ADJUST
}

data class EditorTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
) {
    fun normalized(): EditorTransform = copy(
        scaleX = scaleX.coerceIn(0.05f, 10f),
        scaleY = scaleY.coerceIn(0.05f, 10f),
        rotationDegrees = rotationDegrees % 360f,
        opacity = opacity.coerceIn(0f, 1f)
    )
}

enum class KeyframeProperty { POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY, VOLUME }

data class EditorKeyframe(
    val id: String,
    val timeMs: Long,
    val property: KeyframeProperty,
    val value: Float
)

data class EditorTimelineClip(
    val id: String,
    val trackId: String,
    val title: String,
    val timelineStartMs: Long,
    val durationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceStartMs + durationMs,
    val sourceUri: String? = null,
    val isEnabled: Boolean = true,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val transform: EditorTransform = EditorTransform(),
    val keyframes: List<EditorKeyframe> = emptyList(),
    val effectIds: List<String> = emptyList()
) {
    val timelineEndMs: Long get() = timelineStartMs + durationMs.coerceAtLeast(0L)
    val sourceDurationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)

    fun moveTo(newTimelineStartMs: Long): EditorTimelineClip =
        copy(timelineStartMs = newTimelineStartMs.coerceAtLeast(0L))

    fun trimTo(newStartMs: Long, newEndMs: Long): EditorTimelineClip? {
        val start = newStartMs.coerceIn(timelineStartMs, timelineEndMs)
        val end = newEndMs.coerceIn(start, timelineEndMs)
        if (end <= start) return null
        val leftTrim = start - timelineStartMs
        val rightTrim = timelineEndMs - end
        val sourceStart = (sourceStartMs + (leftTrim * speed.coerceAtLeast(0.01f)).toLong()).coerceAtMost(sourceEndMs)
        val sourceEnd = (sourceEndMs - (rightTrim * speed.coerceAtLeast(0.01f)).toLong()).coerceAtLeast(sourceStart)
        return copy(
            timelineStartMs = start,
            durationMs = end - start,
            sourceStartMs = sourceStart,
            sourceEndMs = sourceEnd,
            keyframes = keyframes.filter { it.timeMs in start..end }
        )
    }

    fun splitAt(positionMs: Long, rightClipId: String): Pair<EditorTimelineClip, EditorTimelineClip>? {
        if (positionMs <= timelineStartMs || positionMs >= timelineEndMs) return null
        val leftDuration = positionMs - timelineStartMs
        val rightDuration = timelineEndMs - positionMs
        val sourceSplit = (sourceStartMs + (leftDuration * speed.coerceAtLeast(0.01f)).toLong())
            .coerceIn(sourceStartMs, sourceEndMs)
        val left = copy(durationMs = leftDuration, sourceEndMs = sourceSplit,
            keyframes = keyframes.filter { it.timeMs <= positionMs })
        val right = copy(id = rightClipId, timelineStartMs = positionMs, durationMs = rightDuration,
            sourceStartMs = sourceSplit, keyframes = keyframes.filter { it.timeMs >= positionMs })
        return left to right
    }
}

data class EditorTimelineTrack(
    val id: String,
    val kind: EditorTrackKind,
    val title: String,
    val clips: List<EditorTimelineClip> = emptyList(),
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false
) {
    val endMs: Long get() = clips.filter { it.isEnabled }.maxOfOrNull { it.timelineEndMs } ?: 0L

    fun withClip(clip: EditorTimelineClip): EditorTimelineTrack {
        require(clip.trackId == id) { "Clip trackId must match the destination track id." }
        require(clips.none { it.id == clip.id }) { "Clip id must be unique within a track." }
        return copy(clips = (clips + clip).sortedBy { it.timelineStartMs })
    }

    fun withoutClip(clipId: String): EditorTimelineTrack =
        copy(clips = clips.filterNot { it.id == clipId })

    fun replaceClip(updated: EditorTimelineClip): EditorTimelineTrack {
        require(updated.trackId == id) { "Clip trackId must match the destination track id." }
        return copy(clips = clips.map { if (it.id == updated.id) updated else it }.sortedBy { it.timelineStartMs })
    }
}

data class EditorTimelineProject(
    val id: String = "default",
    val name: String = "Untitled project",
    val tracks: List<EditorTimelineTrack> = emptyList(),
    val frameRate: Int = 30,
    val width: Int = 1080,
    val height: Int = 1920
) {
    val durationMs: Long get() = tracks.maxOfOrNull { it.endMs } ?: 0L

    fun track(trackId: String): EditorTimelineTrack? = tracks.firstOrNull { it.id == trackId }

    fun updateTrack(updated: EditorTimelineTrack): EditorTimelineProject =
        copy(tracks = tracks.map { if (it.id == updated.id) updated else it })

    fun addTrack(track: EditorTimelineTrack): EditorTimelineProject {
        require(tracks.none { it.id == track.id }) { "Track id must be unique." }
        return copy(tracks = tracks + track)
    }

    fun removeTrack(trackId: String): EditorTimelineProject =
        copy(tracks = tracks.filterNot { it.id == trackId })

    fun updateClip(clip: EditorTimelineClip): EditorTimelineProject {
        val owner = track(clip.trackId) ?: error("Track not found: ${clip.trackId}")
        return updateTrack(owner.replaceClip(clip))
    }

    fun removeClip(trackId: String, clipId: String): EditorTimelineProject {
        val owner = track(trackId) ?: return this
        return updateTrack(owner.withoutClip(clipId))
    }

    fun allClips(): List<EditorTimelineClip> = tracks.flatMap { it.clips }
}
