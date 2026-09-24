package com.igsave.node

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

class NetworkHelper(private val context: Context) {

    fun getCarrierName(): String {
        try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val operator = telephony?.networkOperatorName
            if (!operator.isNullOrEmpty()) {
                val netType = getNetworkGeneration()
                return "$operator $netType"
            }
        } catch (e: Exception) {}
        return "Cellular / Wi-Fi"
    }

    fun getNetworkGeneration(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Network"
        val activeNetwork = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Offline"

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Wi-Fi (LAN)"
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "5G / 4G LTE"
        }
        return "Online"
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
