package com.kelsos.mbrc.core.networking

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.networking.data.DeserializationAdapterImpl
import com.kelsos.mbrc.core.networking.dto.AlbumCoverDto
import com.kelsos.mbrc.core.networking.dto.CoverDto
import com.kelsos.mbrc.core.networking.protocol.base.Protocol
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApiBaseTest {

  @Test
  fun `getAll closes connection when collector stops after first response`() = runTest {
    val adapter = DeserializationAdapterImpl(Moshi.Builder().build())
    val requestManager = mockk<RequestManager>()
    val connection = mockk<ActiveConnection>(relaxed = true)
    val cover = CoverDto(status = ApiStatus.SUCCESS, cover = null, hash = null)

    coEvery { requestManager.openConnection() } returns connection
    coEvery { requestManager.request(connection, any()) } returns
      "{\"context\":\"${Protocol.LibraryCover.context}\",\"data\":{\"status\":200,\"cover\":null,\"hash\":null}}"

    val result = ApiBase(adapter, requestManager).getAll(
      request = Protocol.LibraryCover,
      payload = listOf(AlbumCoverDto("Artist", "Album", null)),
      clazz = CoverDto::class,
      progress = null
    ).first()

    assertThat(result.response).isEqualTo(cover)
    verify(exactly = 1) { connection.close() }
  }
}
