/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.grewal.notgamemode

import android.content.Context

class GamePrefs(context: Context) {

    private val prefs =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var customPackages: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM, emptySet())!!.toSet()
        private set(value) = prefs.edit().putStringSet(KEY_CUSTOM, value).apply()

    fun addCustom(pkg: String) {
        customPackages = customPackages.toMutableSet().apply { add(pkg) }
    }

    fun removeCustom(pkg: String) {
        customPackages = customPackages.toMutableSet().apply { remove(pkg) }
    }

    fun isEnabled(pkg: String) = prefs.getBoolean(enabledKey(pkg), false)

    fun isSuperReport(pkg: String) = prefs.getBoolean(superKey(pkg), true)

    fun tuningValue(pkg: String, mode: Int, default: Int) =
        prefs.getInt(tuneKey(pkg, mode), default)

    fun isExpert(pkg: String) = prefs.getBoolean(expertKey(pkg), false)

    fun expertPreset(pkg: String) =
        prefs.getInt(expertPresetKey(pkg), TouchFeatureManager.EXPERT_RANGE.def)

    /** One of the ThermalProfile.MODE_* values. */
    val thermalMode: String
        get() = prefs.getString(KEY_THERMAL_MODE, ThermalProfile.MODE_AUTO)!!

    companion object {
        const val NAME = "game_mode"
        const val KEY_THERMAL_MODE = "thermal_mode"
        private const val KEY_CUSTOM = "custom_packages"

        fun enabledKey(pkg: String) = "enabled_$pkg"

        fun superKey(pkg: String) = "super_$pkg"

        fun tuneKey(pkg: String, mode: Int) = "tune_${pkg}_$mode"

        fun expertKey(pkg: String) = "expert_$pkg"

        fun expertPresetKey(pkg: String) = "expertpreset_$pkg"
    }
}
