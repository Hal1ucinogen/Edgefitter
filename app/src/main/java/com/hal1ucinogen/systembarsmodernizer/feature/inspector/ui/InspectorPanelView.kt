package com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.databinding.ItemInspectorExtraBinding
import com.hal1ucinogen.systembarsmodernizer.databinding.ViewInspectorPanelBinding
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.advisor.HeuristicAdvisor
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorViewNode
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.RuleSuggestion
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.adapter.InspectorSuggestionAdapter
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui.adapter.InspectorTreeAdapter

@SuppressLint("ViewConstructor")
class InspectorPanelView(
    context: Context,
    private val onRecapture: () -> Unit,
    private val onMinimize: () -> Unit,
    private val onClose: () -> Unit,
    private val onApplyAction: (packageName: String, activityName: String, action: ExtraAction) -> Unit
) : FrameLayout(
    android.view.ContextThemeWrapper(
        context,
        com.hal1ucinogen.systembarsmodernizer.R.style.Theme_SystemBarsModernizer
    )
) {

    private val binding: ViewInspectorPanelBinding =
        ViewInspectorPanelBinding.inflate(LayoutInflater.from(this.context), this, true)

    private var currentReport: InspectorReport? = null
    private var treeAdapter: InspectorTreeAdapter? = null
    private val suggestionAdapter = InspectorSuggestionAdapter(
        onApply = { suggestion -> handleApplySuggestion(suggestion) },
        onCopy = { suggestion -> copyToClipboard(generateKotlinSnippet(suggestion.suggestedAction)) }
    )

    init {
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnPanelRefresh.setOnClickListener { onRecapture() }
        binding.btnPanelMinimize.setOnClickListener { onMinimize() }
        binding.btnPanelClose.setOnClickListener { onClose() }

        binding.tabLayoutInspector.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> switchTab(0)
                    1 -> switchTab(1)
                    2 -> switchTab(2)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionAdapter
        }

        binding.etSearchTree.doAfterTextChanged { text ->
            treeAdapter?.filter(text?.toString().orEmpty())
        }

        binding.btnCopyActivity.setOnClickListener {
            currentReport?.activityName?.let { copyToClipboard(it) }
        }

        binding.btnCopyWildcard.setOnClickListener {
            currentReport?.activityName?.let {
                val wildcard = if (it.contains(".")) {
                    it.substringBeforeLast(".") + ".*"
                } else "$it*"
                copyToClipboard(wildcard)
            }
        }
    }

    private fun switchTab(index: Int) {
        binding.containerTabInfo.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.containerTabTree.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.containerTabAdvisor.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    fun bindReport(report: InspectorReport) {
        currentReport = report

        // Header info
        val simpleName = report.activityName.substringAfterLast(".")
        binding.tvHeaderTitle.text = simpleName
        binding.tvHeaderPackage.text = report.packageName

        // Tab 1 Info
        binding.tvInfoActivityFull.text = report.activityName
        binding.tvInfoStatusBar.text = context.getString(R.string.inspector_label_status_bar, report.statusBarHeight)
        binding.tvInfoNavBar.text = context.getString(R.string.inspector_label_nav_bar, report.navBarHeight)

        // Intent Action
        val actionStr = report.intentAction
        if (actionStr.isNullOrEmpty()) {
            binding.tvInfoIntentAction.text = "Action: (None)"
            binding.btnCopyIntentAction.visibility = View.GONE
        } else {
            binding.tvInfoIntentAction.text = "Action: $actionStr"
            binding.btnCopyIntentAction.visibility = View.VISIBLE
            binding.btnCopyIntentAction.setOnClickListener {
                copyToClipboard(actionStr, context.getString(R.string.inspector_copy_success))
            }
        }

        // Intent Data
        val dataStr = report.intentData
        if (dataStr.isNullOrEmpty()) {
            binding.tvInfoIntentData.text = "Data: (None)"
            binding.btnCopyIntentData.visibility = View.GONE
        } else {
            binding.tvInfoIntentData.text = "Data: $dataStr"
            binding.btnCopyIntentData.visibility = View.VISIBLE
            binding.btnCopyIntentData.setOnClickListener {
                copyToClipboard(dataStr, context.getString(R.string.inspector_copy_success))
            }
        }

        // Copy All Intent Button
        binding.btnCopyAllIntent.setOnClickListener {
            val sb = StringBuilder()
            if (!report.intentAction.isNullOrEmpty()) sb.append("Action: ${report.intentAction}\n")
            if (!report.intentData.isNullOrEmpty()) sb.append("Data: ${report.intentData}\n")
            if (report.intentExtras.isNotEmpty()) {
                sb.append("Extras:\n")
                report.intentExtras.forEach { (k, v) ->
                    sb.append("  $k: $v\n")
                }
            }
            val textToCopy = sb.toString().trimEnd().ifEmpty { "(No Intent data)" }
            copyToClipboard(textToCopy, context.getString(R.string.inspector_copy_all_intent_success))
        }

        // Extras List
        binding.llExtrasContainer.removeAllViews()
        if (report.intentExtras.isEmpty()) {
            binding.tvEmptyExtras.visibility = View.VISIBLE
            binding.tvInfoExtrasHeader.text = context.getString(R.string.inspector_label_intent_extras)
        } else {
            binding.tvEmptyExtras.visibility = View.GONE
            binding.tvInfoExtrasHeader.text = context.getString(
                R.string.inspector_label_extras_count,
                report.intentExtras.size
            )
            report.intentExtras.forEach { (key, value) ->
                val itemBinding = ItemInspectorExtraBinding.inflate(
                    LayoutInflater.from(context),
                    binding.llExtrasContainer,
                    false
                )
                itemBinding.tvExtraKey.text = key
                itemBinding.tvExtraValue.text = value

                // Quick copy key
                itemBinding.btnCopyKey.setOnClickListener {
                    copyToClipboard(key, context.getString(R.string.inspector_copy_key_success, key))
                }

                // Quick copy value
                itemBinding.btnCopyValue.setOnClickListener {
                    copyToClipboard(value, context.getString(R.string.inspector_copy_val_success, value))
                }

                // Long-press or click card for more options
                itemBinding.cardExtraItem.setOnClickListener {
                    showExtraOptionsDialog(key, value)
                }

                binding.llExtrasContainer.addView(itemBinding.root)
            }
        }

        // Tab 2 Tree
        treeAdapter = InspectorTreeAdapter(
            context = context,
            statusBarHeight = report.statusBarHeight,
            navBarHeight = report.navBarHeight,
            onNodeClick = { node -> handleNodeClick(node) }
        )
        binding.rvViewTree.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = treeAdapter
        }
        treeAdapter?.setRootNode(report.viewTree)

        // Tab 3 Advisor
        val suggestions = HeuristicAdvisor.analyze(report)
        suggestionAdapter.submitList(suggestions)
        binding.tvEmptySuggestions.visibility = if (suggestions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleNodeClick(node: InspectorViewNode) {
        val targetId = when {
            node.isDecor -> "decor"
            node.isDecorChild -> "decor"
            node.isContent -> "android:id/content"
            !node.idEntryName.isNullOrEmpty() -> node.idEntryName
            else -> "View[#${node.childIndex}]"
        }

        val options = arrayOf(
            context.getString(R.string.inspector_action_generate_for_view),
            context.getString(R.string.inspector_action_copy_id)
        )

        MaterialAlertDialogBuilder(this.context)
            .setTitle(targetId)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showQuickCreateDialog(node)
                    1 -> copyToClipboard(targetId)
                }
            }
            .create()
            .showAsOverlay()
    }

    private fun showQuickCreateDialog(node: InspectorViewNode) {
        val report = currentReport ?: return
        val targetId = when {
            node.isDecor || node.isDecorChild -> "decor"
            node.isContent -> "content"
            !node.idEntryName.isNullOrEmpty() -> node.idEntryName
            else -> "decor"
        }

        val isDecorChild = node.isDecorChild
        val childIndex = if (isDecorChild) node.childIndex else -1

        // Default to resetting bottom margin or padding if present
        val isMargin = node.marginBottom > 0
        val defaultAction = ExtraAction(
            viewId = targetId,
            isGroup = node.isDecor || node.isDecorChild,
            self = !isDecorChild,
            childIndex = childIndex,
            action = ViewAction.Inset(
                spacingType = if (isMargin) com.hal1ucinogen.systembarsmodernizer.bean.SpacingType.MARGIN
                else com.hal1ucinogen.systembarsmodernizer.bean.SpacingType.PADDING,
                edge = com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge.BOTTOM,
                customInset = 0
            )
        )

        MaterialAlertDialogBuilder(this.context)
            .setTitle(R.string.inspector_dialog_generate_rule)
            .setMessage("目标 View: $targetId\n操作: 底部边距归零 (BOTTOM Inset = 0)")
            .setPositiveButton(R.string.inspector_btn_save_apply) { _, _ ->
                onApplyAction(report.packageName, report.activityName, defaultAction)
                Toast.makeText(context, R.string.inspector_save_success, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.inspector_btn_copy_code) { _, _ ->
                copyToClipboard(generateKotlinSnippet(defaultAction))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .showAsOverlay()
    }

    private fun androidx.appcompat.app.AlertDialog.showAsOverlay() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
        }
        show()
    }

    private fun handleApplySuggestion(suggestion: RuleSuggestion) {
        val report = currentReport ?: return
        onApplyAction(report.packageName, report.activityName, suggestion.suggestedAction)
        Toast.makeText(context, R.string.inspector_save_success, Toast.LENGTH_SHORT).show()
    }

    private fun generateKotlinSnippet(action: ExtraAction): String {
        return buildString {
            append("ExtraAction(\n")
            append("    viewId = \"${action.viewId}\",\n")
            if (action.isGroup) append("    isGroup = true,\n")
            if (!action.self) append("    self = false,\n")
            if (action.childIndex >= 0) append("    childIndex = ${action.childIndex},\n")
            if (action.routeKey != null) append("    routeKey = \"${action.routeKey}\",\n")
            when (val act = action.action) {
                is ViewAction.Inset -> {
                    append("    action = ViewAction.Inset(\n")
                    append("        spacingType = SpacingType.${act.spacingType.name},\n")
                    append("        edge = InsetEdge.${act.edge.name},\n")
                    append("        customInset = ${act.customInset}\n")
                    append("    )\n")
                }
                is ViewAction.Visibility -> {
                    append("    action = ViewAction.Visibility(\n")
                    append("        mode = VisibilityMode.${act.mode.name},\n")
                    append("        collapseSize = ${act.collapseSize}\n")
                    append("    )\n")
                }
            }
            append(")")
        }
    }

    private fun showExtraOptionsDialog(key: String, value: String) {
        val options = arrayOf(
            context.getString(R.string.inspector_copy_dialog_key, key),
            context.getString(R.string.inspector_copy_dialog_val, value),
            context.getString(R.string.inspector_copy_dialog_pair, key, value)
        )
        MaterialAlertDialogBuilder(this.context)
            .setTitle(context.getString(R.string.inspector_copy_dialog_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(key, context.getString(R.string.inspector_copy_key_success, key))
                    1 -> copyToClipboard(value, context.getString(R.string.inspector_copy_val_success, value))
                    2 -> copyToClipboard("$key=$value")
                }
            }
            .create()
            .showAsOverlay()
    }

    private fun copyToClipboard(text: String, toastMsg: String? = null) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Edgefitter", text))
        Toast.makeText(context, toastMsg ?: context.getString(R.string.inspector_copy_success), Toast.LENGTH_SHORT).show()
    }
}
