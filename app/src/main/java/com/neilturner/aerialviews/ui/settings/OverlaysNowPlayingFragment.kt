package com.neilturner.aerialviews.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.SonosPrefs
import com.neilturner.aerialviews.services.sonos.SonosClient
import com.neilturner.aerialviews.services.sonos.SonosDiscovery
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverlaysNowPlayingFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_nowplaying, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Now Playing", this)
        checkPermission()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("nowplaying_permission") &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            openNotificationSettings()
        }

        if (preference.key == "sonos_discover") {
            discoverSonosSpeakers()
            return true
        }

        if (preference.key == "sonos_test") {
            testSonosConnection()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private var discoveryJob: Job? = null

    private fun discoverSonosSpeakers() {
        val discoverPref = findPreference<Preference>("sonos_discover") ?: return

        if (!PermissionHelper.hasLocalNetworkPermission(requireContext())) {
            discoverPref.summary = getString(R.string.sonos_discover_no_permission)
            return
        }

        val ctx = requireContext()
        val discoveredDevices = mutableListOf<SonosDiscovery.SonosDevice>()

        // Spinner row
        val spinnerRow =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 8.dp(ctx)
                layoutParams = lp
            }
        val spinner =
            ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
                isIndeterminate = true
                val size = 20.dp(ctx)
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = 10.dp(ctx)
                layoutParams = lp
            }
        spinnerRow.addView(spinner)
        spinnerRow.addView(
            TextView(ctx).apply {
                text = getString(R.string.sonos_discover_searching)
            },
        )

        // Device list (items added as they're discovered)
        val devicesLayout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }

        // Empty state shown when scan ends with no results
        val noDevicesText =
            TextView(ctx).apply {
                text = getString(R.string.sonos_discover_no_devices)
                visibility = View.GONE
                val pad = 8.dp(ctx)
                setPadding(0, pad, 0, pad)
            }

        // Root view
        val root =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val h = 24.dp(ctx)
                val v = 8.dp(ctx)
                setPadding(h, v, h, v)
                addView(spinnerRow)
                addView(devicesLayout)
                addView(noDevicesText)
            }

        val dialog =
            AlertDialog
                .Builder(ctx)
                .setTitle(R.string.sonos_discover_dialog_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        discoveryJob?.cancel()
        discoveryJob =
            lifecycleScope.launch {
                SonosDiscovery.discoverFlow(ctx).collect { device ->
                    if (!isAdded) return@collect
                    discoveredDevices.add(device)
                    devicesLayout.addView(
                        buildDeviceItem(ctx, device) {
                            SonosPrefs.ipAddress = device.ip
                            findPreference<EditTextPreference>("sonos_ip_address")?.text = device.ip
                            discoverPref.summary = getString(R.string.sonos_discover_selected, device.name)
                            dialog.dismiss()
                        },
                    )
                }
                if (!isAdded) return@launch
                spinnerRow.visibility = View.GONE
                if (discoveredDevices.isEmpty()) noDevicesText.visibility = View.VISIBLE
            }

        dialog.setOnDismissListener { discoveryJob?.cancel() }
        dialog.show()
    }

    private fun buildDeviceItem(
        ctx: Context,
        device: SonosDiscovery.SonosDevice,
        onClick: () -> Unit,
    ): View {
        val selBg =
            TypedValue()
                .also {
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }.resourceId
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            if (selBg != 0) setBackgroundResource(selBg)
            setPadding(8.dp(ctx), 12.dp(ctx), 8.dp(ctx), 12.dp(ctx))
            setOnClickListener { onClick() }
            addView(TextView(ctx).apply { text = "${device.name} (${device.model})" })
            addView(
                TextView(ctx).apply {
                    text = device.ip
                    alpha = 0.7f
                    textSize = 12f
                },
            )
        }
    }

    private fun Int.dp(ctx: Context): Int = (this * ctx.resources.displayMetrics.density).toInt()

    private fun testSonosConnection() {
        val ip = SonosPrefs.ipAddress
        val testPref = findPreference<Preference>("sonos_test") ?: return

        if (ip.isBlank()) {
            testPref.summary = errorSummary(getString(R.string.sonos_test_no_ip))
            return
        }

        if (!PermissionHelper.hasLocalNetworkPermission(requireContext())) {
            testPref.summary = errorSummary(getString(R.string.sonos_test_no_local_network_permission))
            return
        }

        testPref.summary = getString(R.string.sonos_test_testing)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SonosClient.probe(ip) }
            if (!isAdded) return@launch
            testPref.summary =
                when (result) {
                    is SonosClient.ProbeResult.Unreachable -> errorSummary(result.message)
                    is SonosClient.ProbeResult.NotSonos -> errorSummary(getString(R.string.sonos_test_not_sonos))
                    is SonosClient.ProbeResult.Error -> errorSummary("Error: ${result.message}")
                    is SonosClient.ProbeResult.Connected -> okSummary(describeConnection(result))
                }
        }
    }

    private fun describeConnection(result: SonosClient.ProbeResult.Connected): String {
        val model =
            result.device.model
                .takeIf { it.isNotBlank() }
                ?.let { " ($it)" }
                .orEmpty()
        val track = result.track
        val statusLine =
            when (result.transportState) {
                SonosClient.STATE_PLAYING -> {
                    when {
                        track != null && track.title.isNotBlank() && track.artist.isNotBlank() -> "▶  ${track.artist} — ${track.title}"
                        track != null && track.title.isNotBlank() -> "▶  ${track.title}"
                        else -> "▶  ${getString(R.string.sonos_test_nothing_playing)}"
                    }
                }

                SonosClient.STATE_PAUSED -> {
                    "⏸  ${getString(R.string.sonos_test_paused)}"
                }

                SonosClient.STATE_STOPPED -> {
                    "⏹  ${getString(R.string.sonos_test_stopped)}"
                }

                else -> {
                    result.transportState
                }
            }
        return "${result.device.name}$model\n$statusLine"
    }

    private fun okSummary(text: String): SpannableString =
        SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.parseColor("#4CAF50")), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun errorSummary(text: String): SpannableString =
        SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.parseColor("#F44336")), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (ex: Exception) {
            Timber.e(ex, "Unable to open notification settings: ${ex.message}")
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                startActivity(intent)

                val toast = Toast.makeText(requireContext(), R.string.nowplaying_toast_text, Toast.LENGTH_LONG)
                toast.show()
            } catch (ex2: Exception) {
                Timber.e(ex2, "Unable to open manage application settings: ${ex2.message}")
            }
        }
    }

    private fun checkPermission() {
        val toggle = preferenceScreen.findPreference<SwitchPreference>("nowplaying_permission")
        val hasPermission = PermissionHelper.hasNotificationListenerPermission(requireContext())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toggle?.isEnabled = true
        }

        if (hasPermission) {
            toggle?.isChecked = true
            return
        } else {
            toggle?.isChecked = false
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            val notice = preferenceScreen.findPreference<Preference>("nowplaying_permission_legacy_notice")
            notice?.isVisible = true
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
        ) {
            val notice = preferenceScreen.findPreference<Preference>("nowplaying_permission_notice")
            notice?.isVisible = true
        }
    }
}
