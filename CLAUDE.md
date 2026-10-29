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
3. **Merge by time**, then run the **neutral analyzer** (no role-play, prompt `prompts/analyzer.md`): terms with a
   difficulty label (everyday / school / specialist), claims and their reasons, said-vs-shown mismatches, and `steps`
   (conclusions with the premises they depend on). For `steps` the analyzer lists only premises a listener at the
   selected grade would not know, at most 2 per step, about 10 steps per 3 minutes. Then validate in code (see
   "Quote validation" below).
4. **Personas**: CODE decides where hands go up (`PersonaRules`); the persona model call only writes `reason`, `fix`,
   and the kid's `question` for each trigger. No model-proposed extra flags in v1. The "what has been defined so far"
   ledger is kept in CODE, not in the model.
    - Classmate: undefined terms, dense slides, skipped steps (a step with any premise whose `stated_at` is null or
      later than the step's `t`; the quote is the step's `claim_quote`).
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
  "mismatches": [{"t": 33.0, "type": "shown_not_said|said_not_shown|dense_slide|pointing_unnamed", "quote": "..."}],
  "steps": [{"id": "s1", "t": 40.0, "claim_quote": "exact transcript words",
             "requires": [{"text": "premise in one sentence", "stated_at": null, "stated_quote": null}]}]
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

### Quote validation (`QuoteValidator`)

- **Normalize before matching**, always: keep letters and digits only, lowercased. Whitespace, punctuation, and quote
  marks never decide a match. Adjacent transcript segments are joined, so a quote may span a segment boundary.
- Reject any PersonaEvent whose `quote` is empty or not found in the transcript/visual text.
- Step quotes are grounded in the transcript only, never in on-screen text:
    - `claim_quote` not found: drop the step.
    - `stated_at` null: the premise was never stated; keep it (this is what raises a skipped-step hand).
    - `stated_at` set: search for `stated_quote` only in segments within ±10 s of `stated_at`. If it is missing or not
      found, DROP the premise (do not flag) and log the step id. A paraphrase cannot be told apart from a premise that
      was never said, so a miss must not raise a hand.
- Logs carry step ids and times only, never transcript text.

### Grade

The listener grade fills `{{grade}}` in `prompts/analyzer.md` (via `PromptTemplate.analyzer`). It comes from the grade
picker on the setup screen (`SetupRoute`): options 초등학교 1-6학년, 중학교 1-3학년, 고등학교 1-3학년, and
"일반 성인 (demo)"; default "중학교 2학년"; the last choice is remembered in SharedPreferences (`PrefsGradeStore`).
`Grade.resolve` never returns an empty grade: blank or unknown values fall back to the default. Mock mode always uses
the default, because the fixtures were written for it.

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

Model: one Flash model for every stage (audio/video input, structured outputs), set in
`shared/src/commonMain/kotlin/dev/cocoa/speech/GeminiModels.kt`. The intended model is `gemini-3.8-flash`, but it is
**temporarily `gemini-3.6-flash`**: on 2026-10-29, 3.8 Flash took ~48 s for a one-line prompt on our key, while 3.6
Flash answered the same JSON-schema request in ~3 s. Re-time 3.8 Flash before demo day and switch back if it is fast.
`GeminiClient` uses a 120 s request timeout; a timeout is a retryable stage failure. The developer chose to skip the Day 1 benchmark; the
transcription notes below are still unverified, so check them while building Layer 1.

Checkpoints: (skipped) Day 1 benchmark (Korean 60 s and 3 min clips: transcription speed, timestamp precision, fillers, upload
time; plus screen/board/poster clips: can it read the text?). Day 2 go/no-go vs. laundry app. Day 4 demo path works end
to end on a 60 s clip.

## Rules

- **Privacy**: users are students, possibly minors. Delete uploaded media from the Gemini Files API right after
  processing (third-party docs report ~48 h retention; verify). Layer 1 audio (16 kHz mono AAC, ~1 MB for 3 min) is
  sent inline with `store: false`, so nothing is uploaded to the Files API; the local file is deleted as soon as it is
  read into memory. `allowBackup` is off. Keep only transcript, pins, and report on the device. No
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

Run from the repo root (Git Bash or PowerShell). `local.properties` (gitignored) must contain
`sdk.dir=C:/Users/cocoa/AppData/Local/Android/Sdk`; use forward slashes, because backslashes are escape characters there.

- Unit tests (commonTest + androidHostTest): `./gradlew :shared:testAndroidHostTest`
- Debug APK: `./gradlew :androidApp:assembleDebug` → `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Install and launch on a connected device:
  `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk` then
  `adb shell am start -n dev.cocoa.speech/.MainActivity`

Test results land in `shared/build/test-results/`. In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb shell` commands
that take device paths like `/sdcard/...`, or Git Bash rewrites them.

## Open questions

- Kid persona is observational only (no voice Q&A). Add tap-to-answer later?
- Framing: start with room view (presenter + screen) only, or also a screen-only option?
- Is per-word confidence available from the transcription model? If not, how do we mark uncertain words?