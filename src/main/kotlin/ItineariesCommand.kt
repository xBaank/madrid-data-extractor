import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import simpleJson.asJson
import simpleJson.serialized
import java.io.File
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val itinerariesIds = hashSetOf(
    "19884a02ac044270b91fa478d80f7858_3",
)

private fun getStopsInfoDownloadUrl(id: String) =
    "https://opendata.arcgis.com/api/v3/datasets/$id/downloads/data?format=csv&spatialRefId=25830&where=1%3D1"

class ItineariesCommand : CliktCommand() {
    val output: String by option("--output-itineraries", help = "Output file name").default("itineraries.json")
    override fun run(): Unit = runBlocking {
        try {
            val streams = itinerariesIds.map {
                println("Downloading ${getStopsInfoDownloadUrl(it)}")
                async {
                    httpClient.sendAsync(
                        HttpRequest.newBuilder()
                            .GET()
                            .uri(getStopsInfoDownloadUrl(it).let(URI::create))
                            .build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                    )
                        .await()
                        .body()
                }
            }.awaitAll()

            //read csvs
            val json = streams.mapIndexed { index, inputStream ->
                println("Reading itineraries")
                csvReader().readAllWithHeader(inputStream).let(::csvToJson)
            }.flatten().asJson().asJson()

            //write as json to output
            println("Writing to $output")
            val file = File(output)
            file.parentFile?.mkdirs()
            file.createNewFile()
            file.outputStream().use { json.serialized().byteInputStream().copyTo(it) }

        } catch (e: Exception) {
            println(e)
        } finally {
            //cleanup
            println("Cleaning up")
            File("temp").deleteRecursively()
        }
    }
}