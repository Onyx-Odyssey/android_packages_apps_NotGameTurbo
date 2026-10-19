/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.grewal.notgamemode

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Picks the mi_thermald scenario for the foreground app by writing thermal_message/sconfig.
 *
 * The numbers are the stock HyperOS scenarios (PowerKeeper CollectionData scenario map); the
 * profiles behind them are Xiaomi's own thermal configs, and mi_thermald keeps enforcing them.
 */
object ThermalProfile {
    private const val TAG = "ThermalProfile"
    // Real path: the /sys/class/thermal link would need read access to more sysfs_thermal
    private const val SCONFIG = "/sys/devices/virtual/thermal/thermal_message/sconfig"

    const val NORMAL = 0
    // "evaluation" in the stock map: Xiaomi's benchmark scenario, the loosest one
    const val NO_LIMITS = 6
    const val NAVIGATION = 10
    const val VIDEO = 11
    const val CAMERA = 15
    const val GAME = 19

    /** Settings values for [GamePrefs.thermalMode]. */
    const val MODE_AUTO = "auto"
    const val MODE_NORMAL = "normal"
    const val MODE_GAME = "game"
    /** Reset to [MODE_AUTO] on every boot. */
    const val MODE_NO_LIMITS = "nolimits"

    private var cameraPackages: Set<String>? = null
    private var written: Int? = null

    fun isAvailable() = File(SCONFIG).canWrite()

    /** The scenario for [pkg] in automatic mode; [isGame] covers apps enabled for game mode. */
    fun forPackage(context: Context, pkg: String?, isGame: Boolean): Int {
        if (pkg == null) return NORMAL
        if (isGame) return GAME
        if (pkg in cameraPackages(context)) return CAMERA
        val info =
            try {
                context.packageManager.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return NORMAL
            }
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> GAME
            ApplicationInfo.CATEGORY_VIDEO -> VIDEO
            ApplicationInfo.CATEGORY_MAPS -> NAVIGATION
            else -> NORMAL
        }
    }

    /** Writes [scenario] unless it is already the last value written. */
    fun apply(scenario: Int) {
        if (scenario == written) return
        try {
            File(SCONFIG).writeText(scenario.toString())
            written = scenario
            Log.i(TAG, "sconfig $scenario")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sconfig $scenario", e)
        }
    }

    private fun cameraPackages(context: Context): Set<String> {
        cameraPackages?.let {
            return it
        }
        val pm = context.packageManager
        val packages =
            listOf(
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
                    MediaStore.INTENT_ACTION_VIDEO_CAMERA,
                    MediaStore.ACTION_IMAGE_CAPTURE,
                )
                .flatMap { action ->
                    pm.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
                }
                .map { it.activityInfo.packageName }
                .toSet()
        cameraPackages = packages
        return packages
    }
}
