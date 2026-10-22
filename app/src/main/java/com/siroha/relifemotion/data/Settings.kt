package com.siroha.relifemotion.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class AccessMode { NONROOT, SHIZUKU, ROOT }

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("relife_settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
        private set
    var dynamicColor by mutableStateOf(sp.getBoolean("dynamic", true)); private set
    var lowPreview by mutableStateOf(sp.getBoolean("lowPreview", true)); private set
    var demoMode by mutableStateOf(sp.getBoolean("demo", false)); private set
    var defaultDurSec by mutableStateOf(sp.getInt("durSec", 2)); private set
    var audioQuality by mutableStateOf(sp.getInt("audioQ", 1)); private set
    var accessMode by mutableStateOf(runCatching { AccessMode.valueOf(sp.getString("access", "NONROOT")!!) }.getOrDefault(AccessMode.NONROOT))
        private set
    var userName by mutableStateOf(sp.getString("userName", "Siroha")!!); private set
    var showGrid by mutableStateOf(false); private set

    fun applyThemeMode(v: ThemeMode) { themeMode = v; sp.edit().putString("theme", v.name).apply() }
    fun setDynamic(v: Boolean) { dynamicColor = v; sp.edit().putBoolean("dynamic", v).apply() }
    fun applyLowPreview(v: Boolean) { lowPreview = v; sp.edit().putBoolean("lowPreview", v).apply() }
    fun setDemo(v: Boolean) { demoMode = v; sp.edit().putBoolean("demo", v).apply() }
    fun setDefaultDur(v: Int) { defaultDurSec = v; sp.edit().putInt("durSec", v).apply() }
    fun applyAudioQuality(v: Int) { audioQuality = v; sp.edit().putInt("audioQ", v).apply() }
    fun applyAccessMode(v: AccessMode) { accessMode = v; sp.edit().putString("access", v.name).apply() }
    fun applyUserName(v: String) { userName = v; sp.edit().putString("userName", v).apply() }
}
