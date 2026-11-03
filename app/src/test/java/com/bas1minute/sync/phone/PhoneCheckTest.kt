package com.bas1minute.sync.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Run: ./gradlew test */
class PhoneCheckTest {
    private val H = 3_600_000L
    private val MB = 1024L * 1024
    private val now = 100 * 24 * H

    /** 24 h of samples every 15 min: screen on 8–10 and 18–21, drain on 10%/h, off 0.8%/h (or [off]). */
    private fun day(off: Double = 0.8): Pair<List<BatterySample>, List<Pair<Long, Long>>> {
        val start = now - 24 * H
        val on = listOf(start + 8 * H to start + 10 * H, start + 18 * H to start + 21 * H)
        var level = 100.0
        val out = ArrayList<BatterySample>()
        var t = start
        while (t <= now) {
            out += BatterySample(t, level.toInt().coerceAtLeast(0), false, 31f)
            val isOn = on.any { t >= it.first && t < it.second }
            level -= (if (isOn) 10.0 else off) / 4
            t += 15 * 60_000L
        }
        return out to on
    }

    @Test fun intervals() {
        assertEquals(listOf(0L to 10L, 12L to 20L), PhoneCheck.merge(listOf(0L to 5L, 3L to 10L, 12L to 20L)))
        assertEquals(6L, PhoneCheck.minus(listOf(0L to 10L), listOf(2L to 6L)))
        assertEquals(10L, PhoneCheck.total(listOf(0L to 5L, 5L to 10L)))
    }

    @Test fun batteryRatesSplitScreenOnAndOff() {
        val (s, on) = day()
        val b = PhoneCheck.battery(s, on, now)
        assertTrue("off ${b.offRate}", abs(b.offRate!! - 0.8) < 0.2)
        assertTrue("on ${b.onRate}", abs(b.onRate!! - 10.0) < 1.5)
        assertTrue(abs(b.screenOnHours - 5.0) < 0.01)
        assertTrue(b.dischargedPct in 60.0..72.0)
    }

    @Test fun fastScreenOffDrainIsFlagged() {
        val (s, on) = day(off = 2.8)
        val f = PhoneCheck.findings(emptyList(), PhoneCheck.battery(s, on, now), now, true)
        assertEquals(Severity.High, f.first { it.id == "drain_off" }.severity)
        val ok = PhoneCheck.findings(emptyList(), PhoneCheck.battery(day().first, on, now), now, true)
        assertTrue(ok.none { it.id == "drain_off" })
    }

    @Test fun chargingIsCountedNotDrain() {
        val s = listOf(BatterySample(now - 3 * H, 40, false), BatterySample(now - 2 * H, 30, false),
            BatterySample(now - H, 80, true), BatterySample(now, 78, false))
        val b = PhoneCheck.battery(s, emptyList(), now)
        assertEquals(1, b.charges); assertEquals(50.0, b.chargedPct, 0.01); assertEquals(12.0, b.dischargedPct, 0.01)
    }

    @Test fun appBatteryScalesToMeasuredDrop() {
        val (s, on) = day()
        val b = PhoneCheck.battery(s, on, now)
        val apps = listOf(
            AppUse("yt", "YouTube", foregroundMs = 3 * H, mobileFg = 600 * MB),
            AppUse("wa", "WhatsApp", foregroundMs = 2 * H, backgroundMs = H, mobileBg = 30 * MB),
        )
        val rows = PhoneCheck.appBattery(apps, b)
        assertEquals("YouTube", rows.first().label)
        assertTrue(abs(rows.sumOf { it.pct } - b.dischargedPct) < 1.0)
        assertTrue(rows.any { it.pkg == null })   // idle row
    }

    @Test fun appAnomalies() {
        val day = 24 * H
        val apps = listOf(
            AppUse("bad.vpn", "FreeVPN", foregroundMs = 0, backgroundMs = 10 * H, mobileBg = 250 * MB, unrestricted = true),
            AppUse("old.cam", "OldCam", lastUsedMs = now - 47 * day, perms = setOf(Perm.Camera, Perm.Microphone)),
            AppUse("loan.apk", "QuickLoan", sideloaded = true, perms = setOf(Perm.Sms, Perm.Contacts), special = setOf(Special.NotificationAccess)),
            AppUse("maps", "Maps", foregroundMs = H, perms = setOf(Perm.BackgroundLocation, Perm.Location), lastUsedMs = now),
            AppUse("music", "Music", foregroundMs = 30 * 60_000L, backgroundMs = 4 * H, lastUsedMs = now),
            AppUse("sys", "System", system = true, perms = setOf(Perm.Camera), lastUsedMs = now - 90 * day),
        )
        val f = PhoneCheck.findings(apps, BatteryReport(), now, true)
        val ids = f.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("bgdata_bad.vpn", "bgrun_bad.vpn", "unres_bad.vpn", "unused_old.cam",
            "special_NotificationAccess_loan.apk", "bgloc_maps", "bgrun_music")))
        assertEquals(Severity.High, f.first { it.id == "bgdata_bad.vpn" }.severity)
        assertEquals(Severity.High, f.first { it.id == "special_NotificationAccess_loan.apk" }.severity)
        assertEquals(Severity.Medium, f.first { it.id == "bgrun_music" }.severity)
        assertTrue("system apps aren't nagged about permissions", "unused_sys" !in ids)
        assertTrue(f.first().severity == Severity.High)
        assertTrue(f.first { it.id == "unused_old.cam" }.detail.contains("use your camera and use your microphone"))
        val score = PhoneCheck.score(f)
        assertTrue("score $score", score in 0..60)
        assertEquals(100, PhoneCheck.score(emptyList()))
    }

    @Test fun formatting() {
        assertEquals("250 MB", PhoneCheck.bytes(250 * MB)); assertEquals("1.5 GB", PhoneCheck.bytes(1536 * MB))
        assertEquals("2h 5m", PhoneCheck.hm(125 * 60_000L)); assertEquals("1.5%", PhoneCheck.pct(1.5))
    }
}
