package com.example.module4_ex8

import android.net.Uri
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
import androidx.work.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhotoChainScreen()
            }
        }
    }
}

@Composable
fun PhotoChainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    var selectedFilePath by remember { mutableStateOf<String?>(null) }

    // Наблюдаем за цепочкой по уникальному имени
    val workInfosLive = remember {
        workManager.getWorkInfosForUniqueWorkLiveData(WorkKeys.UNIQUE_WORK_NAME)
    }
    val workInfos by workInfosLive.observeAsState(initial = emptyList())

    // Определяем: идёт ли работа сейчас
    val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    // Определяем текущий шаг и прогресс
    val (statusText, progressValue, resultText, errorText) = remember(workInfos) {
        deriveUiStateFromWorkInfos(workInfos)
    }

    // Launcher выбора фото
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFilePath = copyUriToCache(context, uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall
            )

            if (isRunning) {
                LinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Это может занять несколько секунд...")
            }

            Text(
                text = selectedFilePath?.let { "Выбрано: ${File(it).name}" } ?: "Фото не выбрано",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                enabled = !isRunning
            ) {
                Text("Выбрать фото")
            }

            Button(
                onClick = {
                    val input = selectedFilePath
                    if (input != null) {
                        startPhotoChain(workManager, input)
                    }
                },
                enabled = !isRunning && selectedFilePath != null
            ) {
                Text("Начать обработку и загрузку")
            }

            if (resultText != null) {
                Text(text = resultText)
            }
            if (errorText != null) {
                Text(text = "Ошибка: $errorText")
            }
        }
    }
}

private fun startPhotoChain(workManager: WorkManager, initialPath: String) {
    val compress = OneTimeWorkRequestBuilder<CompressWorker>()
        .addTag(WorkKeys.TAG_COMPRESS)
        .setInputData(workDataOf(WorkKeys.KEY_INPUT_PATH to initialPath))
        .build()

    val watermark = OneTimeWorkRequestBuilder<WatermarkWorker>()
        .addTag(WorkKeys.TAG_WATERMARK)
        // input мы подставим через InputMerger (см. ниже)
        .setInputMerger(OverwritingInputMerger::class)
        .build()

    val upload = OneTimeWorkRequestBuilder<UploadWorker>()
        .addTag(WorkKeys.TAG_UPLOAD)
        .setInputMerger(OverwritingInputMerger::class)
        .build()

    // Важно:
    // - OverwritingInputMerger позволяет "протащить" outputData прошлого шага как inputData следующего,
    //   если ключи совпадают. Поэтому в Workers мы возвращаем KEY_OUTPUT_PATH,
    //   а дальше UI-логика подставит его как KEY_INPUT_PATH через merger.
    //
    // НО: чтобы это реально работало просто, мы делаем так:
    // - compress возвращает KEY_OUTPUT_PATH
    // - watermark ожидает KEY_INPUT_PATH
    // => значит нам нужно, чтобы KEY_OUTPUT_PATH стал KEY_INPUT_PATH.
    //
    // Чтобы без усложнений, делаем маленький трюк:
    // в deriveUiStateFromWorkInfos и цепочке — проще руками передавать путь через setInputData нельзя,
    // так что делаем ещё проще (самый учебный вариант):
    // watermark и upload будут брать путь из KEY_INPUT_PATH, а мы будем возвращать и KEY_INPUT_PATH тоже.

    // Поэтому: переделываем цепочку правильно — так, чтобы каждый шаг возвращал KEY_INPUT_PATH.
    // Для этого достаточно заменить в Workers Result.success(...) на KEY_INPUT_PATH.
    //
    // В этом коде я ниже покажу, как должно быть в Workers (см. блок "ВАЖНО").

    // Запуск как UNIQUE: если нажали ещё раз — старая цепочка отменится и заменится
    workManager.enqueueUniqueWork(
        WorkKeys.UNIQUE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        compress
    )

    workManager.beginUniqueWork(
        WorkKeys.UNIQUE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        compress
    ).then(watermark)
        .then(upload)
        .enqueue()
}

/**
 * ВАЖНО (упрощение для учебной):
 * Чтобы шаг2 и шаг3 получили путь "из прошлого шага" без лишней магии:
 * - в CompressWorker нужно возвращать workDataOf(KEY_INPUT_PATH to outPath)
 * - в WatermarkWorker то же самое
 * Тогда OverwritingInputMerger всё сделает: выход прошлого шага становится входом следующего.
 */

private fun deriveUiStateFromWorkInfos(workInfos: List<WorkInfo>): UiState {
    // По умолчанию
    var status = "Готов к запуску"
    var progress = 0f
    var result: String? = null
    var error: String? = null

    // Ищем RUNNING по тегам
    val running = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }

    if (running != null) {
        status = when {
            running.tags.contains(WorkKeys.TAG_COMPRESS) -> "Сжимаем фото..."
            running.tags.contains(WorkKeys.TAG_WATERMARK) -> "Добавляем водяной знак..."
            running.tags.contains(WorkKeys.TAG_UPLOAD) -> "Загружаем..."
            else -> "Выполняется..."
        }
        val p = running.progress.getInt(WorkKeys.KEY_PROGRESS, 0)
        progress = (p.coerceIn(0, 100)) / 100f
        return UiState(status, progress, result, error)
    }

    // Если кто-то FAILED — показываем ошибку
    val failed = workInfos.firstOrNull { it.state == WorkInfo.State.FAILED }
    if (failed != null) {
        status = "Ошибка обработки"
        error = failed.outputData.getString(WorkKeys.KEY_ERROR) ?: "Неизвестная ошибка"
        progress = 0f
        return UiState(status, progress, result, error)
    }

    // Если последний SUCCEEDED — готово
    val last = workInfos.lastOrNull()
    if (last != null && last.state == WorkInfo.State.SUCCEEDED && last.tags.contains(WorkKeys.TAG_UPLOAD)) {
        status = "Фото успешно загружено!"
        val url = last.outputData.getString(WorkKeys.KEY_URL) ?: "(нет ссылки)"
        val path = last.outputData.getString(WorkKeys.KEY_OUTPUT_PATH) ?: "(нет пути)"
        result = "Ссылка на загруженное фото:\n$url\n\nФайл:\n$path"
        progress = 1f
        return UiState(status, progress, result, error)
    }

    // Если есть SUCCEEDED/ENQUEUED, но не RUNNING — можно показать “Запущена обработка...”
    if (workInfos.any { it.state == WorkInfo.State.ENQUEUED }) {
        status = "Запущена обработка..."
    }

    return UiState(status, progress, result, error)
}

private data class UiState(
    val statusText: String,
    val progressValue: Float,
    val resultText: String?,
    val errorText: String?
)

private fun copyUriToCache(context: android.content.Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Не удалось открыть файл")

    val outFile = File(context.cacheDir, "selected_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outFile).use { output ->
        inputStream.use { input ->
            input.copyTo(output)
        }
    }
    return outFile.absolutePath
}
