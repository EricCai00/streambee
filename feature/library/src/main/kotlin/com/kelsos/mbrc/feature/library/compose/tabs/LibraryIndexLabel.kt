package com.kelsos.mbrc.feature.library.compose.tabs

private val FourDigitYear = Regex("\\d{4}")

internal fun alphabeticIndexLabel(value: String, ignoreLeadingThe: Boolean = false): String {
  val trimmed = value.trim()
  val sortable = if (ignoreLeadingThe && trimmed.startsWith("the ", ignoreCase = true)) {
    trimmed.drop(4).trimStart()
  } else {
    trimmed
  }
  val first = sortable.firstOrNull() ?: return "#"
  return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

internal fun yearIndexLabel(value: String): String = FourDigitYear.find(value)?.value ?: "—"
