/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.grewal.notgamemode

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class GameModeActivity : CollapsingToolbarBaseActivity() {

    companion object {
        private const val TAG = "GameModeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TouchFeatureManager.attach(this)

        supportFragmentManager
            .beginTransaction()
            .replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                GameModeFragment(),
                TAG,
            )
            .commit()

        startService(Intent(this, GameModeService::class.java))

        checkTouchFeature()
    }

    private fun checkTouchFeature() {
        Thread {
                val backend = TouchFeatureManager.backend
                val available = TouchFeatureManager.isAvailable()
                val alternatives =
                    if (available) emptyList()
                    else
                        TouchFeatureManager.alternativesTo(backend).filter {
                            TouchFeatureManager.isAvailable(it)
                        }
                runOnUiThread {
                    if (!available && !isFinishing && !isDestroyed) {
                        showBackendError(backend, alternatives)
                    }
                }
            }
            .start()
    }

    private fun showBackendError(backend: Backend, alternatives: List<Backend>) {
        val builder =
            AlertDialog.Builder(this)
                .setTitle(titleOf(backend))
                .setMessage(messageOf(backend))
                .setCancelable(false)
                .setPositiveButton(R.string.retry) { _, _ -> checkTouchFeature() }
                .setNegativeButton(R.string.exit) { _, _ -> finish() }

        alternatives.firstOrNull()?.let { alternative ->
            builder.setNeutralButton(switchLabelOf(alternative)) { _, _ ->
                TouchFeatureManager.backend = alternative
                Toast.makeText(this, switchedLabelOf(alternative), Toast.LENGTH_SHORT).show()
                checkTouchFeature()
            }
        }

        builder.show()
    }

    private fun titleOf(backend: Backend) =
        when (backend) {
            Backend.HAL -> R.string.touchfeature_error_title
            Backend.SYSFS -> R.string.sysfs_error_title
        }

    private fun messageOf(backend: Backend) =
        when (backend) {
            Backend.HAL -> R.string.touchfeature_error_message
            Backend.SYSFS -> R.string.sysfs_error_message
        }

    private fun switchLabelOf(backend: Backend) =
        when (backend) {
            Backend.HAL -> R.string.backend_switch_hal
            Backend.SYSFS -> R.string.backend_switch_sysfs
        }

    private fun switchedLabelOf(backend: Backend) =
        when (backend) {
            Backend.HAL -> R.string.backend_switched_hal
            Backend.SYSFS -> R.string.backend_switched_sysfs
        }
}
