package com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.databinding.ItemInspectorSuggestionBinding
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.RuleSuggestion

class InspectorSuggestionAdapter(
    private val onApply: (RuleSuggestion) -> Unit,
    private val onCopy: (RuleSuggestion) -> Unit
) : RecyclerView.Adapter<InspectorSuggestionAdapter.ViewHolder>() {

    private val suggestions = mutableListOf<RuleSuggestion>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<RuleSuggestion>) {
        suggestions.clear()
        suggestions.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInspectorSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    inner class ViewHolder(private val binding: ItemInspectorSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RuleSuggestion) {
            binding.tvSuggestionTitle.text = item.title
            binding.tvSuggestionDesc.text = item.description
            binding.tvConfidenceBadge.text = "${(item.confidence * 100).toInt()}% 置信度"

            val act = item.suggestedAction
            val actionSummary = buildString {
                append("ExtraAction(viewId = \"${act.viewId}\"")
                if (act.isGroup) append(", isGroup = true")
                if (!act.self) append(", self = false")
                if (act.childIndex >= 0) append(", childIndex = ${act.childIndex}")
                if (act.routeKey != null) append(", routeKey = \"${act.routeKey}\"")
                when (val va = act.action) {
                    is ViewAction.Inset -> append(", Inset(${va.spacingType}, ${va.edge}, ${va.customInset})")
                    is ViewAction.Visibility -> append(", Visibility(${va.mode})")
                }
                append(")")
            }
            binding.tvActionSummary.text = actionSummary

            binding.btnApplySuggestion.setOnClickListener {
                onApply(item)
            }

            binding.btnCopySuggestion.setOnClickListener {
                onCopy(item)
            }
        }
    }
}
