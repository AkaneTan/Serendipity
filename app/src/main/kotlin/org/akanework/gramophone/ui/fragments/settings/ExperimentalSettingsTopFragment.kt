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

import android.os.Bundle
import androidx.preference.Preference
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class ExperimentalSettingsFragment : BaseSettingFragment(R.string.settings_experimental_settings,
    { ExperimentalSettingsTopFragment() })

class ExperimentalSettingsTopFragment : BasePreferenceFragment() {

    private lateinit var e: Exception

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_experimental, rootKey)
        findPreference<Preference>("crash")!!.isVisible = BuildConfig.DEBUG
        if (BuildConfig.DEBUG)
            e = RuntimeException("skill issue")
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "crash" && BuildConfig.DEBUG) {
            throw IllegalArgumentException("I crashed your app >:)", e)
        }
        return super.onPreferenceTreeClick(preference)
    }
}
