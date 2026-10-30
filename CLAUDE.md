# CLAUDE.md — Confused Listener (working title)

## What this is

An Android app where a student explains a topic out loud (optionally with slides, a board, or a poster). AI "listeners"
watch and hear the recording and mark the exact seconds where they would get lost, and why. Three listeners: a
classmate, a teacher, and a curious younger kid.

Context: team project on "Solving problems using AI", demoed live to Google engineers. One developer, one week.

**Status: chosen.** The team voted for this app on 2026-10-29 (the laundry drying-time fallback is dropped). The
current screens are test scaffolding; a full UI overhaul is planned, spec first.

## What makes it different (keep this in mind when deciding scope)

- Output is a **comprehension map pinned to timestamps**, not a delivery score card. Speech coaches (Yoodli, Orai, etc.)
  already own delivery scoring; do not build confidence, loudness, or eye-contact scores.
- Listeners react to **what is said vs. what is shown** (needs video).
- Every hand-raise must quote evidence. No quote, no hand-raise.

## Stack

- Kotlin Multiplatform + Compose Multiplatform, **Android-first**. Camera and recording code lives in `androidMain`
  (CameraX).
- UI: Material 3 Expressive (`theme/AppTheme.kt`: `MaterialExpressiveTheme`, expressive motion scheme). All text uses
  Pretendard v1.3.9 (SIL OFL 1.1, four bundled weights in `composeResources/font`, license in
  `composeResources/files/licenses`); do not fall back to the system font. Listener colors are fixed
  (`ListenerColors`), not themed.
- Navigation: Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.1.2, added 2026-10-30 at the
  developer's request). The back stack lives in `App.kt`; back never returns to processing or a finished recording.
- Gemini API for everything AI: transcription, video understanding, analyzer and persona calls (structured JSON output).
- **No backend server.** Calls go straight from the app. Keep the API key out of git (`local.properties` / BuildConfig);
  a shipped APK would expose it, so this is demo-only.
- Model IDs change often and some are preview. Put every model ID in ONE config file and verify names and features
  against current Gemini docs. Do not guess API fields.

## Pipeline

1. **Record**: rear camera, up to 3 min (soft warning 2:30, auto-stop 3:00), minimum 20-30 s, pause supported. Compress
   video on device. Extract the audio track separately. Implemented: microphone-only (`AndroidAudioRecorder`) and
   camera (`CameraCapture`, CameraX at HD 720p, 2.5 Mbps, see "PDF pages on screen" for why not SD; the AAC track is
   copied out to `.m4a` without re-encoding). 3 minutes of video is ~56 MB, so video goes through the Files API
   (`GeminiFiles`: upload, wait for ACTIVE, use, then DELETE in a `finally`). The local video file lives only until its
   run ends.
2. **In parallel**
    - Audio -> transcript with timestamps (Korean, ko-KR; handle Korean-English mixing).
    - Video -> scene timeline (what is on screen, slide changes, pointing, MM:SS). Prompt `prompts/video.md`;
      `SceneDescriber` turns each scene into a `Visual` whose summary carries the on-screen text verbatim (and the matched
      PDF page's text, see "Speaker's material"), so screen quotes can be grounded. If video fails, the run continues audio-only and `ResultsReport.video` is `FAILED`.
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
6. **Results screen** (designed on the "Listener characters" canvas, board D; M3 Expressive theme and motion): replay
   on top with a subtitle; a draggable scrubber with listener-colored marks, showing a small pill (time + faces) while
   dragging; the listener stage (hands rise while playback is at their moments); speech bubbles with the reason or the
   kid's question; a centered pager of the top 3 fixes (choosing one seeks to it); a thin pace graph on the scrubber's time
   axis (a bar per sentence, taller when faster, gaps for pauses, dots for fillers) with the average syllables per
   second, descriptive only. No separate questions tab or heatmap. When no hand went up (board H), the stage area shows
   the three listeners nodding, one true-by-construction check line per listener, and "다른 주제로 녹음하기".

## Transcription notes (verify on Day 1)

- Sentence-level timestamps are enough for pins. Word-level timestamps reportedly degrade accuracy; use only if the
  benchmark shows it is fine.
- Check that "smart dictation" does NOT strip filler words and false starts, or the pace/filler strip will be empty.
  Turn it off if needed.
- Vocabulary hints from the user's pasted notes (best with ~100 terms) may not combine with timestamps. Test it.
- Video is sampled at ~1 frame per second by default. Fine for slide changes; fast gestures may be missed. For Gemini 3
  video, default, low and medium media resolution are all 70 tokens per frame; high is 280 (docs, 2026-10-30). The
  scene call sends the video part with `mediaResolution.level = MEDIA_RESOLUTION_HIGH` for slide text.

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

### Screens

Designed on the "Listener characters" canvas (boards D-M). Flow: onboarding (first run only, flag in `PrefsGradeStore`)
-> home (past runs grouped by day, FAB "새 발표") -> setup (topic, grade, optional memo; video is the primary button)
-> record (camera or voice; a refused permission shows a settings screen, camera refused offers voice only) ->
processing -> results (saved to history when the run finishes). "발표 자료 불러오기" (system picker, `ActivityMaterialPicker`) takes a
PDF or a text file: a text file fills the memo; a PDF is not read on the device but copied to `files/material/deck.pdf`
(one kept, replaced on the next pick) and sent to Gemini with the run (see "Speaker's material" below).
PPTX is not accepted: Gemini documents PDF only, and on-device conversion needs a paid library or a server. Results from a video run whose scene call failed show
a dismissible notice instead of an error.

### Speaker's material

The setup memo (`TalkSetup.memo`) and the attached PDF (`TalkSetup.deckPath`) go to the analyzer and persona calls as
`Material` (memo as the `notes` input field, PDF as an inline `application/pdf` part before the text; analyzer v7,
persona v4). Listeners never heard the memo, so it never defines a term, states a premise, or gives a reason. The
analyzer uses it to recognize terms and to pick premises from the speaker's own reasoning instead of guessing; a fix
may point to it and quote it ("메모에 적어 둔 ‘X’의 뜻을 …").

PDF pages on screen (decided 2026-10-30): on a video run the PDF also goes to the scene call (video v3), which names
the page on screen per scene (`deck_page`), copies that page's text from the PDF (`deck_text`), and marks a slide that
is still building up (`deck_partial`; the PDF holds the finished slide). The camera reads titles but often not body
text, so the video says WHEN a page is shown and the PDF says WHAT is on it. A matched page counts
as shown: its text goes into the `Visual` summary ("PDF page N: …"), so the analyzer can use it for `dense_slide`,
`shown_not_said` and `evidence_on_screen`, and `QuoteValidator` grounds those quotes. Pages no scene names were never
shown. The match is the model's judgment; code keeps it only if a readable word in the frame (`on_screen_text`) is on
the matched page, and a picture-only slide keeps the model's match. Each persona trigger gets the page on screen at its
time from code, and a fix names only that page. `dense_slide` is not raised on a partial scene (analyzer v8, and code
drops such triggers in `Scene.withoutDenseOnPartialSlides`).

Tested 2026-10-30 in the Gemini app (thinking LOW, a 13-page deck with morph transitions and builds, not yet through our
API): pages and builds matched on a 1080p clip and a 720p copy, and `on_screen_text` held only readable text. On a 480p
1 Mbps copy (the old SD setting) the pages still matched, but small text the video could not show was copied into
`on_screen_text` from the PDF, which makes `deck_partial` and the frame-word check meaningless. Hence HD recording and
high media resolution.

Memo and PDF go inline with `store: false` (PDFs up to 50 MB inline, checked 2026-10-30), so nothing is left to
delete. The PDF is sent up to three times (scene, analyzer, persona calls). Logs carry sizes and times only.

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
- **Deferred**: custom personas, extra preset personas, live/streaming feedback, PDF slide import,
  body-language scoring.

Model: one Flash model for every stage (audio/video input, structured outputs), set in
`shared/src/commonMain/kotlin/dev/cocoa/speech/GeminiModels.kt`. The intended model is `gemini-3.8-flash`, but it is
**temporarily `gemini-3.6-flash`**: on 2026-10-29, 3.8 Flash took ~48 s for a one-line prompt on our key, while 3.6
Flash answered the same JSON-schema request in ~3 s. Re-time 3.8 Flash before demo day and switch back if it is fast.
`GeminiClient` uses a 120 s request timeout; a timeout is a retryable stage failure. The developer chose to skip the Day 1 benchmark; the
transcription notes below are still unverified, so check them while building Layer 1.

Checkpoints: (skipped) Day 1 benchmark (Korean 60 s and 3 min clips: transcription speed, timestamp precision, fillers, upload
time; plus screen/board/poster clips: can it read the text?). Day 2 go/no-go vs. laundry app (done 2026-10-29: go). Day 4 demo path works end
to end on a 60 s clip.

## Rules

- **Privacy**: users are students, possibly minors. Delete uploaded media from the Gemini Files API right after
  processing (the Files API keeps files 48 h; verified in the docs 2026-10-29). Layer 1 audio (16 kHz mono AAC, ~1 MB for 3 min) is
  sent inline with `store: false`. Video goes through the Files API and is deleted there right after the scene call.
  Replay (developer decision, 2026-10-29): the latest recording (audio or video) is kept in app-private storage
  (`files/replay`, `ReplayFiles`) so the results screen can replay it with synced pins; only one is kept, and starting
  a new recording deletes it. `allowBackup` is off. Otherwise keep only transcript, pins, and report on the device:
  history (home screen) is one JSON file per run in `files/history` (`HistoryEntry`, `FileHistoryStore`) holding the
  topic, grade, memo, transcript segments, grounded events and visuals, never media; only the newest entry may use the
  kept recording. No
  accounts. Never send audio, video, or transcripts to analytics or logs. Check consent rules for minors before any
  public release.
- **Tone**: critique the explanation, never the student. No labels like "nervous" or "unconfident".
- **Clarity, never truth** (decided 2026-10-29): listeners judge whether the explanation is clear, never whether it is
  correct. The model may not know the topic (recent, local, class-specific, the student's own data), so no stage may
  call a statement wrong or add facts the speaker did not say. Hand-raise rules are structural (undefined term, unstated
  premise, claim without a reason). A `fix` names what is missing and where ("‘A’와 ‘B’ 사이에 이유를 한 문장"),
  using only the speaker's words, on-screen text, and the speaker's own material (memo, attached PDF); it never supplies
  a definition, cause, or example from anywhere else.
  Analyzer `steps` list a premise only when the conclusion logically depends on it.
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

Debug builds only (the API key is on the free tier, 20 requests per day; one run is 3 requests, or 5 with
`PipelineConfig.PARALLEL_PERSONA_CALLS`):

- Analyze a WAV instead of the microphone: `pwsh tools/make-sample.ps1 -Push` speaks
  `fixtures/sample-seasons/script.txt` with Windows TTS (byte-identical each time, so the answer cache hits) and copies it
  into the app; tap "샘플 파일로 분석 (디버그)". After the run the log prints `Expectations: n/m met; missed=[ids];
  violated=[ids]`, checked against the hand-written `fixtures/sample-seasons/expected.json`.
- Model answers are cached on the device by request content (`FileResponseCache`), so rerunning the same input spends
  no requests; a changed prompt re-spends only the stages it affects. Video scenes are cached by the video's bytes,
  checked before uploading, so a cached video is not uploaded again. The cache survives `adb install -r`; clear it with
  `adb shell run-as dev.cocoa.speech rm -r files/gemini-cache`.
- "마지막 녹음 다시 분석 (디버그)" re-runs the pipeline on the kept latest recording with byte-identical input, so a
  real recording can be re-tested for free after changing rules, ranking, or UI. Testing habit: record once, then
  re-analyze; only record again when the recording itself is what is being tested.
- Token counts per call are logged as `GeminiUsage:` (counts only). Watch with `adb logcat System.out:I *:S`.

Test results land in `shared/build/test-results/`. In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb shell` commands
that take device paths like `/sdcard/...`, or Git Bash rewrites them.

## Open questions

- Memo as vocabulary hints for transcription: not done; the transcribe call does not get the memo yet.
- Kid persona is observational only (no voice Q&A). Add tap-to-answer later?
- Framing: start with room view (presenter + screen) only, or also a screen-only option?
- Is per-word confidence available from the transcription model? If not, how do we mark uncertain words?