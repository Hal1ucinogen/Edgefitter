package com.hal1ucinogen.systembarsmodernizer.feature.inspector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hal1ucinogen.systembarsmodernizer.MainActivity
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.SBMApp
import com.hal1ucinogen.systembarsmodernizer.feature.applist.data.sync.ConfigSyncManager
import io.github.libxposed.service.XposedService
import com.hal1ucinogen.systembarsmodernizer.bean.AppConfig
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.PageConfig
import com.hal1ucinogen.systembarsmodernizer.database.SBMDatabase
import com.hal1ucinogen.systembarsmodernizer.database.entity.SBMItem
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ipc.InspectorIpc
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.InspectorBubbleView
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.InspectorPanelView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InspectorFloatingService : LifecycleService() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "channel_inspector"
        const val ACTION_STOP_SERVICE = "com.hal1ucinogen.systembarsmodernizer.ACTION_STOP_INSPECTOR"

        fun start(context: Context) {
            val intent = Intent(context, InspectorFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, InspectorFloatingService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var themedContext: Context
    private var bubbleView: InspectorBubbleView? = null
    private var panelView: InspectorPanelView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isPanelShowing = false

    private val handler = Handler(Looper.getMainLooper())
    private var captureTimeoutRunnable: Runnable? = null
    private var lastErrorMsg: String? = null
    private var errorRepeatCount = 1

    private val inspectResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == InspectorIpc.ACTION_INSPECT_RESULT) {
                val errorMsg = intent.getStringExtra(InspectorIpc.EXTRA_ERROR_MESSAGE)
                if (!errorMsg.isNullOrEmpty()) {
                    handleInspectError(errorMsg)
                    return
                }
                val payloadBytes = intent.getByteArrayExtra(InspectorIpc.EXTRA_PAYLOAD_BYTES)
                if (payloadBytes != null) {
                    handleInspectResult(payloadBytes)
                }
            }
        }
    }

    private val serviceStateListener = object : SBMApp.ServiceStateListener {
        override fun onServiceStateChanged(service: XposedService?) {
            if (service != null) {
                ConfigSyncManager.setInspectorActive(true)
            }
        }
    }

    override fun onCreate() {
        setTheme(R.style.Theme_SystemBarsModernizer)
        super.onCreate()
        themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_SystemBarsModernizer)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        ConfigSyncManager.setInspectorActive(true)
        SBMApp.addServiceStateListener(serviceStateListener, false)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        registerResultReceiver()
        initBubbleView()
        initPanelView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.inspector_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.inspector_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val appIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, InspectorFloatingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_inspect)
            .setContentTitle(getString(R.string.inspector_notification_title))
            .setContentText(getString(R.string.inspector_notification_desc))
            .setContentIntent(appIntent)
            .addAction(R.drawable.ic_close, getString(R.string.inspector_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerResultReceiver() {
        val filter = IntentFilter(InspectorIpc.ACTION_INSPECT_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inspectResultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(inspectResultReceiver, filter)
        }
    }

    private fun initBubbleView() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val bubbleWidth = (displayMetrics.widthPixels * 0.88f).toInt()
            .coerceIn((280 * density).toInt(), (340 * density).toInt())

        val bubbleParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = bubbleWidth
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START

            x = (displayMetrics.widthPixels - bubbleWidth) / 2
            y = (70 * density).toInt()
        }

        bubbleView = InspectorBubbleView(
            context = themedContext,
            windowManager = windowManager,
            layoutParams = bubbleParams,
            onInspectClick = { triggerInspect() },
            onCloseClick = { stopSelf() }
        )

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun initPanelView() {
        val displayMetrics = resources.displayMetrics
        val panelWidth = (displayMetrics.widthPixels * 0.92f).toInt()
        val panelHeight = (displayMetrics.heightPixels * 0.75f).toInt()

        panelParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = panelWidth
            height = panelHeight
            gravity = Gravity.CENTER
        }

        panelView = InspectorPanelView(
            context = themedContext,
            onRecapture = {
                hidePanel()
                triggerInspect()
            },
            onMinimize = { hidePanel() },
            onClose = { stopSelf() },
            onApplyAction = { pkg, actName, extraAction ->
                saveAndApplyAction(pkg, actName, extraAction)
            }
        )
    }

    private fun triggerInspect() {
        bubbleView?.setLoadingState()

        // Cancel previous timeout if any
        captureTimeoutRunnable?.let { handler.removeCallbacks(it) }

        // Send trigger broadcast
        val triggerIntent = Intent(InspectorIpc.ACTION_TRIGGER_INSPECT)
        sendBroadcast(triggerIntent)

        // Set timeout runnable (2.5s)
        val timeoutRunnable = Runnable {
            bubbleView?.setErrorState(getString(R.string.inspector_capture_timeout))
            vibrateErrorFeedback()
        }
        captureTimeoutRunnable = timeoutRunnable
        handler.postDelayed(timeoutRunnable, 2500L)
    }

    private fun handleInspectResult(bytes: ByteArray) {
        captureTimeoutRunnable?.let { handler.removeCallbacks(it) }
        captureTimeoutRunnable = null
        bubbleView?.setIdleState()
        lastErrorMsg = null
        errorRepeatCount = 1

        try {
            val report = InspectorIpc.decompress(bytes)
            vibrateFeedback()

            panelView?.bindReport(report)
            showPanel()
        } catch (e: Throwable) {
            bubbleView?.setErrorState("Decompress error: ${e.localizedMessage}")
            vibrateErrorFeedback()
        }
    }

    private fun handleInspectError(errorMsg: String) {
        captureTimeoutRunnable?.let { handler.removeCallbacks(it) }
        captureTimeoutRunnable = null
        vibrateErrorFeedback()

        val baseMsg = if (errorMsg.contains("No resumed activity", ignoreCase = true)) {
            getString(R.string.inspector_error_no_resumed_activity)
        } else {
            getString(R.string.inspector_error_generic, errorMsg)
        }

        val displayMsg = if (baseMsg == lastErrorMsg) {
            errorRepeatCount++
            "$baseMsg (x$errorRepeatCount)"
        } else {
            lastErrorMsg = baseMsg
            errorRepeatCount = 1
            baseMsg
        }

        bubbleView?.setErrorState(displayMsg)
    }

    private fun vibrateErrorFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 100, 80), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 80, 100, 80), -1)
        }
    }

    private fun showPanel() {
        if (!isPanelShowing) {
            isPanelShowing = true
            bubbleView?.let { bubble ->
                if (bubble.isAttachedToWindow) {
                    runCatching { windowManager.removeView(bubble) }
                }
            }
            panelView?.let { panel ->
                panelParams?.let { params ->
                    if (!panel.isAttachedToWindow) {
                        runCatching { windowManager.addView(panel, params) }
                    }
                }
            }
        }
    }

    private fun hidePanel() {
        if (isPanelShowing) {
            isPanelShowing = false
            panelView?.let { panel ->
                if (panel.isAttachedToWindow) {
                    runCatching { windowManager.removeView(panel) }
                }
            }
            bubbleView?.let { bubble ->
                bubble.setIdleState()
                if (!bubble.isAttachedToWindow) {
                    runCatching { windowManager.addView(bubble, bubble.layoutParams) }
                }
            }
        }
    }

    private fun vibrateFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun saveAndApplyAction(packageName: String, activityName: String, action: ExtraAction) {
        val sbmApp = application as? SBMApp ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = SBMDatabase.getDatabase(this@InspectorFloatingService).sbmItemDao()
            val existingItem = dao.getItemByPackageName(packageName)

            val currentConfig = existingItem?.config ?: AppConfig(
                packageName = packageName,
                configVersion = 1,
                scope = emptyMap()
            )

            val existingPageConfig = currentConfig.scope[activityName] ?: PageConfig(edgeToEdge = true)
            val updatedActions = (existingPageConfig.extraActions + action).distinctBy { it.viewId to it.childIndex }
            val newPageConfig = existingPageConfig.copy(extraActions = updatedActions)

            val newScope = currentConfig.scope.toMutableMap()
            newScope[activityName] = newPageConfig
            val updatedConfig = currentConfig.copy(scope = newScope)

            val updatedItem = if (existingItem != null) {
                existingItem.copy(
                    config = updatedConfig,
                    features = (if (updatedConfig.general != null) 1 else 0) + updatedConfig.scope.size,
                    lastUpdatedTime = System.currentTimeMillis()
                )
            } else {
                SBMItem(
                    label = packageName.substringAfterLast("."),
                    packageName = packageName,
                    versionName = "",
                    versionCode = 0L,
                    installedTime = System.currentTimeMillis(),
                    lastUpdatedTime = System.currentTimeMillis(),
                    isSystem = false,
                    targetApi = 35.toShort(),
                    features = (if (updatedConfig.general != null) 1 else 0) + updatedConfig.scope.size,
                    config = updatedConfig
                )
            }

            sbmApp.repository.saveItemConfig(updatedItem)
        }
    }

    override fun onDestroy() {
        SBMApp.removeServiceStateListener(serviceStateListener)
        ConfigSyncManager.setInspectorActive(false)
        runCatching {
            sendBroadcast(Intent(InspectorIpc.ACTION_DEACTIVATE_INSPECTOR))
        }
        captureTimeoutRunnable?.let { handler.removeCallbacks(it) }
        runCatching { unregisterReceiver(inspectResultReceiver) }
        bubbleView?.let {
            if (it.isAttachedToWindow) {
                runCatching { windowManager.removeView(it) }
            }
        }
        panelView?.let {
            if (it.isAttachedToWindow) {
                runCatching { windowManager.removeView(it) }
            }
        }
        super.onDestroy()
    }
}
