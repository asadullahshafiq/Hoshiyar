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
                listOf(Locale.ENGLISH).forEach { lang ->
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

        // ===== MULTI STEP — > se alag =====
        // Examples:
        // youtube > search "coding" > slowly scroll up
        // whatsapp > mama > message "kya hal hai"
        if (text.contains(">")) {
            val steps = text.split(">").map { it.trim() }
            executeSteps(steps, 0)
            return
        }

        // ===== FLOATING BALL REMOVE =====
        if (t == "remove ball" || t == "hide ball" || t == "ball remove"
            || t == "ball hide" || t == "remove floating") {
            FloatingBallService.stop(this)
            return
        }

        // ===== FLOATING BALL SHOW =====
        if (t == "show ball" || t == "ball show" || t == "floating ball") {
            FloatingBallService.start(this)
            return
        }

        // ===== APP CLOSE =====
        if (t.startsWith("close ") || t.startsWith("exit ")) {
            val appName = t.replace("close ", "").replace("exit ", "").trim()
            closeApp(appName)
            return
        }
        if (t == "close" || t == "exit" || t == "go back home") {
            closeCurrentApp()
            return
        }

        // ===== YOUTUBE =====
        if (t.startsWith("youtube") || currentAppIs("youtube")) {
            if (handleYouTube(t)) return
        }

        // ===== WHATSAPP =====
        if (t.startsWith("whatsapp") || currentAppIs("whatsapp")) {
            if (handleWhatsApp(t)) return
        }

        // ===== INSTAGRAM =====
        if (t.startsWith("instagram") || currentAppIs("instagram")) {
            if (handleInstagram(t)) return
        }

        // ===== FACEBOOK =====
        if (t.startsWith("facebook") || currentAppIs("facebook")) {
            if (handleFacebook(t)) return
        }

        // ===== MEDIA CONTROLS =====
        if (t == "play" || t == "pause" || t == "next" || t == "previous") {
            handleMedia(t)
            return
        }

        // ===== SCROLL =====
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

        // ===== NAVIGATION =====
        if (t == "back") { performGlobalAction(GLOBAL_ACTION_BACK); return }
        if (t == "home") { performGlobalAction(GLOBAL_ACTION_HOME); return }
        if (t == "recent" || t == "recent apps") {
            performGlobalAction(GLOBAL_ACTION_RECENTS); return
        }
        if (t == "notifications") {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); return
        }

        // ===== SEARCH =====
        if (t.startsWith("search ")) {
            typeText(t.replace("search ", "").trim())
            return
        }

        // ===== CLICK =====
        if (t.startsWith("click ") || t.startsWith("tap ")) {
            val target = t.replace("click ", "").replace("tap ", "").trim()
            clickElement(target)
            return
        }

        // ===== OPEN APP =====
        if (t.startsWith("open ")) {
            actionExecutor.openApp(t.replace("open ", "").trim())
            return
        }

        // ===== WIFI =====
        if (t.contains("wifi on")) { actionExecutor.controlWifi(true); return }
        if (t.contains("wifi off")) { actionExecutor.controlWifi(false); return }

        // ===== TORCH =====
        if (t.contains("torch on") || t.contains("flashlight on")) {
            actionExecutor.controlTorch(true); return
        }
        if (t.contains("torch off") || t.contains("flashlight off")) {
            actionExecutor.controlTorch(false); return
        }

        // ===== CUSTOM + BUILTIN =====
        val custom = commandStorage.findCommand(text)
        if (custom != null) { actionExecutor.execute(custom, text); return }
        actionExecutor.executeBuiltIn(text)
    }

    // ===== MULTI STEP EXECUTOR =====
    // Supports:
    // youtube > search "coding" > slowly scroll up
    // whatsapp > mama > message "kya hal hai"
    private fun executeSteps(steps: List<String>, index: Int) {
        if (index >= steps.size) return
        val step = steps[index].trim()
        val t = step.lowercase()

        // Special step parsing
        when {
            // message "..." — WhatsApp message bhejo
            t.startsWith("message ") && index > 0 -> {
                val msg = step.substringAfter("message ").trim()
                    .removeSurrounding("\"")
                typeText(msg)
                handler.postDelayed({
                    clickByDescription("Send")
                    executeSteps(steps, index + 1)
                }, 1500)
                return
            }

            // search "..." — search karo
            t.startsWith("search ") -> {
                val query = step.substringAfter("search ").trim()
                    .removeSurrounding("\"")
                // YouTube search bar click
                if (currentAppIs("youtube")) {
                    clickByDescription("Search")
                    handler.postDelayed({
                        typeText(query)
                        handler.postDelayed({
                            executeSteps(steps, index + 1)
                        }, 2000)
                    }, 1000)
                } else {
                    typeText(query)
                    handler.postDelayed({
                        executeSteps(steps, index + 1)
                    }, 2000)
                }
                return
            }

            // contact name — WhatsApp mein contact dhundo
            index > 0 && steps[index-1].lowercase().contains("whatsapp") -> {
                // Contact search
                val contactName = step.removeSurrounding("\"")
                handler.postDelayed({
                    clickByDescription("Search")
                    handler.postDelayed({
                        typeText(contactName)
                        handler.postDelayed({
                            clickElement(contactName)
                            handler.postDelayed({
                                executeSteps(steps, index + 1)
                            }, 1500)
                        }, 1500)
                    }, 1000)
                }, 500)
                return
            }

            else -> {
                processCommand(step)
                handler.postDelayed({
                    executeSteps(steps, index + 1)
                }, 2500)
                return
            }
        }
    }

    // ===== CLOSE APP =====
    private fun closeApp(appName: String) {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val root = rootInActiveWindow
            val node = root?.let { findNodeByText(it, appName) }
            if (node != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                performSwipe(
                    bounds.centerX().toFloat(), bounds.centerY().toFloat(),
                    bounds.centerX().toFloat(), 50f, 300
                )
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

    // ===== YOUTUBE =====
    private fun handleYouTube(t: String): Boolean {
        return when {
            t.contains("play") -> { clickByDescription("Play"); clickByDescription("Pause"); true }
            t.contains("pause") -> { clickByDescription("Pause"); true }
            t.contains("next") -> { clickByDescription("Next"); swipeLeft(); true }
            t.contains("fullscreen") || t.contains("full screen") -> {
                clickByDescription("Full screen"); true
            }
            t.contains("like") -> { clickByDescription("like this video"); true }
            t.contains("subscribe") -> { clickElement("subscribe"); true }
            t.contains("skip") -> { clickElement("skip"); true }
            t.contains("comment") -> { clickElement("comment"); true }
            t.contains("search") -> {
                clickByDescription("Search")
                val query = t.replace("youtube", "").replace("search", "").trim()
                if (query.isNotEmpty()) {
                    handler.postDelayed({ typeText(query) }, 1000)
                }
                true
            }
            else -> false
        }
    }

    // ===== WHATSAPP =====
    private fun handleWhatsApp(t: String): Boolean {
        return when {
            t.contains("new chat") || t.contains("new message") -> {
                clickByDescription("New chat"); true
            }
            t.contains("camera") -> { clickByDescription("Camera"); true }
            t.contains("search") -> { clickByDescription("Search"); true }
            t.contains("status") -> { clickElement("status"); true }
            t.contains("calls") -> { clickElement("calls"); true }
            t.contains("send") -> { clickByDescription("Send"); true }
            t.contains("attach") -> { clickByDescription("Attach"); true }
            else -> false
        }
    }

    // ===== INSTAGRAM =====
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

    // ===== FACEBOOK =====
    private fun handleFacebook(t: String): Boolean {
        return when {
            t.contains("like") -> { clickByDescription("Like"); true }
            t.contains("comment") -> { clickByDescription("Comment"); true }
            t.contains("share") -> { clickByDescription("Share"); true }
            t.contains("story") -> { clickElement("story"); true }
            t.contains("marketplace") -> { clickElement("marketplace"); true }
            else -> false
        }
    }

    // ===== MEDIA =====
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
        performSwipe(d.widthPixels/2f, d.heightPixels*0.75f,
            d.widthPixels/2f, d.heightPixels*0.45f, 2500)
    }

    private fun slowScrollUp() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels/2f, d.heightPixels*0.45f,
            d.widthPixels/2f, d.heightPixels*0.75f, 2500)
    }

    private fun scrollToTop() {
        repeat(8) { i -> handler.postDelayed({ scrollUp() }, i * 300L) }
    }

    private fun scrollToBottom() {
        repeat(8) { i -> handler.postDelayed({ scrollDown() }, i * 300L) }
    }

    private fun swipeLeft() {
        val d = resources.displayMetrics
        performSwipe(d.widthPixels*0.8f, d.heightPixels/2f,
            d.widthPixels*0.2f, d.heightPixels/2f, 300)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
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
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                handler.postDelayed({
                    clickByDescription("Search")
                    clickByDescription("Send")
                }, 600)
            }, 500)
        }
    }

    // ===== CLICK BY DESCRIPTION =====
    private fun clickByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        if (!nodes.isNullOrEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return findAndClickByDesc(root, desc.lowercase())
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        val d = node.contentDescription?.toString()?.lowercase() ?: ""
        if (d.contains(desc)) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickByDesc(child, desc)) return true
        }
        return false
    }

    // ===== CLICK BY TEXT =====
    private fun clickElement(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text.lowercase()) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return true
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nt = node.text?.toString()?.lowercase() ?: ""
        val nd = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nt.contains(text) || nd.contains(text)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun currentAppIs(appName: String): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString()?.lowercase() ?: return false
        return pkg.contains(appName.lowercase())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
    }
}
