package com.assistant.personal.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.*
import android.graphics.Path
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import com.assistant.personal.storage.CommandStorage
import com.assistant.personal.actions.ActionExecutor
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Accessibility Service
 * Doosri apps ko control karta hai
 * Click, scroll, type sab kuch
 */
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

        registerReceiver(commandReceiver,
            IntentFilter("com.assistant.personal.EXECUTE_COMMAND"))
    }

    private fun processCommand(text: String) {
        val t = text.lowercase()

        // ===== MULTI STEP COMMANDS (> se alag karo) =====
        if (text.contains(">")) {
            val steps = text.split(">").map { it.trim() }
            executeSteps(steps, 0)
            return
        }

        // ===== SCROLL =====
        if (t.contains("scroll")) {
            when {
                t.contains("up") || t.contains("upar") -> scrollUp()
                t.contains("down") || t.contains("neeche") -> scrollDown()
                t.contains("slowly") || t.contains("dheere") -> slowScroll()
                else -> scrollDown()
            }
            return
        }

        // ===== BACK =====
        if (t.contains("back") || t.contains("peeche")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // ===== HOME =====
        if (t.contains("home")) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // ===== RECENT APPS =====
        if (t.contains("recent") || t.contains("recent apps")) {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            return
        }

        // ===== SEARCH =====
        if (t.contains("search") || t.contains("dhundo")) {
            val query = t.replace("search", "").replace("dhundo", "").trim()
            typeText(query)
            return
        }

        // ===== CLICK =====
        if (t.contains("click") || t.contains("tap") || t.contains("dabao")) {
            val target = t.replace("click", "").replace("tap", "")
                .replace("dabao", "").trim()
            clickElement(target)
            return
        }

        // ===== CUSTOM + BUILTIN COMMANDS =====
        val custom = commandStorage.findCommand(text)
        if (custom != null) {
            actionExecutor.execute(custom, text)
            return
        }
        actionExecutor.executeBuiltIn(text)
    }

    // ===== MULTI STEP EXECUTION =====
    private fun executeSteps(steps: List<String>, index: Int) {
        if (index >= steps.size) return

        val step = steps[index]
        processCommand(step)

        // Next step 2 second baad
        handler.postDelayed({
            executeSteps(steps, index + 1)
        }, 2000)
    }

    // ===== SCROLL =====
    private fun scrollDown() {
        val display = resources.displayMetrics
        val w = display.widthPixels / 2f
        val startY = display.heightPixels * 0.7f
        val endY = display.heightPixels * 0.3f
        performSwipe(w, startY, w, endY, 300)
    }

    private fun scrollUp() {
        val display = resources.displayMetrics
        val w = display.widthPixels / 2f
        val startY = display.heightPixels * 0.3f
        val endY = display.heightPixels * 0.7f
        performSwipe(w, startY, w, endY, 300)
    }

    private fun slowScroll() {
        val display = resources.displayMetrics
        val w = display.widthPixels / 2f
        val startY = display.heightPixels * 0.7f
        val endY = display.heightPixels * 0.4f
        performSwipe(w, startY, w, endY, 1500)
    }

    private fun performSwipe(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        duration: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
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
            val args = Bundle().apply {
                putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            // Search/Enter
            handler.postDelayed({
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$text\n")
                })
            }, 500)
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    // ===== CLICK ELEMENT =====
    private fun clickElement(text: String) {
        val root = rootInActiveWindow ?: return
        val node = findNodeByText(root, text)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String
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

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
    }
}
