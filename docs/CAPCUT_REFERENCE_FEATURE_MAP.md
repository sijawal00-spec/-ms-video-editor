# MS Video Editor — CapCut Reference Feature Map

This document records features identified in the supplied CapCut XML archive and the planned original MS Video Editor implementation. CapCut XML is used only as a UI/architecture reference; MS Video Editor uses its own Kotlin/Jetpack Compose implementation.

## Confirmed reference components

The archive contains 4,902 XML files. The following layouts contain timeline-related custom views:

- `2l.xml`: `MultiTrackLayout`, `TimeRulerScroller`, `TrackFlexibleRuler`, `FrameScroller`, `TimeCursorView`, `TrackGroup`, `TopTrackContainer`, `TopPipTrack`, `TopAudioTrack`, `TopTextTrack`, `TopEffectTrack`, `TopFilterAdjustTrack`, `PipTrackLine`, `TextTrackLine`, `EffectTrackLine`, `AudioTrackLine`, and `AudioEffectTrackLine`.
- `2k.xml`: `MultiTrackLayout`, `AudioVisualLine`, `AudioWaveCollectScroller`, `StickerVisualLine`, `TrackLineMixer`, `TrackGroup`, and the shared ruler/frame-scroller components.
- `k+v.xml`: compact timeline layout with ruler, frame scroller, and `MultiTrackLayout`.
- `kg.xml`: timeline plus video preview, gesture layout, sticker editor and dock controls.
- `l0.xml`: video preview, timeline, frame scroller and camera-edit track group.

Other inspected references:
- `+192.xml`: keyframe segment editor.
- `+135.xml`: cover-frame strip/selection.
- `+214.xml`, `+217.xml`, `+238.xml`, `+243.xml`: video preview and playback controls.
- `+242.xml`: generic property slider row.
- `+241.xml`: audio copyright/status panel.
- `+218.xml`: property adjustment panel with seek bars and colour selection.

## MS Video Editor implementation status

### Existing in the app
- Main video preview using Media3 ExoPlayer.
- Multiple imported main-video clips in a sequential timeline.
- Video ruler and playhead.
- Audio clip model, playback synchronization, audio split/trim/delete and volume.
- Text overlay preview.
- Shared horizontal scroll state for the ruler and the existing video/audio/text timeline rows.
- Basic transition selection and preview.

### Added in this milestone
- `timeline/TimelineModels.kt`: UI-independent project, track and clip models.
- Track kinds defined for main video, PIP video, audio, audio effects, text, stickers, effects and filter/adjustment layers.
- Basic clip move, trim, split, track add/remove/replace operations and project duration calculation.

### Planned implementation order
1. Integrate the new track models with the existing editor state without removing current functionality.
2. Add synchronized PIP, sticker, effect, filter/adjustment and audio-effect lanes to the timeline.
3. Add per-track clip selection, move, trim, split, delete, lock, mute and visibility controls.
4. Add keyframe editing and connect it to preview properties.
5. Extend preview composition for PIP, text, stickers, effects and transitions.
6. Connect the project timeline to a real export/render pipeline and verify exported output.

## Engineering constraints

- Keep MS Video Editor separate from MS Editor Media Hub and MS App Store.
- Preserve the existing black/emerald/gold visual theme unless the user asks for a redesign.
- Add features incrementally; do not silently remove or rewrite working features.
- UI controls must not be presented as functional until their editing and export behavior is implemented and tested.
- Use original MS Video Editor code. Do not copy CapCut proprietary source or decompiled implementation.
