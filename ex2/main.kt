import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
    val bytes = file.readBytes() // максимально просто (для реально огромных файлов лучше поток/буфер)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    digest.joinToString("") { "%02x".format(it) }
}

fun main(args: Array<String>) = runBlocking {
    val dirPath = args.firstOrNull() ?: "json_test"
    val timeoutSec = 5L

    val dir = File(dirPath)

    val files = dir.walkTopDown()
        .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        .toList()

    if (files.isEmpty()) {
        println("JSON-файлы не найдены.")
        return@runBlocking
    }

    val jobs = mutableListOf<Deferred<Pair<File, String>>>()

    val pairs: List<Pair<File, String>>? = withTimeoutOrNull(timeoutSec * 1000) {
        coroutineScope {
            for (f in files) {
                jobs += async { f to sha256(f) }
            }
            jobs.awaitAll()
        }
    }

    if (pairs == null) {
        jobs.forEach { it.cancel() }
        println("Поиск прерван по таймауту")
        return@runBlocking
    }

    val duplicates = pairs.groupBy { it.second }
        .filter { (_, list) -> list.size > 1 }

    if (duplicates.isEmpty()) {
        println("Дубликаты не найдены.")
    } else {
        duplicates.forEach { (hash, list) ->
            println("SHA-256: $hash")
            list.forEach { (file, _) -> println(" - ${file.absolutePath}") }
            println()
        }
    }
}
