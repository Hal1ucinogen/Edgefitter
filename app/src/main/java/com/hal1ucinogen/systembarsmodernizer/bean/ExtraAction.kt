@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.hal1ucinogen.systembarsmodernizer.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SpacingType {
    PADDING,
    MARGIN
}

enum class InsetEdge {
    TOP,
    BOTTOM
}

enum class VisibilityMode {
    GONE,
    INVISIBLE,
    VISIBLE
}

@Serializable
sealed interface ViewAction {
    @Serializable
    @SerialName("inset")
    data class Inset(
        val spacingType: SpacingType = SpacingType.PADDING,
        val edge: InsetEdge = InsetEdge.BOTTOM,
        val useSystemInsets: Boolean = false,
        val customInset: Int = -1
    ) : ViewAction

    @Serializable
    @SerialName("visibility")
    data class Visibility(
        val mode: VisibilityMode = VisibilityMode.GONE,
        val collapseSize: Boolean = true
    ) : ViewAction
}

@Serializable
data class ExtraAction(
    val viewId: String,
    val isGroup: Boolean = false,
    val self: Boolean = true,
    val childIndex: Int = -1,
    val delay: Long = 100L,
    val routes: List<String> = emptyList(),
    val isRouteExclusive: Boolean = false,
    val routeKey: String? = null,
    val action: ViewAction = ViewAction.Inset()
)
