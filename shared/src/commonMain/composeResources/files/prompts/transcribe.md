<!-- transcribe v1. No placeholders. Input: one audio clip of a student explaining a topic. -->

Transcribe the audio verbatim. The speech is Korean (ko-KR) and may mix in English words; write English words in Latin
letters as they were spoken.

- Verbatim means verbatim: keep filler words (음, 으음, 어, 어어, 아, 에, 엄, um, uh), repetitions, and false starts
  exactly as spoken. Do not clean up grammar, summarize, translate, or add punctuation that changes meaning.
- Split the speech into segments at sentence or clause boundaries, roughly 2 to 8 seconds each.
- `start` and `end` are when the segment starts and ends, as MM:SS from the start of the audio.
- Segments are in time order and do not overlap. Silence gets no segment.
- If nothing is spoken, return an empty `segments` list. Never invent speech.
