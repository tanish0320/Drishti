package com.drishti.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.drishti.models.ThreatLevel
import com.drishti.navigation.LatLng
import com.drishti.navigation.NavigationSession
import com.drishti.navigation.PlaceSearchResult
import com.drishti.navigation.SpeechRecognitionStatus
import com.drishti.ui.NavigationViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // States from view model
    val currentLocation by viewModel.currentLocation.collectAsState()
    val currentBearing by viewModel.currentBearing.collectAsState()
    val currentInstruction by viewModel.currentInstruction.collectAsState()
    val session by viewModel.navigationSession.collectAsState()
    val speechStatus by viewModel.speechStatus.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val showCandidates by viewModel.showCandidateSelection.collectAsState()
    val candidates by viewModel.searchCandidates.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val gpsAccuracy by viewModel.gpsAccuracy.collectAsState()
    val sceneAnalysis by viewModel.sceneAnalysis.collectAsState()
    val decisionState by viewModel.navigationDecision.collectAsState()
    val isSpeechMuted by viewModel.isSpeechMuted.collectAsState()

    // Permission tracking
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationPermissionGranted) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocationUpdates()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Live Google Maps full-screen background
        val origin = currentLocation ?: LatLng(12.9716, 77.5946)
        MapViewContainer(
            currentLocation = origin,
            currentBearing = currentBearing,
            session = session,
            modifier = Modifier.fillMaxSize()
        )

        // Top shadow gradient overlay for readability of elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // 2. Floating Top Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Modular Navigation Top Bar
            NavigationTopBar(
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // Destination Card
            if (session?.isActive == true) {
                DestinationCard(
                    session = session,
                    currentInstruction = currentInstruction
                )
            }

            // Command feedback toast chip
            CommandFeedbackChip(
                speechStatus = speechStatus,
                recognizedText = recognizedText
            )
        }

        // 3. Floating Bottom Controls & Panels
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row containing warnings card and map controls floating directly above the bottom card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // AI Warnings Card (Fades in/out based on threat level)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize()
                ) {
                    WarningCard(
                        sceneSummary = sceneAnalysis.summary,
                        threatLevel = decisionState.threatLevel
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Floating Map Controls + Voice Search FAB
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapControls(
                        isMuted = isSpeechMuted,
                        onMuteToggle = { viewModel.toggleMuteSpeech() },
                        onRecenterClick = {
                            // Recenters map views
                        }
                    )

                    VoiceFAB(
                        speechStatus = speechStatus,
                        recognizedText = recognizedText,
                        onClick = {
                            if (speechStatus == SpeechRecognitionStatus.LISTENING) {
                                viewModel.stopListeningAndParse()
                            } else {
                                viewModel.startListening()
                            }
                        }
                    )
                }
            }

            // Scrollable Quick Destination chip row (only visible when not actively navigating)
            if (session?.isActive != true) {
                QuickDestinationRow(
                    onDestinationClick = { dest ->
                        viewModel.startNavigationDirectly(dest)
                    }
                )
            }

            // Navigation Metrics Bottom panel
            NavigationBottomCard(
                session = session,
                currentSpeedMps = currentSpeed,
                gpsAccuracyMeters = gpsAccuracy,
                onStopClick = { viewModel.stopNavigation() }
            )
        }
    }

    // Place Disambiguation Dialog
    if (showCandidates) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCandidateSelection() },
            title = {
                Text(
                    text = "Select Destination",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(candidates) { candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCandidate(candidate) }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = candidate.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = candidate.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${candidate.distanceMeters.toInt()}m away",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCandidateSelection() }) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationTopBar(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "AI NAVIGATION",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DestinationCard(
    session: NavigationSession?,
    currentInstruction: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = session?.destinationName ?: "Active Session",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = currentInstruction,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WarningCard(
    sceneSummary: String,
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    if (sceneSummary.isEmpty() || sceneSummary.contains("clear", ignoreCase = true)) return

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (threatLevel) {
                ThreatLevel.CRITICAL -> Color(0xFFF8D7DA)
                ThreatLevel.WARNING -> Color(0xFFFFF3CD)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = when (threatLevel) {
                    ThreatLevel.CRITICAL -> Color(0xFF842029)
                    ThreatLevel.WARNING -> Color(0xFF664D03)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = sceneSummary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = when (threatLevel) {
                    ThreatLevel.CRITICAL -> Color(0xFF842029)
                    ThreatLevel.WARNING -> Color(0xFF664D03)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun MapControls(
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onRecenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Voice Guidance Mute Button
        FloatingActionButton(
            onClick = onMuteToggle,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute Voice guidance",
                modifier = Modifier.size(22.dp)
            )
        }

        // Location Recenter Button
        FloatingActionButton(
            onClick = onRecenterClick,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter Map View",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun VoiceFAB(
    speechStatus: SpeechRecognitionStatus,
    recognizedText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = speechStatus == SpeechRecognitionStatus.LISTENING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    FloatingActionButton(
        onClick = onClick,
        containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary,
        contentColor = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = modifier
            .size(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = "Voice Search Command",
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun QuickDestinationRow(
    onDestinationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Pair("🏥 Pharmacy", "Pharmacy"),
        Pair("🏧 ATM", "ATM"),
        Pair("🏠 Home", "Home"),
        Pair("🚉 Station", "Station"),
        Pair("🛒 Grocery", "Grocery Store")
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items) { item ->
            AssistChip(
                onClick = { onDestinationClick(item.second) },
                label = { Text(item.first) },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                modifier = Modifier.height(40.dp)
            )
        }
    }
}

@Composable
fun NavigationBottomCard(
    session: NavigationSession?,
    currentSpeedMps: Float,
    gpsAccuracyMeters: Float,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance & ETA info
                if (session?.isActive == true) {
                    Column {
                        val seconds = session.durationRemainingSeconds
                        val minutes = seconds / 60
                        val etaText = if (minutes > 0) "$minutes min" else "$seconds sec"
                        Text(
                            text = etaText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${session.distanceRemainingMeters.toInt()} meters remaining",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Ready to Navigate",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Telemetry row (Speed & Accuracy)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val speedKmh = (currentSpeedMps * 3.6f).toInt()
                        Text(
                            text = "$speedKmh",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Text(
                            text = "km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${gpsAccuracyMeters.toInt()}m",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Text(
                            text = "accuracy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (session?.isActive == true) {
                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "STOP NAVIGATION",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CommandFeedbackChip(
    speechStatus: SpeechRecognitionStatus,
    recognizedText: String,
    modifier: Modifier = Modifier
) {
    if (speechStatus == SpeechRecognitionStatus.IDLE) return

    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(speechStatus, recognizedText) {
        if (speechStatus == SpeechRecognitionStatus.SUCCESS || speechStatus == SpeechRecognitionStatus.ERROR) {
            delay(3000)
            visible = false
        } else {
            visible = true
        }
    }

    if (visible) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (speechStatus) {
                    SpeechRecognitionStatus.SUCCESS -> Color(0xFFD1E7DD)
                    SpeechRecognitionStatus.ERROR -> Color(0xFFF8D7DA)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = when (speechStatus) {
                        SpeechRecognitionStatus.SUCCESS -> Icons.Default.Check
                        SpeechRecognitionStatus.ERROR -> Icons.Default.Close
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (speechStatus) {
                        SpeechRecognitionStatus.SUCCESS -> Color(0xFF0F5132)
                        SpeechRecognitionStatus.ERROR -> Color(0xFF842029)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (speechStatus == SpeechRecognitionStatus.LISTENING) "Listening..." 
                           else if (recognizedText.isNotEmpty()) recognizedText 
                           else "Error",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = when (speechStatus) {
                        SpeechRecognitionStatus.SUCCESS -> Color(0xFF0F5132)
                        SpeechRecognitionStatus.ERROR -> Color(0xFF842029)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}

@Composable
fun MapViewContainer(
    currentLocation: LatLng,
    currentBearing: Float,
    session: NavigationSession?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    // Manage Map Lifecycle safely to prevent leaks
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_CREATE -> mapView.onCreate(android.os.Bundle())
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.getMapAsync { googleMap ->
                googleMap.clear()
                
                // Standard visual setup
                googleMap.uiSettings.isZoomControlsEnabled = false // using custom or gesture zoom
                googleMap.uiSettings.isCompassEnabled = false // using custom digital compass
                googleMap.uiSettings.isMyLocationButtonEnabled = false

                val userPos = com.google.android.gms.maps.model.LatLng(currentLocation.latitude, currentLocation.longitude)
                
                // User location pin (Azure color)
                googleMap.addMarker(
                    MarkerOptions()
                        .position(userPos)
                        .title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )

                // Walk route line drawing
                session?.let { activeSession ->
                    if (activeSession.routePoints.isNotEmpty()) {
                        val googlePoints = activeSession.routePoints.map {
                            com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
                        }
                        googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(googlePoints)
                                .color(android.graphics.Color.parseColor("#0066FF"))
                                .width(12f)
                        )

                        // Destination location pin (Red color)
                        val dest = googlePoints.last()
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(dest)
                                .title(activeSession.destinationName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )

                        // Waypoint checkpoints drawing
                        activeSession.waypoints.forEach { wp ->
                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(com.google.android.gms.maps.model.LatLng(wp.location.latitude, wp.location.longitude))
                                    .title(wp.instruction)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                        if (wp.isVisited) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_ORANGE
                                    ))
                            )
                        }
                    }
                }

                // Smooth camera translation following coordinate trajectory and device heading orientation
                val cameraPosition = CameraPosition.Builder()
                    .target(userPos)
                    .zoom(18f)
                    .bearing(currentBearing)
                    .tilt(40f)
                    .build()
                googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    )
}
