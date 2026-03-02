package com.example.compass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.module4_ex14.CompassViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CompassScreen(vm: CompassViewModel) {
    val hasSensor by vm.hasSensor.collectAsState()
    val azimuth by vm.azimuthDeg.collectAsState()

    Surface(color = Color(0xFF0B0B0B)) {
        Box(modifier = Modifier.fillMaxSize()) {

            if (!hasSensor) {
                Text(
                    text = "Устройство не поддерживает датчик ориентации",
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@Surface
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Компас",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(24.dp))

                // Компас занимает ~70-80% ширины
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "N",
                        color = Color.Red,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    CompassDial(azimuthDeg = azimuth)
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Азимут: ${azimuth.roundToInt()}°",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CompassDial(azimuthDeg: Float) {
    // Нам нужно, чтобы стрелка всегда показывала на север.
    // Поэтому вращаем стрелку на -azimuth
    val target = -azimuthDeg

    // Чтобы не крутилось “почти полный круг” при переходе 359 -> 1,
    // делаем корректировку по кратчайшему пути.
    var last by remember { mutableStateOf(target) }
    val adjusted = remember(target) {
        val fixed = shortestAngle(from = last, to = target)
        last = fixed
        fixed
    }

    val animated by animateFloatAsState(
        targetValue = adjusted,
        animationSpec = tween(durationMillis = 250),
        label = "compassRotation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Круг компаса
        drawCircle(
            color = Color(0xFF2A2A2A),
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.06f)
        )

        // Стрелка ~80% диаметра => длина ~0.4 * minDimension (радиус * 0.8)
        val arrowLen = radius * 0.75f
        val stroke = radius * 0.07f

        rotate(degrees = animated, pivot = center) {
            // Красная часть (на север) - вверх
            drawLine(
                color = Color(0xFFE53935),
                start = center,
                end = Offset(center.x, center.y - arrowLen),
                strokeWidth = stroke
            )
            // Серая часть (на юг) - вниз
            drawLine(
                color = Color(0xFF555555),
                start = center,
                end = Offset(center.x, center.y + arrowLen * 0.65f),
                strokeWidth = stroke
            )
        }
    }
}
private fun shortestAngle(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return from + delta
}
