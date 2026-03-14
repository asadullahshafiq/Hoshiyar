package com.assistant.personal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

class MainActivity : AppCompatActivity() {

    private lateinit var commandStorage: CommandStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commandStorage = CommandStorage(this)
        commandStorage.loadDefaultCommandsIfEmpty()

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Assistant kholo
        findViewById<Button>(R.id.btn_open_assistant).setOnClickListener {
            startActivity(Intent(this, AssistActivity::class.java))
        }

        // Commands
        findViewById<Button>(R.id.btn_manage_commands).setOnClickListener {
            startActivity(Intent(this, CommandManagerActivity::class.java))
        }

        // Default assistant
        findViewById<Button>(R.id.btn_set_default).setOnClickListener {
            openDefaultAssistantSettings()
        }

        // Floating Ball Toggle
        findViewById<Button>(R.id.btn_floating_ball).setOnClickListener {
            toggleFloatingBall()
        }

        // Accessibility
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            openAccessibilitySettings()
        }

        // Settings
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateCommandCount()
        checkDefaultAssistant()
    }

    private fun toggleFloatingBall() {
        // Overlay permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Chahiye")
                .setMessage("Floating ball ke liye 'Display over other apps' allow karein")
                .setPositiveButton("Settings Kholo") { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                }
                .setNegativeButton("Baad Mein", null)
                .show()
            return
        }
        FloatingBallService.start(this)
        Toast.makeText(this, "🤖 Floating ball on! Screen par dikh raha hai", Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Automation On Karein")
            .setMessage(
                "Scroll, click, type karne ke liye:\n\n" +
                "1. Settings khulegi\n" +
                "2. 'Hoshiyar Automation' dhundein\n" +
                "3. ON karein\n\n" +
                "Yeh sirf aapke phone par kaam karta hai"
            )
            .setPositiveButton("Settings Kholo") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Baad Mein", null)
            .show()
    }

    private fun updateCommandCount() {
        val count = commandStorage.loadCommands().size
        findViewById<TextView>(R.id.tv_command_count)?.text = "Kul Commands: $count"
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    private fun checkDefaultAssistant() {
        val statusView = findViewById<TextView>(R.id.tv_default_status)
        val assistPackage = Settings.Secure.getString(
            contentResolver, "voice_interaction_service")
        if (assistPackage?.contains(packageName) == true) {
            statusView?.text = "✅ Default Assistant Set Hai"
            statusView?.setTextColor(getColor(R.color.green))
        } else {
            statusView?.text = "⚠️ Default Assistant Set Nahi"
            statusView?.setTextColor(getColor(R.color.orange))
        }
    }

    private fun openDefaultAssistantSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateCommandCount()
        checkDefaultAssistant()
    }
}
