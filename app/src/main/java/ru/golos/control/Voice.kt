package ru.golos.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

class Voice : AccessibilityService() {

    companion object {
        var instance: Voice? = null
        val MIC_APPS = listOf(
            "com.whatsapp", "org.telegram.messenger", "com.viber.voip", "com.discord",
            "com.huawei.camera", "com.android.camera", "com.android.soundrecorder",
            "com.android.dialer", "com.android.phone", "com.google.android.googlequicksearchbox"
        )
        val NUMBERS = mapOf(
            "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
            "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
            "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
            "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
            "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20
        )
        val ALIASES = mapOf(
            "телеграм" to "org.telegram.messenger", "телега" to "org.telegram.messenger",
            "ватсап" to "com.whatsapp", "вотсап" to "com.whatsapp",
            "ютуб" to "com.google.android.youtube", "youtube" to "com.google.android.youtube",
            "вконтакте" to "com.vkontakte.android", "вк" to "com.vkontakte.android",
            "инстаграм" to "com.instagram.android", "хром" to "com.android.chrome",
            "почта" to "com.google.android.gm", "карты" to "com.google.android.apps.maps"
        )
    }

    private val marks = ArrayList<View>()
    private val targets = ArrayList<AccessibilityNodeInfo>()
    private var wm: WindowManager? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isNotEmpty() && pkg != packageName) {
                Ear.micBusy = MIC_APPS.any { pkg.startsWith(it) }
                clearMarks()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        clearMarks()
        instance = null
        return super.onUnbind(intent)
    }

    private fun say(t: String) { Ear.instance?.say(t) }

    fun execute(text: String) {
        main.post { handle(text.lowercase(Locale("ru")).trim()) }
    }

    private fun handle(c: String) {
        when {
            c.startsWith("открой ") -> openApp(c.removePrefix("открой ").trim())
            c.startsWith("запусти ") -> openApp(c.removePrefix("запусти ").trim())
            c.contains("покажи номера") -> showNumbers()
            c.contains("убери номера") || c.contains("скрой номера") -> { clearMarks(); say("убрал") }
            c.startsWith("нажми ") -> press(c.removePrefix("нажми ").trim())
            c.startsWith("набери ") -> type(c.removePrefix("набери ").trim())
            c.startsWith("напиши ") -> type(c.removePrefix("напиши ").trim())
            c.contains("прокрути вниз") || c == "вниз" -> scroll(true)
            c.contains("прокрути вверх") || c == "вверх" -> scroll(false)
            c.contains("назад") -> { performGlobalAction(GLOBAL_ACTION_BACK); say("назад") }
            c.contains("домой") -> { performGlobalAction(GLOBAL_ACTION_HOME); say("домой") }
            c.contains("недавние") -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            c.contains("прочитай экран") || c.contains("что на экране") -> readScreen()
            else -> say("не понял команду")
        }
    }

    private fun translit(s: String): String {
        val m = mapOf('a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф",
            'g' to "г", 'h' to "х", 'i' to "и", 'j' to "дж", 'k' to "к", 'l' to "л",
            'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р",
            's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс",
            'y' to "й", 'z' to "з")
        val sb = StringBuilder()
        for (ch in s) sb.append(m[ch] ?: ch.toString())
        return sb.toString()
    }

    private fun openApp(name: String) {
        if (name.isEmpty()) { say("скажите название"); return }
        val pm = packageManager
        val alias = ALIASES[name]
        if (alias != null) {
            val i = pm.getLaunchIntentForPackage(alias)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                say("открываю")
                return
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPkg: String? = null
        var bestScore = 0
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val label = ri.loadLabel(pm).toString().lowercase(Locale("ru"))
            val ru = translit(label)
            val score = when {
                label == name || ru == name -> 4
                label.startsWith(name) || ru.startsWith(name) -> 3
                label.contains(name) || ru.contains(name) -> 2
                else -> 0
            }
            if (score > bestScore) { bestScore = score; bestPkg = ri.activityInfo.packageName }
        }
        if (bestPkg != null) {
            val i = pm.getLaunchIntentForPackage(bestPkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                say("открываю")
                return
            }
        }
        say("не нашёл приложение " + name)
    }

    private fun collect(n: AccessibilityNodeInfo, out: ArrayList<AccessibilityNodeInfo>, seen: HashSet<String>) {
        if (out.size >= 25) return
        val r = Rect()
        n.getBoundsInScreen(r)
        if (n.isVisibleToUser && (n.isClickable || n.isCheckable) && r.width() > 0 && r.height() > 0) {
            if (seen.add(r.toString())) out.add(n)
        }
        for (i in 0 until n.childCount) {
            val ch = n.getChild(i) ?: continue
            collect(ch, out, seen)
        }
    }

    private fun showNumbers() {
        clearMarks()
        val root = rootInActiveWindow ?: run { say("экран недоступен"); return }
        val list = ArrayList<AccessibilityNodeInfo>()
        collect(root, list, HashSet())
        if (list.isEmpty()) { say("кнопок не нашёл"); return }
        var n = 0
        for (node in list) {
            val r = Rect()
            node.getBoundsInScreen(r)
            n++
            targets.add(node)
            val tv = TextView(this)
            tv.text = n.toString()
            tv.textSize = 13f
            tv.setTextColor(Color.WHITE)
            tv.setBackgroundColor(Color.parseColor("#CC1B5E20"))
            tv.setPadding(10, 2, 10, 2)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = r.left
            lp.y = r.top
            try { wm?.addView(tv, lp); marks.add(tv) } catch (e: Exception) {}
        }
        say("номера показаны")
    }

    private fun clearMarks() {
        for (v in marks) { try { wm?.removeView(v) } catch (e: Exception) {} }
        marks.clear()
        targets.clear()
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    private fun press(arg: String) {
        if (arg.isEmpty()) { say("скажите что нажать"); return }
        val num = NUMBERS[arg] ?: arg.toIntOrNull()
        if (num != null) {
            if (num in 1..targets.size && clickNode(targets[num - 1])) { say("нажал"); clearMarks() }
            else say("номера нет на экране")
            return
        }
        val root = rootInActiveWindow ?: run { say("экран недоступен"); return }
        val found = root.findAccessibilityNodeInfosByText(arg)
        if (found != null) {
            for (node in found) {
                if (node.isVisibleToUser && clickNode(node)) { say("нажал"); return }
            }
        }
        say("не нашёл " + arg)
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val r = findScrollable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun scroll(down: Boolean) {
        val node = findScrollable(rootInActiveWindow)
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node != null && node.performAction(action)) {
            say(if (down) "вниз" else "вверх")
            return
        }
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = dm.heightPixels * (if (down) 0.7f else 0.3f)
        val y2 = dm.heightPixels * (if (down) 0.3f else 0.7f)
        val path = Path()
        path.moveTo(x, y1)
        path.lineTo(x, y2)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build()
        dispatchGesture(g, null, null)
        say(if (down) "вниз" else "вверх")
    }

    private fun type(text: String) {
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus == null) { say("нет поля ввода"); return }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        say("набрал")
    }

    private fun readScreen() {
        val root = rootInActiveWindow ?: run { say("экран недоступен"); return }
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo) {
            val t = n.text?.toString()
            if (!t.isNullOrEmpty() && sb.length < 400) sb.append(t).append(". ")
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        say(if (sb.isEmpty()) "текста нет" else sb.toString())
    }
}
