package com.example.smgallery

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import java.lang.RuntimeException
import java.nio.charset.Charset

class GalleryServerHelper(val host: String, val port: String) {
    suspend fun getRandomPhoto(): Image {
        if (host.isEmpty() || port.isEmpty()) {
            throw RuntimeException("Host and Port must be specified!")
        }
        val data = HttpClient().get<HttpResponse>("http://$host:$port/api/v1/photos")
        if (data.status != HttpStatusCode.OK) {
            throw RuntimeException("Cant get a new image")
        }
        val json = data.readText(Charset.defaultCharset())
        return Gson().fromJson(json, object : TypeToken<Image>() {}.type)
    }

    suspend fun downloadPhoto(id: String): ByteArray {
        if (host.isEmpty() || port.isEmpty()) {
            throw RuntimeException("Host and Port must be specified!")
        }
        val data = HttpClient().get<HttpResponse>("http://$host:$port/api/v1/photos/$id/image")
        if (data.status != HttpStatusCode.OK) {
            throw RuntimeException("Can't download image data")
        }
        return data.readBytes()
    }
}