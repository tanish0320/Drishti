package com.drishti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drishti.models.AppMode
import com.drishti.ui.HomeViewModel
import com.drishti.ui.components.CameraPreview
import com.drishti.ui.components.CommonStatusBar
import com.drishti.ui.components.ModeButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSettingsClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "DRISHTI",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            android.util.Log.d("DrishtiDebug", "Button pressed: TopAppBar Settings")
                            onSettingsClick()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.border(width = 2.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        },
        bottomBar = {
            CommonStatusBar(statusMessage = uiState.statusMessage)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Current Active Mode Status Pill
            val modeText = when (uiState.currentMode) {
                AppMode.WALK -> "WALK MODE ACTIVE"
                AppMode.READ -> "READ MODE ACTIVE"
                AppMode.AUTO -> "AUTO MODE ACTIVE"
            }
            Row(
                modifier = Modifier
                    .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                )
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Camera Guidance View Area (Aspect Ratio 1:1)
            CameraPreview(
                frameProvider = viewModel.frameProvider,
                detectionEngine = viewModel.detectionEngine,
                decisionEngine = viewModel.decisionEngine,
                speechEngine = viewModel.speechEngine,
                hapticEngine = viewModel.hapticEngine,
                ocrProcessor = viewModel.ocrProcessor,
                currentMode = uiState.effectiveMode,
                modeController = viewModel.modeController,
                autoModeManager = viewModel.autoModeManager,
                settingsRepository = viewModel.settingsRepository,
                performanceMonitor = viewModel.performanceMonitor
            )

            // Mode Selection Grid (Massive Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModeButton(
                    title = "WALK",
                    icon = Icons.Default.DirectionsWalk,
                    selected = uiState.currentMode == AppMode.WALK,
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: WALK Mode Button")
                        viewModel.setMode(AppMode.WALK)
                    },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    title = "READ",
                    icon = Icons.Default.MenuBook,
                    selected = uiState.currentMode == AppMode.READ,
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: READ Mode Button")
                        viewModel.setMode(AppMode.READ)
                    },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    title = "AUTO",
                    icon = Icons.Default.SmartToy,
                    selected = uiState.currentMode == AppMode.AUTO,
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: AUTO Mode Button")
                        viewModel.setMode(AppMode.AUTO)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // AI NAVIGATION ASSISTANT
                Button(
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: AI NAVIGATION ASSISTANT")
                        onNavigateClick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(4.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "AI NAVIGATION ASSISTANT",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // SYSTEM SETTINGS
                Button(
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: SYSTEM SETTINGS")
                        onSettingsClick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(4.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "SYSTEM SETTINGS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // EMERGENCY STOP
                Button(
                    onClick = {
                        android.util.Log.d("DrishtiDebug", "Button pressed: EMERGENCY STOP")
                        viewModel.emergencyStop()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(4.dp, MaterialTheme.colorScheme.onErrorContainer, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "EMERGENCY STOP",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            }
        }
    }
}
