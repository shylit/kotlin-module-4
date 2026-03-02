package com.example.module4_ex4

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

data class Post(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val avatarUrl: String
)

data class Comment(
    val postId: Int,
    val id: Int,
    val name: String,
    val body: String
)

// Состояние карточки поста (Loading / Ready / Error)
sealed class PostUiState {
    data object Loading : PostUiState()
    data class Ready(val avatarText: String, val comments: List<Comment>) : PostUiState()
    data class Error(val avatarText: String?, val comments: List<Comment>?) : PostUiState()
}

data class PostItemUi(
    val post: Post,
    val state: PostUiState
)

//чтение json
suspend fun loadPosts(context: Context): List<Post> = withContext(Dispatchers.IO) {
    val text = context.assets.open("social_posts.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(text)
    val list = ArrayList<Post>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list += Post(
            id = o.getInt("id"),
            userId = o.getInt("userId"),
            title = o.getString("title"),
            body = o.getString("body"),
            avatarUrl = o.getString("avatarUrl")
        )
    }
    list
}

suspend fun loadAllComments(context: Context): List<Comment> = withContext(Dispatchers.IO) {
    val text = context.assets.open("comments.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(text)
    val list = ArrayList<Comment>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list += Comment(
            postId = o.getInt("postId"),
            id = o.getInt("id"),
            name = o.getString("name"),
            body = o.getString("body")
        )
    }
    list
}

// имитация сети - аватар и комментарии
suspend fun fakeLoadAvatar(post: Post): String {
    delay(400) // имитация сети
    //имитируем ошибку (примерно 15%)
    if (post.id % 7 == 0) error("Avatar load failed")

    return "U${post.userId}"
}

suspend fun fakeLoadCommentsForPost(postId: Int, all: List<Comment>): List<Comment> {
    delay(600) // имитация сети
    // Иногда имитируем ошибку (примерно 15%)
    if (postId % 6 == 0) error("Comments load failed")
    return all.filter { it.postId == postId }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SocialFeedScreen(context = this)
                }
            }
        }
    }
}

@Composable
fun SocialFeedScreen(context: Context) {
    val scope = rememberCoroutineScope()

    //Лента (посты + их состояние)
    var feed by remember { mutableStateOf<List<PostItemUi>>(emptyList()) }

    //чтобы "Обновить" отменяло всё разом
    var feedJob by remember { mutableStateOf<Job?>(null) }

    //общий список комментариев, прочитан один раз (или можно читать на каждое обновление)
    var allComments by remember { mutableStateOf<List<Comment>>(emptyList()) }

    //функция загрузки ленты (мы будем вызывать её при старте и при нажатии "Обновить") ---
    fun startLoadingFeed() {
        //отменяем все текущие загрузки
        feedJob?.cancel()

        //запускаем новую общую job
        feedJob = scope.launch {
            //читаем данные из JSON
            val posts = loadPosts(context)
            allComments = loadAllComments(context)

            //сразу показываем посты, но в состоянии Loading
            feed = posts.map { PostItemUi(it, PostUiState.Loading) }

            //для каждого поста запускаем параллельную загрузку аватара+комментов
            posts.forEachIndexed { index, post ->
                launch {
                    //Внутри одного поста подзадачи должны идти параллельно:
                    //avatar и comments одновременно.
                    val state: PostUiState = supervisorScope {
                        val avatarDeferred = async {
                            try {
                                fakeLoadAvatar(post)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val commentsDeferred = async {
                            try {
                                fakeLoadCommentsForPost(post.id, allComments)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val avatar = avatarDeferred.await()
                        val comments = commentsDeferred.await()

                        when {
                            avatar != null && comments != null -> PostUiState.Ready(avatar, comments)
                            else -> PostUiState.Error(
                                avatarText = avatar,                // если null — покажем placeholder
                                comments = comments                 // если null — покажем ошибку
                            )
                        }
                    }

                    //обновляем только этот пост в ленте
                    feed = feed.toMutableList().also { mutable ->
                        mutable[index] = PostItemUi(post, state)
                    }
                }
            }
        }
    }

    //стартовая загрузка один раз
    LaunchedEffect(Unit) {
        startLoadingFeed()
    }

    //UI
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Социальная лента", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { startLoadingFeed() }) {
                Text("Обновить")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(feed) { item ->
                PostCard(item)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun PostCard(item: PostItemUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {

            //Заголовок
            Text(item.post.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(item.post.body, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(10.dp))

            //Состояние загрузки (Loading / Ready / Error)
            when (val st = item.state) {
                PostUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Загрузка аватара и комментариев...")
                    }
                }

                is PostUiState.Ready -> {
                    Text("Аватар: ${st.avatarText}")
                    Spacer(Modifier.height(6.dp))
                    Text("Комментарии:")
                    st.comments.forEach { c ->
                        Text("• ${c.name}: ${c.body}")
                    }
                }

                is PostUiState.Error -> {
                    // аватар: placeholder, если null
                    val avatarText = st.avatarText ?: "👤 (ошибка аватара)"
                    Text("Аватар: $avatarText")

                    Spacer(Modifier.height(6.dp))

                    if (st.comments == null) {
                        Text("Комментарии: ⚠️ не загрузились")
                    } else {
                        Text("Комментарии:")
                        st.comments.forEach { c ->
                            Text("• ${c.name}: ${c.body}")
                        }
                    }
                }
            }
        }
    }
}
