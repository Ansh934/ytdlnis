package com.deniscerri.ytdl.ui.more.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar
import java.util.concurrent.TimeUnit


class DownloadSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.downloads

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.downloading_preferences, rootKey)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        findPreference<Preference>("prevent_duplicate_downloads")?.apply {
            //TODO transitioning code, delete after couple releases
            if (preferences.getBoolean("download_archive", false)){
                preferences.edit(commit = true){
                    putBoolean("download_archive", false).apply()
                    putString("prevent_duplicate_downloads", "download_archive")
                }
            }
        }

        val rememberDownloadType = findPreference<SwitchPreferenceCompat>("remember_download_type")
        val downloadType = findPreference<ListPreference>("preferred_download_type")
        downloadType?.isEnabled = rememberDownloadType?.isChecked == false
        rememberDownloadType?.setOnPreferenceClickListener {
            downloadType?.isEnabled = !rememberDownloadType.isChecked
            true
        }

        val useScheduler = findPreference<SwitchPreferenceCompat>("use_scheduler")
        val scheduleStart = findPreference<Preference>("schedule_start")
        scheduleStart?.summary = preferences.getString("schedule_start", "00:00")
        val scheduleEnd = findPreference<Preference>("schedule_end")
        scheduleEnd?.summary = preferences.getString("schedule_end", "05:00")
        val scheduler = AlarmScheduler(requireContext())

        useScheduler?.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean){
                scheduler.schedule()
            }else{
                scheduler.cancel()
                //start worker if there are leftover downloads waiting for scheduler
                val workConstraints = Constraints.Builder()
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .addTag("download")
                    .setConstraints(workConstraints.build())
                    .setInitialDelay(1000L, TimeUnit.MILLISECONDS)

                WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.REPLACE,
                    workRequest.build()
                )
            }
            true
        }

        scheduleStart?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager){
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_start",formattedTime).apply()
                scheduleStart.summary = formattedTime

                scheduler.schedule()
            }
            true
        }

        scheduleEnd?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager){
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_end",formattedTime).apply()
                scheduleEnd.summary = formattedTime

                scheduler.schedule()
            }
            true
        }
    }

}