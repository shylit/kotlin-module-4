package com.example.module4_ex11

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.module4_ex11.alarm.ReminderPrefs
import com.example.module4_ex11.alarm.ReminderScheduler
import com.example.module4_ex11.ui.theme.Module4ex11Theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ок */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ нужно разрешение на уведомления
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            Module4ex11Theme {
                MaterialTheme {
                    PillReminderScreen()
                }
            }
        }
    }
}

@Composable
fun PillReminderScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var enabled by remember { mutableStateOf(ReminderPrefs.isEnabled(context)) }
    var nextTime by remember { mutableStateOf(ReminderPrefs.getNextTimeMillis(context)) }

    val nextText = remember(enabled, nextTime) {
        if (!enabled || nextTime == 0L) {
            "Следующее напоминание: —"
        } else {
            val dayWord = if (isSameDay(System.currentTimeMillis(), nextTime)) "сегодня" else "завтра"
            "Следующее напоминание: $dayWord в 20:00"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Индикатор состояния
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(if (enabled) Color(0xFF2E7D32) else Color(0xFF9E9E9E)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (enabled) "✓" else "!", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        Text(text = "Напоминание о таблетке")

        Spacer(Modifier.height(8.dp))

        Text(text = if (enabled) "Включено" else "Выключено")

        Spacer(Modifier.height(12.dp))

        Text(text = nextText)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (!enabled) {
                    val t = ReminderScheduler.enable(context)
                    enabled = true
                    nextTime = t
                } else {
                    ReminderScheduler.disable(context)
                    enabled = false
                    nextTime = 0L
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Color(0xFFC62828) else Color(0xFF1565C0)
            )
        ) {
            Text(text = if (enabled) "Выключить напоминание" else "Включить напоминание в 20:00")
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return fmt.format(Date(a)) == fmt.format(Date(b))
}
