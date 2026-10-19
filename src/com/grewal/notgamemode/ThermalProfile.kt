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
import java.util.zip.ZipFile

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

    // Many games do not declare appCategory="game", so also look for a game engine's native
    // library or Play Games Services
    private const val PLAY_GAMES_APP_ID = "com.google.android.gms.games.APP_ID"
    private val ENGINE_LIBS =
        setOf(
            "libunity.so",
            "libil2cpp.so",
            "libUE4.so",
            "libUnreal.so",
            "libgodot_android.so",
        )
    private const val COCOS_LIB_PREFIX = "libcocos"

    private var cameraPackages: Set<String>? = null
    // Package -> (sourceDir, looks like a game); sourceDir changes on every update
    private val engineGames = HashMap<String, Pair<String, Boolean>>()
    private var written: Int? = null

    fun isAvailable() = File(SCONFIG).canWrite()

    /** The scenario for [pkg] in automatic mode; [isGame] covers apps enabled for game mode. */
    fun forPackage(context: Context, pkg: String?, isGame: Boolean): Int {
        if (pkg == null) return NORMAL
        if (isGame) return GAME
        if (pkg in cameraPackages(context)) return CAMERA
        val info =
            try {
                context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            } catch (e: PackageManager.NameNotFoundException) {
                return NORMAL
            }
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> GAME
            ApplicationInfo.CATEGORY_VIDEO -> VIDEO
            ApplicationInfo.CATEGORY_MAPS -> NAVIGATION
            else -> if (looksLikeGame(info)) GAME else NORMAL
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

    private fun looksLikeGame(info: ApplicationInfo): Boolean {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return false
        val sourceDir = info.sourceDir ?: return false
        engineGames[info.packageName]?.let { (dir, isGame) ->
            if (dir == sourceDir) return isGame
        }
        val isGame =
            info.metaData?.containsKey(PLAY_GAMES_APP_ID) == true || hasEngineLib(info)
        engineGames[info.packageName] = sourceDir to isGame
        if (isGame) Log.i(TAG, "${info.packageName} looks like a game")
        return isGame
    }

    private fun isEngineLib(name: String) =
        name in ENGINE_LIBS || (name.startsWith(COCOS_LIB_PREFIX) && name.endsWith(".so"))

    private fun hasEngineLib(info: ApplicationInfo): Boolean {
        // Extracted libraries, for apps installed with extractNativeLibs
        if (info.nativeLibraryDir?.let { File(it).list() }?.any(::isEngineLib) == true) {
            return true
        }
        // Otherwise they stay in the APKs (the ABI split for bundles); only the zip's
        // central directory is read
        val apks = listOf(info.sourceDir) + (info.splitSourceDirs ?: emptyArray())
        return apks.any { apk ->
            try {
                ZipFile(apk).use { zip ->
                    zip.entries().asSequence().any {
                        it.name.startsWith("lib/") && isEngineLib(it.name.substringAfterLast('/'))
                    }
                }
            } catch (e: Exception) {
                // Cached as "not a game" like any other answer, so a bad APK is not retried
                Log.w(TAG, "Cannot read $apk", e)
                false
            }
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
