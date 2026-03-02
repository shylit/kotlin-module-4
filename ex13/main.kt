package com.example.module4_ex13

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CurrencyScreen()
                }
            }
        }
    }
}

@Composable
fun CurrencyScreen(vm: CurrencyViewModel = viewModel()) {

    val rate by vm.rate.collectAsStateWithLifecycle()
    val lastUpdate by vm.lastUpdate.collectAsStateWithLifecycle()

    // Храним предыдущий курс, чтобы понять направление
    var prevRate by remember { mutableStateOf(rate) }

    // direction: 1 = вверх, -1 = вниз, 0 = без изменений
    val direction = when {
        rate > prevRate -> 1
        rate < prevRate -> -1
        else -> 0
    }

    // Обновляем prevRate после того, как UI получил новый rate
    LaunchedEffect(rate) {
        prevRate = rate
    }

    val arrowText = when (direction) {
        1 -> "↑"
        -1 -> "↓"
        else -> "→"
    }

    val arrowColor = when (direction) {
        1 -> Color(0xFF2E7D32) // зелёный
        -1 -> Color(0xFFC62828) // красный
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Курс USD → RUB", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.size(200.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.2f ₽", rate),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = arrowText,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = arrowColor
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text("Последнее обновление: $lastUpdate")

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { vm.refreshNow() },
            modifier = Modifier.fillMaxWidth(0.75f)
        ) {
            Text("Обновить сейчас")
        }
    }
}
