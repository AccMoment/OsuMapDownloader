import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn

class MapDownloader {
    private val mapChannel = Channel<Int>()

    init {
        CoroutineScope(Dispatchers.Default+CoroutineName("MapDownloader")).launch {
            launch { produceMapId() }
            launch { consumeMapId() }
        }


    }
    private suspend fun CoroutineScope.produceMapId() {
        (1..10).forEach {
            mapChannel.send(it)
            println("suspend...")
        }
    }

    private suspend fun CoroutineScope.consumeMapId(){
        for(id in mapChannel){
            delay(100)
            println("receive id:$id")
        }
    }
}