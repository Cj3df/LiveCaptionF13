package com.livecaption.f13.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.livecaption.f13.R
import com.livecaption.f13.model.CaptionFontSize
import com.livecaption.f13.utils.PreferenceHelper

class FloatingCaptionOverlay(
    private val context: Context,
    private val onCloseClicked: () -> Unit
) {
    companion object {
        private const val TAG = "FloatingCaptionOverlay"
        private const val MAX_HISTORY_LENGTH = 300
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefHelper = PreferenceHelper(context)
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var tvCaptionText: TextView? = null
    private var tvStatus: TextView? = null
    private var scrollView: ScrollView? = null
    private var overlayRoot: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var finalizedHistory = StringBuilder()
    private var currentInterim = ""

    // Full session storage for complete Copy & TXT/SRT Export
    private val sessionFullTranscript = StringBuilder()
    private val sessionTimestampedCaptions = mutableListOf<com.livecaption.f13.utils.TimestampedCaption>()
    private var sessionStartTimeMs = System.currentTimeMillis()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (overlayView != null) return

        sessionStartTimeMs = System.currentTimeMillis()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 200 // Initial vertical margin from bottom
        }

        // Use a themed context so theme attributes like ?selectableItemBackgroundBorderless
        // resolve correctly when inflating from a Service context.
        val themedContext = ContextThemeWrapper(context, R.style.Theme_LiveCaptionF13)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.layout_floating_caption, null)
        overlayView = view

        overlayRoot = view.findViewById(R.id.overlayRoot)
        tvCaptionText = view.findViewById(R.id.tvCaptionText)
        tvStatus = view.findViewById(R.id.tvCaptionStatus)
        scrollView = view.findViewById(R.id.captionScrollView)

        val headerBar = view.findViewById<View>(R.id.headerBar)
        val btnCopy = view.findViewById<ImageView>(R.id.btnOverlayCopy)
        val btnSave = view.findViewById<ImageView>(R.id.btnOverlaySave)
        val btnClear = view.findViewById<ImageView>(R.id.btnOverlayClear)
        val btnClose = view.findViewById<ImageView>(R.id.btnOverlayClose)

        // Apply saved visual styles
        applyAppearance()

        // Copy transcript button
        btnCopy.setOnClickListener {
            val textToCopy = if (sessionFullTranscript.isNotEmpty()) sessionFullTranscript.toString() else finalizedHistory.toString()
            if (com.livecaption.f13.utils.TranscriptExporter.copyToClipboard(context, textToCopy)) {
                updateStatus("✓ Copied to clipboard!", R.color.accent_green)
            }
        }

        // Save & Export transcript button (Saves .txt and .srt to Downloads/LiveCaptions)
        btnSave.setOnClickListener {
            val textToSave = if (sessionFullTranscript.isNotEmpty()) sessionFullTranscript.toString() else finalizedHistory.toString()
            val txtFile = com.livecaption.f13.utils.TranscriptExporter.exportToTxt(context, textToSave)
            val srtFile = com.livecaption.f13.utils.TranscriptExporter.exportToSrt(context, sessionTimestampedCaptions)
            if (txtFile != null || srtFile != null) {
                updateStatus("✓ Saved to Downloads!", R.color.accent_green)
            }
        }

        // Dragging logic
        headerBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    params?.let {
                        initialX = it.x
                        initialY = it.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        it.x = initialX + (event.rawX - initialTouchX).toInt()
                        it.y = initialY - (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(overlayView, it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Update overlay position error", e)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener {
            onCloseClicked()
        }

        btnClear.setOnClickListener {
            clearCaptions()
        }

        try {
            windowManager.addView(view, params)
            Log.d(TAG, "Floating caption overlay added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating caption overlay", e)
        }
    }

    fun updateStatus(status: String, colorRes: Int = R.color.accent_blue) {
        mainHandler.post {
            tvStatus?.text = status
            tvStatus?.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }

    fun onNewTranscript(transcript: String, isFinal: Boolean, speechFinal: Boolean, detectedLanguage: String? = null) {
        mainHandler.post {
            // Update detected language badge in status if provided
            if (!detectedLanguage.isNullOrBlank()) {
                val langTag = when (detectedLanguage.lowercase()) {
                    "hi" -> "Hindi [HI]"
                    "en" -> "English [EN]"
                    "hi-latn" -> "Hinglish [HI-EN]"
                    else -> detectedLanguage.uppercase()
                }
                val srcName = if (prefHelper.audioSource == com.livecaption.f13.model.AudioSourceType.INTERNAL) "Internal Audio" else "Microphone"
                tvStatus?.text = "Live Captions • $langTag • $srcName"
                tvStatus?.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
            }

            if (isFinal) {
                val cleanText = transcript.trim()
                if (cleanText.isNotBlank()) {
                    if (sessionFullTranscript.isNotEmpty()) {
                        sessionFullTranscript.append(" ")
                    }
                    sessionFullTranscript.append(cleanText)

                    val now = System.currentTimeMillis()
                    val elapsed = now - sessionStartTimeMs
                    val durationEstimate = (cleanText.split(" ").size * 400L).coerceAtLeast(1500L)
                    sessionTimestampedCaptions.add(
                        com.livecaption.f13.utils.TimestampedCaption(
                            startMs = (elapsed - durationEstimate).coerceAtLeast(0L),
                            endMs = elapsed,
                            text = cleanText
                        )
                    )
                }

                if (finalizedHistory.isNotEmpty()) {
                    finalizedHistory.append(" ")
                }
                finalizedHistory.append(cleanText)

                // Truncate oldest sentences if history becomes too long
                if (finalizedHistory.length > MAX_HISTORY_LENGTH) {
                    val excess = finalizedHistory.length - MAX_HISTORY_LENGTH
                    val nextSpace = finalizedHistory.indexOf(" ", excess)
                    if (nextSpace != -1) {
                        finalizedHistory.delete(0, nextSpace + 1)
                    }
                }
                currentInterim = ""
            } else {
                currentInterim = transcript.trim()
            }

            renderSpannableCaption()
        }
    }

    private fun renderSpannableCaption() {
        val ssb = SpannableStringBuilder()

        if (finalizedHistory.isNotEmpty()) {
            val start = 0
            ssb.append(finalizedHistory.toString())
            ssb.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_primary)),
                start,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (currentInterim.isNotEmpty()) {
            if (ssb.isNotEmpty()) ssb.append(" ")
            val interimStart = ssb.length
            ssb.append(currentInterim)
            ssb.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_interim)),
                interimStart,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.setSpan(
                StyleSpan(Typeface.ITALIC),
                interimStart,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (ssb.isEmpty()) {
            tvCaptionText?.text = context.getString(R.string.waiting_for_speech)
        } else {
            tvCaptionText?.text = ssb
        }

        // Auto scroll to bottom
        scrollView?.post {
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun clearCaptions() {
        mainHandler.post {
            finalizedHistory.clear()
            sessionFullTranscript.clear()
            sessionTimestampedCaptions.clear()
            sessionStartTimeMs = System.currentTimeMillis()
            currentInterim = ""
            tvCaptionText?.text = context.getString(R.string.waiting_for_speech)
            updateStatus("Captions Cleared", R.color.accent_blue)
        }
    }

    fun applyAppearance() {
        mainHandler.post {
            tvCaptionText?.textSize = prefHelper.fontSize.spValue
            overlayRoot?.alpha = prefHelper.overlayOpacity
        }
    }

    fun hide() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating overlay view", e)
            } finally {
                overlayView = null
            }
        }
    }
}
