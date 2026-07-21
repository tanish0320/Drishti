package com.drishti.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {
    private val _appVersion = MutableStateFlow("Version 2.4.0 (Stable Build)")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()
}
