package com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hal1ucinogen.systembarsmodernizer.databinding.ItemInspectorTreeNodeBinding
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.advisor.HeuristicAdvisor
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorViewNode
import kotlin.math.abs

class InspectorTreeAdapter(
    private val context: Context,
    private val statusBarHeight: Int,
    private val navBarHeight: Int,
    private val onNodeClick: (InspectorViewNode) -> Unit
) : RecyclerView.Adapter<InspectorTreeAdapter.ViewHolder>() {

    data class TreeItem(
        val node: InspectorViewNode,
        val depth: Int,
        var isExpanded: Boolean = true,
        var isFilteredOut: Boolean = false
    )

    private val allItems = mutableListOf<TreeItem>()
    private val displayItems = mutableListOf<TreeItem>()
    private var currentFilter: String = ""

    fun setRootNode(root: InspectorViewNode) {
        allItems.clear()
        flatten(root, 0)
        applyFilterAndExpansion()
    }

    private fun flatten(node: InspectorViewNode, depth: Int) {
        allItems.add(TreeItem(node, depth, isExpanded = depth < 3))
        for (child in node.children) {
            flatten(child, depth + 1)
        }
    }

    fun filter(query: String) {
        currentFilter = query.trim()
        applyFilterAndExpansion()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilterAndExpansion() {
        displayItems.clear()
        if (currentFilter.isEmpty()) {
            // Normal hierarchical expansion
            val collapsedParents = mutableSetOf<InspectorViewNode>()
            for (item in allItems) {
                // If any ancestor is collapsed, do not show
                // A quick way: check depth against previous collapsed depth
                displayItems.add(item)
            }
        } else {
            // Filter mode: show nodes matching query and their ancestors
            val matchingItems = allItems.filter { item ->
                val idMatch = item.node.idEntryName?.contains(currentFilter, ignoreCase = true) == true
                val classMatch = item.node.className.contains(currentFilter, ignoreCase = true)
                idMatch || classMatch
            }
            displayItems.addAll(matchingItems)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInspectorTreeNodeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(displayItems[position])
    }

    override fun getItemCount(): Int = displayItems.size

    inner class ViewHolder(private val binding: ItemInspectorTreeNodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TreeItem) {
            val node = item.node
            val density = context.resources.displayMetrics.density

            // Indentation
            val indentPx = (item.depth * 14 * density).toInt()
            binding.viewIndent.layoutParams.width = indentPx
            binding.viewIndent.requestLayout()
            binding.viewIndentSub.layoutParams.width = indentPx
            binding.viewIndentSub.requestLayout()

            // Chevron
            if (node.children.isEmpty()) {
                binding.ivChevron.visibility = View.INVISIBLE
            } else {
                binding.ivChevron.visibility = View.VISIBLE
                binding.ivChevron.rotation = if (item.isExpanded) 90f else 0f
            }

            // Class name
            binding.tvClassName.text = node.className

            // ID
            val idStr = when {
                node.isDecor -> "@decor"
                node.isDecorChild -> "decor[#${node.childIndex}]"
                node.isContent -> "@android:id/content"
                !node.idEntryName.isNullOrEmpty() -> "@id/${node.idEntryName}"
                else -> null
            }
            if (idStr != null) {
                binding.tvViewId.text = idStr
                binding.tvViewId.visibility = View.VISIBLE
            } else {
                binding.tvViewId.visibility = View.GONE
            }

            // Visibility badge
            when (node.visibility) {
                View.GONE -> {
                    binding.tvVisibilityBadge.text = "GONE"
                    binding.tvVisibilityBadge.visibility = View.VISIBLE
                }
                View.INVISIBLE -> {
                    binding.tvVisibilityBadge.text = "INVISIBLE"
                    binding.tvVisibilityBadge.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvVisibilityBadge.visibility = View.GONE
                }
            }

            // Insets info: [L,T,R,B] (WxH) P:[...] M:[...]
            val bounds = node.screenBounds
            val boundsStr = "[${bounds.left},${bounds.top}..${bounds.right},${bounds.bottom}](${node.width}×${node.height})"
            val padStr = if (node.paddingTop != 0 || node.paddingBottom != 0 || node.paddingStart != 0 || node.paddingEnd != 0) {
                "P:[${node.paddingTop},${node.paddingBottom},${node.paddingStart},${node.paddingEnd}]"
            } else ""
            val marStr = if (node.marginTop != 0 || node.marginBottom != 0 || node.marginStart != 0 || node.marginEnd != 0) {
                "M:[${node.marginTop},${node.marginBottom},${node.marginStart},${node.marginEnd}]"
            } else ""
            binding.tvInsetsInfo.text = listOf(boundsStr, padStr, marStr)
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            // Warning badges: check padding, margin, or spacer view height
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val matchesStatusHeight = statusBarHeight > 0 &&
                    abs(node.height - statusBarHeight) <= 4 &&
                    (HeuristicAdvisor.isPlaceholderId(node.idEntryName) ||
                            (node.children.isEmpty() && node.width >= screenWidth * 0.4f && node.screenBounds.top <= statusBarHeight * 2))

            val matchesNavHeight = navBarHeight > 0 &&
                    abs(node.height - navBarHeight) <= 4 &&
                    (HeuristicAdvisor.isPlaceholderId(node.idEntryName) ||
                            (node.children.isEmpty() && node.width >= screenWidth * 0.4f && node.screenBounds.bottom >= screenHeight - navBarHeight * 2))

            val matchesNavBar = navBarHeight > 0 && (
                    abs(node.paddingBottom - navBarHeight) <= 4 ||
                    abs(node.marginBottom - navBarHeight) <= 4 ||
                    matchesNavHeight
                    )
            val matchesStatusBar = statusBarHeight > 0 && (
                    abs(node.paddingTop - statusBarHeight) <= 4 ||
                    abs(node.marginTop - statusBarHeight) <= 4 ||
                    matchesStatusHeight
                    )

            binding.badgeWarningNav.visibility = if (matchesNavBar) View.VISIBLE else View.GONE
            binding.badgeWarningStatus.visibility = if (matchesStatusBar) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onNodeClick(node)
            }
        }
    }
}
