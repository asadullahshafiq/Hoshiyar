package com.assistant.personal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.assistant.personal.R
import com.assistant.personal.storage.CommandStorage

class CommandManagerActivity : AppCompatActivity() {

    private lateinit var commandStorage: CommandStorage
    private lateinit var adapter: CommandAdapter
    private var commands = mutableListOf<CommandStorage.CustomCommand>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commands)
        commandStorage = CommandStorage(this)
        setupRecyclerView()
        loadCommands()

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fab_add_command
        ).setOnClickListener { showAddCommandDialog() }

        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filterCommands(s.toString())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = CommandAdapter(commands,
            onEdit = { showAddCommandDialog(it) },
            onDelete = { deleteCommand(it) },
            onToggle = { cmd, enabled -> commandStorage.updateCommand(cmd.copy(isEnabled = enabled)) }
        )
        findViewById<RecyclerView>(R.id.rv_commands).apply {
            layoutManager = LinearLayoutManager(this@CommandManagerActivity)
            adapter = this@CommandManagerActivity.adapter
        }
    }

    private fun loadCommands() {
        commands = commandStorage.loadCommands()
        adapter.updateList(commands)
        title = "Commands (${commands.size})"
    }

    private fun filterCommands(query: String) {
        val all = commandStorage.loadCommands()
        val filtered = if (query.isEmpty()) all
        else all.filter { it.trigger.contains(query, true) || it.action.contains(query, true) }
        adapter.updateList(filtered.toMutableList())
    }

    private fun showAddCommandDialog(existing: CommandStorage.CustomCommand? = null) {
        val actions = listOf(
            "ACTION_CALL" to "Phone Call",
            "ACTION_SMS" to "SMS Bhejo",
            "ACTION_APP" to "App Kholo",
            "ACTION_TORCH" to "Torch",
            "ACTION_WIFI" to "WiFi",
            "ACTION_BLUETOOTH" to "Bluetooth",
            "ACTION_VOLUME" to "Volume",
            "ACTION_ALARM" to "Alarm",
            "ACTION_TIME" to "Time Batao",
            "ACTION_BATTERY" to "Battery Batao",
            "ACTION_SPEAK" to "Kuch Bolo"
        )

        val hints = mapOf(
            "ACTION_CALL" to "Phone number: 03001234567",
            "ACTION_SMS" to "Phone number likhein",
            "ACTION_APP" to "App naam: camera, whatsapp...",
            "ACTION_TORCH" to "on ya off",
            "ACTION_WIFI" to "on ya off",
            "ACTION_BLUETOOTH" to "on ya off",
            "ACTION_VOLUME" to "up, down, ya mute",
            "ACTION_ALARM" to "Khali chhod sakte hain",
            "ACTION_TIME" to "Khali chhod sakte hain",
            "ACTION_BATTERY" to "Khali chhod sakte hain",
            "ACTION_SPEAK" to "Jo bolwana ho wo likhein"
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTrigger = EditText(this).apply { hint = "Command phrase likhein" }
        val spinnerAction = Spinner(this)
        val etParameter = EditText(this).apply { hint = "Parameter" }
        val tvHint = TextView(this).apply { setTextColor(0xFFFF6600.toInt()); textSize = 12f }

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actions.map { it.second })
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAction.adapter = spinnerAdapter

        spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                tvHint.text = hints[actions[pos].first] ?: ""
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        existing?.let {
            etTrigger.setText(it.trigger)
            etParameter.setText(it.parameter)
            val idx = actions.indexOfFirst { a -> a.first == it.action }
            if (idx >= 0) spinnerAction.setSelection(idx)
        }

        layout.addView(etTrigger)
        layout.addView(spinnerAction)
        layout.addView(etParameter)
        layout.addView(tvHint)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nai Command" else "Command Edit")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val trigger = etTrigger.text.toString().trim()
                val action = actions[spinnerAction.selectedItemPosition].first
                val parameter = etParameter.text.toString().trim()
                if (trigger.isEmpty()) { Toast.makeText(this, "Command likhein", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val command = existing?.copy(trigger = trigger, action = action, parameter = parameter)
                    ?: CommandStorage.CustomCommand(trigger = trigger, action = action, parameter = parameter)
                if (existing == null) commandStorage.addCommand(command) else commandStorage.updateCommand(command)
                loadCommands()
                Toast.makeText(this, "Command save ho gayi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCommand(command: CommandStorage.CustomCommand) {
        AlertDialog.Builder(this)
            .setTitle("Delete?")
            .setMessage("\"${command.trigger}\" delete karna chahte hain?")
            .setPositiveButton("Delete") { _, _ -> commandStorage.deleteCommand(command.id); loadCommands() }
            .setNegativeButton("Nahi", null)
            .show()
    }
}

class CommandAdapter(
    private var commands: MutableList<CommandStorage.CustomCommand>,
    private val onEdit: (CommandStorage.CustomCommand) -> Unit,
    private val onDelete: (CommandStorage.CustomCommand) -> Unit,
    private val onToggle: (CommandStorage.CustomCommand, Boolean) -> Unit
) : RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrigger: TextView = view.findViewById(R.id.tv_trigger)
        val tvAction: TextView = view.findViewById(R.id.tv_action)
        val tvParameter: TextView = view.findViewById(R.id.tv_parameter)
        val switchEnabled: Switch = view.findViewById(R.id.switch_enabled)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_command, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cmd = commands[position]
        holder.tvTrigger.text = "\"${cmd.trigger}\""
        holder.tvAction.text = cmd.action.replace("ACTION_", "")
        holder.tvParameter.text = if (cmd.parameter.isNotEmpty()) cmd.parameter else "-"
        holder.switchEnabled.isChecked = cmd.isEnabled
        holder.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(cmd, checked) }
        holder.btnEdit.setOnClickListener { onEdit(cmd) }
        holder.btnDelete.setOnClickListener { onDelete(cmd) }
    }

    override fun getItemCount() = commands.size

    fun updateList(newList: MutableList<CommandStorage.CustomCommand>) {
        commands = newList
        notifyDataSetChanged()
    }
}
