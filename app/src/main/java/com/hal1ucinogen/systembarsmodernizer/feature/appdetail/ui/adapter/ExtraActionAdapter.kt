package com.hal1ucinogen.systembarsmodernizer.feature.appdetail.ui.adapter

import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isGone
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction

class ExtraActionAdapter(
    var isEditable: Boolean = true,
    private val onDeleteClick: ((Int) -> Unit)? = null
) : BaseQuickAdapter<ExtraAction, BaseViewHolder>(R.layout.item_extra_action) {

    override fun convert(holder: BaseViewHolder, item: ExtraAction) {
        val tvViewId = holder.getView<TextView>(R.id.tv_view_id)
        val tvSummary = holder.getView<TextView>(R.id.tv_action_summary)
        val btnDelete = holder.getView<AppCompatImageButton>(R.id.btn_delete_action)

        val idDisplay = if (item.childIndex >= 0) {
            "${item.viewId} [child: ${item.childIndex}]"
        } else {
            item.viewId
        }
        tvViewId.text = idDisplay

        val summary = buildString {
            when (val act = item.action) {
                is ViewAction.Visibility -> {
                    append(act.mode.name)
                }
                is ViewAction.Inset -> {
                    append(if (act.spacingType == SpacingType.PADDING) "Padding" else "Margin")
                    append(" • ")
                    append(if (act.edge == InsetEdge.TOP) "Top" else "Bottom")
                    append(" • ")
                    if (act.useSystemInsets) {
                        append("System Inset")
                    } else {
                        append("${act.customInset}px")
                    }
                }
            }
            if (item.isGroup) {
                append(" • Group")
            }
            if (item.delay != 100L) {
                append(" • ${item.delay}ms")
            }
            if (item.routes.isNotEmpty()) {
                val keyPrefix = if (!item.routeKey.isNullOrEmpty()) "[${item.routeKey}] " else ""
                append(if (item.isRouteExclusive) " • ${keyPrefix}Exclude: " else " • ${keyPrefix}Include: ")
                append(item.routes.joinToString(", "))
            }
        }
        tvSummary.text = summary

        btnDelete.isGone = !isEditable
        if (isEditable) {
            btnDelete.setOnClickListener {
                onDeleteClick?.invoke(holder.bindingAdapterPosition)
            }
        }
    }
}
