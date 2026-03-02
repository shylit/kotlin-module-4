package com.example.module4_ex9

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)

        setContent {
            MaterialTheme {
                WeatherScreen()
            }
        }
    }
}

@Composable
fun WeatherScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wm = remember { WorkManager.getInstance(context) }

    val cities = remember { listOf("Москва", "Лондон", "Нью-Йорк") } // можно добавить 4-й

    // Android 13+: спросим разрешение на уведомления
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val infos by wm.getWorkInfosForUniqueWorkLiveData(WeatherKeys.UNIQUE_WORK)
        .observeAsState(initial = emptyList())

    val isRunning = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    // Ищем ReportWorker и берём отчёт
    val reportInfo = infos.firstOrNull { it.tags.contains(WeatherKeys.TAG_REPORT) }
    val reportText = reportInfo?.outputData?.getString("report")

    val statusText = when {
        infos.any { it.state == WorkInfo.State.FAILED } -> "Ошибка"
        isRunning -> "Загрузка... (${infos.count { it.state == WorkInfo.State.RUNNING }} в процессе)"
        reportInfo?.state == WorkInfo.State.SUCCEEDED -> "Все данные получены!"
        else -> "Готов начать"
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Прогноз погоды", style = MaterialTheme.typography.headlineSmall)
            Text(statusText)

            Button(
                onClick = { WorkStarter.start(wm, cities) },
                enabled = !isRunning
            ) {
                Text("Собрать прогноз")
            }

            OutlinedButton(
                onClick = { WorkStarter.cancel(wm) },
                enabled = isRunning
            ) {
                Text("Отменить")
            }

            if (reportText != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(reportText)
                    }
                }
            }
        }
    }
}
