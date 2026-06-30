package com.crowdprediction.ui.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crowdprediction.R
import com.crowdprediction.data.models.Zone
import com.crowdprediction.databinding.ItemZoneCardBinding

class ZoneCardAdapter(
    private val zones: List<Zone>,
    private val onPredict: (Zone) -> Unit,
    private val onEdit: (Zone) -> Unit
) : RecyclerView.Adapter<ZoneCardAdapter.VH>() {

    inner class VH(val b: ItemZoneCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemZoneCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = zones.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val z = zones[pos]
        h.b.apply {
            tvZoneName.text    = z.name
            tvCrowdCount.text  = "${"%,d".format(z.current)} / ${"%,d".format(z.capacity)}"
            tvDensity.text     = "${z.densityPercent}%"
            progressDensity.progress = z.densityPercent

            val (colorHex, bgRes, badgeText) = when (z.status) {
                "critical" -> Triple("#E24B4A", R.drawable.bg_badge_red,    "Critical")
                "warning"  -> Triple("#EF9F27", R.drawable.bg_badge_yellow, "Warning")
                else       -> Triple("#639922", R.drawable.bg_badge_green,  "OK")
            }
            tvStatus.text = badgeText
            tvStatus.setBackgroundResource(bgRes)
            tvDensity.setTextColor(Color.parseColor(colorHex))
            progressDensity.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex))

            btnPredict.setOnClickListener { onPredict(z) }
            root.setOnClickListener { onEdit(z) }
        }
    }
}
