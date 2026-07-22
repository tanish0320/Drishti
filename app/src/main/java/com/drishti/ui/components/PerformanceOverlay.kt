package com.drishti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drishti.utils.PerformanceMonitor

@Composable
fun PerformanceOverlay(
    performanceMonitor: PerformanceMonitor,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.SideEffect {
        android.util.Log.d("DrishtiDebug", "Overlay recomposed: PerformanceOverlay")
    }

    val snapshot by performanceMonitor.snapshotState.collectAsState()

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Drishti Perf Diagnostics",
            color = Color.Yellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "YOLO FPS : ${"%.1f".format(snapshot.yoloFps)}",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "OCR FPS  : ${"%.1f".format(snapshot.ocrFps)}",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Inf Lat  : ${snapshot.inferenceLatencyMs}ms",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Proc Lat : ${snapshot.frameProcessingLatencyMs}ms",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Drops    : ${snapshot.droppedFrames}",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Heap Mem : ${snapshot.memoryUsageMb}MB",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "CPU Load : ${"%.1f".format(snapshot.cpuUsagePercent)}%",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Battery  : ${if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "N/A"}",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
