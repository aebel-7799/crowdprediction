package com.crowdprediction.ui.prediction

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.crowdprediction.R
import com.crowdprediction.data.repository.Result
import com.crowdprediction.databinding.FragmentPredictionBinding
import com.crowdprediction.viewmodel.MainViewModel
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class PredictionFragment : Fragment() {

    private var _binding: FragmentPredictionBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels { MainViewModel.Factory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPredictionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart(); setupZoneSpinner(); setupSimulator()

        vm.prediction.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val d = result.data
                    updateForecastChart(d.predictedDensity, d.timeLabels)
                    binding.tvPeakTime.text   = "Peak at ${d.peakTime}"
                    binding.tvPeakValue.text  = "${d.peakValue.toInt()}%"
                    binding.tvConfidence.text = "Confidence: ${(d.confidence * 100).toInt()}%"
                    val (colorRes, label) = when (d.riskLevel) {
                        "critical" -> Pair(R.color.colorDanger,  "Critical Risk")
                        "warning"  -> Pair(R.color.colorWarning, "Moderate Risk")
                        else       -> Pair(R.color.colorSuccess, "Low Risk")
                    }
                    binding.tvRiskLevel.text = label
                    binding.tvRiskLevel.setTextColor(requireContext().getColor(colorRes))
                }
                is Result.Error -> binding.progressBar.visibility = View.GONE
            }
        }

        vm.simulation.observe(viewLifecycleOwner) { result ->
            if (result is Result.Success) {
                binding.cardSimResult.visibility = View.VISIBLE
                val d = result.data
                binding.tvSimTimeToFull.text  = "${d.timeToFull} min"
                binding.tvSimPeakCrowd.text   = "%,d".format(d.peakCrowd)
                binding.tvSimRisk.text        = d.riskLevel
                val colorRes = when (d.riskLevel.lowercase()) {
                    "critical" -> R.color.colorDanger
                    "warning"  -> R.color.colorWarning
                    else       -> R.color.colorSuccess
                }
                binding.tvSimRisk.setTextColor(requireContext().getColor(colorRes))
                binding.tvSimRecommendations.text = d.recommendations.joinToString("\n") { "• $it" }
            }
        }

        vm.loadPrediction(1)
    }

    private fun setupZoneSpinner() {
        val zones = listOf("Gate A","Main Stage","Food Court","VIP Zone","Parking","Exit A")
        binding.spinnerZone.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, zones)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { vm.loadPrediction(pos + 1) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupChart() {
        binding.forecastChart.apply {
            description.isEnabled = false; legend.isEnabled = false
            setTouchEnabled(true); isDragEnabled = true; setScaleEnabled(false)
            setDrawGridBackground(false); axisRight.isEnabled = false
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); textColor = Color.GRAY; textSize = 9f; labelRotationAngle = -30f }
            axisLeft.apply { axisMinimum = 0f; axisMaximum = 100f; textColor = Color.GRAY; textSize = 10f }
        }
    }

    private fun updateForecastChart(values: List<Float>, labels: List<String>) {
        val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val ds = LineDataSet(entries, "Predicted").apply {
            color = Color.parseColor("#1D9E75"); lineWidth = 2f
            setCircleColor(Color.parseColor("#1D9E75")); circleRadius = 3f
            setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true); fillColor = Color.parseColor("#1D9E75"); fillAlpha = 30
            enableDashedLine(12f, 6f, 0f)
        }
        val threshold = LimitLine(85f, "Danger").apply {
            lineColor = Color.parseColor("#E24B4A"); lineWidth = 1.5f
            enableDashedLine(10f, 5f, 0f)
            textColor = Color.parseColor("#E24B4A"); textSize = 10f
        }
        binding.forecastChart.apply {
            axisLeft.removeAllLimitLines(); axisLeft.addLimitLine(threshold)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
            xAxis.labelCount = labels.size.coerceAtMost(8)
            data = LineData(ds); animateX(600); invalidate()
        }
    }

    private fun setupSimulator() {
        val eventTypes = listOf("Music festival","Sports match","Trade expo","Transport hub")
        binding.spinnerEventType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, eventTypes)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.sliderAttendees.addOnChangeListener { _, value, _ ->
            binding.tvAttendeesValue.text = "%,d".format(value.toInt()) }
        binding.sliderEntryRate.addOnChangeListener { _, value, _ ->
            binding.tvEntryRateValue.text = value.toInt().toString() }
        binding.btnRunSim.setOnClickListener {
            vm.runSimulation(
                binding.spinnerEventType.selectedItem.toString(),
                binding.sliderAttendees.value.toInt(),
                binding.sliderEntryRate.value.toInt())
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
