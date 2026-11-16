package com.ms.videoeditor.timeline

/**
 * Core timeline model for MS Video Editor.
 *
 * This is an original, UI-independent model. It represents the track types
 * needed by a layered editor without depending on a particular screen layout.
 */
enum class EditorTrackKind {
    MAIN_VIDEO,
    PIP_VIDEO,
    AUDIO,
    AUDIO_EFFECT,
    TEXT,
    STICKER,
    EFFECT,
    FILTER_ADJUST
}

/**
 * A timeline item stores its position on the project timeline separately from
 * the source-media in/out points. This allows trimming and moving without
 * losing the original source range.
 */
data class EditorTimelineClip(
    val id: String,
    val trackId: String,
    val title: String,
    val timelineStartMs: Long,
    val durationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceStartMs + durationMs,
    val sourceUri: String? = null,
    val isEnabled: Boolean = true
) {
    val timelineEndMs: Long
        get() = timelineStartMs + durationMs.coerceAtLeast(0L)

    val sourceDurationMs: Long
        get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)

    fun moveTo(newTimelineStartMs: Long): EditorTimelineClip =
        copy(timelineStartMs = newTimelineStartMs.coerceAtLeast(0L))

    /**
     * Trim the visible left/right edges while keeping the source and timeline
     * ranges in sync. Returns null if the requested range is empty.
     */
    fun trimTo(newStartMs: Long, newEndMs: Long): EditorTimelineClip? {
        val start = newStartMs.coerceIn(timelineStartMs, timelineEndMs)
        val end = newEndMs.coerceIn(start, timelineEndMs)
        if (end <= start) return null

        val leftTrim = start - timelineStartMs
        val rightTrim = timelineEndMs - end
        val sourceStart = (sourceStartMs + leftTrim).coerceAtMost(sourceEndMs)
        val sourceEnd = (sourceEndMs - rightTrim).coerceAtLeast(sourceStart)

        return copy(
            timelineStartMs = start,
            durationMs = end - start,
            sourceStartMs = sourceStart,
            sourceEndMs = sourceEnd
        )
    }

    /**
     * Split at a project-timeline position. The split point must be strictly
     * inside the clip; otherwise no split is returned.
     */
    fun splitAt(positionMs: Long, rightClipId: String): Pair<EditorTimelineClip, EditorTimelineClip>? {
        if (positionMs <= timelineStartMs || positionMs >= timelineEndMs) return null

        val leftDuration = positionMs - timelineStartMs
        val rightDuration = timelineEndMs - positionMs
        val sourceSplit = (sourceStartMs + leftDuration).coerceIn(sourceStartMs, sourceEndMs)

        val left = copy(
            durationMs = leftDuration,
            sourceEndMs = sourceSplit
        )
        val right = copy(
            id = rightClipId,
            timelineStartMs = positionMs,
            durationMs = rightDuration,
            sourceStartMs = sourceSplit
        )
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
    val endMs: Long
        get() = clips.filter { it.isEnabled }.maxOfOrNull { it.timelineEndMs } ?: 0L

    fun withClip(clip: EditorTimelineClip): EditorTimelineTrack {
        require(clip.trackId == id) { "Clip trackId must match the destination track id." }
        return copy(clips = clips + clip)
    }

    fun withoutClip(clipId: String): EditorTimelineTrack =
        copy(clips = clips.filterNot { it.id == clipId })

    fun replaceClip(updated: EditorTimelineClip): EditorTimelineTrack {
        require(updated.trackId == id) { "Clip trackId must match the destination track id." }
        return copy(clips = clips.map { if (it.id == updated.id) updated else it })
    }
}

data class EditorTimelineProject(
    val tracks: List<EditorTimelineTrack> = emptyList()
) {
    val durationMs: Long
        get() = tracks.maxOfOrNull { it.endMs } ?: 0L

    fun track(trackId: String): EditorTimelineTrack? =
        tracks.firstOrNull { it.id == trackId }

    fun updateTrack(updated: EditorTimelineTrack): EditorTimelineProject =
        copy(tracks = tracks.map { if (it.id == updated.id) updated else it })
}
