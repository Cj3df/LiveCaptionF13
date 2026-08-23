package com.livecaption.f13.model

sealed class CaptionUiState {
    object Idle : CaptionUiState()
    object Connecting : CaptionUiState()
    data class Active(val currentText: String) : CaptionUiState()
    data class Error(val message: String) : CaptionUiState()
}
