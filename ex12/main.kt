package com.example.module4_ex12

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimalFactsScreen()
                }
            }
        }
    }
}

@Composable
fun AnimalFactsScreen(vm: AnimalFactsViewModel = viewModel()) {
    val scope = rememberCoroutineScope()

    // Берём сохранённый факт (если уже был)
    var fact by remember { mutableStateOf(vm.currentFact) }
    var isLoading by remember { mutableStateOf(false) }

    // Чтобы при повторном нажатии отменять прошлую “генерацию”
    var job by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Факты о животных",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(16.dp))

        // Карточка с фактом (анимация появления/исчезновения)
        AnimatedVisibility(
            visible = fact != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = fact ?: "",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (fact == null && !isLoading) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Нажми кнопку, чтобы узнать факт!",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))

        // Индикатор загрузки + текст
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(10.dp))
            Text("Ищем интересный факт...")
            Spacer(Modifier.height(10.dp))
        }

        // Кнопка
        Button(
            onClick = {
                // отменяем прошлую генерацию (если нажали снова)
                job?.cancel()

                isLoading = true
                job = scope.launch {
                    try {
                        // ВАЖНО: тут мы запускаем cold flow через collect
                        vm.getRandomFact().collect { newFact ->
                            fact = newFact
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(if (isLoading) "Генерируем..." else "Новый факт!")
        }
    }
}
