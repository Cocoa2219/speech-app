# CLAUDE.md — Confused Listener (working title)

## What this is

An Android app where a student explains a topic out loud (optionally with slides, a board, or a poster). AI "listeners"
watch and hear the recording and mark the exact seconds where they would get lost, and why. Three listeners: a
classmate, a teacher, and a curious younger kid.

Context: team project on "Solving problems using AI", demoed live to Google engineers. One developer, one week.

**Status: proposed. The team has not voted yet.** Fallback is a laundry drying-time prediction app that shares the same
Android + Kotlin Multiplatform shell. Do not sink time into polish before the Day 2 go/no-go.

## What makes it different (keep this in mind when deciding scope)

- Output is a **comprehension map pinned to timestamps**, not a delivery score card. Speech coaches (Yoodli, Orai, etc.)
  already own delivery scoring; do not build confidence, loudness, or eye-contact scores.
- Listeners react to **what is said vs. what is shown** (needs video).
- Every hand-raise must quote evidence. No quote, no hand-raise.

## Stack

- Kotlin Multiplatform + Compose Multiplatform, **Android-first**. Camera and recording code lives in `androidMain`
  (CameraX).
- Gemini API for everything AI: transcription, video understanding, analyzer and persona calls (structured JSON output).
- **No backend server.** Calls go straight from the app. Keep the API key out of git (`local.properties` / BuildConfig);
  a shipped APK would expose it, so this is demo-only.
- Model IDs change often and some are preview. Put every model ID in ONE config file and verify names and features
  against current Gemini docs. Do not guess API fields.

## Pipeline

1. **Record**: rear camera, up to 3 min (soft warning 2:30, auto-stop 3:00), minimum 20-30 s, pause supported. Compress
   video on device. Extract the audio track separately.
2. **In parallel**
    - Audio -> transcript with timestamps (Korean, ko-KR; handle Korean-English mixing).
    - Video -> scene timeline (what is on screen, slide changes, pointing, MM:SS).
3. **Merge by time**, then run the **neutral analyzer** (no role-play): terms with a difficulty label (everyday /
   school / specialist), claims and their reasons, dependencies, and said-vs-shown mismatches.
4. **Personas** run in parallel as rule filters over the analyzer output. The "what has been defined so far" ledger is
   kept in CODE, not in the model.
    - Classmate: undefined terms, skipped steps, dense slides.
    - Teacher: claims without evidence, "as you can see" with nothing visible.
    - Kid: "why?" where a claim has no reason, "what's that?" for unexplained terms or visuals. Written as the kid's own
      question.
5. **Merge and rank**: dedupe, rank fixes by how many listeners they help. Pace, pause, and filler strip comes from
   transcript timing; it is descriptive only.
6. **Results screen**: replay with synced pins, heatmap bar, pace strip, tabs "Fixes (top 3)" and "Questions your
   listeners would ask".

## Transcription notes (verify on Day 1)

- Sentence-level timestamps are enough for pins. Word-level timestamps reportedly degrade accuracy; use only if the
  benchmark shows it is fine.
- Check that "smart dictation" does NOT strip filler words and false starts, or the pace/filler strip will be empty.
  Turn it off if needed.
- Vocabulary hints from the user's pasted notes (best with ~100 terms) may not combine with timestamps. Test it.
- Video is sampled at ~1 frame per second by default. Fine for slide changes; fast gestures may be missed. Use default
  media resolution (not low) when on-screen text matters.

## Draft data contracts (generate Kotlin data classes with kotlinx.serialization; ask before changing)

```json
// AnalyzerOutput
{
  "segments": [{"t_start": 0.0, "t_end": 4.2, "text": "..."}],
  "terms": [{"term": "...", "t": 12.0, "level": "everyday|school|specialist", "defined_at": null}],
  "claims": [{"t": 20.5, "text": "...", "has_reason": false, "evidence_on_screen": null}],
  "visual": [{"t": 18.0, "summary": "...", "slide_index": 2}],
  "mismatches": [{"t": 33.0, "type": "shown_not_said|said_not_shown|dense_slide|pointing_unnamed", "quote": "..."}]
}
// PersonaEvent
{
  "persona": "classmate|teacher|kid",
  "t": 42.0,
  "type": "undefined_term|skipped_step|no_evidence|why_question|whats_that|dense_slide",
  "quote": "exact phrase from transcript or on-screen text",
  "reason": "one sentence",
  "fix": "one-line suggestion",
  "question": "kid only: the question in the kid's own words"
}
```

Reject any PersonaEvent whose `quote` is empty or not found in the transcript/visual text.

## Build order (layers)

- **Layer 0, mock mode first**: fixture JSON + a sample recording so the UI never depends on live API latency. This is
  also the demo safety net if the network fails.
- **Layer 1, audio only, end to end**: record -> transcribe -> analyzer -> 3 personas -> results. The demo path must
  work before any video work.
- **Layer 2, video**: scene timeline, visual mismatches, video replay with synced pins.
- **Layer 3, stretch (only if ahead at the Day 4 checkpoint)**: second framing option, keyframe zoom / targeted re-look
  with higher fps around "here / this", low-confidence word check and transcript editor.
- **Deferred**: custom personas, history, extra preset personas, live/streaming feedback, PDF slide import,
  body-language scoring.

Checkpoints: Day 1 benchmark (Korean 60 s and 3 min clips: transcription speed, timestamp precision, fillers, upload
time; plus screen/board/poster clips: can it read the text?). Day 2 go/no-go vs. laundry app. Day 4 demo path works end
to end on a 60 s clip.

## Rules

- **Privacy**: users are students, possibly minors. Delete uploaded media from the Gemini Files API right after
  processing (third-party docs report ~48 h retention; verify). Keep only transcript, pins, and report on the device. No
  accounts. Never send audio, video, or transcripts to analytics or logs. Check consent rules for minors before any
  public release.
- **Tone**: critique the explanation, never the student. No labels like "nervous" or "unconfident".
- **Language**: speech is Korean (ko-KR). Prompts and JSON keys in English. Quotes stay in the original language. UI
  language: confirm with the developer.
- **Failure handling**: retry button per stage; if video processing fails, fall back to Layer 1 results.
- **Latency**: target results within ~20 s for a 60 s clip. This is an unmeasured guess; the Day 1 benchmark decides.

## Working agreements

- Spec first, then vertical slices: one working path before adding features. Small commits.
- Suggested layout: shared module for models, ranking, ledger, UI; `androidMain` for camera/recording; `prompts/*.md`
  for every prompt (versioned, never inline); `fixtures/` for sample transcripts with expected pins.
- Unit-test pure logic (timestamp merge, dedupe, ranking, ledger rules, quote validation). Use `fixtures/` for manual
  prompt regression.
- No new dependencies, no backend, no auth without asking.
- Do not invent API fields or model names. Check the docs, and leave a TODO if unsure.

## Commands

TODO: fill in build, run, and test commands once the project exists. Do not guess.

## Open questions

- Kid persona is observational only (no voice Q&A). Add tap-to-answer later?
- Framing: start with room view (presenter + screen) only, or also a screen-only option?
- Is per-word confidence available from the transcription model? If not, how do we mark uncertain words?