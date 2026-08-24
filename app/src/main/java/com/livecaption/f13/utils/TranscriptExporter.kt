package com.livecaption.f13.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TimestampedCaption(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object TranscriptExporter {
    private const val TAG = "TranscriptExporter"

    fun copyToClipboard(context: Context, text: String): Boolean {
        if (text.isBlank()) {
            Toast.makeText(context, "No caption text to copy", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Live Captions Transcript", text.trim())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Captions copied to clipboard", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
            false
        }
    }

    fun exportToTxt(context: Context, text: String): File? {
        if (text.isBlank()) {
            Toast.makeText(context, "No transcript to export", Toast.LENGTH_SHORT).show()
            return null
        }
        return try {
            val dir = getExportDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "LiveCaptions_$timeStamp.txt")

            FileWriter(file).use { writer ->
                writer.write("=== Live Captions Transcript ===\n")
                writer.write("Recorded: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
                writer.write(text.trim())
                writer.write("\n")
            }

            Log.d(TAG, "Transcript saved to TXT: ${file.absolutePath}")
            Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting TXT", e)
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun exportToSrt(context: Context, captions: List<TimestampedCaption>): File? {
        if (captions.isEmpty()) {
            Toast.makeText(context, "No timestamps available for SRT export", Toast.LENGTH_SHORT).show()
            return null
        }
        return try {
            val dir = getExportDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "LiveCaptions_$timeStamp.srt")

            FileWriter(file).use { writer ->
                for ((index, item) in captions.withIndex()) {
                    val srtIndex = index + 1
                    val startStr = formatSrtTime(item.startMs)
                    val endStr = formatSrtTime(if (item.endMs > item.startMs) item.endMs else item.startMs + 2000)

                    writer.write("$srtIndex\n")
                    writer.write("$startStr --> $endStr\n")
                    writer.write("${item.text.trim()}\n\n")
                }
            }

            Log.d(TAG, "Transcript saved to SRT: ${file.absolutePath}")
            Toast.makeText(context, "Saved Subtitles: ${file.name}", Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting SRT", e)
            Toast.makeText(context, "SRT export error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun shareTranscript(context: Context, text: String, file: File? = null) {
        if (text.isBlank() && file == null) {
            Toast.makeText(context, "No transcript to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                if (file != null && file.exists()) {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    type = if (file.name.endsWith(".srt")) "application/x-subrip" else "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }

            val chooser = Intent.createChooser(intent, "Share Live Captions Transcript").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing transcript", e)
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getExportDirectory(context: Context): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadDir, "LiveCaptions")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return if (appDir.canWrite()) appDir else context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    }

    private fun formatSrtTime(millis: Long): String {
        val safeMillis = if (millis < 0) 0L else millis
        val hours = safeMillis / 3600000
        val minutes = (safeMillis % 3600000) / 60000
        val seconds = (safeMillis % 60000) / 1000
        val ms = safeMillis % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }
}
