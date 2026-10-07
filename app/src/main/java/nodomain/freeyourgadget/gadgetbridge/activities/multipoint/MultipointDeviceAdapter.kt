package nodomain.freeyourgadget.gadgetbridge.activities.multipoint

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import nodomain.freeyourgadget.gadgetbridge.R

class MultipointDeviceAdapter(
    private val devices: List<MultipointDevice>,
    private val onAction: (MultipointDevice, Action) -> Unit
) : RecyclerView.Adapter<MultipointDeviceAdapter.DeviceViewHolder>() {

    var allowAction = false

    enum class Action {
        CONNECT,
        DISCONNECT,
        FORGET,
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceIcon: ImageView = itemView.findViewById(R.id.device_icon)
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val deviceAddress: TextView = itemView.findViewById(R.id.device_address)
        val connectionButton: Button = itemView.findViewById(R.id.connection_button)
        val forgetButton: Button = itemView.findViewById(R.id.forget_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_multipoint_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val context = holder.itemView.context

        holder.deviceName.text = device.name ?: context.getString(R.string.unknown)
        holder.deviceAddress.text = if (device.isActive) {
            context.getString(R.string.bluetooth_multipoint_active_device, device.address)
        } else {
            device.address
        }

        val (colorFilter, buttonText, action) = if (device.isConnected) {
            Triple(
                null,
                context.getString(R.string.controlcenter_disconnect),
                Action.DISCONNECT
            )
        } else {
            Triple(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }),
                context.getString(R.string.connect),
                Action.CONNECT
            )
        }

        val allowConnect = allowAction && devices.count { it.isConnected } < 2
        val allowDisconnect = allowAction

        holder.deviceIcon.colorFilter = colorFilter
        holder.connectionButton.text = buttonText
        holder.connectionButton.isEnabled = when (action) {
            Action.CONNECT -> allowConnect
            Action.DISCONNECT -> allowDisconnect
            Action.FORGET -> false
        }
        holder.connectionButton.setOnClickListener {
            onAction(device, action)
        }
        holder.forgetButton.visibility = if (device.canForget) View.VISIBLE else View.GONE
        holder.forgetButton.isEnabled = allowAction
        holder.forgetButton.setOnClickListener {
            onAction(device, Action.FORGET)
        }
    }

    override fun getItemCount(): Int = devices.size
}
