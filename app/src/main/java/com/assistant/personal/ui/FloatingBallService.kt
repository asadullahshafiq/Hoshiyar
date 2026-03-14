package com.assistant.personal.ui

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
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
                CHANNEL_ID, "Hoshiyar Ball",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tap to stop notification
        val stopIntent = Intent(this, FloatingBallService::class.java).apply {
            action = "STOP"
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hoshiyar Active")
            .setContentText("Tap to remove floating ball")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Remove Ball", stopPending)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupFloatingBall() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ===== BALL =====
        val ballView = TextView(this).apply {
            text = "🤖"
            textSize = 26f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC6C63FF"))
            setPadding(8, 8, 8, 8)
        }

        val ballParams = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }

        var initX = 0; var initY = 0
        var initTX = 0f; var initTY = 0f
        var dragging = false

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = ballParams.x; initY = ballParams.y
                    initTX = event.rawX; initTY = event.rawY
                    dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initTX).toInt()
                    val dy = (event.rawY - initTY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true
                    ballParams.x = initX + dx; ballParams.y = initY + dy
                    windowManager.updateViewLayout(ballView, ballParams); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(ballView, ballParams)
        floatingBall = ballView

        // ===== PANEL =====
        setupPanel()
    }

    private fun setupPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE1A1A2E"))
            setPadding(20, 16, 20, 16)
        }

        val panelParams = WindowManager.LayoutParams(
            dpToPx(270), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 70; y = 200 }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "🤖 Hoshiyar"
            textSize = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Close/Remove buttons
        val btnClose = Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(32)).apply {
                setMargins(4, 0, 4, 0)
            }
            setOnClickListener { togglePanel() }
        }

        val btnRemove = Button(this).apply {
            text = "Remove"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F44336"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32))
            setOnClickListener { stop(this@FloatingBallService) }
        }

        header.addView(title)
        header.addView(btnClose)
        header.addView(btnRemove)
        panel.addView(header)

        // Divider
        val div = View(this).apply {
            setBackgroundColor(Color.parseColor("#333355"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        panel.addView(div)

        // Input box
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
        }
        val etInput = EditText(this).apply {
            hint = "Command likhein..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0D1117"))
            textSize = 13f
            setPadding(12, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 6, 0)
            }
        }
        val btnSend = Button(this).apply {
            text = "▶"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6C63FF"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
            setOnClickListener {
                val cmd = etInput.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    etInput.setText("")
                    sendCommand(cmd)
                    togglePanel()
                }
            }
        }
        inputLayout.addView(etInput)
        inputLayout.addView(btnSend)
        panel.addView(inputLayout)

        // Commands scroll list
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(180))
        }
        val container = LinearLayout(this).apply {
            id = android.view.View.generateViewId()
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(container)
        panel.addView(scroll)

        panel.visibility = View.GONE
        windowManager.addView(panel, panelParams)
        commandPanel = panel

        // Store container ref
        commandPanel.tag = container
        loadCommandButtons()
    }

    private fun loadCommandButtons() {
        val container = commandPanel.tag as? LinearLayout ?: return
        container.removeAllViews()
        val commands = commandStorage.loadCommands().filter { it.isEnabled }

        commands.forEach { cmd ->
            val btn = Button(this).apply {
                text = cmd.trigger
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#6C63FF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 3, 0, 3) }
                setOnClickListener {
                    sendCommand(cmd.trigger)
                    togglePanel()
                }
            }
            container.addView(btn)
        }

        if (commands.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No commands yet.\nAdd them in the app."
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(16, 20, 16, 20)
            }
            container.addView(tv)
        }
    }

    private fun sendCommand(text: String) {
        sendBroadcast(Intent("com.assistant.personal.EXECUTE_COMMAND").apply {
            putExtra("command", text)
        })
    }

    private fun togglePanel() {
        isPanelOpen = !isPanelOpen
        if (isPanelOpen) {
            loadCommandButtons()
            commandPanel.visibility = View.VISIBLE
        } else {
            commandPanel.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(floatingBall) } catch (e: Exception) {}
        try { windowManager.removeView(commandPanel) } catch (e: Exception) {}
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
