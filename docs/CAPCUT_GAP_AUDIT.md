# CapCut-to-MS Video Editor implementation audit

This audit is based on the current main-branch Kotlin source and the supplied CapCut archives. It is an engineering checklist, not a claim that the features are already implemented.

## Non-negotiable product constraint
Keep the current MS Video Editor visual identity, colors, screen layout, and navigation. Rebuild the editing behavior and underlying media pipeline; do not redesign the app to look like CapCut.

## What the supplied CapCut package exposes
The decompiled resource archive contains timeline components including MultiTrackLayout, TimeRulerScroller, TrackFlexibleRuler, FrameScroller, TimeCursorView, TrackGroup, TopTrackContainer, TopPipTrack, TopAudioTrack, TopTextTrack, TopEffectTrack, TopFilterAdjustTrack, PipTrackLine, TextTrackLine, EffectTrackLine, AudioTrackLine and AudioEffectTrackLine. Other DEX/resource strings indicate keyframes, masks, chroma key, stabilization, retouch, color curves, captions, transitions and overlay handling. The archives are compiled/decompiled app material, not original Kotlin/Java source code; behavior must be reimplemented in MS Video Editor.

## Verified current MS Video Editor gaps

| Area | Current source behavior | Required engineering work |
|---|---|---|
| Main video timeline | Imported videos are represented as sequential clips, but the ExoPlayer playlist remains the original URI list. Timeline split currently changes the UI list only. | One authoritative clip model must drive preview, seek, trim, split, reorder, playback and export. |
| Export | VideoExport exports one source URI with a trim range. | Build a project-level render pipeline for multiple clips, overlays, effects, transitions and audio. |
| Audio | Separate ExoPlayer previews an audio URI; audio split/trim/volume state is local UI state. | Persist audio clip edits and mix them into the final composition, including volume and fades. |
| Speed | PlaybackParameters changes preview playback only. | Store per-clip speed and apply it to the export media item. |
| Text | Text is drawn over the preview only. | Add editable text clip timing/style and render text into exported frames. |
| PIP / overlay | A PIP lane is currently an empty add-row; no PIP clip compositor is connected. | Add overlay clip model, preview layer, transform controls and compositor sequence. |
| Stickers / effects / audio FX / adjust | Timeline rows are currently empty add-row controls. Some menu items have no connected action. | Implement each layer as real project data with selection, timing, properties, preview and export behavior. |
| Transitions | Selection state and a simple preview animation exist around a cut. | Apply transition effects to the actual adjacent clips in preview and export. |
| Keyframes | A toolbar action exists, but no keyframe property model/editor is wired into rendering. | Add time-based property curves and evaluate them consistently in preview/export. |
| Project persistence | Recent source URIs are stored in preferences; editing operations are not saved as a complete project document. | Save and restore the complete timeline/project model and asset references. |
| Undo/redo | Toolbar currently seeks by one second; it is not an edit history. | Implement command-based undo/redo for edits. |

## Implementation sequence

1. **Project core:** introduce one serializable project state with tracks, clips, source ranges, timeline positions, speed, volume, transforms, opacity and enabled/locked/muted state. Make editing operations update this state.
2. **Main-track correctness:** make multi-clip playback, split, trim, move, reorder and delete operate on the same clip model.
3. **Overlay system:** implement PIP and image/sticker/text overlays with selection, move, resize, pinch, timing and persistence.
4. **Audio engine:** combine multiple audio clips with trim, split, move, volume and fade controls.
5. **Effects and transitions:** add filter/adjustment, effect, mask/chroma and transition processing with matching preview/export results.
6. **Keyframes and advanced tools:** add keyframe curves and then stabilization, captions, retouch and other advanced tools where the Android implementation can support them.
7. **Export and validation:** export the complete composition, verify audio/video sync and compare rendered output against the project preview.

## Acceptance rules
- Do not change the approved MS Video Editor theme or layout without explicit permission.
- A control is not complete merely because it is visible or changes preview state.
- Each feature is complete only when it edits project data, survives reopening, previews correctly and appears correctly in the exported video.
- Do not claim a feature is complete until a build succeeds and its behavior is tested.
