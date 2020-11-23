package com.example.smgallery

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import java.lang.Exception
import java.lang.RuntimeException
import java.nio.charset.Charset

class SynologyNasHelper(val host: String, val port: String) {

    private val tag = "SynologyNasHelper"
    private val nasImageProcessorHost = ""
    private val nasImageProcessorPort = 0

    suspend fun getToken(login: String, password: String): String {
        val data = HttpClient().get<HttpResponse>(
            "http://$host:$port/webapi/auth.cgi?" +
                    "api=SYNO.API.Auth" +
                    "&version=3" +
                    "&method=login" +
                    "&account=$login" +
                    "&passwd=$password" +
                    "&session=FileStation" +
                    "&format=sid"
        )
        val json = data.readText(Charset.defaultCharset())
        Log.i(tag, "Response body /getToken: $json")
        val nasObject: NasObject<Sid> = Gson().fromJson(json, object : TypeToken<NasObject<Sid>>() {}.type)

        if (!nasObject.success) {
            throw RuntimeException("Can't execute query because server returns an error with code ${nasObject.error.code}: " +
                    AuthErrorConverter().convert(nasObject.error.code))
        }
        return nasObject.data.sid
    }

    suspend fun list(baseDirectory: String, patterns: List<String> = listOf(), token: String?): NasDataFile {
        if (!(!token.isNullOrEmpty() && !token.isBlank())) {
            throw RuntimeException("The token must be provided!")
        }
        val queryPatterns = patterns.joinToString(separator = ",")
        val data = HttpClient().get<HttpResponse>(
            "http://$host:$port/webapi/entry.cgi?" +
                    "api=SYNO.FileStation.List" +
                    "&version=2" +
                    "&method=list" +
                    "&folder_path=$baseDirectory" +
                    if (queryPatterns.isNotEmpty()) "&pattern=$queryPatterns" else "" +
                    "&session=FileStation" +
                    "&additional=real_path" +
                    "&_sid=$token"
        )
        if (data.status != HttpStatusCode.OK) {
            throw RuntimeException("Cant retrieve list of files. Server returns ${data.status}")
        }
        val json = data.readText(Charset.defaultCharset())
        Log.i(tag, "Response body /list: $json")
        val nasObject: NasObject<NasDataFile> = Gson().fromJson(json, object : TypeToken<NasObject<NasDataFile>>() {}.type)
        if (!nasObject.success) {
            throw RuntimeException("Can't execute query because server returns an error with code ${nasObject.error.code}: " +
                    CommonErrorConverter().convert(nasObject.error.code))
        }
        return nasObject.data
    }

    suspend fun downloadFile(path: String, mode: Mode, token: String?): ByteArray {
        if (!(!token.isNullOrEmpty() && !token.isBlank())) {
            throw RuntimeException("The token must be provided!")
        }
        val data = HttpClient().get<HttpResponse>(
            "http://$host:$port/webapi/entry.cgi?" +
                    "api=SYNO.FileStation.Download" +
                    "&version=2" +
                    "&method=download" +
                    "&path=$path" +
                    "&mode=${mode.mode}" +
                    "&_sid=$token"
        )
        if (data.status != HttpStatusCode.OK) {
            throw RuntimeException("Cant retrieve list of files. Server returns ${data.status}")
        }
        return data.readBytes()
    }

    suspend fun processAndDownloadFile(path: String, width: Int, height: Int): ByteArray {
        val data = HttpClient().get<HttpResponse>(
            "http://$nasImageProcessorHost:$nasImageProcessorPort/api/v1/image?" +
                    "&path=$path" +
                    "&width=$width" +
                    "&height=$height"
        )
        if (data.status != HttpStatusCode.OK) {
            throw NasHelperExceptions().fileCantBeProcessed(path)
        }
        return data.readBytes()
    }

    suspend fun checkConnection(): Boolean {
        try {
            val data = HttpClient().get<HttpResponse>(
                "http://$nasImageProcessorHost:$nasImageProcessorPort/api/v1"
            )
            if (data.status == HttpStatusCode.OK) {
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, e.message.orEmpty())
        }
        return false
    }
}