package com.hal1ucinogen.systembarsmodernizer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.hal1ucinogen.systembarsmodernizer.bean.AppConfig
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.PageConfig
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.bean.VisibilityMode
import com.hal1ucinogen.systembarsmodernizer.tool.Task
import com.hal1ucinogen.systembarsmodernizer.util.getNavigationHeight
import com.hal1ucinogen.systembarsmodernizer.util.getStatusHeight
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlinx.serialization.json.Json

class ModuleMain : XposedModule() {

    companion object {
        const val TAG = "Edgefitter"
    }

    val configMap = mutableMapOf<String, AppConfig>()
    private val activityConfigCache = java.util.WeakHashMap<Activity, PageConfig?>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: " + param.processName)
        log(Log.INFO, TAG, "framework: $frameworkName($frameworkVersionCode) API $apiVersion")

        val hasProp: (Long) -> Boolean = { prop -> frameworkProperties.and(prop) != 0L }
        log(Log.INFO, TAG, "system supported: " + hasProp(PROP_CAP_SYSTEM))
        log(Log.INFO, TAG, "remote supported: " + hasProp(PROP_CAP_REMOTE))
        log(Log.INFO, TAG, "api protection: " + hasProp(PROP_RT_API_PROTECTION))
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: " + param.packageName)
        log(Log.INFO, TAG, "default classloader is " + param.defaultClassLoader)
        log(Log.INFO, TAG, "----------")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        log(Log.INFO, TAG, "onPackageReady: " + param.packageName)
        log(Log.INFO, TAG, "app classloader is " + param.classLoader)
        log(Log.INFO, TAG, "app acf is " + param.appComponentFactory)
        log(Log.INFO, TAG, "module apk path: " + this.moduleApplicationInfo.sourceDir)
        log(Log.INFO, TAG, "----------")
        if (!param.isFirstPackage) return
        if (param.applicationInfo.processName.contains(":")) return
        try {
            val packageName = param.packageName
            val prefs = getRemotePreferences(CONFIG_PREF_NAME)
            val appConfigStr = prefs.getString(packageName, null)
            if (appConfigStr.isNullOrEmpty()) {
                log(Log.INFO, TAG, "Remote prefs: app config is null")
                return
            }
            log(Log.INFO, TAG, "Remote prefs: got config str - $appConfigStr")
            val remoteConfig = Json.decodeFromString<AppConfig>(appConfigStr)
            log(Log.INFO, TAG, "Remote prefs: app config - $remoteConfig")
            configMap[packageName] = remoteConfig
            val onCreateMethod = Activity::class.java.getDeclaredMethod(
                METHOD_ON_CREATE, Bundle::class.java
            )
            hook(onCreateMethod).intercept { c ->
                c.proceed()
                doOnActivityCreated(c)
            }

            listOf("setPadding", "setPaddingRelative").forEach { methodName ->
                val method = View::class.java.getDeclaredMethod(
                    methodName,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                hook(method).intercept { chain ->
                    handlePaddingIntercept(chain)
                }
            }

            val setVisibilityMethod = View::class.java.getDeclaredMethod(
                "setVisibility",
                Int::class.javaPrimitiveType
            )
            hook(setVisibilityMethod).intercept { chain ->
                handleVisibilityIntercept(chain)
            }

            val setLayoutParamsMethod = View::class.java.getDeclaredMethod(
                "setLayoutParams",
                ViewGroup.LayoutParams::class.java
            )
            hook(setLayoutParamsMethod).intercept { chain ->
                handleMarginIntercept(chain)
            }
        } catch (e: UnsupportedOperationException) {
            log(Log.INFO, TAG, "app config access failed", e)
        } catch (e: Exception) {
            log(Log.INFO, TAG, "app config decode failed", e)
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        log(Log.INFO, TAG, "onHotReloading")
        param.setSavedInstanceState("Hello from last generation")
        return super.onHotReloading(param)
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        log(Log.INFO, TAG, "onHotReloaded: ${param.processName}, ${param.oldHookHandles.size} old hooks")
        log(Log.INFO, TAG, "savedInstanceState: " + param.savedInstanceState)
        param.oldHookHandles.forEach { it.unhook() }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting, system classloader: " + param.classLoader)
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun getPageConfig(activity: Activity): PageConfig? {
        val config = configMap[activity.packageName] ?: return null
        val activityName = activity.javaClass.name
        val scope = config.scope

        // 1. 优先精确匹配
        var pageConfig = scope[activityName]

        // 2. 内部类/影子多任务类自动继承外部基类规则 (例如 XRiverActivity$App01 -> XRiverActivity)
        if (pageConfig == null && activityName.contains("$")) {
            val baseClassName = activityName.substringBefore("$")
            pageConfig = scope[baseClassName]
        }

        // 3. 通配符/前缀匹配 (支持 key 以 * 结尾，例如 "com.alipay.android.phone.msgboxapp.ui.activity.*")
        if (pageConfig == null) {
            pageConfig = scope.entries.firstOrNull { (key, _) ->
                key.endsWith("*") && activityName.startsWith(key.removeSuffix("*"))
            }?.value
        }

        // 4. 通用兜底规则 (GeneralConfig)
        if (pageConfig == null) {
            val general = config.general
            val isExclusive = general?.exclusive?.any { excl ->
                activityName == excl ||
                (activityName.contains("$") && activityName.substringBefore("$") == excl) ||
                (excl.endsWith("*") && activityName.startsWith(excl.removeSuffix("*")))
            } ?: false

            if (general == null || isExclusive) {
                return null
            } else {
                pageConfig = config.general.config
            }
        }
        return pageConfig
    }

    private fun View.findViewByEntryName(targetEntryName: String): View? {
        if (this.id != View.NO_ID) {
            val entryName = runCatching { this.resources.getResourceEntryName(this.id) }.getOrNull()
            if (entryName == targetEntryName) {
                return this
            }
        }
        if (this is ViewGroup) {
            for (i in 0 until this.childCount) {
                val found = this.getChildAt(i).findViewByEntryName(targetEntryName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun Activity.getIntentRoute(customKey: String): String? {
        val intent = this.intent ?: return null
        if (customKey == "data") return intent.dataString
        return runCatching { intent.getStringExtra(customKey) }.getOrNull()
            ?: runCatching { intent.extras?.get(customKey)?.toString() }.getOrNull()
    }

    private fun ExtraAction.isRouteMatch(activity: Activity): Boolean {
        if (routes.isEmpty()) return true
        if (routeKey.isNullOrBlank()) return true
        val route = activity.getIntentRoute(routeKey) ?: return isRouteExclusive
        log(Log.INFO, TAG, "Activity Intent Route | $route (key: $routeKey)")
        val matched = routes.any { route.contains(it) }
        return if (isRouteExclusive) !matched else matched
    }

    private fun handlePaddingIntercept(chain: XposedInterface.Chain): Any? {
        val view = chain.thisObject as? View ?: return chain.proceed()
        val activity = view.context.findActivity() ?: view.rootView?.context?.findActivity()
            ?: return chain.proceed()
        val pageConfig = getPageConfigFast(activity) ?: return chain.proceed()

        // Fast-Path: 如果页面无 Padding 规则直接放行
        if (pageConfig.extraActions.isEmpty()) return chain.proceed()
        val paddingActions = pageConfig.extraActions.filter {
            val act = it.action
            act is ViewAction.Inset && act.spacingType == SpacingType.PADDING && it.isRouteMatch(activity)
        }
        if (paddingActions.isEmpty()) return chain.proceed()

        val entryName = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        } else null

        val isDecor = view === activity.window.decorView
        val isDecorChild = view.parent === activity.window.decorView
        val decorChildIndex = if (isDecorChild) (view.parent as? ViewGroup)?.indexOfChild(view) else -1

        val action = paddingActions.firstOrNull { act ->
            if (act.viewId == "decor") {
                if (act.self) isDecor else (isDecorChild && act.childIndex == decorChildIndex)
            } else if (act.viewId == "content" || act.viewId == "android:id/content") {
                view.id == android.R.id.content
            } else {
                entryName != null && act.viewId == entryName
            }
        } ?: return chain.proceed()

        val insetAction = action.action as ViewAction.Inset
        val inset = if (insetAction.useSystemInsets) {
            if (insetAction.edge == InsetEdge.TOP) activity.getStatusHeight() else activity.getNavigationHeight()
        } else {
            insetAction.customInset
        }

        var top = chain.args[1] as Int
        var bottom = chain.args[3] as Int
        if (insetAction.edge == InsetEdge.TOP) {
            top = inset
        } else {
            bottom = inset
        }

        return chain.proceed(
            arrayOf(chain.args[0], top, chain.args[2], bottom)
        )
    }

    private fun handleVisibilityIntercept(chain: XposedInterface.Chain): Any? {
        val view = chain.thisObject as? View ?: return chain.proceed()
        val activity = view.context.findActivity() ?: view.rootView?.context?.findActivity()
            ?: return chain.proceed()
        val pageConfig = getPageConfigFast(activity) ?: return chain.proceed()

        // Fast-Path: 如果页面无 Visibility 规则直接放行
        if (pageConfig.extraActions.isEmpty()) return chain.proceed()
        val visibilityActions = pageConfig.extraActions.filter {
            it.action is ViewAction.Visibility && it.isRouteMatch(activity)
        }
        if (visibilityActions.isEmpty()) return chain.proceed()

        if (view.id == View.NO_ID) return chain.proceed()
        val entryName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            ?: return chain.proceed()

        val action = visibilityActions.firstOrNull { it.viewId == entryName }
            ?: return chain.proceed()

        val visAction = action.action as ViewAction.Visibility
        val targetVisibility = when (visAction.mode) {
            VisibilityMode.GONE -> View.GONE
            VisibilityMode.INVISIBLE -> View.INVISIBLE
            VisibilityMode.VISIBLE -> View.VISIBLE
        }

        return chain.proceed(arrayOf(targetVisibility))
    }

    private fun handleMarginIntercept(chain: XposedInterface.Chain): Any? {
        val view = chain.thisObject as? View ?: return chain.proceed()
        val params = chain.args[0] as? ViewGroup.MarginLayoutParams ?: return chain.proceed()
        val activity = view.context.findActivity() ?: view.rootView?.context?.findActivity()
            ?: return chain.proceed()
        val pageConfig = getPageConfigFast(activity) ?: return chain.proceed()

        // Fast-Path: 如果页面无 Margin 规则直接放行
        if (pageConfig.extraActions.isEmpty()) return chain.proceed()
        val marginActions = pageConfig.extraActions.filter {
            val act = it.action
            act is ViewAction.Inset && act.spacingType == SpacingType.MARGIN && it.isRouteMatch(activity)
        }
        if (marginActions.isEmpty()) return chain.proceed()

        val entryName = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        } else null

        val isDecor = view === activity.window.decorView
        val isDecorChild = view.parent === activity.window.decorView
        val decorChildIndex = if (isDecorChild) (view.parent as? ViewGroup)?.indexOfChild(view) else -1

        val action = marginActions.firstOrNull { act ->
            if (act.viewId == "decor") {
                if (act.self) isDecor else (isDecorChild && act.childIndex == decorChildIndex)
            } else if (act.viewId == "content" || act.viewId == "android:id/content") {
                view.id == android.R.id.content
            } else {
                entryName != null && act.viewId == entryName
            }
        } ?: return chain.proceed()

        val insetAction = action.action as ViewAction.Inset
        val inset = if (insetAction.useSystemInsets) {
            if (insetAction.edge == InsetEdge.TOP) activity.getStatusHeight() else activity.getNavigationHeight()
        } else {
            insetAction.customInset
        }

        if (insetAction.edge == InsetEdge.TOP) {
            params.topMargin = inset
        } else {
            params.bottomMargin = inset
        }

        return chain.proceed(arrayOf(params))
    }

    private fun getPageConfigFast(activity: Activity): PageConfig? {
        return activityConfigCache.getOrPut(activity) {
            getPageConfig(activity)
        }
    }

    private fun doOnActivityCreated(chain: XposedInterface.Chain) {
        val activity = chain.thisObject as? Activity ?: return
        val window = activity.window
        log(Log.INFO, TAG, "Activity onCreate | ${activity.javaClass.name}")
        log(Log.INFO, TAG, "Activity Intent data: ${activity.intent.data}, extras: ${activity.intent.extras?.keySet()?.associateWith { activity.intent.extras?.get(it) }}")
        val pageConfig = getPageConfigFast(activity) ?: return
        log(Log.INFO, TAG, "Activity ${activity.javaClass.name} config | $pageConfig")
        if (pageConfig.edgeToEdge) {
            pageConfig.windowBackgroundColor?.let {
                window.setBackgroundDrawable(
                    ColorDrawable(it)
                )
            }
            window.statusBarColor = Color.TRANSPARENT
            window.isStatusBarContrastEnforced = false
            window.decorView.post {
                window.navigationBarColor = Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
//                    activity.enableEdgeToEdgeCompat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else {
            Task.onMain(100) {
                if (pageConfig.uiModeWBC != null) {
                    val currentNightMode =
                        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val color = when (currentNightMode) {
                        Configuration.UI_MODE_NIGHT_NO -> pageConfig.uiModeWBC.first// Night mode is not active, we're using the light theme.
                        Configuration.UI_MODE_NIGHT_YES -> pageConfig.uiModeWBC.second // Night mode is active, we're using dark theme.
                        else -> pageConfig.uiModeWBC.second
                    }
                    window.setBackgroundDrawable(color.toDrawable())
                } else if (pageConfig.windowBackgroundColor != null) {
                    window.setBackgroundDrawable(pageConfig.windowBackgroundColor.toDrawable())
                }
                if (pageConfig.clearTranslucent) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                }
                window.statusBarColor = pageConfig.statusColor
                window.isStatusBarContrastEnforced = false
                window.decorView.post {
                    window.navigationBarColor = pageConfig.navigationColor
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }

        // View 级 ExtraAction 执行
        // TODO There is a inset change issue. Try to useViewCompat.setOnApplyWindowInsetsListener instead
        pageConfig.extraActions.filter { it.isRouteMatch(activity) }.forEach { extraAction ->
            log(Log.INFO, TAG, "In ExtraAction | $extraAction")
            Task.onMain(extraAction.delay) {
                val target = when (extraAction.viewId) {
                    "decor" -> {
                        val decor = window.decorView as? ViewGroup
                        if (extraAction.self) decor else decor?.getChildAt(extraAction.childIndex)
                    }
                    "content", "android:id/content" -> activity.findViewById<View>(android.R.id.content)
                    else -> {
                        if (extraAction.isGroup) {
                            val group = run {
                                val id = window.decorView.resources.getIdentifier(
                                    extraAction.viewId,
                                    "id",
                                    activity.packageName
                                )
                                val v = if (id != 0) activity.findViewById<ViewGroup>(id) else null
                                v ?: (window.decorView.findViewByEntryName(extraAction.viewId) as? ViewGroup)
                            }
                            if (group != null) {
                                log(Log.INFO, TAG, "Group | child count:${group.childCount}")
                                if (extraAction.self) {
                                    group
                                } else {
                                    group.children.elementAtOrNull(extraAction.childIndex)
                                }
                            } else null
                        } else {
                            val id = window.decorView.resources.getIdentifier(
                                extraAction.viewId,
                                "id",
                                activity.packageName
                            )
                            val v = if (id != 0) activity.findViewById<View>(id) else null
                            v ?: window.decorView.findViewByEntryName(extraAction.viewId)
                        }
                    }
                } ?: return@onMain

                log(Log.INFO, TAG, "Find Target - $target")
                log(
                    Log.INFO,
                    TAG,
                    "Target Padding - ${target.paddingTop}|${target.paddingBottom}|${target.paddingStart}|${target.paddingEnd}"
                )
                log(
                    Log.INFO,
                    TAG,
                    "Target Margin - ${target.marginTop}|${target.marginBottom}|${target.marginStart}|${target.marginEnd}"
                )
                when (val act = extraAction.action) {
                    is ViewAction.Visibility -> {
                        target.visibility = when (act.mode) {
                            VisibilityMode.GONE -> View.GONE
                            VisibilityMode.INVISIBLE -> View.INVISIBLE
                            VisibilityMode.VISIBLE -> View.VISIBLE
                        }
                        if (act.collapseSize) {
                            target.updateLayoutParams<ViewGroup.LayoutParams> {
                                height = 0
                            }
                        }
                    }
                    is ViewAction.Inset -> {
                        val inset = if (act.useSystemInsets) {
                            when (act.edge) {
                                InsetEdge.TOP -> activity.getStatusHeight()
                                InsetEdge.BOTTOM -> activity.getNavigationHeight()
                            }
                        } else {
                            act.customInset
                        }
                        when (act.spacingType) {
                            SpacingType.PADDING -> when (act.edge) {
                                InsetEdge.TOP -> target.updatePadding(top = inset)
                                InsetEdge.BOTTOM -> target.updatePadding(bottom = inset)
                            }
                            SpacingType.MARGIN -> when (act.edge) {
                                InsetEdge.TOP -> target.updateLayoutParams<ViewGroup.LayoutParams> {
                                    (this as? ViewGroup.MarginLayoutParams)?.topMargin = inset
                                }
                                InsetEdge.BOTTOM -> target.updateLayoutParams<ViewGroup.LayoutParams> {
                                    (this as? ViewGroup.MarginLayoutParams)?.bottomMargin = inset
                                }
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Task.onMain(1000) {
                log(Log.INFO, TAG, "=== View Hierarchy Dump for ${activity.javaClass.name} ===")
                dumpViewHierarchy(window.decorView)
                log(Log.INFO, TAG, "=== End of Dump for ${activity.javaClass.name} ===")
            }
        }
    }

    private fun dumpViewHierarchy(view: View, depth: Int = 0) {
        if (depth > 15) return
        val idName = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        } else null

        val indent = "  ".repeat(depth)
        val idStr = if (idName != null) " id=@id/$idName" else ""
        val padStr = "pad=[T:${view.paddingTop}, B:${view.paddingBottom}, S:${view.paddingStart}, E:${view.paddingEnd}]"
        val marginStr = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            " mar=[T:${it.topMargin}, B:${it.bottomMargin}, S:${it.marginStart}, E:${it.marginEnd}]"
        } ?: ""
        val visStr = when (view.visibility) {
            View.GONE -> " GONE"
            View.INVISIBLE -> " INVISIBLE"
            else -> ""
        }
        val sizeStr = " (${view.width}x${view.height})"

        log(Log.INFO, TAG, "$indent- [${view.javaClass.simpleName}]$idStr$sizeStr$visStr $padStr$marginStr")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpViewHierarchy(view.getChildAt(i), depth + 1)
            }
        }
    }
}