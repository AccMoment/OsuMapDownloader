import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val client= HttpClient(Curl)
    val response: HttpResponse = client.get("https://ktor.io/docs/welcome.html")
    println(response.status)
    readln()
}