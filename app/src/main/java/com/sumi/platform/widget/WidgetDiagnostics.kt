package com.sumi.platform.widget

import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.sumi.BuildConfig
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only diagnostics for the widget-refresh investigation (owner: vivo V2322,
 * Android 15 — widget frozen until the app is opened). Everything here is compiled out
 * of release builds via [BuildConfig.DEBUG].
 *
 * Two sinks, because each has a different reader:
 *  - logcat, tag `SumiWidgetDiag`, for the adb logger on the phone;
 *  - a plain-text timeline at `Download/sumi-widget-diag.txt` via MediaStore (no storage
 *    permission needed on Android 10+), so the full receiver -> worker -> render chain
 *    can be read after the fact, including which WorkManager job actually ran (`tags`)
 *    and whether any widget instance exists (`placed`).
 */
internal object WidgetDiagnostics {
    private const val TAG = "SumiWidgetDiag"
    private const val FILE_NAME = "sumi-widget-diag.txt"
    private const val DEBUG_TRIGGER = "androidx.glance.appwidget.action.DEBUG_UPDATE"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedUri: Uri? = null

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** Called from Application.onCreate so file logging has a context. */
    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        appContext = context.applicationContext
        log("=== PROCESS START pid=${android.os.Process.myPid()} ${deviceState(appContext!!)} ===")
    }

    /** Glance's DEBUG_UPDATE is a non-protected broadcast we can send from a shell. */
    fun isDebugTrigger(action: String?): Boolean = BuildConfig.DEBUG && action == DEBUG_TRIGGER

    @Synchronized
    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        val line = "${stamp.format(Date())} $message"
        Log.i(TAG, line)
        appendFile(line)
    }

    @Synchronized
    fun error(message: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) return
        val line = "${stamp.format(Date())} ERROR $message :: ${Log.getStackTraceString(throwable)}"
        Log.e(TAG, line)
        appendFile(line)
    }

    /** Standby bucket + battery state: the two throttles we suspect on the Vivo. */
    fun deviceState(context: Context): String {
        if (!BuildConfig.DEBUG) return ""
        val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)
                ?.appStandbyBucket?.toString() ?: "?"
        } else {
            "n/a"
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName)?.toString() ?: "?"
        val idle = power?.isDeviceIdleMode?.toString() ?: "?"
        val powerSave = power?.isPowerSaveMode?.toString() ?: "?"
        return "bucket=$bucket unrestricted=$unrestricted idle=$idle powerSave=$powerSave"
    }

    /** Placed instances for a concrete receiver — a widget may not exist at all. */
    fun placedWidgets(context: Context, receiver: Class<*>): String {
        if (!BuildConfig.DEBUG) return ""
        return runCatching {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, receiver))
            "placed=${ids.size} ids=${ids.joinToString()}"
        }.getOrDefault("placed=?")
    }

    /** Never let a diagnostics failure break the caller. */
    private fun appendFile(line: String) {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = context.contentResolver
            val uri = cachedUri ?: findOrCreate(resolver).also { cachedUri = it }
            resolver.openOutputStream(uri, "wa")?.use { out ->
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "timeline write failed: ${t.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findOrCreate(resolver: ContentResolver): Uri {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(FILE_NAME),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return resolver.insert(collection, values) ?: throw IOException("MediaStore insert returned null")
    }
}
