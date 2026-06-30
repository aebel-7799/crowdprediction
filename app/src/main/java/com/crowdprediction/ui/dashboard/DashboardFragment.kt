package com.crowdprediction.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.crowdprediction.data.models.DashboardSummary
import com.crowdprediction.data.models.Zone
import com.crowdprediction.data.repository.Result
import com.crowdprediction.databinding.FragmentDashboardBinding
import com.crowdprediction.viewmodel.MainViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels { MainViewModel.Factory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        binding.swipeRefresh.setOnRefreshListener { vm.loadDashboard() }

        vm.dashboard.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> binding.swipeRefresh.isRefreshing = true
                is Result.Success -> { binding.swipeRefresh.isRefreshing = false; updateUI(result.data) }
                is Result.Error   -> binding.swipeRefresh.isRefreshing = false
            }
        }

        vm.updateResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> {
                    Toast.makeText(requireContext(),
                        "${result.data.name} updated to ${"%,d".format(result.data.current)}",
                        Toast.LENGTH_SHORT).show()
                    vm.clearUpdateResult()
                }
                is Result.Error -> {
                    Toast.makeText(requireContext(),
                        "Update failed: ${result.message}", Toast.LENGTH_LONG).show()
                    vm.clearUpdateResult()
                }
                else -> {}
            }
        }
    }

    private fun showEditDialog(zone: Zone) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Current population"
            setText(zone.current.toString())
            setSelection(text.length)
        }
        container.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(zone.name)
            .setMessage("Capacity: ${"%,d".format(zone.capacity)}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value == null || value < 0) {
                    Toast.makeText(requireContext(), "Enter a valid number", Toast.LENGTH_SHORT).show()
                } else {
                    vm.updateZone(zone.id, value, zone.capacity)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUI(data: DashboardSummary) {
        binding.tvTotalCrowd.text   = "%,d".format(data.totalCrowd)
        binding.tvAvgDensity.text   = "${data.avgDensity}%"
        binding.tvActiveAlerts.text = data.activeAlerts.toString()
        binding.tvPeakIn.text       = "${data.peakInMinutes} min"
        binding.tvActiveAlerts.setTextColor(
            requireContext().getColor(
                if (data.activeAlerts > 0) android.R.color.holo_red_dark
                else android.R.color.holo_green_dark))
        updateChart(data)
        binding.rvZones.adapter = ZoneCardAdapter(
            data.zones,
            onPredict = { vm.loadPrediction(it.id) },
            onEdit = { showEditDialog(it) }
        )
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false; legend.isEnabled = false
            setTouchEnabled(true); isDragEnabled = true; setScaleEnabled(false)
            setDrawGridBackground(false); axisRight.isEnabled = false
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); textColor = Color.GRAY; textSize = 10f }
            axisLeft.apply { axisMinimum = 0f; axisMaximum = 100f; textColor = Color.GRAY; textSize = 10f }
        }
    }

    private fun updateChart(data: DashboardSummary) {
        val labels  = data.recentHistory.map { it.time }
        val entries = data.recentHistory.mapIndexed { i, pt -> Entry(i.toFloat(), pt.value) }
        val ds = LineDataSet(entries, "Density").apply {
            color = Color.parseColor("#378ADD"); lineWidth = 2f
            setCircleColor(Color.parseColor("#378ADD")); circleRadius = 3f
            setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true); fillColor = Color.parseColor("#378ADD"); fillAlpha = 30
        }
        binding.lineChart.apply {
            this.data = LineData(ds)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
            xAxis.labelCount = labels.size.coerceAtMost(6)
            animateX(800); invalidate()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
