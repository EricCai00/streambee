package com.kelsos.mbrc.feature.content.playlists

import com.kelsos.mbrc.core.common.data.Mapper
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.playlist.Playlist
import com.kelsos.mbrc.core.data.playlist.PlaylistEntity
import com.kelsos.mbrc.core.networking.dto.PlaylistDto
import com.kelsos.mbrc.core.networking.dto.TrackDto

object PlaylistEntityMapper : Mapper<PlaylistEntity, Playlist> {
  override fun map(from: PlaylistEntity): Playlist = Playlist(
    name = from.name,
    url = from.url,
    id = from.id
  )
}

object PlaylistDtoMapper : Mapper<PlaylistDto, PlaylistEntity> {
  override fun map(from: PlaylistDto): PlaylistEntity = PlaylistEntity(
    name = from.name,
    url = from.url
  )
}

fun PlaylistEntity.toPlaylist(): Playlist = PlaylistEntityMapper.map(this)

fun PlaylistDto.toEntity(): PlaylistEntity = PlaylistDtoMapper.map(this)

fun TrackDto.toTrack(): Track = Track(
  artist = artist,
  title = title,
  src = src,
  trackno = trackno,
  disc = disc,
  albumArtist = albumArtist,
  album = album,
  genre = genre,
  year = year,
  id = 0,
  loved = loved
)
