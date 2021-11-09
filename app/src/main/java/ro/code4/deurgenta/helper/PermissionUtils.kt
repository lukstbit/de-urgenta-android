package ro.code4.deurgenta.helper

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import ro.code4.deurgenta.R
import ro.code4.deurgenta.ui.address.ConfigureAddressFragment

fun showPermissionExplanation(
    context: Context,
    preferences: SharedPreferences,
    initiatePermission: () -> Unit
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.permission_info_title))
        .setMessage(context.getString(R.string.permission_info_content))
        .setPositiveButton(context.getString(R.string.permission_info_btn)) { _, btnId: Int ->
            if (btnId == Dialog.BUTTON_POSITIVE) {
                preferences.putBoolean(ConfigureAddressFragment.PREFS_HAS_SEEN_PERMISSION_NOTICE, true)
                initiatePermission()
            }
        }
        .setCancelable(false)
        .create()
    dialog.show()
}

fun getPermissionStatus(context: Context, permission: String): Int = context.packageManager.checkPermission(
    permission,
    context.packageName
)
