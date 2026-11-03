package com.bas1minute.sync

import android.os.Build

/**
 * Phone-specific help. Menus differ by brand, Android version and software update, so every topic gives:
 * the usual steps for this brand, a Google search for this exact phone, and a question to paste into
 * Google / Gemini / ChatGPT.
 */
object PhoneHelp {

    data class Device(val brand: String, val model: String, val android: String, val skin: String) {
        /** "OnePlus CPH2581 · Android 15 · OxygenOS" */
        val label: String get() = listOf("$brand $model", "Android $android", skin).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }

    data class Help(val title: String, val steps: List<String>, val searchQuery: String, val aiQuestion: String)

    fun device(): Device {
        val maker = Build.MANUFACTURER.lowercase()
        val brand = when {
            maker.contains("samsung") -> "Samsung"
            maker.contains("oneplus") -> "OnePlus"
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> Build.BRAND.replaceFirstChar { it.uppercase() }
            maker.contains("oppo") -> "OPPO"
            maker.contains("realme") -> "realme"
            maker.contains("vivo") || maker.contains("iqoo") -> Build.BRAND.replaceFirstChar { it.uppercase() }
            maker.contains("google") -> "Google"
            maker.contains("motorola") -> "Motorola"
            maker.contains("nothing") -> "Nothing"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
        val sdk = Build.VERSION.SDK_INT
        val skin = when {
            maker.contains("samsung") -> "One UI"
            maker.contains("oneplus") -> "OxygenOS"
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> if (sdk >= 34) "HyperOS" else "MIUI"
            maker.contains("oppo") -> "ColorOS"
            maker.contains("realme") -> "realme UI"
            maker.contains("vivo") || maker.contains("iqoo") -> "Funtouch OS"
            maker.contains("nothing") -> "Nothing OS"
            else -> ""
        }
        return Device(brand, Build.MODEL, Build.VERSION.RELEASE, skin)
    }

    private fun family(d: Device): String = when (d.brand) {
        "Samsung" -> "samsung"
        "OnePlus", "OPPO", "realme" -> "oplus"
        "Xiaomi", "Redmi", "Poco", "POCO" -> "xiaomi"
        "Vivo", "vivo", "Iqoo", "iQOO" -> "vivo"
        else -> "stock"
    }

    /** "App was denied access" for Usage access on an APK install (Android 13+ restricted settings). */
    fun usageAccess(d: Device = device()): Help {
        val appInfo = when (family(d)) {
            "samsung" -> "Settings → Apps → Bas 1 Minute. Tap ⋮ (top right) and choose Allow restricted settings. On some One UI versions it’s at the bottom of the App info page."
            "oplus" -> "Settings → Apps → App management → Bas 1 Minute. Tap ⋮ (top right), or scroll to the bottom of the page, and choose Allow restricted settings."
            "xiaomi" -> "Settings → Apps → Manage apps → Bas 1 Minute. Look for ⋮ or “More” at the top right, or the bottom of the page, and choose Allow restricted settings."
            "vivo" -> "Settings → Apps → Apps → Bas 1 Minute. Tap ⋮ (top right) and choose Allow restricted settings."
            else -> "Settings → Apps → See all apps → Bas 1 Minute. Tap ⋮ (top right) and choose Allow restricted settings."
        }
        return Help(
            title = "Allow usage access on your ${d.brand}",
            steps = listOf(
                "Try to switch on Usage access for Bas 1 Minute once and see “App was denied access”. This unlocks the next step.",
                appInfo + " Confirm with your PIN or fingerprint.",
                "Switch on Usage access for Bas 1 Minute again. It works this time.",
            ),
            searchQuery = "${d.brand} ${d.model} Android ${d.android} ${d.skin} allow restricted settings usage access".replace(Regex("\\s+"), " ").trim(),
            aiQuestion = "I have a ${d.brand} phone, model ${d.model}, Android ${d.android}" + (if (d.skin.isNotBlank()) " (${d.skin})" else "") +
                ". I installed an app (Bas 1 Minute) from an APK file. When I try to turn on Usage access for it, I get " +
                "“App was denied access. Access to this permission can put your personal and financial info at risk.” " +
                "Step by step, with the exact menu names on my phone, how do I allow restricted settings for this app and turn on Usage access?",
        )
    }

    /** Battery savers that stop background sync. Only relevant on brands known to be strict. */
    fun batteryStrict(d: Device = device()): Boolean = family(d) != "stock"

    fun battery(d: Device = device()): Help {
        val steps = when (family(d)) {
            "samsung" -> listOf(
                "Settings → Apps → Bas 1 Minute → Battery → choose Unrestricted.",
                "Settings → Battery → Background usage limits: make sure Bas 1 Minute isn’t in Sleeping or Deep sleeping apps.",
            )
            "oplus" -> listOf(
                "Settings → Apps → App management → Bas 1 Minute → Battery usage.",
                "Switch on Allow background activity (and Allow auto launch if you see it).",
            )
            "xiaomi" -> listOf(
                "Settings → Apps → Manage apps → Bas 1 Minute → Battery saver → choose No restrictions.",
                "In the same screen, switch on Autostart.",
            )
            "vivo" -> listOf(
                "Settings → Battery → Background power consumption management → Bas 1 Minute → Allow.",
                "Settings → Apps → Autostart: switch on Bas 1 Minute if listed.",
            )
            else -> listOf("Settings → Apps → Bas 1 Minute → Battery → choose Unrestricted.")
        }
        return Help(
            title = "Keep syncing in the background",
            steps = steps + "Without this, your phone may pause syncing until you open the app (nothing is lost).",
            searchQuery = "${d.brand} ${d.model} Android ${d.android} ${d.skin} allow app background activity battery unrestricted".replace(Regex("\\s+"), " ").trim(),
            aiQuestion = "I have a ${d.brand} phone, model ${d.model}, Android ${d.android}" + (if (d.skin.isNotBlank()) " (${d.skin})" else "") +
                ". How do I stop battery saving from pausing one app (Bas 1 Minute) in the background? Please give the exact menu names on my phone.",
        )
    }
}
