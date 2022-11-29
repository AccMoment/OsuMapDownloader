import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Config
import model.SinceDate
import okio.FileSystem
import okio.Path.Companion.toPath


private val json = Json { prettyPrint = true }
fun main(): Unit = runBlocking {
    val configFileName = "download_config.json"
    val configFileExist = FileSystem.SYSTEM.exists(configFileName.toPath())

    if (!configFileExist) {
        println("检测到配置文件缺失...")
        println("生成设置列表中...")
        FileSystem.SYSTEM.write(configFileName.toPath()) {
            val str = json.encodeToString(Config())
            writeUtf8(str)
        }
        println("请稍后在配置文件($configFileName)中添加osu!apikey和osu路径")
        println("请按任意键退出....")
        readln()
        return@runBlocking
    }

    var config: Config? = null

    FileSystem.SYSTEM.read(configFileName.toPath()) {
        config = Json.decodeFromString(readUtf8())
    }

    if (!FileSystem.SYSTEM.exists("${config?.osuPath}\\Songs".toPath())) {
        println("osu!路径错误! 请重新设置osu!路径")
        println("请按任意键退出....")
        readln()
        return@runBlocking
    }
    val mode = getMode()
    val sinceDate = SinceDate(2022, 11, 25)
    val mapDownloader =
        MapDownloader(apiKey = config?.apiKey!!, sinceTime = sinceDate, osuPath = config?.osuPath!!, mode = mode)
    try {
        mapDownloader.start()
        mapDownloader.job?.join()
    } catch (e: Exception) {
        println(e.message)
    }
//    val list=FileSystem.SYSTEM.list("${config?.osuPath!!}\\Songs".toPath())
//    println(list.size)
//    println(list.first().name)
    println("all downloads completed")
    readln()
}

fun getMode(): Int {
    var mode = 0
    println("请选择需要下载的模式(std=0,taiko=1,ctb=2,mania=3),空默认为std")
    while (true) {
        try {
            val str = readln()
            if (str.isEmptyOrBlack()) return mode
            mode = str.toInt()
            if (mode < 0 || mode > 3) throw Exception("无效数字")
            break
        } catch (e: Exception) {
            println("无效数字,请重新输入")
        }
    }
    return mode
}


fun CharSequence.isEmptyOrBlack() = isEmpty() || isBlank()