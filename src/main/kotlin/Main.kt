import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import simpleJson.*
import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipFile

private const val BUFFER_SIZE = 4096

val ids = hashSetOf(
    "5c7f2951962540d69ffe8f640d94c246",
    "868df0e58fca47e79b942902dffd7da0",
    "357e63c2904f43aeb5d8a267a64346d8",
    "885399f83408473c8d815e40c5e702b7",
    "aaed26cc0ff64b0c947ac0bc3e033196",
    "1a25440bf66f499bae2657ec7fb40144"
)
val httpClient = HttpClient.newHttpClient()
fun getDownloadUrl(id:String) = "https://crtm.maps.arcgis.com/sharing/rest/content/items/$id/data"

fun main(): Unit = runBlocking {
    val streams = ids.map {
        println(getDownloadUrl(it))
        httpClient.sendAsync(
            HttpRequest
                .newBuilder()
                .GET()
                .uri(getDownloadUrl(it).let(URI::create))
                .build(),
            HttpResponse
                .BodyHandlers
                .ofByteArray())
            .await()
            .body()
    }

    //write to temp files
    streams.forEachIndexed { index, stream ->
        val file = File("temp/$index.zip")
        file.parentFile.mkdirs()
        file.createNewFile()
        file.outputStream().use { stream.inputStream().copyTo(it) }
    }

    //unzip
    streams.forEachIndexed { index, _ ->
        unzip(File("temp/$index.zip"), "temp/$index")
    }

    //read csvs
    val jsons = List(streams.size) { index ->
        csvReader { escapeChar = '\'' }.readAllWithHeader(File("temp/$index/stops.txt")).let(::csvToJson)
    }.flatten().asJson()


    //write jsons
    val file = File("stops.json")
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.outputStream().use { jsons.serialized().byteInputStream().copyTo(it) }

    //cleanup
    File("temp").deleteRecursively()
}

fun unzip(zipFilePath: File, destDirectory: String) {
    File(destDirectory).run {
        if (!exists()) {
            mkdirs()
        }
    }

    ZipFile(zipFilePath).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val filePath = destDirectory + File.separator + entry.name

                if (!entry.isDirectory) {
                    // if the entry is a file, extracts it
                    extractFile(input, filePath)
                } else {
                    // if the entry is a directory, make the directory
                    val dir = File(filePath)
                    dir.mkdir()
                }
            }
        }
    }
}

@Throws(IOException::class)
private fun extractFile(inputStream: InputStream, destFilePath: String) {
    val bos = BufferedOutputStream(FileOutputStream(destFilePath))
    val bytesIn = ByteArray(BUFFER_SIZE)
    var read: Int
    while (inputStream.read(bytesIn).also { read = it } != -1) {
        bos.write(bytesIn, 0, read)
    }
    bos.close()
}

private fun csvToJson(csv: List<Map<String, String>>): List<JsonNode> {
    return csv.map { jObject { it.forEach { entry ->  entry.key += entry.value } }.asJson() }
}
