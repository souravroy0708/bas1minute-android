package com.bas1minute.sync.detox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.bas1minute.sync.MainActivity

/**
 * The "detox is on" block screen, shown over a blocked app.
 *
 * Drawn as a system overlay window (TYPE_APPLICATION_OVERLAY) rather than a launched Activity. Android 10+
 * restricts starting an Activity from a background Service, and Android 14 tightened the exemption further:
 * simply holding "Display over other apps" no longer lets a plain startActivity() call succeed from the
 * background — an app is only exempt once it already has a visible window. That's exactly what an overlay
 * window is, so this covers the blocked app reliably on every supported Android version, and once it's shown,
 * the same app can start further activities (Call, Messages, Home, End early) because it then has that
 * visible window.
 *
 * One overlay is added per blocked app and reused while the same app stays in front; it's removed the moment
 * the foreground app changes or the detox ends. There is nothing clever to defeat here: the same instant
 * "Go to home screen" and "End detox early" controls as the user always had are right on the screen.
 */
object BlockOverlay {
    private var wm: WindowManager? = null
    private var root: View? = null
    private var countdown: TextView? = null
    private var attempts: TextView? = null
    private var ticker: Handler? = null
    private var tickRunnable: Runnable? = null
    private var shownPkg: String? = null
    private var appCtx: Context? = null

    val isShowing: Boolean get() = root != null

    /** Shows (or keeps showing) the block screen for [pkg]. A no-op if it's already showing for this package. */
    fun show(context: Context, pkg: String) {
        if (pkg == shownPkg && root != null) return
        val ctx = context.applicationContext
        if (!DetoxController.canDrawOverlays(ctx)) return
        hide()
        val rules = AppRules(ctx, DetoxStore.get(ctx).extraAllowed)
        val view = buildUi(ctx, rules.label(pkg))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            manager.addView(view, params)
            wm = manager; root = view; shownPkg = pkg; appCtx = ctx
            view.isFocusableInTouchMode = true
            view.requestFocus()
            startTicking(ctx)
        } catch (e: Exception) {
            wm = null; root = null; shownPkg = null
        }
    }

    /** Removes the block screen, if one is showing. Safe to call when it isn't. */
    fun hide() {
        stopTicking()
        val manager = wm; val view = root
        if (manager != null && view != null) runCatching { manager.removeView(view) }
        wm = null; root = null; countdown = null; attempts = null; shownPkg = null
    }

    private fun startTicking(ctx: Context) {
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                val s = DetoxStore.get(ctx).active()
                if (s == null) { hide(); return }
                val now = System.currentTimeMillis()
                countdown?.text = DetoxLogic.formatDuration(s.remainingMs(now))
                attempts?.text = when (s.blockedAttempts) {
                    0, 1 -> "Your promise: ${DetoxLogic.formatMinutes(s.plannedMinutes)} with only calls and messages."
                    else -> "Stopped ${s.blockedAttempts} times so far. You're doing the hard part."
                }
                h.postDelayed(this, 1000)
            }
        }
        ticker = h; tickRunnable = r
        h.post(r)
    }

    private fun stopTicking() {
        tickRunnable?.let { ticker?.removeCallbacks(it) }
        ticker = null; tickRunnable = null
    }

    private fun goHome(ctx: Context) {
        hide()
        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openDialer(ctx: Context) {
        hide()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openMessages(ctx: Context) {
        hide()
        val sms = Telephony.Sms.getDefaultSmsPackage(ctx)
        val i = (sms?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /** Opens the app on the Detox tab, where "End early" walks through the real, honest confirmation steps. */
    private fun openDetoxInApp(ctx: Context) {
        hide()
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(DetoxService.EXTRA_OPEN, DetoxService.OPEN_DETOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    // ---- view building (plain Android views: no Activity, no Compose host needed for an overlay) ----

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun buildUi(ctx: Context, appLabel: String): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(NAVY)
            setPadding(dp(ctx, 28), dp(ctx, 72), dp(ctx, 28), dp(ctx, 36))
            isClickable = true   // swallow touches so they never reach the app underneath
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { goHome(ctx); true } else false
            }
        }
        fun text(size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(ctx).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(ctx, top) }
        }
        val badge = TextView(ctx).apply {
            text = "1"; textSize = 34f; setTextColor(NAVY); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(HALDI) }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 76), dp(ctx, 76))
        }
        root.addView(badge)
        root.addView(text(15f, MUTED, top = 28).apply { text = "DIGITAL DETOX IS ON"; letterSpacing = 0.12f })
        root.addView(text(26f, Color.WHITE, bold = true, top = 8).apply { text = "$appLabel is paused" })
        countdown = text(64f, HALDI, bold = true, top = 24).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        root.addView(countdown)
        root.addView(text(15f, MUTED).apply { text = "left" })
        attempts = text(16f, Color.WHITE, top = 24)
        root.addView(attempts)

        root.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(pill(ctx, "Call", HALDI, NAVY) { openDialer(ctx) }, weighted(ctx, end = 6))
        row.addView(pill(ctx, "Messages", HALDI, NAVY) { openMessages(ctx) }, weighted(ctx, start = 6))
        root.addView(row)
        root.addView(pill(ctx, "Go to home screen", Color.WHITE, NAVY) { goHome(ctx) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 54)).apply { topMargin = dp(ctx, 12) })
        root.addView(Button(ctx).apply {
            text = "End detox early"
            isAllCaps = false
            setTextColor(MUTED)
            background = null
            setOnClickListener { openDetoxInApp(ctx) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)).apply { topMargin = dp(ctx, 8) })
        return root
    }

    private fun weighted(ctx: Context, start: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(0, dp(ctx, 54), 1f).apply { marginStart = dp(ctx, start); marginEnd = dp(ctx, end) }

    private fun pill(ctx: Context, label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { cornerRadius = dp(ctx, 27).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    private const val NAVY = 0xFF0F1235.toInt()
    private const val HALDI = 0xFFFFC21A.toInt()
    private const val MUTED = 0xFFBCC1E0.toInt()
}
