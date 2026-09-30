package com.github.panlelapin.simplercal

import android.app.backup.BackupAgentHelper
import android.content.Context

/** Never restore a Calendar Provider row ID from another installation/device. */
class AppBackupAgent : BackupAgentHelper() {
    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // Also strip the legacy key from backups created before calendar_device existed.
        listOf(PREFERENCES_NAME, "calendar_device").forEach { name ->
            check(getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .remove(SELECTED_CALENDAR_KEY).commit()) { "Cannot clear restored calendar selection" }
        }
    }
}
