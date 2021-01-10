package com.example.smgallery

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.OptIn as OptIn1

class MainActivity : Activity() {

    private val tag = "MyActivity"

    private val switchImageInterval = 15; //sec
    private val host = "192.168.88.71"
    private val port = "8082"
    private val galleryServerHelper = GalleryServerHelper(host, port)
    private val countOfDownloadedImages = 200
    private val imageChannel = Channel<ByteArray>()

    @ExperimentalCoroutinesApi
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setDefaultExceptionHandler()
        fullScreen()
        setupAnimation()
        start()
//        try {
//            start()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            val alertDialog: AlertDialog = AlertDialog.Builder(this@MainActivity).create()
//            alertDialog.setTitle("Error")
//            alertDialog.setMessage(e.localizedMessage)
//            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK") { _, _ -> exitProcess(-1) }
//            alertDialog.show()
//        }
    }

    @ExperimentalCoroutinesApi
    private fun start() {
        GlobalScope.launch {
            while (true) {
                val imageBuffer = downloadRandomImage()
                imageChannel.send(imageBuffer)
                @OptIn1(kotlin.time.ExperimentalTime::class)
                delay(switchImageInterval.toDuration(DurationUnit.SECONDS))
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                updateImage()
                updateDateTime()
                delay(200)
            }
        }
    }

    private fun updateDateTime() {
        val dateTime = Calendar.getInstance().time
        val strTime = DateFormat.getTimeFormat(applicationContext).format(dateTime)
        val strDate = SimpleDateFormat("d MMMM", Locale("ru")).format(dateTime)
        val strDayOfWeek = SimpleDateFormat("EEEE", Locale("ru")).format(dateTime)
        timeTextView.text = strTime
        dateTextView.text = strDate
        dayTextView.text = strDayOfWeek
    }

    @ExperimentalCoroutinesApi
    private suspend fun updateImage() {
        if (imageChannel.isEmpty) {
            return
        }
        val buffer = imageChannel.receive()
        val nextView = mainImageSwitcher.nextView as ImageView

        if (nextView.drawable != null) {
            (nextView.drawable as BitmapDrawable).bitmap.recycle()
        }
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size, options)
        nextView.setImageDrawable(null)
        nextView.setImageBitmap(bitmap)

        mainImageSwitcher.showNext()
    }

    private suspend fun downloadRandomImage(): ByteArray {
        val image = galleryServerHelper.getRandomPhoto()
        Log.i(tag, "Download image with id ${image.id}")
        return galleryServerHelper.downloadPhoto(image.id)
    }

    private fun setupAnimation() {
        val inAnimation = AlphaAnimation(0f, 1f)
        inAnimation.duration = 1500

        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 1500

        mainImageSwitcher.inAnimation = inAnimation
        mainImageSwitcher.outAnimation = outAnimation
    }

    //The code is from https://stackoverflow.com/questions/24463691/how-to-show-imageview-full-screen-on-imageview-click
    //FIXME: It has to be refactored
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

    //FIXME: Must be removed after fixing memory leak
    private fun setDefaultExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, throwable: Throwable ->
            val launchIntent = Intent(this.intent)
            val pendingIntent = PendingIntent.getActivity(applicationContext, 0, launchIntent, this.intent.flags)
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent)
            defaultHandler.uncaughtException(thread, throwable)
        }
    }
}