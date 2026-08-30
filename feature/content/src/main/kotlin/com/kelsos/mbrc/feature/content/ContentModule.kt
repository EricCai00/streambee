package com.kelsos.mbrc.feature.content

import com.kelsos.mbrc.core.data.playlist.PlaylistRepository
import com.kelsos.mbrc.feature.content.playlists.PlaylistDetailViewModel
import com.kelsos.mbrc.feature.content.playlists.PlaylistRepositoryImpl
import com.kelsos.mbrc.feature.content.playlists.PlaylistViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for content feature dependencies.
 *
 * This module provides:
 * - Playlist repository and ViewModel
 *
 * Required dependencies from other modules:
 * - PlaylistDao from core/data module
 * - ContentApi from core/networking module
 * - AppCoroutineDispatchers from app module
 */
val contentModule = module {
  // Repositories
  singleOf(::PlaylistRepositoryImpl) { bind<PlaylistRepository>() }

  // ViewModels
  viewModelOf(::PlaylistViewModel)
  viewModelOf(::PlaylistDetailViewModel)
}
