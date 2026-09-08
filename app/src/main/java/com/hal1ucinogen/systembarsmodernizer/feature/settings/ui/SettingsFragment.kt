package com.hal1ucinogen.systembarsmodernizer.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hal1ucinogen.systembarsmodernizer.BuildConfig
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.bean.AppConfig
import com.hal1ucinogen.systembarsmodernizer.bean.ConfigsBackup
import com.hal1ucinogen.systembarsmodernizer.database.SBMDatabase
import com.hal1ucinogen.systembarsmodernizer.database.entity.SBMItem
import com.hal1ucinogen.systembarsmodernizer.feature.applist.data.sync.ConfigSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.preference.PreferenceFragmentCompat
import rikka.insets.WindowInsetsHelper
import rikka.preference.SimpleMenuPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    private val jsonFormatter = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportConfigsToUri(uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importConfigsFromUri(uri)
        }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.clipToPadding = false
        rikka.insets.WindowInsetsHelper.attach(
            recyclerView,
            false,
            android.view.Gravity.BOTTOM,
            0,
            0
        )
        return recyclerView
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // 0. Launch Inspector
        findPreference<Preference>("pref_launch_inspector")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(ctx)
            ) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.inspector_permission_title)
                    .setMessage(R.string.inspector_permission_desc)
                    .setPositiveButton(R.string.inspector_permission_grant) { _, _ ->
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                        runCatching { startActivity(intent) }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            } else {
                com.hal1ucinogen.systembarsmodernizer.feature.inspector.service.InspectorFloatingService.start(ctx)
                Toast.makeText(ctx, R.string.inspector_notification_title, Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 1. Force Sync
        findPreference<Preference>("pref_force_sync_configs")?.setOnPreferenceClickListener {
            forceSyncConfigs()
            true
        }

        // 2. Export Configs
        findPreference<Preference>("pref_export_configs")?.setOnPreferenceClickListener {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("SBM_Configs_$dateStr.json")
            true
        }

        // 3. Import Configs
        findPreference<Preference>("pref_import_configs")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            true
        }

        // 4. Reset All Configs
        findPreference<Preference>("pref_reset_all_configs")?.setOnPreferenceClickListener {
            showResetAllDialog()
            true
        }

        // 5. Dark Mode
        findPreference<SimpleMenuPreference>("pref_dark_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val mode = (newValue as? String)?.toIntOrNull() ?: 0
            when (mode) {
                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            true
        }

        // 6. Version Info
        findPreference<Preference>("pref_app_version")?.summary =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • Target API 35"

        // 7. GitHub Link
        findPreference<Preference>("pref_github")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hal1ucinogen/Edgefitter"))
            runCatching { startActivity(intent) }
            true
        }

        // 8. License
        findPreference<Preference>("pref_license")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_license_title)
                .setMessage(
                    "Copyright 2026 Hal1ucinogen\n\n" +
                    "Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "you may not use this file except in compliance with the License.\n" +
                    "You may obtain a copy of the License at\n\n" +
                    "    http://www.apache.org/licenses/LICENSE-2.0\n\n" +
                    "Unless required by applicable law or agreed to in writing, software\n" +
                    "distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied."
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
    }

    private fun forceSyncConfigs() {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = SBMDatabase.getDatabase(appContext).sbmItemDao()
            val configuredItems = dao.getConfiguredItemsSync()
            val configs = configuredItems.mapNotNull { it.config }

            ConfigSyncManager.pushAllConfigs(configs)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                view?.let {
                    Snackbar.make(
                        it,
                        getString(R.string.msg_sync_success, configs.size),
                        Snackbar.LENGTH_SHORT
                    ).show()
                } ?: Toast.makeText(
                    requireContext(),
                    getString(R.string.msg_sync_success, configs.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun exportConfigsToUri(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = SBMDatabase.getDatabase(appContext).sbmItemDao()
                val configuredItems = dao.getConfiguredItemsSync()
                val configs = configuredItems.mapNotNull { it.config }

                val backup = ConfigsBackup(
                    version = 1,
                    timestamp = System.currentTimeMillis(),
                    configs = configs
                )
                val jsonString = jsonFormatter.encodeToString(backup)

                appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open output stream")

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), R.string.msg_export_success, Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.msg_export_failed, e.localizedMessage ?: "Unknown"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun importConfigsFromUri(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val content = appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("Unable to open input stream")

                val configs = runCatching {
                    jsonFormatter.decodeFromString<ConfigsBackup>(content).configs
                }.getOrElse {
                    jsonFormatter.decodeFromString<List<AppConfig>>(content)
                }

                val dao = SBMDatabase.getDatabase(appContext).sbmItemDao()
                val now = System.currentTimeMillis()

                for (config in configs) {
                    val existingItem = dao.getItemByPackageName(config.packageName)
                    val featureScore = (if (config.general != null) 1 else 0) + config.scope.size
                    if (existingItem != null) {
                        val newItem = existingItem.copy(
                            config = config,
                            features = featureScore,
                            lastUpdatedTime = now
                        )
                        dao.updateItem(newItem)
                    } else {
                        val newItem = SBMItem(
                            label = config.packageName.substringAfterLast("."),
                            packageName = config.packageName,
                            versionName = "",
                            versionCode = 0L,
                            installedTime = now,
                            lastUpdatedTime = now,
                            isSystem = false,
                            targetApi = 35.toShort(),
                            features = featureScore,
                            config = config
                        )
                        dao.insertItem(newItem)
                    }
                }

                ConfigSyncManager.pushAllConfigs(configs)

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.msg_import_success, configs.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.msg_import_failed, e.localizedMessage ?: "Unknown"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun showResetAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_reset_all_title)
            .setMessage(R.string.dialog_reset_all_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                resetAllConfigs()
            }
            .show()
    }

    private fun resetAllConfigs() {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = SBMDatabase.getDatabase(appContext).sbmItemDao()
            val allItems = dao.getAllItemsSync()
            val clearedItems = allItems.map { item ->
                if (item.config != null || item.features > 0) {
                    item.copy(config = null, features = 0)
                } else {
                    item
                }
            }
            dao.insertItems(clearedItems)
            ConfigSyncManager.clearAllConfigs()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                view?.let {
                    Snackbar.make(it, R.string.msg_reset_all_success, Snackbar.LENGTH_SHORT).show()
                } ?: Toast.makeText(requireContext(), R.string.msg_reset_all_success, Toast.LENGTH_SHORT).show()
            }
        }
    }
}