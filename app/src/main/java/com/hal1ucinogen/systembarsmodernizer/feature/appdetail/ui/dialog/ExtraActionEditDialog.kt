package com.hal1ucinogen.systembarsmodernizer.feature.appdetail.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isGone
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.bean.VisibilityMode
import com.hal1ucinogen.systembarsmodernizer.databinding.DialogExtraActionEditBinding

class ExtraActionEditDialog(
    private val initialAction: ExtraAction? = null,
    private val onActionSaved: (ExtraAction) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogExtraActionEditBinding? = null
    private val binding get() = _binding!!

    private val currentRoutes = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogExtraActionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun refreshRouteChips() {
            binding.chipGroupEditRoutes.removeAllViews()
            binding.tvEmptyRoutesHint.isGone = currentRoutes.isNotEmpty()
            binding.btnClearRoutes.isGone = currentRoutes.isEmpty()

            val density = requireContext().resources.displayMetrics.density
            currentRoutes.forEach { route ->
                val chip = Chip(requireContext()).apply {
                    text = route
                    textSize = 11.5f
                    chipMinHeight = 26f * density
                    setEnsureMinTouchTargetSize(false)
                    chipStartPadding = 8f * density
                    chipEndPadding = 4f * density
                    textStartPadding = 0f
                    textEndPadding = 2f * density
                    closeIconSize = 14f * density
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(6f * density).build()
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        currentRoutes.remove(route)
                        refreshRouteChips()
                    }
                }
                binding.chipGroupEditRoutes.addView(chip)
            }
        }

        fun addRoutesFromInput(rawInput: String) {
            val delimiters = charArrayOf(',', '，', ';', '；', '\n')
            val newItems = rawInput.split(*delimiters)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (newItems.isNotEmpty()) {
                newItems.forEach { item ->
                    if (!currentRoutes.contains(item)) {
                        currentRoutes.add(item)
                    }
                }
                refreshRouteChips()
            }
            binding.etRouteInput.setText("")
        }

        // Init values if editing existing action
        initialAction?.let { action ->
            binding.etViewId.setText(action.viewId)

            when (val act = action.action) {
                is ViewAction.Visibility -> {
                    if (act.mode == VisibilityMode.INVISIBLE) {
                        binding.rbInvisible.isChecked = true
                    } else {
                        binding.rbGone.isChecked = true
                    }
                }
                is ViewAction.Inset -> {
                    if (act.spacingType == SpacingType.PADDING) {
                        binding.rbPadding.isChecked = true
                    } else {
                        binding.rbMargin.isChecked = true
                    }

                    if (act.edge == InsetEdge.TOP) {
                        binding.rbStatusBar.isChecked = true
                    } else {
                        binding.rbNavBar.isChecked = true
                    }

                    binding.switchUseSystemInsets.isChecked = act.useSystemInsets
                    if (act.customInset >= 0) {
                        binding.etCustomInset.setText(act.customInset.toString())
                    }
                }
            }

            binding.switchIsGroup.isChecked = action.isGroup
            binding.switchSelf.isChecked = action.self
            if (action.childIndex >= 0) {
                binding.etChildIndex.setText(action.childIndex.toString())
            }
            binding.etDelay.setText(action.delay.toString())
            binding.etRouteKey.setText(action.routeKey.orEmpty())

            if (action.isRouteExclusive) {
                binding.rbRouteExclude.isChecked = true
            } else {
                binding.rbRouteInclude.isChecked = true
            }

            currentRoutes.clear()
            currentRoutes.addAll(action.routes)
        } ?: run {
            binding.etDelay.setText("100")
            binding.rbRouteInclude.isChecked = true
        }

        refreshRouteChips()

        fun updateVisibility() {
            val isVisibilityAction = binding.rbGone.isChecked || binding.rbInvisible.isChecked
            binding.layoutBarDirection.visibility = if (isVisibilityAction) View.GONE else View.VISIBLE
            binding.layoutInsetSettings.visibility = if (isVisibilityAction) View.GONE else View.VISIBLE
        }

        binding.rgActionType.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updateVisibility()

        // Routes dynamic input handlers
        binding.btnAddRoute.setOnClickListener {
            addRoutesFromInput(binding.etRouteInput.text?.toString().orEmpty())
        }

        binding.btnClearRoutes.setOnClickListener {
            currentRoutes.clear()
            refreshRouteChips()
        }

        binding.etRouteInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addRoutesFromInput(binding.etRouteInput.text?.toString().orEmpty())
                true
            } else false
        }

        binding.etRouteInput.doAfterTextChanged { s ->
            val text = s?.toString().orEmpty()
            if (text.endsWith(",") || text.endsWith("，") || text.endsWith("\n") || text.endsWith(";") || text.endsWith("；")) {
                addRoutesFromInput(text)
            }
        }

        binding.btnCancelAction.setOnClickListener {
            dismiss()
        }

        binding.btnSaveAction.setOnClickListener {
            val viewId = binding.etViewId.text?.toString().orEmpty().trim()
            if (viewId.isEmpty()) {
                binding.tilViewId.error = "View ID cannot be empty"
                return@setOnClickListener
            }

            val actionPayload: ViewAction = if (binding.rbGone.isChecked || binding.rbInvisible.isChecked) {
                ViewAction.Visibility(
                    mode = if (binding.rbInvisible.isChecked) VisibilityMode.INVISIBLE else VisibilityMode.GONE,
                    collapseSize = true
                )
            } else {
                val spacingType = if (binding.rbPadding.isChecked) SpacingType.PADDING else SpacingType.MARGIN
                val edge = if (binding.rbStatusBar.isChecked) InsetEdge.TOP else InsetEdge.BOTTOM
                val useSystemInsets = binding.switchUseSystemInsets.isChecked
                val customInset = binding.etCustomInset.text?.toString()?.toIntOrNull() ?: 0
                ViewAction.Inset(
                    spacingType = spacingType,
                    edge = edge,
                    useSystemInsets = useSystemInsets,
                    customInset = customInset
                )
            }

            // Include any unsubmitted route text
            val leftoverRoute = binding.etRouteInput.text?.toString().orEmpty().trim()
            if (leftoverRoute.isNotEmpty()) {
                addRoutesFromInput(leftoverRoute)
            }

            val isGroup = binding.switchIsGroup.isChecked
            val self = binding.switchSelf.isChecked
            val childIndex = binding.etChildIndex.text?.toString()?.toIntOrNull() ?: -1
            val delay = binding.etDelay.text?.toString()?.toLongOrNull() ?: 100L
            val routeKey = binding.etRouteKey.text?.toString().orEmpty().trim().ifEmpty { null }
            val routes = currentRoutes.toList()
            val isRouteExclusive = binding.rbRouteExclude.isChecked

            val action = ExtraAction(
                viewId = viewId,
                isGroup = isGroup,
                self = self,
                childIndex = childIndex,
                delay = delay,
                routes = routes,
                isRouteExclusive = isRouteExclusive,
                routeKey = routeKey,
                action = actionPayload
            )

            onActionSaved(action)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
