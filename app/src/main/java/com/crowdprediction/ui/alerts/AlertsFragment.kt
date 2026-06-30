package com.crowdprediction.ui.alerts

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.crowdprediction.data.repository.Result
import com.crowdprediction.databinding.FragmentAlertsBinding
import com.crowdprediction.viewmodel.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels { MainViewModel.Factory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = AlertAdapter { vm.resolveAlert(it) }
        binding.rvAlerts.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { vm.loadAlerts() }

        binding.btnResolveAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Resolve all alerts?")
                .setMessage("This will mark all active alerts as resolved.")
                .setPositiveButton("Resolve all") { _, _ -> vm.resolveAllAlerts() }
                .setNegativeButton("Cancel", null).show()
        }

        vm.alerts.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> binding.swipeRefresh.isRefreshing = true
                is Result.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    val active = result.data.filter { !it.resolved }
                    adapter.submitList(active)
                    binding.tvCriticalCount.text = active.count { it.level == "critical" }.toString()
                    binding.tvWarningCount.text  = active.count { it.level == "warning" }.toString()
                    binding.tvResolvedCount.text = result.data.count { it.resolved }.toString()
                    binding.tvEmptyState.visibility = if (active.isEmpty()) View.VISIBLE else View.GONE
                    binding.btnResolveAll.isEnabled = active.isNotEmpty()
                }
                is Result.Error -> binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
