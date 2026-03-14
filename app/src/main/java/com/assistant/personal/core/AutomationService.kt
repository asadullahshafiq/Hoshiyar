package com.assistant.personal.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.*
import android.graphics.Path
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.assistant.personal.storage.CommandStorage
import com.assistant.personal.actions.ActionExecutor
import com.assistant.personal.ui.FloatingBallService
import android.speech.tts.TextToSpeech
import java.util.Locale

class AutomationService : AccessibilityService() {

    private lateinit var commandStorage: CommandStorage
    private lateinit var tts: TextToSpeech
    private lateinit var actionExecutor: ActionExecutor
    private val handler = Handler(Looper.getMainLooper())

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.assistant.personal.EXECUTE_COMMAND") {
                val command = intent.getStringExtra("command") ?: return
                handler.post { processCommand(command) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        commandStorage = CommandStorage(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH)
                actionExecutor = ActionExecutor(this, tts, commandStorage)
            }
        }
        registerReceiver(
            commandReceiver,
            IntentFilter("com.assistant.personal.EXECUTE_COMMAND")
        )
    }

    private fun processCommand(text: String) {
        val t = text.lowercase().trim()

        if (text.contains(">")) {
            executeSteps(text.split(">").map { it.trim() }, 0)
            return
        }

        when {
            t == "remove ball" || t == "hide ball" || t == "ball remove" -> {
                FloatingBallService.stop(this); return
            }
            t == "show ball" || t == "ball show" -> {
                FloatingBallService.start(this); return
            }
        }

        if (t.startsWith("close ") || t.startsWith("exit ")) {
            closeApp(t.replace("close ", "").replace("exit ", "").trim()); return
        }
        if (t == "close" || t == "exit") { closeCurrentApp(); return }

        if (t.startsWith("youtube") || currentAppIs("youtube")) {
            if (handleYouTube(t)) return
        }
        if (t.startsWith("whatsapp") || currentAppIs("whatsapp")) {
            if (handleWhatsApp(t)) return
        }
        if (t.startsWith("instagram") || currentAppIs("instagram")) {
            if (handleInstagram(t)) return
        }
        if (t.startsWith("facebook") || currentAppIs("facebook")) {
            if (handleFacebook(t)) return
        }

        if (t == "play" || t == "pause" || t == "next" || t == "previous") {
            handleMedia(t); return
        }

        if (t.contains("scroll")) {
            when {
                t.contains("slowly") && t.contains("up") -> slowScrollUp()
                t.contains("slowly") -> slowScroll()
                t.contains("up") -> scrollUp()
                t.contains("top") -> scrollToTop()
                t.contains("bottom") -> scrollToBottom()
                else -> scrollDown()
            }
            return
        }

        when (t) {
            "back" -> { performGlobalAction(GLOBAL_ACTION_BACK); return }
            "home" -> { performGlobalAction(GLOBAL_ACTION_HOME); return }
            "recent", "recent apps" -> { performGlobalAction(GLOBAL_ACTION_RECENTS); return }
            "notifications" -> { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); return }
        }

        if (t.startsWith("search ")) { typeText(t.replace("search ", "").trim()); return }
        if (t.startsWith("click ") || t.startsWith("tap ")) {
            clickElement(t.replace("click ", "").replace("tap ", "").trim()); return
        }
        if (t.startsWith("open ")) { actionExecutor.openApp(t.replace("open ", "").trim()); return }
        if (t.contains("wifi on")) { actionExecutor.controlWifi(true); return }
        if (t.contains("wifi off")) { actionExecutor.controlWifi(false); return }
        if (t.contains("torch on")) { actionExecutor.controlTorch(true); return }
        if (t.contains("torch off")) { actionExecutor.controlTorch(false); return }

        val custom = commandStorage.findCommand(text)
        if (custom != null) { actionExecutor.execute(custom, text); return }
        actionExecutor.executeBuiltIn(text)
    }

    private fun executeSteps(steps: List<String>, index: Int) {
        if (index >= steps.size) return
        val step = steps[index].trim()
        val t = step.lowercase()

        when {
            t.startsWith("message ") && index > 0 -> {
                val msg = step.substringAfter("message ").trim().removeSurrounding("\"")
                typeText(msg)
                handler.postDelayed({
                    clickByDescription("Send")
                    executeSteps(steps, index + 1)
                }, 1500)
            }
            t.startsWith("search ") -> {
                val query = step.substringAfter("search ").trim().removeSurrounding("\"")
                if (currentAppIs("youtube")) {
                    clickByDescription("Search")
                    handler.postDelayed({
                        typeText(query)
                        handler.postDelayed({ executeSteps(steps, index + 1) }, 2000)
                    }, 1000)
                } else {
                    typeText(query)
                    handler.postDelayed({ executeSteps(steps, index + 1) }, 2000)
                }
            }
            index > 0 && steps[index - 1].lowercase().contains("whatsapp") -> {
                val contactName = step.removeSurrounding("\"")
                handler.postDelayed({
                    clickByDescription("Search")
                    handler.postDelayed({
                        typeText(contactName)
                        handler.postDelayed({
                            clickElement(contactName)
                            handler.postDelayed({ executeSteps(steps, index + 1) }, 1500)
                        }, 1500)
                    }, 1000)
                }, 500)
            }
            else -> {
                processCommand(step)
                handler.postDelayed({ executeSteps(steps, index + 1) }, 2500)
            }
        }
    }

    private fun closeApp(appName: String) {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val node = rootInActiveWindow?.let { findNodeByText(it, appName) }
            if (node != null) {
                val b = android.graphics.Rect()
                node.getBoundsInScreen(b)
                performSwipe(b.centerX().toFloat(), b.centerY().toFloat(),
                    b.centerX().toFloat(), 50f, 300)
            } else closeCurrentApp()
        }, 800)
    }

    private fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val d = resources.displayMetrics
            performSwipe(d.widthPixels/2f, d.heightPixels*0.5f,
                d.widthPixels/2f, 50f, 400)
        }, 800)
    }

    private fun handleYouTube(t: String): Boolean {
        return when {
            t.contains("play") -> { clickByDescription("Play"); true }
            t.contains("pause") -> { clickByDescription("Pause"); true }
            t.contains("next") -> { swipeLeft(); true }
            t.contains("fullscreen") -> { clickByDescription("Full screen"); true }
            t.contains("like") -> { clickByDescription("like this video"); true }
            t.contains("subscribe") -> { clickElement("subscribe"); true }
            t.contains("skip") -> { clickElement("skip"); true }
            t.contains("comment") -> { clickElement("comment"); true }
            t.contains("search") -> {
                clickByDescription("Search")
                val q = t.replace("youtube","").replace("search","").trim()
                if (q.isNotEmpty()) handler.postDelayed({ typeText(q) }, 1000)
                true
            }
            else -> false
        }
    }

    private fun handleWhatsApp(t: String): Boolean {
        return when {
            t.contains("new chat") || t.contains("new message") -> { clickByDescription("New chat"); true }
            t.contains("camera") -> { clickByDescription("Camera"); true }
            t.contains("search") -> { clickByDescription("Search"); true }
            t.contains("status") -> { clickElement("status"); true }
            t.contains("calls") -> { clickElement("calls"); true }
            t.contains("send") -> { clickByDescription("Send"); true }
            else -> false
        }
    }

    private fun handleInstagram(t: String): Boolean {
        return when {
            t.contains("like") -> { clickByDescription("Like"); true }
            t.contains("comment") -> { clickByDescription("Comment"); true }
            t.contains("share") -> { clickByDescription("Share"); true }
            t.contains("story") -> { clickElement("story"); true }
            t.contains("reel") -> { clickElement("reels"); true }
            t.contains("search") -> { clickByDescription("Search and explore"); true }
            t.contains("follow") -> { clickElement("follow"); true }
            else -> false
        }
    }

    private fun handleFacebook(t: String): Boolean {
        return when {
            t.contains("like") -> { clickByDescription("Like"); true }
            t.contains("comment") -> { clickByDescription("Comment"); true }
            t.contains("share") -> { clickByDescription("Share"); true }
            t.contains("marketplace") -> { clickElement("marketplace"); true }
            else -> false
        }
    }

    private fun handleMedia(t: String) {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val key = when {
            t.contains("play") -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            t.contains("pause") -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            t.contains("next") -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            t.contains("previous") -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
    }

    private fun scrollDown() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.7f, d.widthPixels/2f, d.heightPixels*0.3f, 400)
    }
    private fun scrollUp() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.3f, d.widthPixels/2f, d.heightPixels*0.7f, 400)
    }
    private fun slowScroll() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.75f, d.widthPixels/2f, d.heightPixels*0.45f, 2500)
    }
    private fun slowScrollUp() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.45f, d.widthPixels/2f, d.heightPixels*0.75f, 2500)
    }
    private fun scrollToTop() { repeat(8) { i -> handler.postDelayed({ scrollUp() }, i * 300L) } }
    private fun scrollToBottom() { repeat(8) { i -> handler.postDelayed({ scrollDown() }, i * 300L) } }
    private fun swipeLeft() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels*0.8f, d.heightPixels/2f, d.widthPixels*0.2f, d.heightPixels/2f, 300)
    }
    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            dispatchGesture(GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build(), null, null)
        }
    }

    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        findEditText(root)?.let { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                })
                handler.postDelayed({
                    clickByDescription("Search")
                    clickByDescription("Send")
                }, 600)
            }, 500)
        }
    }

    private fun clickByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        if (!nodes.isNullOrEmpty()) { nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
        return findAndClickByDesc(root, desc.lowercase())
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        if (node.contentDescription?.toString()?.lowercase()?.contains(desc) == true) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true
        }
        for (i in 0 until node.childCount) {
            if (findAndClickByDesc(node.getChild(i) ?: continue, desc)) return true
        }
        return false
    }

    private fun clickElement(text: String): Boolean {
        val node = rootInActiveWindow?.let { findNodeByText(it, text.lowercase()) } ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.lowercase()?.contains(text) == true ||
            node.contentDescription?.toString()?.lowercase()?.contains(text) == true) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i) ?: continue, text)?.let { return it }
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun currentAppIs(appName: String): Boolean {
        return rootInActiveWindow?.packageName?.toString()?.lowercase()
            ?.contains(appName.lowercase()) == true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
    }
}
