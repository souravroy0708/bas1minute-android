package com.bas1minute.sync.detox

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.bas1minute.sync.MainActivity

/**
 * Full-screen "detox is on" screen shown over a blocked app. Plain Android views so it opens instantly and
 * has no Compose start-up cost. Back always goes to the home screen, never to the blocked app.
 */
class BlockActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var attempts: TextView
    private lateinit var appLine: TextView

    private val tick = object : Runnable {
        override fun run() {
            val s = DetoxStore.get(this@BlockActivity).active()
            if (s == null) { finish(); return }
            val now = System.currentTimeMillis()
            countdown.text = DetoxLogic.formatDuration(s.remainingMs(now))
            attempts.text = when (s.blockedAttempts) {
                0, 1 -> "Your promise: ${DetoxLogic.formatMinutes(s.plannedMinutes)} with only calls and messages."
                else -> "Stopped ${s.blockedAttempts} times so far. You’re doing the hard part."
            }
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NAVY
        window.navigationBarColor = NAVY
        setContentView(buildUi())
        showBlockedApp(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goHome() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showBlockedApp(intent)
    }

    override fun onResume() { super.onResume(); ui.post(tick) }
    override fun onPause() { ui.removeCallbacks(tick); super.onPause() }

    @Deprecated("Back before Android 13")
    override fun onBackPressed() { goHome() }

    private fun showBlockedApp(i: Intent) {
        val pkg = i.getStringExtra(EXTRA_PKG)
        val name = pkg?.let { AppRules(this, DetoxStore.get(this).extraAllowed).label(it) }
        appLine.text = if (name != null) "$name is paused" else "This app is paused"
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openDialer() = launch(Intent(Intent.ACTION_DIAL))

    private fun openMessages() {
        val sms = Telephony.Sms.getDefaultSmsPackage(this)
        val i = sms?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        launch(i)
    }

    private fun openDetoxInApp() {
        val i = Intent(this, MainActivity::class.java)
            .putExtra(DetoxService.EXTRA_OPEN, DetoxService.OPEN_DETOX)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launch(i)
    }

    private fun launch(i: Intent) {
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    // ---- views -----------------------------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(NAVY)
            setPadding(dp(28), dp(72), dp(28), dp(36))
        }
        fun text(size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(top) }
        }
        val badge = TextView(this).apply {
            text = "1"; textSize = 34f; setTextColor(NAVY); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(HALDI) }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }
        root.addView(badge)
        root.addView(text(15f, MUTED, top = 28).apply { text = "DIGITAL DETOX IS ON"; letterSpacing = 0.12f })
        appLine = text(26f, Color.WHITE, bold = true, top = 8)
        root.addView(appLine)
        countdown = text(64f, HALDI, bold = true, top = 24).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        root.addView(countdown)
        root.addView(text(15f, MUTED).apply { text = "left" })
        attempts = text(16f, Color.WHITE, top = 24)
        root.addView(attempts)

        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(pill("Call", HALDI, NAVY) { openDialer() }, weighted(end = 6))
        row.addView(pill("Messages", HALDI, NAVY) { openMessages() }, weighted(start = 6))
        root.addView(row)
        root.addView(pill("Go to home screen", Color.WHITE, NAVY) { goHome() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })
        root.addView(Button(this).apply {
            text = "End detox early"
            isAllCaps = false
            setTextColor(MUTED)
            background = null
            setOnClickListener { openDetoxInApp() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        return root
    }

    private fun weighted(start: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(start); marginEnd = dp(end) }

    private fun pill(label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { cornerRadius = dp(27).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    companion object {
        const val EXTRA_PKG = "blocked_pkg"
        private const val NAVY = 0xFF0F1235.toInt()
        private const val HALDI = 0xFFFFC21A.toInt()
        private const val MUTED = 0xFFBCC1E0.toInt()
    }
}
