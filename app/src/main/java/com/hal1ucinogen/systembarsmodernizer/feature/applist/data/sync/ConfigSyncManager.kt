package com.hal1ucinogen.systembarsmodernizer.feature.applist.data.sync

import android.util.Log
import androidx.core.content.edit
import com.hal1ucinogen.systembarsmodernizer.CONFIG_PREF_NAME
import com.hal1ucinogen.systembarsmodernizer.SBMApp
import com.hal1ucinogen.systembarsmodernizer.bean.AppConfig
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ipc.InspectorIpc
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigSyncManager {

    private const val TAG = "ConfigSyncManager"

    val isServiceAvailable: Boolean
        get() = SBMApp.mService != null

    fun pushConfig(config: AppConfig): Boolean {
        val service = SBMApp.mService ?: run {
            Log.w(TAG, "Cannot push config: XposedService is null")
            return false
        }
        return runCatching {
            val prefs = service.getRemotePreferences(CONFIG_PREF_NAME)
            val json = Json.encodeToString(config)
            prefs.edit {
                putString(config.packageName, json)
            }
            Log.i(TAG, "Successfully pushed config for ${config.packageName}")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to push config for ${config.packageName}", e)
            false
        }
    }

    fun removeConfig(packageName: String): Boolean {
        val service = SBMApp.mService ?: run {
            Log.w(TAG, "Cannot remove config: XposedService is null")
            return false
        }
        return runCatching {
            val prefs = service.getRemotePreferences(CONFIG_PREF_NAME)
            prefs.edit {
                remove(packageName)
            }
            Log.i(TAG, "Successfully removed config for $packageName")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to remove config for $packageName", e)
            false
        }
    }

    fun pushAllConfigs(configs: List<AppConfig>): Boolean {
        val service = SBMApp.mService ?: run {
            Log.w(TAG, "Cannot push all configs: XposedService is null")
            return false
        }
        return runCatching {
            val prefs = service.getRemotePreferences(CONFIG_PREF_NAME)
            prefs.edit {
                configs.forEach { config ->
                    val json = Json.encodeToString(config)
                    putString(config.packageName, json)
                }
            }
            Log.i(TAG, "Successfully pushed ${configs.size} configs to LSPosed")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to push all configs", e)
            false
        }
    }

    fun clearAllConfigs(): Boolean {
        val service = SBMApp.mService ?: run {
            Log.w(TAG, "Cannot clear configs: XposedService is null")
            return false
        }
        return runCatching {
            val prefs = service.getRemotePreferences(CONFIG_PREF_NAME)
            prefs.edit {
                clear()
            }
            Log.i(TAG, "Successfully cleared all remote configs")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to clear remote configs", e)
            false
        }
    }

    fun setInspectorActive(active: Boolean): Boolean {
        val service = SBMApp.mService ?: run {
            Log.w(TAG, "Cannot set inspector active: XposedService is null")
            return false
        }
        return runCatching {
            val prefs = service.getRemotePreferences(CONFIG_PREF_NAME)
            prefs.edit {
                putBoolean(InspectorIpc.PREF_KEY_INSPECTOR_ACTIVE, active)
            }
            Log.i(TAG, "Successfully updated inspector active state to $active")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to update inspector active state to $active", e)
            false
        }
    }
}
