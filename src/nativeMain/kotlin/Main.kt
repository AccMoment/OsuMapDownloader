import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking


fun main(): Unit = runBlocking {
    //MapDownloader()
    val client = HttpClient(Curl) {
    }
//    val file = FileSystem.SYSTEM.read("C:\\Users\\AccMoment\\Desktop\\1.txt".toPath()) {
//        readUtf8()
//    }
//    println(file)
    client.get {

    }
    readln()
}