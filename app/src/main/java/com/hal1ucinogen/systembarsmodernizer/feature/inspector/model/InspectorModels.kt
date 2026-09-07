package com.hal1ucinogen.systembarsmodernizer.feature.inspector.model

import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import kotlinx.serialization.Serializable

@Serializable
data class ScreenRect(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

@Serializable
data class InspectorViewNode(
    val className: String,
    val fullClassName: String,
    val id: Int,
    val idEntryName: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val visibility: Int = 0, // View.VISIBLE = 0, INVISIBLE = 4, GONE = 8
    val screenBounds: ScreenRect = ScreenRect(),
    val paddingTop: Int = 0,
    val paddingBottom: Int = 0,
    val paddingStart: Int = 0,
    val paddingEnd: Int = 0,
    val marginTop: Int = 0,
    val marginBottom: Int = 0,
    val marginStart: Int = 0,
    val marginEnd: Int = 0,
    val isDecor: Boolean = false,
    val isDecorChild: Boolean = false,
    val isContent: Boolean = false,
    val childIndex: Int = -1,
    val depth: Int = 0,
    val children: List<InspectorViewNode> = emptyList()
)

@Serializable
data class InspectorReport(
    val packageName: String,
    val activityName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusBarHeight: Int = 0,
    val navBarHeight: Int = 0,
    val intentAction: String? = null,
    val intentData: String? = null,
    val intentFlags: Int = 0,
    val intentCategories: List<String> = emptyList(),
    val intentExtras: Map<String, String> = emptyMap(),
    val viewTree: InspectorViewNode
)

@Serializable
data class RuleSuggestion(
    val id: String,
    val title: String,
    val description: String,
    val confidence: Float, // 0.0 ~ 1.0
    val targetViewId: String,
    val suggestedAction: ExtraAction
)
