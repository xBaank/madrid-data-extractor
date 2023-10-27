import arrow.core.Either
import arrow.core.continuations.either
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import simpleJson.*
import simpleJson.exceptions.JsonException
import java.io.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.zip.ZipFile

private val gtfsIds = hashSetOf(
    "5c7f2951962540d69ffe8f640d94c246",
    "868df0e58fca47e79b942902dffd7da0",
    "357e63c2904f43aeb5d8a267a64346d8",
    "885399f83408473c8d815e40c5e702b7",
    "aaed26cc0ff64b0c947ac0bc3e033196",
    "1a25440bf66f499bae2657ec7fb40144"
)

private val itinerariesIds = hashSetOf(
    "885399f83408473c8d815e40c5e702b7",
)

private fun getGtfsDownloadUrl(id: String) = "https://crtm.maps.arcgis.com/sharing/rest/content/items/$id/data"

class GtfsCommands : CliktCommand() {
    val output: String by option("--output-stops", help = "Output file name").default("stops.json")
    val itinerariesOutput: String by option(
        "--output-itineraries",
        help = "Output file name"
    ).default("itineraries.json")

    override fun run(): Unit = runBlocking {
        try {
            val streams = gtfsIds.map {
                println("Downloading ${getGtfsDownloadUrl(it)}")
                async {
                    it to httpClient.sendAsync(
                        HttpRequest.newBuilder()
                            .GET()
                            .uri(getGtfsDownloadUrl(it).let(URI::create))
                            .build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                    )
                        .await()
                        .body()
                }
            }.awaitAll()

            //write to temp files
            streams.forEach { (id, stream) ->
                println("Writing to temp/$id.zip")
                val file = File("temp/$id.zip")
                file.parentFile.mkdirs()
                file.createNewFile()
                file.outputStream().use { stream.copyTo(it) }
            }

            //unzip
            streams.forEach { (id, _) ->
                println("Unzipping temp/$id.zip")
                unzip(File("temp/$id.zip"), "temp/$id")
            }

            //read csvs
            val stopsJson = streams.map { (id, _) ->
                println("Reading temp/$id/stops.txt")
                csvReader { escapeChar = '\'' }.readAllWithHeader(File("temp/$id/stops.txt")).let(::csvToJson)
            }.flatten().asJson()

            val itinerariesJson = streams
                .filter { (id, _) -> id in itinerariesIds }
                .map { (id, _) ->
                    println("Reading temp/$id/trips.txt")
                    csvReader { escapeChar = '\'' }.readAllWithHeader(File("temp/$id/trips.txt")).let(::csvToJson)
                }.flatten().asJson()

            val stops = extractStops(stopsJson)
            val itineraries = extractItineraries(itinerariesJson)


            stops.fold(
                { println(it) },
                { jsonTransformed ->
                    println("Writing to $output")
                    val file = File(output)
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    file.outputStream().use { jsonTransformed.serialized().byteInputStream().copyTo(it) }
                }
            )

            itineraries.fold(
                { println(it) },
                { jsonTransformed ->
                    println("Writing to $itinerariesOutput")
                    val file = File(itinerariesOutput)
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    file.outputStream().use { jsonTransformed.serialized().byteInputStream().copyTo(it) }
                }
            )

        } catch (e: Exception) {
            println(e)
        } finally {
            //cleanup
            println("Cleaning up")
            File("temp").deleteRecursively()
        }
    }

    private suspend fun extractItineraries(json: JsonArray): Either<JsonException, JsonArray> = either {
        json
            .mapNotNull {
                jObject {
                    "itineraryCode" += it["shape_id"].asString().bind()
                    "lineCode" += it["route_id"].asString().bind()
                    "direction" += it["direction_id"].asString().bind().toInt()
                }
            }
            .distinct()
            .asJson()
    }

    private suspend fun extractStops(json: JsonArray): Either<JsonException, JsonArray> = either {
        val result =
            json
                .mapNotNull { if (!it["stop_id"].asString().bind().contains("par")) null else it }
                .onEach {
                    it["cod_mode"] =
                        it["stop_id"].asString().bind().substringAfter("_").substringBefore("_").toInt()
                }
                .onEach {
                    it["stop_code"] = it["stop_code"].asString().map(String::trim).bind()
                }
                .onEach {
                    it["full_stop_code"] =
                        it["cod_mode"].asNumber().bind().toString() + "_" + it["stop_code"].asString().bind()
                }
                .onEach {
                    it["stop_lat"] = it["stop_lat"].asString().map(String::toDouble).bind()
                    it["stop_lon"] = it["stop_lon"].asString().map(String::toDouble).bind()
                }
                //This a hack to remove duplicates, since the same stop on metro can be repeated with different names
                .distinctBy {
                    Pair(
                        if (it["cod_mode"].asNumber().bind().toString() == metroCodMode) 1 else UUID.randomUUID()
                            .toString(),
                        it["stop_name"].asString().bind()
                    )
                }
                .distinctBy { it["stop_id"].asString().bind() }
                .asJson()
        result
    }
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