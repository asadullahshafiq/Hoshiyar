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
                listOf(Locale("ur","PK"), Locale("hi","IN"), Locale.ENGLISH)
                    .forEach { lang ->
                        val r = tts.setLanguage(lang)
                        if (r != TextToSpeech.LANG_NOT_SUPPORTED &&
                            r != TextToSpeech.LANG_MISSING_DATA) return@forEach
                    }
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

        // ===== MULTI STEP (> se alag) =====
        if (text.contains(">")) {
            val steps = text.split(">").map { it.trim() }
            executeSteps(steps, 0)
            return
        }

        // ===== APP BAND KARO =====
        if (t.startsWith("close ") || t.startsWith("band ")) {
            val appName = t.replace("close ", "").replace("band ", "").trim()
            closeApp(appName)
            return
        }

        // ===== CURRENT APP BAND =====
        if (t == "close" || t == "band karo" || t == "band" || t == "exit") {
            closeCurrentApp()
            return
        }

        // ===== YOUTUBE COMMANDS =====
        if (t.contains("youtube") || currentAppIs("youtube")) {
            if (handleYouTube(t)) return
        }

        // ===== WHATSAPP COMMANDS =====
        if (t.contains("whatsapp") || currentAppIs("whatsapp")) {
            if (handleWhatsApp(t)) return
        }

        // ===== INSTAGRAM COMMANDS =====
        if (t.contains("instagram") || currentAppIs("instagram")) {
            if (handleInstagram(t)) return
        }

        // ===== FACEBOOK COMMANDS =====
        if (t.contains("facebook") || currentAppIs("facebook")) {
            if (handleFacebook(t)) return
        }

        // ===== MUSIC / MEDIA CONTROLS =====
        if (t.contains("play") || t.contains("pause") ||
            t.contains("next") || t.contains("previous") ||
            t.contains("agla") || t.contains("pichla")) {
            handleMedia(t)
            return
        }

        // ===== SCROLL =====
        if (t.contains("scroll")) {
            when {
                t.contains("up") || t.contains("upar") -> scrollUp()
                t.contains("slowly") || t.contains("dheere") -> slowScroll()
                t.contains("top") || t.contains("shuruaat") -> scrollToTop()
                t.contains("bottom") || t.contains("akhir") -> scrollToBottom()
                else -> scrollDown()
            }
            return
        }

        // ===== BACK =====
        if (t == "back" || t == "peeche" || t == "wapas") {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // ===== HOME =====
        if (t == "home" || t == "ghar") {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // ===== RECENT APPS =====
        if (t.contains("recent") || t.contains("recent apps")) {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            return
        }

        // ===== NOTIFICATIONS =====
        if (t.contains("notification")) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            return
        }

        // ===== SEARCH =====
        if (t.startsWith("search ") || t.startsWith("dhundo ")) {
            val query = t.replace("search ", "").replace("dhundo ", "").trim()
            typeText(query)
            return
        }

        // ===== CLICK =====
        if (t.startsWith("click ") || t.startsWith("tap ") ||
            t.startsWith("dabao ")) {
            val target = t.replace("click ", "").replace("tap ", "")
                .replace("dabao ", "").trim()
            clickElement(target)
            return
        }

        // ===== OPEN APP =====
        if (t.startsWith("open ") || t.contains("kholo")) {
            val appName = t.replace("open ", "").replace("kholo", "").trim()
            actionExecutor.openApp(appName)
            return
        }

        // ===== CUSTOM + BUILTIN =====
        val custom = commandStorage.findCommand(text)
        if (custom != null) {
            actionExecutor.execute(custom, text)
            return
        }
        actionExecutor.executeBuiltIn(text)
    }

    // ===== APP BAND KARO =====
    private fun closeApp(appName: String) {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val root = rootInActiveWindow
            val node = root?.let { findNodeByText(it, appName) }
            if (node != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                performSwipe(
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat(),
                    bounds.centerX().toFloat(),
                    50f, 300
                )
            } else {
                closeCurrentApp()
            }
        }, 1000)
    }

    private fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val d = resources.displayMetrics
            performSwipe(
                d.widthPixels / 2f,
                d.heightPixels * 0.5f,
                d.widthPixels / 2f,
                50f, 400
            )
        }, 800)
    }

    // ===== YOUTUBE =====
    private fun handleYouTube(t: String): Boolean {
        return when {
            t.contains("play") || t.contains("chala") -> {
                clickByDescription("Play")
                clickByDescription("Pause")
                true
            }
            t.contains("pause") || t.contains("rok") -> {
                clickByDescription("Pause")
                true
            }
            t.contains("next") || t.contains("agla") -> {
                clickByDescription("Next")
                swipeLeft()
                true
            }
            t.contains("fullscreen") || t.contains("bada karo") -> {
                clickByDescription("Full screen")
                true
            }
            t.contains("like") || t.contains("pasand") -> {
                clickByDescription("like this video")
                clickElement("like")
                true
            }
            t.contains("subscribe") -> {
                clickElement("subscribe")
                true
            }
            t.contains("search") -> {
                clickByDescription("Search")
                handler.postDelayed({
                    val query = t.replace("youtube", "")
                        .replace("search", "").trim()
                    if (query.isNotEmpty()) typeText(query)
                }, 1000)
                true
            }
            t.contains("comment") -> {
                clickElement("comment")
                true
            }
            t.contains("skip") -> {
                clickElement("skip")
                true
            }
            else -> false
        }
    }

    // ===== WHATSAPP =====
    private fun handleWhatsApp(t: String): Boolean {
        return when {
            t.contains("new message") || t.contains("naya message") -> {
                clickByDescription("New chat")
                true
            }
            t.contains("camera") -> {
                clickByDescription("Camera")
                true
            }
            t.contains("search") -> {
                clickByDescription("Search")
                true
            }
            t.contains("status") -> {
                clickElement("status")
                true
            }
            t.contains("calls") -> {
                clickElement("calls")
                true
            }
            t.contains("attach") || t.contains("file") -> {
                clickByDescription("Attach")
                true
            }
            t.contains("send") -> {
                clickByDescription("Send")
                true
            }
            else -> false
        }
    }

    // ===== INSTAGRAM =====
    private fun handleInstagram(t: String): Boolean {
        return when {
            t.contains("like") || t.contains("pasand") -> {
                clickByDescription("Like")
                true
            }
            t.contains("comment") -> {
                clickByDescription("Comment")
                true
            }
            t.contains("share") -> {
                clickByDescription("Share")
                true
            }
            t.contains("story") -> {
                clickElement("story")
                true
            }
            t.contains("reel") -> {
                clickElement("reels")
                true
            }
            t.contains("search") -> {
                clickByDescription("Search and explore")
                true
            }
            t.contains("home") -> {
                clickByDescription("Home")
                true
            }
            t.contains("follow") -> {
                clickElement("follow")
                true
            }
            else -> false
        }
    }

    // ===== FACEBOOK =====
    private fun handleFacebook(t: String): Boolean {
        return when {
            t.contains("like") -> {
                clickByDescription("Like")
                true
            }
            t.contains("comment") -> {
                clickByDescription("Comment")
                true
            }
            t.contains("share") -> {
                clickByDescription("Share")
                true
            }
            t.contains("marketplace") -> {
                clickElement("marketplace")
                true
            }
            t.contains("story") -> {
                clickElement("story")
                true
            }
            else -> false
        }
    }

    // ===== MEDIA =====
    private fun handleMedia(t: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE)
                as android.media.AudioManager
        val keyCode = when {
            t.contains("play") || t.contains("chala") ->
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            t.contains("pause") || t.contains("rok") ->
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            t.contains("next") || t.contains("agla") ->
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            t.contains("previous") || t.contains("pichla") ->
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        )
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        )
    }

    // ===== SCROLL =====
    private fun scrollDown() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.7f,
            d.widthPixels/2f, d.heightPixels*0.3f, 400)
    }

    private fun scrollUp() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.3f,
            d.widthPixels/2f, d.heightPixels*0.7f, 400)
    }

    private fun slowScroll() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.7f,
            d.widthPixels/2f, d.heightPixels*0.45f, 2000)
    }

    private fun scrollToTop() {
        repeat(5) { i ->
            handler.postDelayed({ scrollUp() }, i * 300L)
        }
    }

    private fun scrollToBottom() {
        repeat(5) { i ->
            handler.postDelayed({ scrollDown() }, i * 300L)
        }
    }

    private fun swipeLeft() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels*0.8f, d.heightPixels/2f,
            d.widthPixels*0.2f, d.heightPixels/2f, 300)
    }

    private fun performSwipe(x1: Float, y1: Float,
        x2: Float, y2: Float, duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    // ===== TYPE TEXT =====
    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val editText = findEditText(root)
        editText?.let { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({
                val args = Bundle().apply {
                    putString(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                handler.postDelayed({
                    clickByDescription("Search")
                    clickElement("search")
                }, 600)
            }, 500)
        }
    }

    // ===== CLICK BY DESCRIPTION =====
    private fun clickByDescription(description: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(description)
        if (!nodes.isNullOrEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return findAndClickByDesc(root, description.lowercase())
    }

    private fun findAndClickByDesc(
        node: AccessibilityNodeInfo, desc: String): Boolean {
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nodeDesc.contains(desc)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickByDesc(child, desc)) return true
        }
        return false
    }

    // ===== CLICK BY TEXT =====
    private fun clickElement(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text.lowercase())
        return if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            true
        } else false
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo, text: String
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nodeText.contains(text) || nodeDesc.contains(text)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true &&
            node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    // ===== CURRENT APP CHECK =====
    private fun currentAppIs(appName: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString()?.lowercase() ?: return false
        return pkg.contains(appName.lowercase())
    }

    // ===== MULTI STEP =====
    private fun executeSteps(steps: List<String>, index: Int) {
        if (index >= steps.size) return
        processCommand(steps[index])
        handler.postDelayed({ executeSteps(steps, index + 1) }, 2500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
    }
}
