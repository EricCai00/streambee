package com.kelsos.mbrc.feature.library.compose.tabs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryIndexLabelTest {
  @Test
  fun `alphabetic label uses first letter`() {
    assertThat(alphabeticIndexLabel("  beatles")).isEqualTo("B")
  }

  @Test
  fun `alphabetic label can ignore leading the`() {
    assertThat(alphabeticIndexLabel("The Cure", ignoreLeadingThe = true)).isEqualTo("C")
  }

  @Test
  fun `alphabetic label groups punctuation and numbers`() {
    assertThat(alphabeticIndexLabel("1979")).isEqualTo("#")
    assertThat(alphabeticIndexLabel("!Song")).isEqualTo("#")
  }

  @Test
  fun `year label extracts four digit year`() {
    assertThat(yearIndexLabel("Released 1997-03-04")).isEqualTo("1997")
  }

  @Test
  fun `year label handles unknown values`() {
    assertThat(yearIndexLabel("Unknown")).isEqualTo("—")
  }
}
