package com.crowdprediction.ui.alerts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crowdprediction.R
import com.crowdprediction.data.models.Alert
import com.crowdprediction.databinding.ItemAlertBinding

class AlertAdapter(
    private val onResolve: (Int) -> Unit
) : ListAdapter<Alert, AlertAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemAlertBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a = getItem(pos)
        val ctx = h.itemView.context
        h.b.apply {
            tvAlertZone.text = a.zone
            tvAlertMessage.text = a.message
            tvAlertTime.text = a.timestamp

            val (iconRes, colorRes, bgRes) = when (a.level) {
                "critical" -> Triple(R.drawable.ic_alert_critical, R.color.colorDanger, R.drawable.bg_alert_critical)
                "warning"  -> Triple(R.drawable.ic_alert_warning,  R.color.colorWarning, R.drawable.bg_alert_warning)
                else       -> Triple(R.drawable.ic_alert_info,     R.color.colorInfo, R.drawable.bg_alert_info)
            }
            ivAlertIcon.setImageResource(iconRes)
            tvAlertLevel.text = a.level.replaceFirstChar { it.uppercase() }
            tvAlertLevel.setTextColor(ctx.getColor(colorRes))
            root.setBackgroundResource(bgRes)
            btnResolve.setOnClickListener { onResolve(a.id) }
        }
    }

    class DiffCb : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(a: Alert, b: Alert) = a.id == b.id
        override fun areContentsTheSame(a: Alert, b: Alert) = a == b
    }
}
