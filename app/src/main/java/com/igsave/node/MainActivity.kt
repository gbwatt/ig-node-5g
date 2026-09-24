package com.igsave.node

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private lateinit var config: AppConfig
    private lateinit var networkHelper: NetworkHelper

    private lateinit var statusBadge: TextView
    private lateinit var nodeIdText: TextView
    private lateinit var carrierText: TextView
    private lateinit var gatewayText: TextView
    private lateinit var latencyText: TextView
    private lateinit var reqsText: TextView
    private lateinit var actionBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateDashboard()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AppConfig(this)
        networkHelper = NetworkHelper(this)

        requestIgnoreBatteryOptimizations()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(48, 48, 48, 48)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "5G"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#06B6D4"))
                cornerRadius = 16f
            }
        }
        header.addView(logo)

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "IG-Node 5G"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        titleBox.addView(title)

        nodeIdText = TextView(this).apply {
            text = config.nodeId
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Node ID", config.nodeId))
                Toast.makeText(this@MainActivity, "Node ID copied!", Toast.LENGTH_SHORT).show()
            }
        }
        titleBox.addView(nodeIdText)
        header.addView(titleBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        statusBadge = TextView(this).apply {
            text = "Standby"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(24, 10, 24, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#334155"))
            }
        }
        header.addView(statusBadge)
        root.addView(header)

        // Spacer
        root.addView(View(this).apply { minimumHeight = 40 })

        // Big Action Button
        actionBtn = Button(this).apply {
            text = "🟢 START 5G NODE"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 24f
            }
            setPadding(0, 36, 0, 36)
            setOnClickListener {
                toggleService()
            }
        }
        root.addView(actionBtn)

        root.addView(View(this).apply { minimumHeight = 40 })

        // Metrics Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#334155"))
            }
            setPadding(36, 36, 36, 36)
        }

        carrierText = addMetricRow(card, "Mobile Carrier", networkHelper.getCarrierName())
        gatewayText = addMetricRow(card, "Target Gateway", config.customGateway)
        latencyText = addMetricRow(card, "Swarm Latency", "-- ms")
        reqsText = addMetricRow(card, "Jobs Handled", "${config.totalRequestsHandled} requests")

        root.addView(card)

        root.addView(View(this).apply { minimumHeight = 30 })

        // Settings / Custom Gateway Button
        val cfgBtn = Button(this).apply {
            text = "⚙️ Configure Gateway URL"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#334155"))
            }
            setOnClickListener {
                showGatewayDialog()
            }
        }
        root.addView(cfgBtn)

        val scroll = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scroll)
    }

    private fun addMetricRow(parent: LinearLayout, label: String, initialVal: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        val lbl = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        row.addView(lbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val v = TextView(this).apply {
            text = initialVal
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        }
        row.addView(v)
        parent.addView(row)
        return v
    }

    private fun toggleService() {
        val intent = Intent(this, NodeService::class.java)
        if (!NodeService.isRunning) {
            config.isServiceEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            config.isServiceEnabled = false
            stopService(intent)
        }
        updateDashboard()
    }

    private fun updateDashboard() {
        val running = NodeService.isRunning
        if (running) {
            statusBadge.text = "🟢 Active"
            statusBadge.setTextColor(Color.parseColor("#10B981"))
            (statusBadge.background as GradientDrawable).setStroke(2, Color.parseColor("#10B981"))

            actionBtn.text = "🛑 STOP NODE"
            (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#EF4444"))
        } else {
            statusBadge.text = "Standby"
            statusBadge.setTextColor(Color.parseColor("#94A3B8"))
            (statusBadge.background as GradientDrawable).setStroke(2, Color.parseColor("#334155"))

            actionBtn.text = "🟢 START 5G NODE"
            (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#10B981"))
        }

        carrierText.text = networkHelper.getCarrierName()
        gatewayText.text = NodeService.activeGateway.ifEmpty { config.customGateway }
        latencyText.text = if (NodeService.lastPingMs > 0) "${NodeService.lastPingMs} ms" else "-- ms"
        reqsText.text = "${config.totalRequestsHandled} requests"
    }

    private fun showGatewayDialog() {
        val input = EditText(this).apply {
            setText(config.customGateway)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Gateway Configuration")
            .setMessage("Set your central hub or local LAN gateway:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) {
                    config.customGateway = v
                    updateDashboard()
                    Toast.makeText(this, "Gateway updated!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Reset Default") { _, _ ->
                config.customGateway = AppConfig.DEFAULT_GATEWAYS[0]
                updateDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }
}
