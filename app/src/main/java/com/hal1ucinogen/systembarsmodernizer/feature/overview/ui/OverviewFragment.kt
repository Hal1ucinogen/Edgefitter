package com.hal1ucinogen.systembarsmodernizer.feature.overview.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.SBMApp
import com.hal1ucinogen.systembarsmodernizer.databinding.FragmentOverviewBinding
import com.hal1ucinogen.systembarsmodernizer.feature.overview.ui.dialog.ScopeListDialog
import com.hal1ucinogen.systembarsmodernizer.feature.overview.ui.viewmodel.OverviewViewModel
import com.hal1ucinogen.systembarsmodernizer.ui.base.BaseFragment
import io.github.libxposed.service.XposedService
import rikka.insets.WindowInsetsHelper

class OverviewFragment : BaseFragment<FragmentOverviewBinding>(), SBMApp.ServiceStateListener {

    private lateinit var viewModel: OverviewViewModel
    private var currentScope: List<String> = emptyList()

    override fun init() {
        val sbmApp = requireActivity().application as SBMApp
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OverviewViewModel(sbmApp.database.sbmItemDao()) as T
            }
        })[OverviewViewModel::class.java]

        initViews()
        observeData()
    }

    private fun initViews() {
        WindowInsetsHelper.attach(
            binding.root,
            false,
            Gravity.BOTTOM,
            0,
            0
        )

        binding.btnViewAppList.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.selectedItemId =
                R.id.navigation_app_list
        }

        binding.chipScope.setOnClickListener {
            if (currentScope.isNotEmpty()) {
                ScopeListDialog(currentScope).show(childFragmentManager, "ScopeListDialog")
            }
        }

        binding.btnLaunchInspector.setOnClickListener {
            launchInspector()
        }
    }

    private fun launchInspector() {
        val context = requireContext()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(context)
        ) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.inspector_permission_title)
                .setMessage(R.string.inspector_permission_desc)
                .setPositiveButton(R.string.inspector_permission_grant) { _, _ ->
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    runCatching { startActivity(intent) }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            com.hal1ucinogen.systembarsmodernizer.feature.inspector.service.InspectorFloatingService.start(context)
            android.widget.Toast.makeText(context, R.string.inspector_notification_title, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvStatConfiguredApps.text = stats.configuredAppsCount.toString()
            binding.tvStatScopeRules.text = stats.totalScopeConfigsCount.toString()
            binding.tvStatGlobalE2e.text = stats.globalE2eAppsCount.toString()
            binding.tvStatExtraActions.text = stats.totalExtraActionsCount.toString()
        }
    }

    override fun onStart() {
        super.onStart()
        SBMApp.addServiceStateListener(this, true)
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusUi(SBMApp.mService)
    }

    override fun onStop() {
        SBMApp.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            updateServiceStatusUi(service)
        }
    }

    private fun updateServiceStatusUi(service: XposedService?) {
        val context = context ?: return
        if (service == null) {
            showInactiveUi()
            return
        }

        try {
            val frameworkName = service.frameworkName
            val frameworkVersion = service.frameworkVersion
            val frameworkVersionCode = service.frameworkVersionCode
            val apiVersion = service.apiVersion
            currentScope = service.scope.orEmpty()

            val primaryColor = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorPrimary
            )
            binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.tvStatusTitle.setText(R.string.overview_framework_connected)
            binding.tvStatusDesc.text =
                "$frameworkName v$frameworkVersion ($frameworkVersionCode) • API $apiVersion"

            val scopeCount = currentScope.size
            binding.chipScope.text = getString(R.string.overview_scope_count, scopeCount)
            binding.chipScope.visibility = View.VISIBLE
        } catch (e: Throwable) {
            e.printStackTrace()
            showInactiveUi()
        }
    }

    private fun showInactiveUi() {
        currentScope = emptyList()
        val errorColor = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorError
        )
        binding.ivStatusIcon.setImageResource(R.drawable.ic_warning_circle)
        binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(errorColor)
        binding.tvStatusTitle.setText(R.string.overview_framework_not_activated)
        binding.tvStatusDesc.setText(R.string.overview_framework_not_activated_desc)
        binding.chipScope.visibility = View.GONE
    }
}