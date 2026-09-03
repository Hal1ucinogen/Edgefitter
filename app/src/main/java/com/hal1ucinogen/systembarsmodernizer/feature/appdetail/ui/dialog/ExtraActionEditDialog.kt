package com.hal1ucinogen.systembarsmodernizer.feature.appdetail.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
            if (action.routes.isNotEmpty()) {
                binding.etRoutes.setText(action.routes.joinToString(", "))
            }
            binding.switchRouteExclusive.isChecked = action.isRouteExclusive
        } ?: run {
            binding.etDelay.setText("100")
        }

        fun updateVisibility() {
            val isVisibilityAction = binding.rbGone.isChecked || binding.rbInvisible.isChecked
            binding.layoutBarDirection.visibility = if (isVisibilityAction) View.GONE else View.VISIBLE
            binding.layoutInsetSettings.visibility = if (isVisibilityAction) View.GONE else View.VISIBLE
        }

        binding.rgActionType.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updateVisibility()

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

            val isGroup = binding.switchIsGroup.isChecked
            val self = binding.switchSelf.isChecked
            val childIndex = binding.etChildIndex.text?.toString()?.toIntOrNull() ?: -1
            val delay = binding.etDelay.text?.toString()?.toLongOrNull() ?: 100L
            val routeKey = binding.etRouteKey.text?.toString().orEmpty().trim().ifEmpty { null }
            val routesStr = binding.etRoutes.text?.toString().orEmpty().trim()
            val routes = if (routesStr.isNotEmpty()) {
                routesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            val isRouteExclusive = binding.switchRouteExclusive.isChecked

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
