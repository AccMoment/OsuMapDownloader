package model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
 class Config @OptIn(ExperimentalSerializationApi::class) constructor(
    @EncodeDefault val apiKey:String ="input your key",
    //@EncodeDefault val startDate:String ="2007-10-6",
    @EncodeDefault val osuPath:String ="input your osu! path"
){

 }