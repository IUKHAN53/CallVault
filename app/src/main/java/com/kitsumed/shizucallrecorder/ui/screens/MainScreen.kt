/*
 * CallVault (a FORK of ShizuCallRecorder: https://github.com/kitsumed/ShizuCallRecorder)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel

/** Post-onboarding shell: Recordings (home) and Settings tabs, with the player docked above the navigation bar. */
@Composable
fun MainScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val recordingsViewModel: RecordingsViewModel = viewModel()
    val player by recordingsViewModel.player.collectAsState()
    val message by recordingsViewModel.message.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // New recordings appear after a call ends, i.e. when the user comes back to the app; folder may change in Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recordingsViewModel.refresh() }
    LaunchedEffect(tab) { if (tab == 0) recordingsViewModel.refresh() }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); recordingsViewModel.consumeMessage() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                MiniPlayer(player, recordingsViewModel)
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.recordings_title)) },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.general_settings)) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            if (tab == 0) RecordingsScreen(recordingsViewModel) else SettingsScreen(viewModel = settingsViewModel)
        }
    }
}
