package com.kelsos.mbrc.feature.playback.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class TimedLyricLine(val text: String, val timestampMs: Long)

internal data class ParsedLyrics(
  val plainLines: List<String> = emptyList(),
  val timedLines: List<TimedLyricLine> = emptyList()
)

internal fun parseLyrics(lines: List<String>): ParsedLyrics {
  if (lines.isEmpty()) return ParsedLyrics()

  val offsetMs = lines.firstNotNullOfOrNull { line ->
    OFFSET.matchEntire(line.trim())?.groupValues?.get(1)?.toLongOrNull()
  } ?: 0L

  val timedLines = buildList {
    lines.forEach { rawLine ->
      val line = rawLine.trimEnd()
      if (METADATA.matches(line.trim())) return@forEach

      val timestamps = TIMESTAMP.findAll(line).toList()
      if (timestamps.isEmpty()) return@forEach

      val text = TIMESTAMP.replace(line, "")
        .let { INLINE_TIMESTAMP.replace(it, "") }
        .trim()
      timestamps.forEach { match ->
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
          1 -> fraction.toLong() * 100L
          2 -> fraction.toLong() * 10L
          3 -> fraction.toLong()
          else -> 0L
        }
        val timestamp = (minutes * 60_000L + seconds * 1_000L + fractionMs + offsetMs)
          .coerceAtLeast(0L)
        add(TimedLyricLine(text = text, timestampMs = timestamp))
      }
    }
  }.sortedBy { it.timestampMs }

  if (timedLines.isNotEmpty()) {
    return ParsedLyrics(
      plainLines = timedLines.map { it.text },
      timedLines = timedLines
    )
  }

  val plainLines = lines
    .filterNot { METADATA.matches(it.trim()) }
    .map { INLINE_TIMESTAMP.replace(TIMESTAMP.replace(it, ""), "").trimEnd() }
    .dropLastWhile(String::isEmpty)
  return ParsedLyrics(plainLines = plainLines)
}

internal fun activeLyricLineIndex(lines: List<TimedLyricLine>, positionMs: Long): Int =
  lines.indexOfLast { it.timestampMs <= positionMs }

private val TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val INLINE_TIMESTAMP = Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>")
private val METADATA = Regex(
  "\\[(?:ar|ti|al|by|offset|re|ve|length):.*]",
  RegexOption.IGNORE_CASE
)
private val OFFSET = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
