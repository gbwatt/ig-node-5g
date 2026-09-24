package com.igsave.node

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NodeService : Service() {

    private val CHANNEL_ID = "ig_node_5g_channel"
    private val NOTIF_ID = 5001

    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var config: AppConfig
    private lateinit var networkHelper: NetworkHelper
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var igResolver: InstagramResolver

    private var activeGatewayIndex = 0

    companion object {
        var isRunning = false
            private set
        var lastPingMs = 0
            private set
        var activeGateway = ""
            private set
    }

    override fun onCreate() {
        super.onCreate()
        config = AppConfig(this)
        networkHelper = NetworkHelper(this)
        okHttpClient = InstagramResolver.createOkHttpClient()
        igResolver = InstagramResolver(okHttpClient)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            config.isServiceEnabled = true
            acquireWakeLock()

            val notif = buildNotification("IG-Node 5G Connecting...", "Joining residential swarm")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notif)
            }

            serviceJob = scope.launch {
                runSwarmLoop()
            }
        }
        return START_STICKY
    }

    private suspend fun runSwarmLoop() {
        registerWithHub()

        while (isRunning && scope.isActive) {
            try {
                pollAndExecuteJob()
                delay(2500)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.w("NodeService", "Poll error: ${e.message}")
                rotateGateway()
                delay(4000)
            }
        }
    }

    private fun getGateway(): String {
        val gateways = config.getAllGateways()
        val gw = gateways[activeGatewayIndex % gateways.size]
        activeGateway = gw
        return gw
    }

    private fun rotateGateway() {
        val gateways = config.getAllGateways()
        activeGatewayIndex = (activeGatewayIndex + 1) % gateways.size
        activeGateway = gateways[activeGatewayIndex % gateways.size]
        Log.i("NodeService", "Failing over to gateway: $activeGateway")
    }

    private fun registerWithHub() {
        val gateways = config.getAllGateways()
        val carrier = networkHelper.getCarrierName()
        val jsonType = "application/json; charset=utf-8".toMediaType()

        for (i in gateways.indices) {
            val gw = getGateway()
            val registerUrl = "$gw/api/fleet/register"
            val payload = JsonObject().apply {
                addProperty("id", config.nodeId)
                addProperty("type", "android_node")
                addProperty("name", "Android 5G (${config.nodeId.takeLast(4)})")
                addProperty("carrier", carrier)
                addProperty("ping_ms", 18)
            }

            val request = Request.Builder()
                .url(registerUrl)
                .post(payload.toString().toRequestBody(jsonType))
                .header("User-Agent", "IG-Node-Android-5G/1.0")
                .build()

            try {
                val t0 = System.currentTimeMillis()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        lastPingMs = (System.currentTimeMillis() - t0).toInt()
                        updateNotification("IG-Node 5G 🟢 Online", "$carrier | Ping: ${lastPingMs}ms")
                        Log.i("NodeService", "Registered with Fleet Hub at $gw")
                        return
                    }
                }
            } catch (e: Exception) {
                rotateGateway()
            }
        }
    }

    private fun pollAndExecuteJob() {
        val gw = getGateway()
        val pollUrl = "$gw/api/fleet/poll"
        val jsonType = "application/json; charset=utf-8".toMediaType()

        val payload = JsonObject().apply {
            addProperty("id", config.nodeId)
            addProperty("ping_ms", lastPingMs)
        }

        val request = Request.Builder()
            .url(pollUrl)
            .post(payload.toString().toRequestBody(jsonType))
            .header("User-Agent", "IG-Node-Android-5G/1.0")
            .build()

        val t0 = System.currentTimeMillis()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                lastPingMs = (System.currentTimeMillis() - t0).toInt()
                val body = response.body?.string() ?: return
                val json = JsonParser.parseString(body).asJsonObject

                if (json.has("has_job") && json.get("has_job").asBoolean) {
                    val job = json.getAsJsonObject("job")
                    handleJob(gw, job)
                }
            } else {
                rotateGateway()
            }
        }
    }

    private fun handleJob(gw: String, job: JsonObject) {
        val jobId = job.get("job_id").asString
        val url = job.get("url").asString

        Log.i("NodeService", "Incoming job $jobId: $url")
        val resolved = igResolver.resolve(url)

        val resultUrl = "$gw/api/fleet/result"
        val jsonType = "application/json; charset=utf-8".toMediaType()

        val resultPayload = JsonObject().apply {
            addProperty("job_id", jobId)
            addProperty("node_id", config.nodeId)
            addProperty("success", resolved != null && resolved.get("success").asBoolean)
            if (resolved != null) {
                add("items", resolved.get("items"))
                addProperty("shortcode", resolved.get("shortcode").asString)
            }
            addProperty("resolved_via", "Tier 3 - Android 5G Phone Node (Native Service)")
        }

        val submitReq = Request.Builder()
            .url(resultUrl)
            .post(resultPayload.toString().toRequestBody(jsonType))
            .header("User-Agent", "IG-Node-Android-5G/1.0")
            .build()

        try {
            okHttpClient.newCall(submitReq).execute().use {
                if (it.isSuccessful) {
                    config.totalRequestsHandled += 1
                    val carrier = networkHelper.getCarrierName()
                    updateNotification("IG-Node 5G 🟢 Online", "$carrier | Reqs: ${config.totalRequestsHandled}")
                    Log.i("NodeService", "Job $jobId submitted successfully!")
                }
            }
        } catch (e: Exception) {
            Log.e("NodeService", "Failed to submit job $jobId: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IGNode::WakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, msg: String): Notification {
        val stopIntent = Intent(this, MainActivity::class.java)
        val pStopIntent = PendingIntent.getActivity(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_node_notification)
            .setContentIntent(pStopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, msg: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(title, msg))
    }

    override fun onDestroy() {
        isRunning = false
        config.isServiceEnabled = false
        serviceJob?.cancel()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
