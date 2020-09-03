package eu.vitinger.systemSettingsTester

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object WriteSettingsPermissionHelper {

    fun checkAndAskPermissionIfNeeded(context: Context): Boolean {
        return if (hasPermission(context)) {
            true
        } else {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
            Toast.makeText(context, "Give me permission!", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun hasPermission(context: Context): Boolean {
        return runCatching {
            Settings.System.canWrite(context)
        }.getOrDefault(false)
    }

}