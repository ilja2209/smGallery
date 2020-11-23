package com.example.smgallery

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class MainActivity : Activity() {

    private val tag = "MyActivity"

    private val firstPartPhotos = "first_part";
    private val secondPartPhotos = "second_part";
    private val switchImageInterval = 20; //sec
    private val host = ""
    private val port = ""
    private val user = ""
    private val psswd = ""
    private val nasTmpFile = "nasFiles.tmp"
    private val countOfDownloadedImages = 200
    private val stringLength = 10;
    private val charPool = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val downloadNewImagesInterval: Int = 4 //hours

    private var currentDirectory = firstPartPhotos
    private val isBuildingImageTree = AtomicBoolean(true)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fullScreen()
        try {
            start()
        } catch (e: Exception) {
            e.printStackTrace()
            val alertDialog: AlertDialog = AlertDialog.Builder(this@MainActivity).create()
            alertDialog.setTitle("Error")
            alertDialog.setMessage(e.localizedMessage)
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK") { _, _ -> exitProcess(-1) }
            alertDialog.show()
        }
    }

    private fun start() {
        val nasHelper = SynologyNasHelper(host, port)
        val token = runBlocking {
            nasHelper.getToken(user, psswd)
        }

        val images = CopyOnWriteArrayList<String>()
        val tempImages = CopyOnWriteArrayList<String>()

        GlobalScope.launch() {
            while (true) {
                //If It is a first running, fill images collection, after fill only tempImages.
                //It done to show image as soon as possible
                downloadNewImages(nasHelper, token, if (images.isEmpty()) images else tempImages)
                //It is needed to provide continuity showing images.
                // While 1 thread show image using images collection, second thread fills tempImages.
                //After filling it copies to images collection.
                if (tempImages.isNotEmpty()) {
                    images.clear()
                    images.addAll(tempImages)
                    tempImages.clear()
                }
                @OptIn(kotlin.time.ExperimentalTime::class)
                delay(downloadNewImagesInterval.toDuration(DurationUnit.HOURS))
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                if (!isBuildingImageTree.get()) {
                    loadingPanel.visibility = View.GONE
                }
                images.forEach { image ->
                    if (imageView.drawable != null) {
                        (imageView.drawable as BitmapDrawable).bitmap.recycle()
                    }
                    imageView.setImageDrawable(null)
                    imageView.setImageURI(Uri.fromFile(File(image)))
                    @OptIn(kotlin.time.ExperimentalTime::class)
                    delay(switchImageInterval.toDuration(DurationUnit.SECONDS))
                }
                delay(100)
            }
        }
    }

    private suspend fun downloadNewImages(
        nasHelper: SynologyNasHelper,
        token: String,
        images: CopyOnWriteArrayList<String>
    ) {
        val outDir = File("/storage/emulated/0" + "/temp");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw RuntimeException("Can't create folder with path ${outDir.canonicalPath}")
        }

        val isConnected = nasHelper.checkConnection()
        if (!isConnected) {
            images.addAll(getImagesFormCache(outDir))
            isBuildingImageTree.set(false)
            return
        }

        val listImages =
            getImagesPaths(nasHelper, token, outDir).shuffled().take(countOfDownloadedImages)
        val directory = getCurrentDirectory(outDir)

        listImages.forEach { image ->
            downloadFileFromNas(nasHelper, image, directory, images)
        }
        Log.i(tag, directory.listFiles().toString())
    }

    private fun getCurrentDirectory(outDir: File): File {
        if (currentDirectory == firstPartPhotos) {
            currentDirectory = secondPartPhotos
        } else {
            currentDirectory = firstPartPhotos
        }
        val directory = File(outDir.absolutePath + "/$currentDirectory")
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        if (!directory.mkdir()) {
            throw RuntimeException("Can't create folder with path ${directory.canonicalPath}")
        }
        return directory
    }

    private suspend fun getImagesPaths(
        nasHelper: SynologyNasHelper,
        token: String,
        outDir: File
    ): List<String> {
        val tempFile = File(outDir.absolutePath + "/$nasTmpFile")
        if (!tempFile.exists()) {
            buildNasImagesPathsTree(nasHelper, token, "/photo/photoframe", tempFile)
        } else {
            tempFile.delete()
            buildNasImagesPathsTree(nasHelper, token, "/photo/photoframe", tempFile)
        }
        isBuildingImageTree.set(false)
        return tempFile.readLines()
    }

    private suspend fun buildNasImagesPathsTree(
        nasHelper: SynologyNasHelper,
        token: String,
        nasBaseDirectory: String,
        tmpFile: File
    ) {
        val nasDataFile = nasHelper.list(nasBaseDirectory, listOf(), token)
        nasDataFile.files
            .filter { nasFile ->
                nasFile.isdir || nasFile.name.toLowerCase(Locale.ROOT).contains(".jpg")
            }
            .forEach { nasFile ->
                run {
                    if (nasFile.isdir) {
                        Log.i(tag, "File " + nasFile.path + " (isDir)")
                        buildNasImagesPathsTree(nasHelper, token, nasFile.path, tmpFile)
                    } else {
                        Log.i(tag, "Add file " + nasFile.path + " to tmp")
                        val fr = FileWriter(tmpFile, true)
                        fr.write(nasFile.path + "\n")
                        fr.close()
                    }
                }
            }
    }

    private suspend fun downloadFileFromNas(
        nasHelper: SynologyNasHelper,
        nasFile: String,
        outDir: File,
        images: CopyOnWriteArrayList<String>
    ) {
        try {
            Log.i(tag, "download file $nasFile")
            val bytes = nasHelper.processAndDownloadFile(nasFile, 1024, 768)
            val outFile = File(outDir.absolutePath + "/" + getRandomString() + ".jpg")
            outFile.writeBytes(bytes)
            Log.i(tag, "Image ${outFile.absolutePath} written")
            images.add(outFile.absolutePath)
        } catch (e: Exception) {
            Log.e(tag, e.message.orEmpty())
        }
    }

    private fun getImagesFormCache(baseDir: File): List<String> {
        val cachedImages = ArrayList<String>()
        val oneDir = File(baseDir.absolutePath + "/" + firstPartPhotos)
        val secondDir = File(baseDir.absolutePath + "/" + secondPartPhotos)
        if (oneDir.exists()) {
            cachedImages.addAll(oneDir.list().map { it -> oneDir.absolutePath + "/" + it }.toList())
        }
        if (secondDir.exists()) {
            cachedImages.addAll(secondDir.list().map { it -> secondDir.absolutePath + "/" + it }.toList())
        }
        return cachedImages
    }

    private fun getRandomString(): String {
        val randomString = (1..stringLength)
            .map { i -> Random.nextInt(0, charPool.length) }
            .joinToString("") { ch -> charPool[ch].toString() };
        return randomString
    }

    //The code from https://stackoverflow.com/questions/24463691/how-to-show-imageview-full-screen-on-imageview-click
    //It is needed to be refactored
    private fun fullScreen() {
        val uiOptions = getWindow().getDecorView().getSystemUiVisibility()
        var newUiOptions = uiOptions
        val isImmersiveModeEnabled =
            ((uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions)
        if (isImmersiveModeEnabled) {
            Log.i(tag, "Turning immersive mode mode off. ");
        } else {
            Log.i(tag, "Turning immersive mode mode on.");
        }
        newUiOptions = newUiOptions xor View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        newUiOptions = newUiOptions xor View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions = newUiOptions xor View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }
}