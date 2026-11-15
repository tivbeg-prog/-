package ru.golos.control

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class Boot : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            context.startForegroundService(Intent(context, Ear::class.java))
        } catch (e: Exception) {
            // служба поднимется при следующем старте экрана
        }
    }
}
