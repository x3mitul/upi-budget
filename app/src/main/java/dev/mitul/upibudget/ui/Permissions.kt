package dev.mitul.upibudget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object Permissions {
    fun smsGranted(c: Context) = c.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        c.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    fun notificationsGranted(c: Context) = if (Build.VERSION.SDK_INT >= 33) c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    fun listenerGranted(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    fun batteryUnrestricted(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    private fun start(c: Context, i: Intent) = c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun openAppSettings(c: Context) = start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")))
    fun openListenerSettings(c: Context) = start(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun requestBatteryExemption(c: Context) = start(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")))
    /** Some phones (vivo, iQOO, Xiaomi, OnePlus) hide autostart and background limits in different places per OS version, so open the battery screen and let the on-screen steps guide the user. */
    fun openVivoBackgroundHelp(c: Context) = start(c, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
}
