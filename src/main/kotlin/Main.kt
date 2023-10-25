import simpleJson.JsonNode
import simpleJson.asJson
import simpleJson.jObject
import java.net.http.HttpClient

const val BUFFER_SIZE = 4096
const val metroCodMode = "4"


val httpClient = HttpClient.newHttpClient()

fun main(args: Array<String>) {
    StopsCommand().main(args)
    StopsInfoCommand().main(args)
    ItineariesCommand().main(args)
}

fun csvToJson(csv: List<Map<String, String>>): List<JsonNode> {
    return csv.map { jObject { it.forEach { entry -> entry.key += entry.value } }.asJson() }
}
