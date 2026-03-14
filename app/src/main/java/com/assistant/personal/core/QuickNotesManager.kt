package com.assistant.personal.core

import android.content.Context
import com.assistant.personal.storage.CommandStorage
import java.text.SimpleDateFormat
import java.util.*

class QuickNotesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)

    data class Note(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val time: Long = System.currentTimeMillis()
    )

    fun saveNote(text: String): Note {
        val note = Note(text = text)
        val all = getAllNotes().toMutableList()
        all.add(0, note)
        val json = com.google.gson.Gson().toJson(all)
        prefs.edit().putString("notes", json).apply()
        return note
    }

    fun getAllNotes(): List<Note> {
        val json = prefs.getString("notes", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Note>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getLastNote(): Note? = getAllNotes().firstOrNull()

    fun deleteNote(id: String) {
        val all = getAllNotes().toMutableList()
        all.removeAll { it.id == id }
        prefs.edit().putString("notes", com.google.gson.Gson().toJson(all)).apply()
    }

    fun clearAll() {
        prefs.edit().remove("notes").apply()
    }

    fun formatTime(time: Long): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.ENGLISH)
        return sdf.format(Date(time))
    }
}
