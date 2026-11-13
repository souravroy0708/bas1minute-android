package com.bas1minute.sync.detox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or an app update, resume (or close) a detox that was running. */
class DetoxBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> DetoxController.reconcile(context)
        }
    }
}
