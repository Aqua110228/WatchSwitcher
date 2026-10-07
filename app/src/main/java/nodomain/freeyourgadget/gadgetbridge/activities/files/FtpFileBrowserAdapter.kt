package nodomain.freeyourgadget.gadgetbridge.activities.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.internethelper.aidl.ftp.FtpEntry
import java.util.Date

class FtpFileBrowserAdapter(
    private val onOpen: (FtpEntry) -> Unit,
    private val onDownload: (FtpEntry) -> Unit,
    private val onShare: (FtpEntry) -> Unit,
    private val onDelete: (FtpEntry) -> Unit,
) : RecyclerView.Adapter<FtpFileBrowserAdapter.ViewHolder>() {
    private val entries = mutableListOf<FtpEntry>()

    fun setEntries(newEntries: List<FtpEntry>) {
        entries.clear()
        entries.addAll(
            newEntries.sortedWith(
                compareBy<FtpEntry> { it.type != FtpEntry.Type.DIRECTORY }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }
            ))
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_manager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context
        holder.name.text = entry.name

        if (entry.type == FtpEntry.Type.DIRECTORY) {
            holder.icon.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_folder))
            holder.description.visibility = View.GONE
            holder.menu.visibility = View.GONE
        } else {
            holder.icon.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_draft))
            val size = FileManagerAdapter.formatFileSize(entry.size)
            holder.description.text = if (entry.timestamp > 0) {
                "$size, ${DateTimeUtils.formatDateTime(Date(entry.timestamp))}"
            } else {
                size
            }
            holder.description.visibility = View.VISIBLE
            holder.menu.visibility = View.VISIBLE
            holder.menu.setOnClickListener {
                val menu = PopupMenu(context, holder.menu)
                menu.inflate(R.menu.ftp_file_browser_file)
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.ftp_file_browser_file_download -> onDownload(entry)
                        R.id.ftp_file_browser_file_share -> onShare(entry)
                        R.id.ftp_file_browser_file_delete -> onDelete(entry)
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }
                menu.show()
            }
        }

        holder.itemView.setOnClickListener { onOpen(entry) }
    }

    override fun getItemCount(): Int = entries.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.file_icon)
        val name: TextView = itemView.findViewById(R.id.file_name)
        val description: TextView = itemView.findViewById(R.id.file_description)
        val menu: ImageView = itemView.findViewById(R.id.file_menu)
    }
}
