package ru.golos.control

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 64, 48, 48)

        status = TextView(this)
        status.textSize = 16f
        root.addView(status)

        root.addView(button("1. Разрешить микрофон") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        root.addView(button("2. Включить службу в спецвозможностях") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("3. Отключить оптимизацию батареи") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(button("Запустить прослушивание") { start() })
        root.addView(button("Остановить прослушивание") {
            stopService(Intent(this, Ear::class.java))
        })

        setContentView(root)
        if (micOk()) start()
    }

    private fun button(text: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = 24
        b.layoutParams = lp
        return b
    }

    private fun micOk(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun serviceOk(): Boolean {
        val list = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return list.contains(packageName)
    }

    private fun start() {
        if (!micOk()) {
            toast("Сначала разрешите микрофон — кнопка 1")
            return
        }
        startForegroundService(Intent(this, Ear::class.java))
        toast("Прослушивание запущено")
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        status.text = "Микрофон: " + (if (micOk()) "разрешён" else "НЕТ разрешения") +
                "\nСлужба спецвозможностей: " + (if (serviceOk()) "включена" else "НЕ включена") +
                "\n\nСкажите «телефон», затем команду." +
                "\nНапример: «открой телеграм», «покажи номера», «нажми пять»," +
                " «прокрути вниз», «назад», «домой»."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (micOk()) start()
        onResume()
    }
}
