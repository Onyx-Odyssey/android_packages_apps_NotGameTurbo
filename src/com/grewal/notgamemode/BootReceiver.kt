/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.grewal.notgamemode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        // No limits never survives a reboot
        val prefs = GamePrefs(context)
        if (prefs.thermalMode == ThermalProfile.MODE_NO_LIMITS) {
            prefs.thermalMode = ThermalProfile.MODE_AUTO
        }
        context.startService(Intent(context, GameModeService::class.java))
    }
}
