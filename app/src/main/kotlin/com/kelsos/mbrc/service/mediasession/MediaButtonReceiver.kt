package com.kelsos.mbrc.service.mediasession

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import timber.log.Timber

// TODO: Check if this receiver is still needed. Media3's MediaSession handles media button
// Media3 routes button events to the app-owned local MediaSession.
class MediaButtonReceiver :
  BroadcastReceiver(),
  KoinComponent {
  override fun onReceive(context: Context, intent: Intent) {
    Timber.v("Incoming %s", intent)
    if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
      return
    }
    getKoin().get<MediaIntentHandler>().handleMediaIntent(intent)
  }
}
