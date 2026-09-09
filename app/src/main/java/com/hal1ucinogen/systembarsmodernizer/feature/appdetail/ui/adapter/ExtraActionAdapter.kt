package com.hal1ucinogen.systembarsmodernizer.feature.appdetail.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isGone
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.util.UiUtils

class ExtraActionAdapter(
    var isEditable: Boolean = true,
    private val onDeleteClick: ((Int) -> Unit)? = null
) : BaseQuickAdapter<ExtraAction, BaseViewHolder>(R.layout.item_extra_action) {

    override fun convert(holder: BaseViewHolder, item: ExtraAction) {
        val tvViewId = holder.getView<TextView>(R.id.tv_view_id)
        val tvActionBadge = holder.getView<TextView>(R.id.tv_action_badge)
        val tvActionMeta = holder.getView<TextView>(R.id.tv_action_meta)
        val btnDelete = holder.getView<AppCompatImageButton>(R.id.btn_delete_action)
        val layoutRoutes = holder.getView<LinearLayout>(R.id.layout_routes_container)
        val tvRouteMode = holder.getView<TextView>(R.id.tv_route_mode_badge)
        val tvRouteKey = holder.getView<TextView>(R.id.tv_route_key)
        val chipGroupRoutes = holder.getView<ChipGroup>(R.id.chip_group_routes)

        val context = holder.itemView.context

        // 1. View ID
        tvViewId.text = if (item.childIndex >= 0) {
            "${item.viewId} [child: ${item.childIndex}]"
        } else {
            item.viewId
        }

        // 2. Action Badge
        when (val act = item.action) {
            is ViewAction.Visibility -> {
                tvActionBadge.text = act.mode.name
                tvActionBadge.setBackgroundResource(R.drawable.bg_badge_tertiary)
                tvActionBadge.setTextColor(context.getAttrColor(com.google.android.material.R.attr.colorOnTertiaryContainer))
            }
            is ViewAction.Inset -> {
                val typeName = if (act.spacingType == SpacingType.PADDING) "Padding" else "Margin"
                val edgeName = if (act.edge == InsetEdge.TOP) "Top" else "Bottom"
                val insetValue = if (act.useSystemInsets) "System" else "${act.customInset}px"
                tvActionBadge.text = "$typeName • $edgeName ($insetValue)"
                tvActionBadge.setBackgroundResource(R.drawable.bg_badge_primary)
                tvActionBadge.setTextColor(context.getAttrColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
            }
        }

        // 3. Action Meta
        val metaList = mutableListOf<String>()
        if (item.delay != 100L) metaList.add("${item.delay}ms")
        if (item.isGroup) metaList.add("Group")
        if (!item.self) metaList.add("Self: false")
        if (metaList.isNotEmpty()) {
            tvActionMeta.isGone = false
            tvActionMeta.text = metaList.joinToString(" • ")
        } else {
            tvActionMeta.isGone = true
        }

        // 4. Routes Section
        if (item.routes.isNotEmpty()) {
            layoutRoutes.isGone = false

            // Route Mode Badge
            if (item.isRouteExclusive) {
                tvRouteMode.text = context.getString(R.string.badge_route_exclude)
                tvRouteMode.setBackgroundResource(R.drawable.bg_badge_tertiary)
                tvRouteMode.setTextColor(context.getAttrColor(com.google.android.material.R.attr.colorOnTertiaryContainer))
            } else {
                tvRouteMode.text = context.getString(R.string.badge_route_include)
                tvRouteMode.setBackgroundResource(R.drawable.bg_badge_secondary)
                tvRouteMode.setTextColor(context.getAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }

            // Route Key
            tvRouteKey.text = if (!item.routeKey.isNullOrBlank()) {
                context.getString(R.string.label_route_key_prefix, item.routeKey)
            } else {
                context.getString(R.string.label_route_key_default)
            }

            // Populate Badges matching General Config's Excluded Activities style
            chipGroupRoutes.removeAllViews()
            item.routes.forEach { route ->
                val badge = UiUtils.createBadge(context, route).apply {
                    setOnClickListener {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Route", route))
                        Toast.makeText(context, context.getString(R.string.msg_route_copied, route), Toast.LENGTH_SHORT).show()
                    }
                }
                chipGroupRoutes.addView(badge)
            }
        } else {
            layoutRoutes.isGone = true
            chipGroupRoutes.removeAllViews()
        }

        // 5. Delete Button
        btnDelete.isGone = !isEditable
        if (isEditable) {
            btnDelete.setOnClickListener {
                onDeleteClick?.invoke(holder.bindingAdapterPosition)
            }
        }
    }

    private fun Context.getAttrColor(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }
}
