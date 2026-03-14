package com.assistant.personal.ui

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.view.WindowManager.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBall: View
    private lateinit var commandPanel: View
    private var isPanelOpen = false
    private lateinit var commandStorage: CommandStorage

    private val CHANNEL_ID = "floating_ball"

    override fun onCreate() {
        super.onCreate()
        commandStorage = CommandStorage(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
        setupFloatingBall()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating Ball",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hoshiyar Active")
            .setContentText("Floating ball chal raha hai")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupFloatingBall() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ===== FLOATING BALL =====
        val ballLayout = LayoutInflater.from(this)
            .inflate(R.layout.floating_ball, null)

        val ballParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // Drag karo
        var initX = 0; var initY = 0
        var initTouchX = 0f; var initTouchY = 0f
        var isDragging = false

        ballLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = ballParams.x; initY = ballParams.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initTouchX).toInt()
                    val dy = (event.rawY - initTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    ballParams.x = initX + dx
                    ballParams.y = initY + dy
                    windowManager.updateViewLayout(ballLayout, ballParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(ballLayout, ballParams)
        floatingBall = ballLayout

        // ===== COMMAND PANEL =====
        setupCommandPanel()
    }

    private fun setupCommandPanel() {
        val panelLayout = LayoutInflater.from(this)
            .inflate(R.layout.floating_panel, null)

        val panelParams = LayoutParams(
            280.dpToPx(),
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60; y = 200
        }

        panelLayout.visibility = View.GONE

        // Commands list
        val container = panelLayout.findViewById<LinearLayout>(R.id.commands_container)

        // Close button
        panelLayout.findViewById<ImageButton>(R.id.btn_close_panel)
            .setOnClickListener { togglePanel() }

        // Text input
        val etInput = panelLayout.findViewById<EditText>(R.id.et_float_input)
        panelLayout.findViewById<Button>(R.id.btn_float_send).setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etInput.setText("")
                executeCommand(text)
                togglePanel()
            }
        }

        loadCommandButtons(container)

        windowManager.addView(panelLayout, panelParams)
        commandPanel = panelLayout
    }

    private fun loadCommandButtons(container: LinearLayout) {
        container.removeAllViews()
        val commands = commandStorage.loadCommands().filter { it.isEnabled }

        commands.forEach { cmd ->
            val btn = Button(this).apply {
                text = cmd.trigger
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#6C63FF"))
                setPadding(16, 12, 16, 12)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
                layoutParams = lp

                setOnClickListener {
                    executeCommand(cmd.trigger)
                    togglePanel()
                }
            }
            container.addView(btn)
        }

        // Agar koi command nahi
        if (commands.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Koi command nahi\nApp mein add karein"
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(16, 20, 16, 20)
            }
            container.addView(tv)
        }
    }

    private fun togglePanel() {
        isPanelOpen = !isPanelOpen
        commandPanel.visibility = if (isPanelOpen) {
            // Refresh commands
            val container = commandPanel.findViewById<LinearLayout>(R.id.commands_container)
            loadCommandButtons(container)
            View.VISIBLE
        } else View.GONE
    }

    private fun executeCommand(text: String) {
        // Accessibility service ko command bhejo
        val intent = Intent("com.assistant.personal.EXECUTE_COMMAND").apply {
            putExtra("command", text)
        }
        sendBroadcast(intent)
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(floatingBall)
            windowManager.removeView(commandPanel)
        } catch (e: Exception) { }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }
    }
}
