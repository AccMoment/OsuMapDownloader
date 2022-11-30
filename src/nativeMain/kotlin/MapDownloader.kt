import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.BeatMapInfo
import model.SinceDate
import okio.FileSystem
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class MapDownloader(
    private val apiKey: String,
    private var sinceTime: SinceDate,
    private val osuPath: String,
    val mode: Int,
) {

    private val mapChannel = Channel<Int>(5)

    private val scope = CoroutineScope(newFixedThreadPoolContext(5, "MapDownloader"))
    var job: Job? = null

    private val client = HttpClient(Curl) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000*60*15L
            connectTimeoutMillis = 1000 * 60L
        }
//        defaultRequest {
//            url("https://dl.sayobot.cn/beatmaps/download/full/")
//            headers {
//                append(HttpHeaders.Referrer, "https://github.com/AccMoment/OsuMapDownloader")
//                append(
//                    HttpHeaders.UserAgent,
//                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.56"
//                )
//            }
//        }
    }

    private suspend fun produceMapList() {
        var mapCount = 500
        var date = sinceTime.toString()
        val songsPath = "$osuPath\\Songs"
        val songs = FileSystem.SYSTEM.list(songsPath.toPath()).map {
            it.name
                .split(" ")[0]
                .filter { id -> id in '0'..'9' }
        }.filter { !it.isEmptyOrBlack() }
            .map { it.toInt() }
        while (mapCount != 0) {

            //println(url)
            println("request from osu!api to query maps....")
            val response = client.get(beatMapsApi) {
                url {
                    //k=$apiKey&m=$mode&since=$date
                    parameters.append("k", apiKey)
                    parameters.append("m", mode.toString())
                    parameters.append("since", date)
                }
            }
            val result = Json.decodeFromString<List<BeatMapInfo>>(response.bodyAsText())
            mapCount = result.size
            if (mapCount != 0) date = result.last().approvedDate
            val mapList = result
                .filter { it.approved.toInt()>0 }
                .map {
                it.beatMapSetId.toInt()
            }.distinct().filter { !songs.contains(it) }
            mapList.forEach {
                mapChannel.send(it)
            }
        }
        mapChannel.close()
    }

    private suspend fun downloadMap(id: Int) {
        try {
            client.prepareGet(sayoDownLoadApi, block = {
                url {
                    appendEncodedPathSegments(id.toString())
                }
                headers {
                    append(HttpHeaders.Referrer, "https://github.com/AccMoment/OsuMapDownloader")
                    append(
                        HttpHeaders.UserAgent,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.56"
                    )
                }
            })
                .execute { response ->
                    val cd = response.headers["content-disposition"]
                    val fileName = cd?.split("filename=\"")?.get(1)?.split("\";")?.get(0)!!.decodeURLPart()

                    val file = FileSystem.SYSTEM.openReadWrite(
                        file = "$osuPath\\Songs\\$fileName".toPath(),
                        mustCreate = false,
                        mustExist = false
                    )
                    try {
                        val channel: ByteReadChannel = response.body()
                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining((10 * 1024 * 1024).toLong())
                            while (!packet.isEmpty) {
                                val bytes = packet.readBytes()
                                file.write(file.size(), bytes, 0, bytes.size)
                            }
                        }
                        file.flush()
                        file.close()
                        println("$fileName was download completed")
                        println()
                    }catch (e:Exception){
                        println("$fileName was download fail")
                        throw Exception(e.message)
                    }
                }
        } catch (e: Exception) {
            println("fail reason:${e.message}")
            println()
        }
    }

    private suspend fun download() {
        for (mapId in mapChannel) {
            downloadMap(mapId)
        }
    }

    fun start() {
        job = scope.launch {
            launch { produceMapList() }
            repeat(5) {
                launch(coroutineContext) {
                    download()
                }
            }
        }
    }
}

const val beatMapsApi="https://osu.ppy.sh/api/get_beatmaps"
const val sayoDownLoadApi="https://dl.sayobot.cn/beatmaps/download/full/"

