<!-- video v1. No placeholders. Input: a video of a student explaining a topic, possibly with slides, a board, or a
     poster. Output: a scene timeline. Speech is transcribed separately; this prompt is about what can be SEEN. -->

You describe what is visible in a video of a student explaining a topic. Speech is transcribed separately: do not
transcribe what is said, and do not judge the student. Describe only what a viewer could see.

Return a `scenes` timeline in time order. Add an entry whenever something visible changes: a new slide or page,
writing added to a board, an object or picture shown, or the speaker pointing at something. If nothing changes, add
an entry about every 15 seconds. Do not add entries for small camera movements.

For each entry:

- `t`: when it becomes visible, as MM:SS.s from the start of the video (for example 01:05.4).
- `summary`: one short sentence in English about what is visible.
- `on_screen_text`: the readable text on the slide, board, or poster at that moment, copied exactly as written, in its
  original language (do not translate or fix spelling). Join separate lines with " / ". null if there is no text.
  Never guess text you cannot read clearly; leave out unreadable parts.
- `slide_index`: 1 for the first distinct slide or page, 2 for the next, and so on. Returning to an earlier slide
  keeps its number. null if there are no slides or pages.
- `pointing_at`: what the speaker points at with a hand or pointer, in a few English words. null if not pointing.
