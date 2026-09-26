package com.example.ui.screens.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NotificationItem
import com.example.data.repository.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotificationRepository(application)

    val notifications: StateFlow<List<NotificationItem>> = repository.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val unreadCount: StateFlow<Int> = repository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            repository.syncCloudNotifications(getApplication())
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }
    
    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }
}
