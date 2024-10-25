/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments.settings

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class ExperimentalSettingsFragment : BaseSettingFragment(R.string.settings_experimental_settings,
    { ExperimentalSettingsTopFragment() })

class ExperimentalSettingsTopFragment : BasePreferenceFragment() {

    private lateinit var e: Exception
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    // Activity Result API 注册权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 用户授予权限，启用状态栏歌词功能
            enableStatusBarLyric()
        } else {
            // 用户拒绝权限，显示提示
            Toast.makeText(requireContext(), "未授予通知权限，无法启用状态栏歌词", Toast.LENGTH_LONG).show()
            disableLyricUISetting()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_experimental, rootKey)
        findPreference<Preference>("crash")!!.isVisible = BuildConfig.DEBUG
        if (BuildConfig.DEBUG)
            e = RuntimeException("skill issue")
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "crash" && BuildConfig.DEBUG) {
            throw IllegalArgumentException("I crashed your app >:)", e)
        }
        return super.onPreferenceTreeClick(preference)
        if (preference.key == "enable_status_bar_lyric") {
            // 检查权限状态并请求权限
            checkAndRequestNotificationPermission()
            return true
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    enableStatusBarLyric()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 如果用户之前拒绝了权限请求但没有选择“永不提醒”
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // 用户选择“永不提醒”，显示提示并关闭开关
                    prefs.edit().putBoolean("lyric_statusbarLyric", false).apply()
                    showPermissionDeniedMessageWithSettings()
                }
            }
        } else {
            enableStatusBarLyric()
        }
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "lyric_statusbarLyric") {
                val isLyricUIEnabled = prefs.getBoolean("lyric_statusbarLyric", true)
                if (isLyricUIEnabled) {
                    checkAndRequestNotificationPermission()
                } else {
                    disableLyricUISetting()
                }
            }
    }

    private fun disableLyricUISetting() {
        // 更新开关状态并通知UI
        prefs.edit().putBoolean("lyric_statusbarLyric", false).apply()
        findPreference<SwitchPreferenceCompat>("lyric_statusbarLyric")?.isChecked = false
        Toast.makeText(requireContext(),getString(R.string.settings_lyrics_StatusBarLyric_disable), Toast.LENGTH_SHORT).show()
    }

    private fun enableStatusBarLyric() {
        // 启用状态栏歌词的提示
        // 发送歌词的地方有单独判断
        Toast.makeText(requireContext(),getString(R.string.settings_lyrics_StatusBarLyric_enabled), Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionDeniedMessageWithSettings() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_lyrics_StatusBarLyric_permission_denied))
            .setMessage(getString(R.string.settings_lyrics_StatusBarLyric_open_permissions))
            .setPositiveButton(getString(R.string.settings_go_to_the_settings)) { _, _ ->
                // 跳转到应用的权限设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.settings_cancel), null)
            .show()
    }
}
