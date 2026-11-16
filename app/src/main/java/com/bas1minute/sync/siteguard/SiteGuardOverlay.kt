package com.bas1minute.sync.siteguard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs

/**
 * Shown the moment a site's daily budget is spent — drawn as a system overlay window, not a launched
 * Activity, for the same reason Digital Detox's own block screen is (see detox/BlockOverlay.kt): Android 14+
 * only exempts an app from the background-activity-start restriction once it already has a visible window,
 * so a plain startActivity() call from an AccessibilityService would silently fail on current phones.
 *
 * There's no "end early" here — this isn't a promise the member is mid-way through breaking, it's a standing
 * daily limit they set up once. The only ways out are going home, or changing the limit itself in the app.
 */
object SiteGuardOverlay {
    private var wm: WindowManager? = null
    private var root: View? = null
    private var shownDomain: String? = null

    val isShowing: Boolean get() = root != null

    fun show(context: Context, domain: String) {
        if (domain == shownDomain && root != null) return
        val ctx = context.applicationContext
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        hide()
        val prefs = Prefs(ctx)
        val view = buildUi(ctx, domain, prefs.siteGuardBudgetMinutes)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            manager.addView(view, params)
            wm = manager; root = view; shownDomain = domain
            view.isFocusableInTouchMode = true
            view.requestFocus()
        } catch (e: Exception) {
            wm = null; root = null; shownDomain = null
        }
    }

    fun hide() {
        val manager = wm; val view = root
        if (manager != null && view != null) runCatching { manager.removeView(view) }
        wm = null; root = null; shownDomain = null
    }

    private fun goHome(ctx: Context) {
        hide()
        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openInApp(ctx: Context) {
        hide()
        runCatching {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .putExtra(com.bas1minute.sync.detox.DetoxService.EXTRA_OPEN, "detox")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun buildUi(ctx: Context, domain: String, budgetMinutes: Int): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0F1235.toInt())
            setPadding(dp(ctx, 28), dp(ctx, 90), dp(ctx, 28), dp(ctx, 36))
            isClickable = true
        }
        fun text(size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(ctx).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(ctx, top) }
        }
        val badge = TextView(ctx).apply {
            text = "⏳"; textSize = 34f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFC21A.toInt()) }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 76), dp(ctx, 76))
        }
        root.addView(badge)
        root.addView(text(15f, 0xFFBCC1E0.toInt(), top = 28).apply { text = "TODAY'S TIME IS UP"; letterSpacing = 0.1f })
        root.addView(text(24f, Color.WHITE, bold = true, top = 8).apply { text = domain })
        root.addView(text(16f, Color.WHITE, top = 20).apply {
            text = "You've used your $budgetMinutes minutes on $domain for today. It'll open again tomorrow."
        })
        root.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        root.addView(Button(ctx).apply {
            text = "Go to home screen"; isAllCaps = false; textSize = 16f
            setTextColor(0xFF0F1235.toInt()); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(ctx, 27).toFloat(); setColor(Color.WHITE) }
            setOnClickListener { goHome(ctx) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 54)))
        root.addView(Button(ctx).apply {
            text = "Change this limit"; isAllCaps = false
            setTextColor(0xFFBCC1E0.toInt()); background = null
            setOnClickListener { openInApp(ctx) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)).apply { topMargin = dp(ctx, 8) })
        return root
    }
}
