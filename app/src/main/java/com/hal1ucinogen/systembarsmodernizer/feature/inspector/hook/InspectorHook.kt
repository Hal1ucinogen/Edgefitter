package com.hal1ucinogen.systembarsmodernizer.feature.inspector.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.hal1ucinogen.systembarsmodernizer.CONFIG_PREF_NAME
import com.hal1ucinogen.systembarsmodernizer.METHOD_CALL_APPLICATION_ON_CREATE
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ipc.InspectorIpc
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorViewNode
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.ScreenRect
import com.hal1ucinogen.systembarsmodernizer.util.getNavigationHeight
import com.hal1ucinogen.systembarsmodernizer.util.getStatusHeight
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object InspectorHook {

    private const val TAG = "Edgefitter-Inspector"
    private const val MAX_DEPTH = 10

    private val isReceiverRegistered = AtomicBoolean(false)
    private var inspectorReceiver: BroadcastReceiver? = null
    private var registeredContext: WeakReference<Context>? = null
    private var xposedModuleRef: WeakReference<XposedModule>? = null

    fun init(module: XposedModule) {
        xposedModuleRef = WeakReference(module)
        val app = runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Application
        }.getOrNull()

        if (app != null) {
            ensureReceiverRegistered(app)
            return
        }

        try {
            val callApplicationOnCreateMethod = Instrumentation::class.java.getDeclaredMethod(
                METHOD_CALL_APPLICATION_ON_CREATE, Application::class.java
            )
            module.hook(callApplicationOnCreateMethod).intercept { chain ->
                val application = chain.args[0] as? Application
                if (application != null) {
                    ensureReceiverRegistered(application)
                }
                chain.proceed()
            }
        } catch (e: Throwable) {
            module.log(Log.WARN, TAG, "Failed to hook callApplicationOnCreate for inspector", e)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun ensureReceiverRegistered(context: Context) {
        if (isReceiverRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter().apply {
                addAction(InspectorIpc.ACTION_TRIGGER_INSPECT)
                addAction(InspectorIpc.ACTION_DEACTIVATE_INSPECTOR)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        InspectorIpc.ACTION_DEACTIVATE_INSPECTOR -> {
                            Log.i(TAG, "Received deactivation broadcast, unregistering receiver...")
                            unregisterReceiverSafely(ctx ?: context)
                        }
                        InspectorIpc.ACTION_TRIGGER_INSPECT -> {
                            val module = xposedModuleRef?.get()
                            val isActive = if (module != null) {
                                runCatching {
                                    val prefs = module.getRemotePreferences(CONFIG_PREF_NAME)
                                    prefs.getBoolean(InspectorIpc.PREF_KEY_INSPECTOR_ACTIVE, false)
                                }.getOrDefault(true)
                            } else true

                            if (!isActive) {
                                Log.i(TAG, "Inspector is inactive according to prefs, self-cleaning...")
                                unregisterReceiverSafely(ctx ?: context)
                                return
                            }

                            handleTriggerInspect(ctx ?: context)
                        }
                    }
                }
            }

            try {
                val appContext = context.applicationContext ?: context
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appContext.registerReceiver(receiver, filter)
                }
                inspectorReceiver = receiver
                registeredContext = WeakReference(appContext)
                Log.i(TAG, "Inspector broadcast receiver registered successfully")
            } catch (e: Throwable) {
                isReceiverRegistered.set(false)
                inspectorReceiver = null
                registeredContext = null
                Log.e(TAG, "Failed to register inspector receiver", e)
            }
        }
    }

    fun unregisterReceiverSafely(context: Context? = null) {
        if (isReceiverRegistered.compareAndSet(true, false)) {
            val ctx = context ?: registeredContext?.get()
            val r = inspectorReceiver
            if (ctx != null && r != null) {
                runCatching {
                    ctx.unregisterReceiver(r)
                    Log.i(TAG, "Inspector receiver cleanly unregistered")
                }.onFailure {
                    Log.w(TAG, "Failed to unregister inspector receiver", it)
                }
            }
            inspectorReceiver = null
            registeredContext = null
        }
    }

    private fun handleTriggerInspect(context: Context) {
        val activity = getForegroundActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "No resumed activity in this process (${context.packageName}), ignoring inspect trigger")
            return
        }

        activity.runOnUiThread {
            try {
                val report = captureActivityReport(activity)
                val compressedBytes = InspectorIpc.compress(report)

                val responseIntent = Intent(InspectorIpc.ACTION_INSPECT_RESULT).apply {
                    putExtra(InspectorIpc.EXTRA_PAYLOAD_BYTES, compressedBytes)
                }
                context.sendBroadcast(responseIntent)
                Log.i(TAG, "Inspector report sent successfully (${compressedBytes.size} bytes)")
            } catch (e: Throwable) {
                Log.e(TAG, "Error capturing inspector report", e)
                sendErrorResult(context, "Error capturing inspector report: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun getForegroundActivity(): Activity? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val activityThread = currentActivityThreadMethod.invoke(null) ?: return null

            val mActivitiesField = activityThreadClass.getDeclaredField("mActivities")
            mActivitiesField.isAccessible = true
            val activities = mActivitiesField.get(activityThread) as? Map<*, *> ?: return null

            var candidateResumed: Activity? = null
            var candidateVisible: Activity? = null

            for (record in activities.values) {
                if (record == null) continue
                val recordClass = record.javaClass

                val activityField = runCatching {
                    recordClass.getDeclaredField("activity").apply { isAccessible = true }
                }.getOrNull() ?: continue
                val activity = activityField.get(record) as? Activity ?: continue

                if (activity.isFinishing || activity.isDestroyed) continue

                val pausedField = runCatching {
                    recordClass.getDeclaredField("paused").apply { isAccessible = true }
                }.getOrNull()
                val isPaused = pausedField?.getBoolean(record) ?: false

                val lifecycleState = runCatching {
                    val method = recordClass.getDeclaredMethod("getLifecycleState")
                    method.isAccessible = true
                    method.invoke(record) as? Int
                }.getOrNull()

                // 3 corresponds to ON_RESUME in ActivityLifecycleItem
                val isResumed = (!isPaused) || (lifecycleState == 3)

                if (isResumed) {
                    candidateResumed = activity
                    break
                } else {
                    if (activity.window?.decorView?.visibility == View.VISIBLE) {
                        candidateVisible = activity
                    }
                }
            }

            candidateResumed ?: candidateVisible
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get foreground activity from ActivityThread", e)
            null
        }
    }

    private fun sendErrorResult(context: Context, errorMessage: String) {
        try {
            val responseIntent = Intent(InspectorIpc.ACTION_INSPECT_RESULT).apply {
                putExtra(InspectorIpc.EXTRA_ERROR_MESSAGE, errorMessage)
            }
            context.sendBroadcast(responseIntent)
            Log.w(TAG, "Sent inspector error broadcast: $errorMessage")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send inspector error broadcast", e)
        }
    }

    private fun captureActivityReport(activity: Activity): InspectorReport {
        val decorView = activity.window.decorView
        val viewTree = captureViewNode(decorView, decorView, depth = 0, childIndex = -1)

        val intent = activity.intent
        val intentAction = intent?.action
        val intentData = intent?.dataString
        val intentFlags = intent?.flags ?: 0
        val intentCategories = intent?.categories?.toList() ?: emptyList()
        val intentExtras = parseIntentExtras(intent?.extras)

        return InspectorReport(
            packageName = activity.packageName,
            activityName = activity.javaClass.name,
            timestamp = System.currentTimeMillis(),
            statusBarHeight = activity.getStatusHeight(),
            navBarHeight = activity.getNavigationHeight(),
            intentAction = intentAction,
            intentData = intentData,
            intentFlags = intentFlags,
            intentCategories = intentCategories,
            intentExtras = intentExtras,
            viewTree = viewTree
        )
    }

    private fun captureViewNode(
        view: View,
        decorView: View,
        depth: Int,
        childIndex: Int
    ): InspectorViewNode {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val screenBounds = ScreenRect(
            left = location[0],
            top = location[1],
            right = location[0] + view.width,
            bottom = location[1] + view.height
        )

        val idEntryName = safeGetResourceEntryName(view)
        val isDecor = view === decorView
        val isDecorChild = view.parent === decorView
        val isContent = view.id == android.R.id.content

        val marginParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        val marginTop = marginParams?.topMargin ?: 0
        val marginBottom = marginParams?.bottomMargin ?: 0
        val marginStart = marginParams?.marginStart ?: marginParams?.leftMargin ?: 0
        val marginEnd = marginParams?.marginEnd ?: marginParams?.rightMargin ?: 0

        val childrenList = mutableListOf<InspectorViewNode>()
        if (depth < MAX_DEPTH && view is ViewGroup) {
            val count = view.childCount
            for (i in 0 until count) {
                val child = view.getChildAt(i) ?: continue
                childrenList.add(captureViewNode(child, decorView, depth + 1, childIndex = i))
            }
        }

        return InspectorViewNode(
            className = view.javaClass.simpleName.ifEmpty { "View" },
            fullClassName = view.javaClass.name,
            id = view.id,
            idEntryName = idEntryName,
            width = view.width,
            height = view.height,
            visibility = view.visibility,
            screenBounds = screenBounds,
            paddingTop = view.paddingTop,
            paddingBottom = view.paddingBottom,
            paddingStart = view.paddingStart,
            paddingEnd = view.paddingEnd,
            marginTop = marginTop,
            marginBottom = marginBottom,
            marginStart = marginStart,
            marginEnd = marginEnd,
            isDecor = isDecor,
            isDecorChild = isDecorChild,
            isContent = isContent,
            childIndex = childIndex,
            depth = depth,
            children = childrenList
        )
    }

    private fun safeGetResourceEntryName(view: View): String? {
        val id = view.id
        if (id == View.NO_ID || id <= 0) return null
        return try {
            view.resources.getResourceEntryName(id)
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseIntentExtras(extras: Bundle?): Map<String, String> {
        if (extras == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            for (key in extras.keySet()) {
                val value = runCatching { extras.get(key) }.getOrNull()
                result[key] = value?.toString() ?: "null"
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error parsing extras bundle", e)
        }
        return result
    }
}
