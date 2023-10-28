import kotlinx.coroutines.*

suspend fun <T, R : Any> Iterable<T>.mapAsync(transform: suspend (T) -> R?): List<R> = coroutineScope {
    map { async { transform(it) } }.awaitAll().filterNotNull()
}

suspend fun <T, C : Iterable<T>> C.onEachIndexedAsync(action: suspend (Int,Int, T) -> Unit): C = coroutineScope {
    mapIndexed { index, node -> launch(Dispatchers.IO) { action(index, count(), node) } }.joinAll()
    this@onEachIndexedAsync
}