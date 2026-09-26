package com.example.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.SupportMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).supportDao()

    val messages: StateFlow<List<SupportMessage>> = dao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            if (dao.getMessageCount() == 0) {
                // Initial greeting message
                dao.insertMessage(
                    SupportMessage(
                        text = application.getString(R.string.support_welcome_message),
                        isFromUser = false
                    )
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            // User message
            dao.insertMessage(SupportMessage(text = text.trim(), isFromUser = true))
            
            // Simulate bot thinking
            delay(1000)
            
            val response = generateResponse(text.trim())
            dao.insertMessage(SupportMessage(text = response, isFromUser = false))
        }
    }
    
    private fun generateResponse(input: String): String {
        val app = getApplication<Application>()
        val lower = input.lowercase()
        return when {
            lower.contains("premium") || lower.contains("plan") -> app.getString(R.string.support_response_premium)
            lower.contains("device") || lower.contains("multiple") -> app.getString(R.string.support_response_device)
            lower.contains("hello") || lower.contains("hi") -> app.getString(R.string.support_response_greeting)
            lower.contains("issue") || lower.contains("problem") || lower.contains("playback") -> app.getString(R.string.support_response_issue)
            else -> app.getString(R.string.support_response_default)
        }
    }
}
