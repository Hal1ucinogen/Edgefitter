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
            val idName = node.idEntryName
            val isStatusSpacer = statusBarH > 0 && abs(node.height - statusBarH) <= 6 &&
                    (isPlaceholderId(idName) || (node.children.isEmpty() && node.screenBounds.top <= statusBarH * 2))
            val isNavSpacer = navBarH > 0 && abs(node.height - navBarH) <= 6 &&
                    (isPlaceholderId(idName) || (node.children.isEmpty() && node.screenBounds.bottom >= report.viewTree.height - navBarH * 2))

            if ((isStatusSpacer || isNavSpacer) && (idName != null || node.isDecorChild)) {
                val targetName = if (node.isDecorChild) "decor[#${node.childIndex}]" else "@id/$idName"
                val barType = if (isStatusSpacer) "状态栏" else "导航栏"
                val targetHeight = if (isStatusSpacer) statusBarH else navBarH
                suggestions.add(
                    RuleSuggestion(
                        id = "placeholder_gone_${node.idEntryName ?: "decor_${node.childIndex}"}",
                        title = "隐藏疑似${barType}占位条: $targetName",
                        description = "检测到高度为 ${node.height}px (匹配${barType}高度 ${targetHeight}px) 的疑似占位条。建议将其设为 GONE 并折叠尺寸。",
                        confidence = if (isPlaceholderId(idName)) 0.95f else 0.82f,
                        targetViewId = if (node.isDecorChild) "decor" else (node.idEntryName ?: "decor"),
                        suggestedAction = ExtraAction(
                            viewId = if (node.isDecorChild) "decor" else (node.idEntryName ?: "decor"),
                            isGroup = node.isDecorChild,
                            self = !node.isDecorChild,
                            childIndex = if (node.isDecorChild) node.childIndex else -1,
                            action = ViewAction.Visibility(
                                mode = VisibilityMode.GONE,
                                collapseSize = true
                            )
                        )
                    )
                )
            }

            // Recurse into children
            for (child in node.children) {
                traverse(child)
            }
        }

        traverse(report.viewTree)
        return suggestions.distinctBy { it.id }.sortedByDescending { it.confidence }
    }

    fun isPlaceholderId(idName: String?): Boolean {
        if (idName == null) return false
        val lower = idName.lowercase()
        return lower.contains("placeholder") ||
                lower.contains("spacer") ||
                lower.contains("fake_status") ||
                lower.contains("fake_nav") ||
                lower.contains("navigation_bar_view") ||
                lower.contains("status_bar_view") ||
                lower.contains("nav_bar_background") ||
                lower.contains("status_bar_background") ||
                lower.contains("statusbar_view") ||
                lower.contains("navbar_view") ||
                lower.contains("status_bar_holder") ||
                lower.contains("nav_bar_holder") ||
                lower.contains("status_bar") ||
                lower.contains("navigation_bar")
    }
}
