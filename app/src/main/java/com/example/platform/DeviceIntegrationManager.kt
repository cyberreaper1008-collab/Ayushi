package com.example.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.content.ContextCompat

data class DeviceTelemetry(
    val batteryPercent: Int,
    val charging: Boolean,
    val networkStatus: String
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class PermissionStatusSummary(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean
)

class DeviceIntegrationManager(private val context: Context) {

    fun getDeviceTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val networkLabel = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Connected"
            else -> "Online"
        }

        return DeviceTelemetry(
            batteryPercent = percent,
            charging = charging,
            networkStatus = networkLabel
        )
    }

    fun getPermissionStatus(): PermissionStatusSummary {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatusSummary(mic, notif)
    }

    fun findInstalledApps(query: String? = null): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, 0)
        return activities
            .map { resolveInfo ->
                InstalledAppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .filter { app ->
                query.isNullOrBlank() || app.label.contains(query, ignoreCase = true)
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String): Result<String> {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return Result.failure(IllegalStateException("App is not launchable or not installed."))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.success("Opened app ($packageName).")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWebsite(rawUrl: String): Result<String> {
        return try {
            val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl
            } else {
                "https://$rawUrl"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened $normalized")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startNavigation(destination: String): Result<String> {
        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Started navigation to $destination")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Reminder" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("Opened clock to set alarm for %02d:%02d.".format(hour, minute))
            } else {
                Result.failure(IllegalStateException("No Clock/Alarm handler available on this device."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setTimer(seconds: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("Opened clock to set ${seconds}s timer.")
            } else {
                Result.failure(IllegalStateException("No Timer handler available on this device."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSettings(settingType: String): Result<String> {
        val action = when (settingType.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "permissions", "app" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                Intent(action, Uri.fromParts("package", context.packageName, null))
            } else {
                Intent(action)
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            Result.success("Opened $settingType settings.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareText(title: String, body: String): Result<String> {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success("Opened Android share sheet.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
