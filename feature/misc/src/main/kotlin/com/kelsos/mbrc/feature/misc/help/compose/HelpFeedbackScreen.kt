package com.kelsos.mbrc.feature.misc.help.compose

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.feature.misc.R
import com.kelsos.mbrc.feature.misc.help.FeedbackUiMessage
import com.kelsos.mbrc.feature.misc.help.FeedbackViewModel
import java.io.File
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private sealed class HelpFeedbackTab(val index: Int) {
  data object Help : HelpFeedbackTab(0)
  data object Feedback : HelpFeedbackTab(1)
}

/**
 * Immutable state for feedback content display.
 */
@Immutable
data class FeedbackContentState(
  val feedbackText: String = "",
  val includeDeviceInfo: Boolean = false,
  val includeLogInfo: Boolean = false,
  val isButtonEnabled: Boolean = true
)

/**
 * Stable interface for feedback actions to avoid recomposition.
 */
@Stable
interface IFeedbackActions {
  val onFeedbackTextChange: (String) -> Unit
  val onIncludeDeviceInfoChange: (Boolean) -> Unit
  val onIncludeLogInfoChange: (Boolean) -> Unit
  val onSendFeedback: () -> Unit
}

/**
 * Empty actions for preview/testing.
 */
object EmptyFeedbackActions : IFeedbackActions {
  override val onFeedbackTextChange: (String) -> Unit = {}
  override val onIncludeDeviceInfoChange: (Boolean) -> Unit = {}
  override val onIncludeLogInfoChange: (Boolean) -> Unit = {}
  override val onSendFeedback: () -> Unit = {}
}

@Composable
fun HelpFeedbackScreen(
  snackbarHostState: SnackbarHostState,
  onOpenDrawer: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FeedbackViewModel = koinViewModel()
) {
  val title = stringResource(R.string.nav_help)

  ScreenScaffold(
    title = title,
    snackbarHostState = snackbarHostState,
    onOpenDrawer = onOpenDrawer,
    modifier = modifier
  ) { paddingValues ->
    HelpFeedbackContent(
      viewModel = viewModel,
      modifier = Modifier.padding(paddingValues)
    )
  }
}

@Composable
private fun HelpFeedbackContent(viewModel: FeedbackViewModel, modifier: Modifier = Modifier) {
  var selectedTab by remember { mutableStateOf<HelpFeedbackTab>(HelpFeedbackTab.Help) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    PrimaryTabRow(selectedTabIndex = selectedTab.index) {
      Tab(
        selected = selectedTab is HelpFeedbackTab.Help,
        onClick = { selectedTab = HelpFeedbackTab.Help },
        text = { Text(stringResource(R.string.tab_help)) }
      )
      Tab(
        selected = selectedTab is HelpFeedbackTab.Feedback,
        onClick = { selectedTab = HelpFeedbackTab.Feedback },
        text = { Text(stringResource(R.string.common_feedback)) }
      )
    }

    when (selectedTab) {
      is HelpFeedbackTab.Help -> HelpContent(
        modifier = Modifier.weight(1f),
        versionName = viewModel.versionName
      )

      is HelpFeedbackTab.Feedback -> FeedbackContent(
        modifier = Modifier.weight(1f),
        viewModel = viewModel
      )
    }
  }
}

@Suppress("COMPOSE_UICOMPOSABLE_INVOCATION")
@Composable
private fun HelpContent(modifier: Modifier = Modifier, versionName: String) {
  LocalGuideContent(modifier = modifier, versionName = versionName)
}

/**
 * The user guide is deliberately rendered from app code so it remains available without a
 * connection to the documentation site. Keep this content aligned with the 2.2.0 feature set.
 */
@Composable
private fun LocalGuideContent(modifier: Modifier = Modifier, versionName: String) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "StreamBee User Guide",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Offline guide for StreamBee 2.2.0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Installed version: $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Stream your MusicBee library and playlists to your Android phone. MusicBee and the StreamBee Plugin provide the library and audio stream; playback, queue, lyrics, and history live on the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    item {
      GuideSection(title = "1. Get connected") {
        GuideParagraph("Install the StreamBee Android app and the matching StreamBee Plugin in MusicBee on Windows.")
        GuideBullet("Keep the phone and PC on the same trusted Wi-Fi or wired LAN. Avoid guest networks and client isolation.")
        GuideBullet("Open Connections, tap Scan, and select the discovered MusicBee computer. If discovery fails, use Add and enter the address and command port shown by the plugin.")
        GuideBullet("In MusicBee, check Edit → Preferences → Plugins to make sure StreamBee Plugin is enabled. Its settings are also available from Tools → StreamBee.")
        GuideBullet("After connecting, open Library and use Sync library. The first sync can take a while for a large collection.")
      }
    }

    item {
      GuideSection(title = "2. Play music on your phone") {
        GuideParagraph("Tap a track in Library, Playlists, or History to start phone playback. The Play on this device action makes the destination explicit.")
        GuideBullet("Play Now, Queue Next, and Queue Last build the phone's local queue. Previous and Next continue through the selected album, artist, playlist, or collection.")
        GuideBullet("Use Previous, Play/Pause, Next, the progress slider, volume, Shuffle, and Repeat from the player. Long-press Play/Pause to stop.")
        GuideBullet("File streams are seekable. An unbounded stream shows a wave indicator instead of a seek bar.")
        GuideBullet("The mini control, Android media notification, and home-screen widget keep the phone player available outside the main screen.")
      }
    }

    item {
      GuideSection(title = "3. Browse the library") {
        GuideParagraph("Library contains Genres, Artists, Albums, and Tracks. The Genres tab starts with MusicBee Genre Categories; open a category to see its genres.")
        GuideBullet("Drill down from genre to artist or album, artist to album, and album to tracks. Album year and genre are clickable links.")
        GuideBullet("Multiple-artist tags can open each artist separately. Artist rows use MusicBee artist pictures when available and a standard icon otherwise.")
        GuideBullet("Genre and Genre Category rows can show stacked album-art previews. Albums support list and cover-grid views.")
        GuideBullet("Use search and sorting on each list. Enable Indexed library scrollbar in Settings to jump through large lists by first letter or year.")
        GuideBullet("Track overflow actions include Play Now, Play on this device, Queue Next, Queue Last, and artist or collection playback.")
      }
    }

    item {
      GuideSection(title = "4. Queue, playlists, and history") {
        GuideParagraph("Queue is an on-device queue, separate from whatever MusicBee is playing on the computer.")
        GuideBullet("Search the queue, tap a row to play it, swipe to remove it, or drag its handle to reorder it. The current track has an accent and playing indicator.")
        GuideBullet("Playlists preserve MusicBee folders. A playlist can include local files that are not in the main library; those files can still be streamed while authorized by the plugin.")
        GuideBullet("History stores qualifying phone listens. A track counts after at least half its duration, up to four minutes, matching MusicBee's default play-count rule.")
        GuideBullet("Tap a history entry to play it again. Completed phone playback can update MusicBee Play Count and Last Played; optional Last.fm scrobbling is controlled from the player menu.")
      }
    }

    item {
      GuideSection(title = "5. Details, ratings, and lyrics") {
        GuideParagraph("Open the player menu and choose Track Details. Tags shows MusicBee metadata; Properties shows the exact streamed file's format, size, duration, dates, play counts, and location.")
        GuideBullet("Rate tracks from zero to five stars, enable half-star ratings in Settings, use a bomb rating, or clear the rating. Show rating on player controls whether the rating appears below the track.")
        GuideBullet("The player heart controls Last.fm Love/Ban. Hearts beside library and playlist rows are MusicBee loved markers and are read-only there.")
        GuideBullet("Open lyrics from the player. Plain lyrics and synchronized LRC lines are supported; tapping a synchronized line seeks the phone player.")
      }
    }

    item {
      GuideSection(title = "6. Settings and troubleshooting") {
        GuideBullet("Appearance offers Dark, Light, and System themes. Incoming call action can do nothing, pause, reduce volume, or stop playback.")
        GuideBullet("Library settings control the default track action, indexed scrollbar, and play buttons in album lists and on album covers. Rating settings control half-stars and the player rating.")
        GuideBullet("Plugin Update Check looks for a newer Windows plugin. Enable Debug Logging only while investigating a problem, then turn it off.")
        GuideBullet("The plugin command service uses its configured port (commonly 3000); the audio service uses the next port (commonly 3001). Allow both on the private Windows Firewall network.")
        GuideBullet("If a connection fails, confirm MusicBee is open, StreamBee Plugin is enabled and listening, both devices share the same LAN, and the plugin's allowed-client filter includes the phone.")
        GuideBullet("Use the Feedback tab to send a report to caiyi1995@gmail.com. The system email app opens with device information and logs as optional attachments.")
      }
    }
  }
}

@Composable
private fun GuideSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary
    )
    content()
  }
}

@Composable
private fun GuideParagraph(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface
  )
}

@Composable
private fun GuideBullet(text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "•",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun FeedbackContent(modifier: Modifier = Modifier, viewModel: FeedbackViewModel) {
  val context = LocalContext.current
  var feedbackText by remember { mutableStateOf("") }
  var includeDeviceInfo by remember { mutableStateOf(false) }
  var includeLogInfo by remember { mutableStateOf(false) }
  var isButtonEnabled by remember { mutableStateOf(true) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.checkIfLogsExist(context.filesDir)
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is FeedbackUiMessage.UpdateLogsExist -> {
          includeLogInfo = event.logsExist
        }

        is FeedbackUiMessage.ZipFailed -> {
          openFeedbackChooser(
            context,
            feedbackText,
            includeDeviceInfo,
            null,
            viewModel.versionName,
            viewModel.applicationId
          )
          isButtonEnabled = true
        }

        is FeedbackUiMessage.ZipSuccess -> {
          openFeedbackChooser(
            context,
            feedbackText,
            includeDeviceInfo,
            event.zipFile,
            viewModel.versionName,
            viewModel.applicationId
          )
          isButtonEnabled = true
        }
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    OutlinedTextField(
      value = feedbackText,
      onValueChange = { feedbackText = it },
      label = { Text(stringResource(R.string.feedback_title)) },
      modifier = Modifier.fillMaxWidth(),
      minLines = 5
    )

    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { includeDeviceInfo = !includeDeviceInfo }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = includeDeviceInfo,
          onCheckedChange = { includeDeviceInfo = it }
        )
        Text(
          text = stringResource(R.string.feedback_device_information),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 8.dp)
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { includeLogInfo = !includeLogInfo }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = includeLogInfo,
          onCheckedChange = { includeLogInfo = it }
        )
        Text(
          text = stringResource(R.string.feedback_logs),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
      onClick = {
        if (feedbackText.isNotBlank()) {
          isButtonEnabled = false
          if (includeLogInfo) {
            scope.launch {
              viewModel.createZip(
                context.filesDir,
                context.externalCacheDir ?: context.cacheDir
              )
            }
          } else {
            openFeedbackChooser(
              context,
              feedbackText,
              includeDeviceInfo,
              null,
              viewModel.versionName,
              viewModel.applicationId
            )
            isButtonEnabled = true
          }
        }
      },
      enabled = isButtonEnabled && feedbackText.isNotBlank(),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(stringResource(R.string.feedback_button_text))
    }
  }
}

/**
 * Help/Feedback screen content that can be used for both the actual screen and screenshot tests.
 * Takes immutable state and stable actions to avoid unnecessary recomposition.
 *
 * @param selectedTabIndex 0 for Help tab, 1 for Feedback tab
 * @param feedbackState State for the feedback form
 * @param actions Actions for the feedback form
 * @param showWebView Whether to show the bundled guide (false for previews)
 */
@Composable
fun HelpFeedbackScreenContent(
  selectedTabIndex: Int,
  feedbackState: FeedbackContentState,
  actions: IFeedbackActions,
  modifier: Modifier = Modifier,
  showWebView: Boolean = false,
  versionName: String = "1.0.0"
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
      Tab(
        selected = selectedTabIndex == 0,
        onClick = { },
        text = { Text(stringResource(R.string.tab_help)) }
      )
      Tab(
        selected = selectedTabIndex == 1,
        onClick = { },
        text = { Text(stringResource(R.string.common_feedback)) }
      )
    }

    when (selectedTabIndex) {
      0 -> {
        if (showWebView) {
          HelpContent(modifier = Modifier.weight(1f), versionName = versionName)
        } else {
          HelpContentPlaceholder(modifier = Modifier.weight(1f))
        }
      }

      1 -> FeedbackContentSection(
        state = feedbackState,
        actions = actions,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

/**
 * Placeholder for help content in previews. The actual bundled guide is rendered by the screen
 * itself; keeping a lightweight placeholder avoids loading the full guide into screenshot fixtures.
 */
@Composable
private fun HelpContentPlaceholder(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Help content loads from web",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * Feedback content section that takes state and actions.
 */
@Composable
internal fun FeedbackContentSection(
  state: FeedbackContentState,
  actions: IFeedbackActions,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    OutlinedTextField(
      value = state.feedbackText,
      onValueChange = actions.onFeedbackTextChange,
      label = { Text(stringResource(R.string.feedback_title)) },
      modifier = Modifier.fillMaxWidth(),
      minLines = 5
    )

    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { actions.onIncludeDeviceInfoChange(!state.includeDeviceInfo) }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = state.includeDeviceInfo,
          onCheckedChange = actions.onIncludeDeviceInfoChange
        )
        Text(
          text = stringResource(R.string.feedback_device_information),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 8.dp)
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { actions.onIncludeLogInfoChange(!state.includeLogInfo) }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = state.includeLogInfo,
          onCheckedChange = actions.onIncludeLogInfoChange
        )
        Text(
          text = stringResource(R.string.feedback_logs),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
      onClick = actions.onSendFeedback,
      enabled = state.isButtonEnabled && state.feedbackText.isNotBlank(),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(stringResource(R.string.feedback_button_text))
    }
  }
}

private fun openFeedbackChooser(
  context: Context,
  feedbackText: String,
  includeDeviceInfo: Boolean,
  logs: File?,
  versionName: String,
  applicationId: String
) {
  var fullFeedbackText = feedbackText.trim()

  if (includeDeviceInfo) {
    fullFeedbackText += context.getString(
      R.string.feedback_version_info,
      Build.MANUFACTURER,
      Build.DEVICE,
      Build.VERSION.RELEASE,
      versionName
    )
  }

  val emailIntent = Intent(Intent.ACTION_SEND).apply {
    putExtra(Intent.EXTRA_EMAIL, arrayOf("caiyi1995@gmail.com"))
    type = "message/rfc822"
    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
    putExtra(Intent.EXTRA_TEXT, fullFeedbackText)

    if (logs != null) {
      val logsUri = FileProvider.getUriForFile(
        context,
        "$applicationId.fileprovider",
        logs
      )
      putExtra(Intent.EXTRA_STREAM, logsUri)
    }
  }

  context.startActivity(
    Intent.createChooser(
      emailIntent,
      context.getString(R.string.feedback_chooser_title)
    )
  )
}
