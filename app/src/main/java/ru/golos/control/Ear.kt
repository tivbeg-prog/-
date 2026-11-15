package ru.golos.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class Ear : Service() {

    companion object {
        var instance: Ear? = null
        var alwaysMode = false
        var muted = false
        var micBusy = false
        const val WAKE = "[\"телефон\", \"[unk]\"]"
    }

    private var model: Model? = null
    private var tts: TextToSpeech? = null
    @Volatile private var running = false
    @Volatile private var speaking = false
    @Volatile private var screenOn = true
    @Volatile private var commandUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_SCREEN_ON) { screenOn = true; muted = false }
            if (i?.action == Intent.ACTION_SCREEN_OFF) screenOn = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_ON)
        f.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, f)
        startForeground(1, notification("Загрузка модели…"))

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ru")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false }
                override fun onError(id: String?) { speaking = false }
            })
        }

        running = true
        Thread { loop() }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        instance = null
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        tts?.shutdown()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("golos", "GoloPom", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm?.createNotificationChannel(ch)
        return Notification.Builder(this, "golos")
            .setContentTitle("GoloPom")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(1, notification(text))
    }

    fun say(text: String) {
        if (text.isEmpty()) return
        speaking = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "g" + System.currentTimeMillis())
        Handler(Looper.getMainLooper()).postDelayed({ speaking = false }, 2000L + text.length * 70L)
    }

    private fun copyAssets(src: String, dst: File) {
        val list = assets.list(src) ?: emptyArray()
        if (list.isEmpty()) {
            dst.parentFile?.mkdirs()
            assets.open(src).use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
        } else {
            dst.mkdirs()
            for (name in list) copyAssets("$src/$name", File(dst, name))
        }
    }

    private fun ensureModel(): Boolean {
        if
