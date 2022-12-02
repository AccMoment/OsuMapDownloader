import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.BeatMapInfo
import model.SinceDate
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.system
import platform.windows.COORD
import platform.windows.GetStdHandle
import platform.windows.STD_OUTPUT_HANDLE
import platform.windows.SetConsoleCursorPosition
import kotlin.math.roundToLong

class MapDownloader(
    private val apiKey: String,
    private var sinceTime: SinceDate,
    private val osuPath: String,
    val mode: Int,
) {
    private var totalCount = 0
    private var currentCount = 0
    private val mapChannel = Channel<Int>(5)
    private var rowPos = atomic(0)
    private val scope = CoroutineScope(newFixedThreadPoolContext(5, "MapDownloader"))
    private var job: Job? = null

    private val client = HttpClient(Curl) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000 * 60 * 15L
            connectTimeoutMillis = 1000 * 15L
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
            setConsoleCursorPosition(0, rowPos.getAndIncrement().toShort())
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
                .filter { it.approved.toInt() > 0 }
                .map {
                    it.beatMapSetId.toInt()
                }.distinct().filter { !songs.contains(it) }
            totalCount += mapList.size
            mapList.forEach {
                mapChannel.send(it)
            }
        }
        mapChannel.close()
    }

    private suspend fun downloadMap(id: Int) {
        val yPos = rowPos.getAndIncrement()
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
                    val contentLength =
                        response.contentLength()?.toDouble()?.div(1024)?.div(1024)?.times(100)?.roundToLong()
                            ?.div(100.0)
                    if (0f.toDouble() == contentLength!!) {
                        throw Exception("content length is 0")
                    }
                    val file = FileSystem.SYSTEM.openReadWrite(
                        file = "$osuPath\\Songs\\$fileName".toPath(),
                        mustCreate = false,
                        mustExist = false
                    )
                    try {
                        val channel: ByteReadChannel = response.body()

                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining(1024 * 512)
                            while (!packet.isEmpty) {
                                val bytes = packet.readBytes()
                                file.write(file.size(), bytes, 0, bytes.size)
                                setConsoleCursorPosition(0, yPos.toShort())
                                val currentProgress = (file.size().toDouble() / 1024 / 1024 * 100).roundToLong() / 100.0
                                print("正在下载 $fileName \u001B[38;5;81m(${currentProgress}M/${contentLength}M%)\u001B[0m")
                                file.flush()
                            }
                        }
                        setConsoleCursorPosition(0, yPos.toShort())
                        currentCount++
                        print("正在下载  $fileName \u001B[38;5;81m(已完成)[$currentCount/$totalCount]\u001B[0m")
                        file.close()
                    } catch (e: Exception) {
                        currentCount++
                        setConsoleCursorPosition(0, yPos.toShort())
                        print("\u001B[38;5;196m下载失败  $fileName \u001B[38;5;81m[$currentCount/$totalCount]\u001B[0m")
                        //throw Exception(e.message)
                    }
                }
        } catch (e: Exception) {
            currentCount++
            setConsoleCursorPosition(0, yPos.toShort())
            print("\u001B[38;5;196m下载失败,发生错误(map:$id) \u001B[38;5;81m[$currentCount/$totalCount]\u001B[0m")
        }
    }

    private suspend fun download() {
        for (mapId in mapChannel) {
            downloadMap(mapId)
        }
    }

    suspend fun start() {
        job = scope.launch {
            launch { produceMapList() }
            repeat(5) {
                launch(coroutineContext) {
                    download()
                }
            }
        }
        job?.join()
        setConsoleCursorPosition(0, rowPos.value.toShort())
        println("all downloads completed")
        system("pause")
    }
}

const val beatMapsApi = "https://osu.ppy.sh/api/get_beatmaps"
const val sayoDownLoadApi = "https://dl.sayobot.cn/beatmaps/download/full/"

fun setConsoleCursorPosition(x: Short, y: Short) {
    val value = cValue<COORD> {
        X = x
        Y = y
    }
    val handle = GetStdHandle(STD_OUTPUT_HANDLE);
    SetConsoleCursorPosition(handle, value)
}

