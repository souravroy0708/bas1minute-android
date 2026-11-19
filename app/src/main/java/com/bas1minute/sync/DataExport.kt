package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * "Export my data": every saved day as a CSV the member can open in Sheets/Excel or keep as a backup. Built on
 * the phone; it goes only where the member chooses to send it. Internal sync columns are left out.
 */
object DataExport {
    private val SKIP = setOf("dirty", "updated_at", "synced_at")

    fun shareIntent(ctx: Context): Intent {
        val db = LocalDb.get(ctx)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "bas1minute-history.csv")
        db.readableDatabase.rawQuery("SELECT * FROM days ORDER BY date", null).use { c ->
            val cols = c.columnNames.withIndex().filter { it.value !in SKIP }
            file.bufferedWriter().use { w ->
                w.write(cols.joinToString(",") { it.value }); w.newLine()
                while (c.moveToNext()) {
                    w.write(cols.joinToString(",") { (i, _) ->
                        if (c.isNull(i)) "" else c.getString(i).let { v -> if (v.any { ch -> ch == ',' || ch == '"' || ch == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v }
                    })
                    w.newLine()
                }
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "My Bas 1 Minute history")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Export my data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
