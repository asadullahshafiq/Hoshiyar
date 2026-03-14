package com.assistant.personal.ui

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.assistant.personal.storage.CommandStorage

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var panelView: View
    private var isPanelOpen = false
    private lateinit var commandStorage: CommandStorage
    private val CHANNEL_ID = "hoshiyar_ball"
    private val prefs by lazy {
        getSharedPreferences("ball_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        commandStorage = CommandStorage(this)
        createChannel()
        startForeground(1, buildNotification())
        setupBall()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager::class.java.let {
                getSystemService(it).createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Hoshiyar",
                        NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingBallService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 Hoshiyar Active")
            .setContentText("Floating ball is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, "Remove Ball", stopIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "REFRESH") { refreshPanel(); return START_STICKY }
        return START_STICKY
    }

    private fun setupBall() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ===== BALL VIEW =====
        val ball = createBallView()

        val ballParams = WindowManager.LayoutParams(
            dp(62), dp(62),
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("ball_x", 0)
            y = prefs.getInt("ball_y", 300)
        }

        // Drag + tap
        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f; var drag = false
        ball.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = ballParams.x; iy = ballParams.y
                    itx = e.rawX; ity = e.rawY; drag = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - itx).toInt()
                    val dy = (e.rawY - ity).toInt()
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) drag = true
                    ballParams.x = ix + dx; ballParams.y = iy + dy
                    windowManager.updateViewLayout(ball, ballParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Save position
                    prefs.edit().putInt("ball_x", ballParams.x)
                        .putInt("ball_y", ballParams.y).apply()
                    if (!drag) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(ball, ballParams)
        ballView = ball

        // Pulse animation
        val pulse = ObjectAnimator.ofFloat(ball, "alpha", 0.7f, 1f).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse.start()

        setupPanel()
    }

    private fun createBallView(): View {
        // Check saved image
        val savedImg = prefs.getString("ball_image_path", null)
        val bitmap = if (savedImg != null) {
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(savedImg)
                createCircularBitmap(bmp)
            } catch (e: Exception) { null }
        } else null

        return if (bitmap != null) {
            ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = createGlowDrawable()
            }
        } else {
            // Default glowing ball
            TextView(this).apply {
                text = "🤖"
                textSize = 24f
                gravity = Gravity.CENTER
                background = createGlowDrawable()
            }
        }
    }

    private fun createGlowDrawable(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                val r = bounds.width() / 2f

                // Glow
                paint.color = Color.parseColor("#6C63FF")
                paint.alpha = 60
                paint.maskFilter = BlurMaskFilter(r * 0.4f, BlurMaskFilter.Blur.OUTER)
                canvas.drawCircle(cx, cy, r * 0.9f, paint)

                // Main circle
                paint.maskFilter = null
                paint.alpha = 220
                val shader = RadialGradient(cx, cy * 0.7f, r,
                    Color.parseColor("#9C95FF"),
                    Color.parseColor("#4A42CC"),
                    Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawCircle(cx, cy, r * 0.85f, paint)

                // Highlight
                paint.shader = null
                paint.color = Color.WHITE
                paint.alpha = 40
                canvas.drawCircle(cx * 0.85f + r * 0.15f,
                    cy * 0.65f, r * 0.25f, paint)
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createCircularBitmap(bmp: Bitmap): Bitmap {
        val size = minOf(bmp.width, bmp.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size/2f, size/2f, size/2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bmp, ((size - bmp.width)/2).toFloat(),
            ((size - bmp.height)/2).toFloat(), paint)
        return output
    }

    private fun setupPanel() {
        // Premium dark panel with glow border
        val panel = createPremiumPanel()

        val params = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 70; y = 200
        }

        panel.visibility = View.GONE
        windowManager.addView(panel, params)
        panelView = panel
    }

    private fun createPremiumPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPanelBackground()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = 20f
        }

        // ===== HEADER =====
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
        }

        val titleTv = TextView(this).apply {
            text = "✦ HOSHIYAR"
            textSize = 13f
            setTextColor(Color.parseColor("#B8B0FF"))
            letterSpacing = 0.15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = createIconBtn("✕", "#555566") { togglePanel() }
        val btnRemove = createIconBtn("⊗", "#CC3355") {
            stop(this@FloatingBallService)
        }
        val btnChangeImg = createIconBtn("🖼", "#336699") {
            openImagePicker()
            togglePanel()
        }

        header.addView(titleTv)
        header.addView(btnChangeImg)
        header.addView(btnClose)
        header.addView(btnRemove)
        panel.addView(header)

        // Glow divider
        panel.addView(createGlowDivider())

        // ===== INPUT =====
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }

        val etInput = EditText(this).apply {
            hint = "Type command..."
            setHintTextColor(Color.parseColor("#44446A"))
            setTextColor(Color.parseColor("#E0DEFF"))
            background = createInputBackground()
            textSize = 13f
            setPadding(dp(12), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0,
                dp(42), 1f).apply {
                setMargins(0, 0, dp(6), 0)
            }
        }

        val btnSend = TextView(this).apply {
            text = "▶"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = createSendBtnBg()
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener {
                val cmd = etInput.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    etInput.setText("")
                    sendCommand(cmd)
                    togglePanel()
                }
            }
        }

        inputRow.addView(etInput)
        inputRow.addView(btnSend)
        panel.addView(inputRow)

        // Glow divider
        panel.addView(createGlowDivider())

        // ===== QUICK ACTIONS =====
        val quickLabel = TextView(this).apply {
            text = "QUICK ACTIONS"
            textSize = 10f
            setTextColor(Color.parseColor("#666688"))
            letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(6), 0, dp(4))
            }
        }
        panel.addView(quickLabel)

        // Quick action row 1
        val quickRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(4))
            }
        }
        listOf(
            "🔦 Torch" to "torch on",
            "📶 WiFi" to "wifi on",
            "🔊 Vol+" to "volume up",
            "🔇 Mute" to "mute"
        ).forEach { (label, cmd) ->
            quickRow1.addView(createQuickBtn(label, cmd))
        }
        panel.addView(quickRow1)

        // Quick action row 2
        val quickRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }
        listOf(
            "🔦 Off" to "torch off",
            "📵 WiFi-" to "wifi off",
            "🔋 Bat" to "battery",
            "🕐 Time" to "time"
        ).forEach { (label, cmd) ->
            quickRow2.addView(createQuickBtn(label, cmd))
        }
        panel.addView(quickRow2)

        // ===== COMMANDS LIST =====
        val cmdsLabel = TextView(this).apply {
            text = "MY COMMANDS"
            textSize = 10f
            setTextColor(Color.parseColor("#666688"))
            letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        }
        panel.addView(cmdsLabel)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "commands_container"
        }
        scroll.addView(container)
        panel.addView(scroll)
        panel.tag = container

        loadCommandButtons(container)
        return panel
    }

    private fun createQuickBtn(label: String, cmd: String): View {
        return TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#C0BEFF"))
            background = createQuickBtnBg()
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                setMargins(2, 0, 2, 0)
            }
            setOnClickListener {
                sendCommand(cmd)
                togglePanel()
            }
        }
    }

    private fun createIconBtn(text: String, color: String, click: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(30)).apply {
                setMargins(3, 0, 0, 0)
            }
            setOnClickListener { click() }
        }
    }

    private fun createGlowDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#2A2A55"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        }
    }

    private fun createPanelBackground(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                // Glow border
                paint.color = Color.parseColor("#6C63FF")
                paint.alpha = 80
                paint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                // Background
                paint.maskFilter = null
                paint.alpha = 245
                val shader = LinearGradient(0f, 0f, 0f, rect.height(),
                    Color.parseColor("#12122A"),
                    Color.parseColor("#0A0A1E"),
                    Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                // Border
                paint.shader = null
                paint.color = Color.parseColor("#2A2A55")
                paint.alpha = 255
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                paint.style = Paint.Style.FILL
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createInputBackground(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                paint.color = Color.parseColor("#0D0D20")
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.color = Color.parseColor("#2A2A55")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.style = Paint.Style.FILL
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createSendBtnBg(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                val shader = LinearGradient(0f, 0f, rect.width(), rect.height(),
                    Color.parseColor("#8C83FF"),
                    Color.parseColor("#5A52CC"),
                    Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRoundRect(rect, 8f, 8f, paint)
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createQuickBtnBg(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val rect = RectF(bounds)
                paint.color = Color.parseColor("#16163A")
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.color = Color.parseColor("#2A2A55")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.style = Paint.Style.FILL
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun loadCommandButtons(container: LinearLayout) {
        container.removeAllViews()
        val commands = commandStorage.loadCommands().filter { it.isEnabled }

        commands.forEach { cmd ->
            val btn = TextView(this).apply {
                text = "▸  ${cmd.trigger}"
                textSize = 12f
                setTextColor(Color.parseColor("#C0BEFF"))
                background = createQuickBtnBg()
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 3, 0, 3)
                }
                setOnClickListener {
                    sendCommand(cmd.trigger)
                    togglePanel()
                }
            }
            container.addView(btn)
        }

        if (commands.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Add commands in the app"
                setTextColor(Color.parseColor("#444466"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(16))
            })
        }
    }

    private fun openImagePicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Toast.makeText(this,
                "Select image, then restart ball", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPanel() {
        val container = panelView.tag as? LinearLayout ?: return
        loadCommandButtons(container)
    }

    private fun togglePanel() {
        isPanelOpen = !isPanelOpen
        if (isPanelOpen) {
            val container = panelView.tag as? LinearLayout
            container?.let { loadCommandButtons(it) }
            panelView.visibility = View.VISIBLE
            // Fade in
            panelView.alpha = 0f
            panelView.animate().alpha(1f).setDuration(200).start()
        } else {
            panelView.animate().alpha(0f).setDuration(150)
                .withEndAction { panelView.visibility = View.GONE }.start()
        }
    }

    private fun sendCommand(text: String) {
        sendBroadcast(Intent("com.assistant.personal.EXECUTE_COMMAND").apply {
            putExtra("command", text)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(ballView) } catch (e: Exception) {}
        try { windowManager.removeView(panelView) } catch (e: Exception) {}
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }
    }
}
