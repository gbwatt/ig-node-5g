package com.igsave.node

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ig_node_prefs", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_GATEWAYS = listOf(
            "http://192.168.190.25/ig-save",
            "https://api.ig-save.com",
            "https://api.thumbnailify.com",
            "https://api.needsuite.com",
            "https://api.mbios.net"
        )
    }

    var nodeId: String
        get() {
            var id = prefs.getString("node_id", null)
            if (id == null) {
                id = "android_5g_" + (10000000..99999999).random().toString(16)
                prefs.edit().putString("node_id", id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString("node_id", value).apply()

    var customGateway: String
        get() = prefs.getString("custom_gateway", DEFAULT_GATEWAYS[0]) ?: DEFAULT_GATEWAYS[0]
        set(value) = prefs.edit().putString("custom_gateway", value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    var totalRequestsHandled: Int
        get() = prefs.getInt("requests_handled", 0)
        set(value) = prefs.edit().putInt("requests_handled", value).apply()

    fun getAllGateways(): List<String> {
        val list = mutableListOf<String>()
        val custom = customGateway.trim()
        if (custom.isNotEmpty() && !list.contains(custom)) {
            list.add(custom)
        }
        for (g in DEFAULT_GATEWAYS) {
            if (!list.contains(g)) {
                list.add(g)
            }
        }
        return list
    }
}
