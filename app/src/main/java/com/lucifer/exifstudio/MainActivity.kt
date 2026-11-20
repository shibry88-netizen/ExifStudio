package com.lucifer.exifstudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucifer.exifstudio.ui.AuroraBackground
import com.lucifer.exifstudio.ui.DevicePickerSheet
import com.lucifer.exifstudio.ui.EditorScreen
import com.lucifer.exifstudio.ui.ExifStudioTheme
import com.lucifer.exifstudio.ui.HomeScreen
import com.lucifer.exifstudio.ui.LensPickerSheet
import com.lucifer.exifstudio.ui.MapPickerScreen
import com.lucifer.exifstudio.vm.StudioViewModel

class MainActivity : ComponentActivity() {

    private val sharedUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUri.value = extractImage(intent)

        setContent {
            ExifStudioTheme {
                val vm: StudioViewModel = viewModel()
                AppRoot(vm = vm, incoming = sharedUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractImage(intent)?.let { sharedUri.value = it }
    }

    @Suppress("DEPRECATION")
    private fun extractImage(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}

private enum class Screen { Home, Editor, Map }

@Composable
private fun AppRoot(vm: StudioViewModel, incoming: MutableState<Uri?>) {

    val state by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Home) }
    var showDevices by remember { mutableStateOf(false) }
    var showLens by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.openPhoto(uri)
            screen = Screen.Editor
        }
    }

    // 从相册「分享」进来
    LaunchedEffect(incoming.value) {
        incoming.value?.let { uri ->
            vm.openPhoto(uri)
            screen = Screen.Editor
            incoming.value = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.Map -> Screen.Editor
            else -> Screen.Home
        }
    }

    AuroraBackground {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    state = state,
                    onPick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenEditor = { screen = Screen.Editor },
                    onOpenLibrary = { showDevices = true },
                    onStripAll = { vm.stripAll { } },
                    onOpenHistory = { item ->
                        vm.openHistory(item)
                        screen = Screen.Editor
                    }
                )

                Screen.Editor -> EditorScreen(
                    state = state,
                    vm = vm,
                    onBack = { screen = Screen.Home },
                    onOpenDevices = { showDevices = true },
                    onOpenLens = {
                        if (state.device == null) showDevices = true else showLens = true
                    },
                    onOpenMap = { screen = Screen.Map }
                )

                Screen.Map -> MapPickerScreen(
                    vm = vm,
                    initialLat = state.profile.latitude,
                    initialLon = state.profile.longitude,
                    onBack = { screen = Screen.Editor },
                    onConfirm = { lat, lon, address ->
                        vm.setLocation(lat, lon, address)
                        screen = Screen.Editor
                    }
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 112.dp, start = 20.dp, end = 20.dp)
            )
        }

        if (showDevices) {
            DevicePickerSheet(
                brands = state.brands,
                selected = state.device,
                onDismiss = { showDevices = false },
                onPick = { device ->
                    showDevices = false
                    val cam = device.rear.firstOrNull() ?: device.cameras.firstOrNull()
                    if (cam != null) {
                        vm.applyDevice(device, cam)
                        showLens = true
                    }
                }
            )
        }

        if (showLens) {
            LensPickerSheet(
                device = state.device,
                selected = state.camera,
                onDismiss = { showLens = false },
                onPick = { camera ->
                    state.device?.let { vm.applyDevice(it, camera) }
                    showLens = false
                }
            )
        }
    }
}
