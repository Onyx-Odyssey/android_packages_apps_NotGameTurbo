/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.grewal.notgamemode

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import android.util.Log

class GameModeService : Service() {

    private lateinit var prefs: GamePrefs
    private lateinit var usageStats: UsageStatsManager
    private lateinit var power: PowerManager
    private lateinit var users: UserManager
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    private var lastForeground: String? = null
    private var gameModeActive = false
    private var activePkg: String? = null
    private var savedTuning: Map<Int, Int> = emptyMap()

    private val poll =
        object : Runnable {
            override fun run() {
                // This is a persistent process: one failed poll must not take it down
                try {
                    update()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "poll failed", e)
                }
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        TouchFeatureManager.attach(this)
        prefs = GamePrefs(this)
        usageStats = getSystemService(UsageStatsManager::class.java)
        power = getSystemService(PowerManager::class.java)
        users = getSystemService(UserManager::class.java)
        thread = HandlerThread(TAG).apply { start() }
        handler = Handler(thread.looper)
        handler.post(poll)
        Log.i(TAG, "started")
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        // On the poll thread, so it cannot race a poll that is still running
        handler.post {
            if (gameModeActive) {
                stopGameMode()
            }
        }
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update() {

        if (manualOverride) {
            return
        }

        // Usage events are not available before the first unlock after boot
        if (!users.isUserUnlocked) {
            return
        }

        val foreground = currentForegroundPackage() ?: lastForeground
        lastForeground = foreground

        val shouldEnable = foreground != null && prefs.isEnabled(foreground)
        updateThermal(foreground, shouldEnable)
        if (shouldEnable) {
            if (!gameModeActive) {
                gameModeActive = true
                activePkg = foreground

                saveTuning()
                applyTuning(foreground!!)
                TouchFeatureManager.setGameMode(true)
                setOrientationTracking(true)
            } else if (foreground != activePkg) {

                activePkg = foreground
                applyTuning(foreground!!)
            }
        } else if (gameModeActive) {
            stopGameMode()
        }
    }

    private fun updateThermal(foreground: String?, isGame: Boolean) {
        val scenario =
            when (prefs.thermalMode) {
                ThermalProfile.MODE_NORMAL -> ThermalProfile.NORMAL
                ThermalProfile.MODE_GAME -> ThermalProfile.GAME
                ThermalProfile.MODE_NO_LIMITS ->
                    if (power.isInteractive) ThermalProfile.NO_LIMITS else ThermalProfile.NORMAL
                // The last foreground app stays "foreground" with the screen off
                else ->
                    if (!power.isInteractive) ThermalProfile.NORMAL
                    else ThermalProfile.forPackage(this, foreground, isGame)
            }
        ThermalProfile.apply(scenario)
    }

    private fun saveTuning() {
        savedTuning =
            (TouchFeatureManager.TUNING_RANGES.keys + TouchFeatureManager.TOUCH_EXPERT_MODE)
                .mapNotNull { mode ->
                    TouchFeatureManager.queryMode(mode).cur?.let { mode to it }
                }
                .toMap()
    }

    private fun stopGameMode() {
        TouchFeatureManager.setGameMode(false)
        setOrientationTracking(false)
        savedTuning.forEach { (mode, value) -> TouchFeatureManager.setTuning(mode, value) }
        savedTuning = emptyMap()
        gameModeActive = false
        activePkg = null
    }

    private fun applyTuning(pkg: String) {
        if (prefs.isExpert(pkg)) {

            TouchFeatureManager.setTuning(
                TouchFeatureManager.TOUCH_EXPERT_MODE,
                prefs.expertPreset(pkg),
            )
            return
        }
        TouchFeatureManager.setTuning(TouchFeatureManager.TOUCH_EXPERT_MODE, 0)
        TouchFeatureManager.TUNING_RANGES.forEach { (mode, range) ->
            TouchFeatureManager.setTuning(mode, prefs.tuningValue(pkg, mode, range.def))
        }
    }

    private fun setOrientationTracking(enabled: Boolean) {
        val intent = Intent(this, TouchOrientationService::class.java)
        if (enabled) startService(intent) else stopService(intent)
    }

    private fun currentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        // queryEvents throws IllegalStateException while the user (or ShortcutService, which
        // finishes unlocking a little later) is still locked
        val events =
            try {
                usageStats.queryEvents(now - QUERY_WINDOW_MS, now)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "usage events unavailable: ${e.message}")
                return null
            } catch (e: SecurityException) {
                Log.w(TAG, "usage events denied: ${e.message}")
                return null
            } ?: return null
        val event = android.app.usage.UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (
                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                pkg = event.packageName
            }
        }
        return pkg
    }

    companion object {
        private const val TAG = "GameModeService"
        private const val POLL_INTERVAL_MS = 2000L
        private const val QUERY_WINDOW_MS = 10_000L

        @Volatile var manualOverride = false
    }
}
