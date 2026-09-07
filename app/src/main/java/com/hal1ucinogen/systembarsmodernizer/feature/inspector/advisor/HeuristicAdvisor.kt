package com.hal1ucinogen.systembarsmodernizer.feature.inspector.advisor

import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.bean.VisibilityMode
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorViewNode
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.RuleSuggestion
import kotlin.math.abs

object HeuristicAdvisor : InspectorAdvisor {

    override fun analyze(report: InspectorReport): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        val navBarH = report.navBarHeight
        val statusBarH = report.statusBarHeight

        // Candidate route keys from intent extras
        val candidateRouteKey = findCandidateRouteKey(report)

        fun traverse(node: InspectorViewNode) {
            val viewTargetId = when {
                node.isDecor -> "decor"
                node.isDecorChild -> "decor"
                node.isContent -> "content"
                !node.idEntryName.isNullOrEmpty() -> node.idEntryName
                else -> null
            }

            // 1. Check DecorView child #0 margin / padding
            if (node.isDecorChild && node.childIndex == 0) {
                if (navBarH > 0 && abs(node.marginBottom - navBarH) <= 4) {
                    suggestions.add(
                        RuleSuggestion(
                            id = "decor_child_0_bottom_margin",
                            title = "DecorView 底部外边距 (Margin) 消除",
                            description = "DecorView 首个子容器 (childIndex=0) 的底边距为 ${node.marginBottom}px，恰好匹配系统导航栏高度 ($navBarH px)。建议将其 Margin 归零以消除黑底或留白。",
                            confidence = 0.95f,
                            targetViewId = "decor",
                            suggestedAction = ExtraAction(
                                viewId = "decor",
                                isGroup = true,
                                self = false,
                                childIndex = 0,
                                routeKey = candidateRouteKey,
                                action = ViewAction.Inset(
                                    spacingType = SpacingType.MARGIN,
                                    edge = InsetEdge.BOTTOM,
                                    useSystemInsets = false,
                                    customInset = 0
                                )
                            )
                        )
                    )
                } else if (navBarH > 0 && abs(node.paddingBottom - navBarH) <= 4) {
                    suggestions.add(
                        RuleSuggestion(
                            id = "decor_child_0_bottom_padding",
                            title = "DecorView 底部内边距 (Padding) 消除",
                            description = "DecorView 首个子容器 (childIndex=0) 的内边距为 ${node.paddingBottom}px，恰好匹配系统导航栏高度 ($navBarH px)。建议将其 Padding 归零。",
                            confidence = 0.90f,
                            targetViewId = "decor",
                            suggestedAction = ExtraAction(
                                viewId = "decor",
                                isGroup = true,
                                self = false,
                                childIndex = 0,
                                routeKey = candidateRouteKey,
                                action = ViewAction.Inset(
                                    spacingType = SpacingType.PADDING,
                                    edge = InsetEdge.BOTTOM,
                                    useSystemInsets = false,
                                    customInset = 0
                                )
                            )
                        )
                    )
                }
            }

            // 2. Named view bottom padding matches nav bar
            if (viewTargetId != null && !node.isDecorChild && navBarH > 0 && abs(node.paddingBottom - navBarH) <= 4) {
                val isContent = node.isContent
                val name = if (isContent) "android:id/content" else "@id/$viewTargetId"
                suggestions.add(
                    RuleSuggestion(
                        id = "padding_bottom_${viewTargetId}_${node.depth}",
                        title = "$name 底部 Padding 消除",
                        description = "控件 $name 底部内边距为 ${node.paddingBottom}px，与系统手势导航栏高度 ($navBarH px) 一致，存在多余垫高。建议将 Padding 归零。",
                        confidence = 0.88f,
                        targetViewId = viewTargetId,
                        suggestedAction = ExtraAction(
                            viewId = viewTargetId,
                            self = true,
                            routeKey = candidateRouteKey,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.PADDING,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                )
            }

            // 3. Named view bottom margin matches nav bar
            if (viewTargetId != null && !node.isDecorChild && navBarH > 0 && abs(node.marginBottom - navBarH) <= 4) {
                suggestions.add(
                    RuleSuggestion(
                        id = "margin_bottom_${viewTargetId}_${node.depth}",
                        title = "@id/$viewTargetId 底部 Margin 消除",
                        description = "控件 @id/$viewTargetId 底部外边距为 ${node.marginBottom}px，与系统导航栏高度一致。建议将 Margin 归零。",
                        confidence = 0.85f,
                        targetViewId = viewTargetId,
                        suggestedAction = ExtraAction(
                            viewId = viewTargetId,
                            self = true,
                            routeKey = candidateRouteKey,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                )
            }

            // 4. Suspect placeholder / spacer view
            val idName = node.idEntryName?.lowercase()
            if (idName != null && isPlaceholderId(idName)) {
                val isHeightMatching = navBarH > 0 && abs(node.height - navBarH) <= 6 ||
                        statusBarH > 0 && abs(node.height - statusBarH) <= 6
                if (isHeightMatching || node.children.isEmpty()) {
                    suggestions.add(
                        RuleSuggestion(
                            id = "placeholder_gone_${node.idEntryName}",
                            title = "隐藏占位空白条: @id/${node.idEntryName}",
                            description = "检测到命名为 '${node.idEntryName}' 的疑似系统栏占位条 (高度: ${node.height}px)。建议将其设为 GONE 并折叠尺寸。",
                            confidence = if (isHeightMatching) 0.92f else 0.75f,
                            targetViewId = node.idEntryName,
                            suggestedAction = ExtraAction(
                                viewId = node.idEntryName,
                                self = true,
                                routeKey = candidateRouteKey,
                                action = ViewAction.Visibility(
                                    mode = VisibilityMode.GONE,
                                    collapseSize = true
                                )
                            )
                        )
                    )
                }
            }

            // Recurse into children
            for (child in node.children) {
                traverse(child)
            }
        }

        traverse(report.viewTree)
        return suggestions.distinctBy { it.id }.sortedByDescending { it.confidence }
    }

    private fun isPlaceholderId(idName: String): Boolean {
        return idName.contains("placeholder") ||
                idName.contains("spacer") ||
                idName.contains("fake_status") ||
                idName.contains("fake_nav") ||
                idName.contains("navigation_bar_view") ||
                idName.contains("status_bar_view") ||
                idName.contains("nav_bar_background") ||
                idName.contains("status_bar_background")
    }

    private fun findCandidateRouteKey(report: InspectorReport): String? {
        val keys = report.intentExtras.keys
        val candidates = listOf("url", "route", "router", "target", "path", "page", "uri")
        for (candidate in candidates) {
            val matched = keys.firstOrNull { it.contains(candidate, ignoreCase = true) }
            if (matched != null) return matched
        }
        return if (report.intentData != null) "data" else null
    }
}
