package com.example.module4_ex3
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray

data class Repo(
    val id: Long,
    val fullName: String,
    val description: String,
    val stars: Int,
    val language: String
)

//Чтение и парсинг JSON из assets
suspend fun loadReposFromAssets(context: Context): List<Repo> = withContext(Dispatchers.IO) {
    val jsonText = context.assets.open("github_repos.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(jsonText)

    val list = ArrayList<Repo>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list += Repo(
            id = obj.getLong("id"),
            fullName = obj.getString("full_name"),
            description = obj.optString("description", ""),
            stars = obj.getInt("stargazers_count"),
            language = obj.optString("language", "")
        )
    }
    list
}

//Debounce: delay + cancel
fun <T> CoroutineScope.debounce(
    waitMs: Long = 500L,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitHubRepoSearchScreen(context = this)
                }
            }
        }
    }
}

@Composable
fun GitHubRepoSearchScreen(context: Context) {
    val scope = rememberCoroutineScope()

    //Все репозитории (загруженные из JSON)
    var allRepos by remember { mutableStateOf<List<Repo>>(emptyList()) }

    //Результаты поиска
    var results by remember { mutableStateOf<List<Repo>>(emptyList()) }

    //Текст поиска
    var query by remember { mutableStateOf("") }

    //Лоадер
    var isLoading by remember { mutableStateOf(false) }

    //Job текущего поиска — чтобы отменять при новом вводе
    var searchJob by remember { mutableStateOf<Job?>(null) }

    //Загружаем JSON один раз при старте
    LaunchedEffect(Unit) {
        isLoading = true
        allRepos = loadReposFromAssets(context)
        results = allRepos
        isLoading = false
    }

    //Создаём debounced-функцию, которая запускает поиск
    val debouncedSearch = remember {
        scope.debounce<String>(waitMs = 500L) { text ->
            //отменяем предыдущий поиск
            searchJob?.cancel()

            //запускаем новый поиск
            searchJob = scope.launch {
                isLoading = true

                //async — чтобы формально использовать async/await
                val deferred = async {
                    delay(600) //имитация "сетевого" поиска
                    val q = text.trim().lowercase()

                    if (q.isEmpty()) {
                        allRepos
                    } else {
                        allRepos.filter { repo ->
                            repo.fullName.lowercase().contains(q) ||
                                    repo.description.lowercase().contains(q) ||
                                    repo.language.lowercase().contains(q)
                        }
                    }
                }

                //await результата async
                results = deferred.await()
                isLoading = false
            }
        }
    }

    //UI
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Поиск репозиториев GitHub (debounce)",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { newText ->
                query = newText
                //не ищем сразу: запускаем debounced поиск
                debouncedSearch(newText)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Введите запрос") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "Найдено: ${results.size}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(results) { repo ->
                RepoItem(repo)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RepoItem(repo: Repo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
            if (repo.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(repo.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⭐ ${repo.stars}")
                Text(repo.language)
            }
        }
    }
}
