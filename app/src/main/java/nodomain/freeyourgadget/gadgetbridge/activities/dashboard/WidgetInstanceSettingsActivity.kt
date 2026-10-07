package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceFragmentCompat
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance

/**
 * Hosts [WidgetInstanceSettingsFragment] for one placed widget instance.
 */
class WidgetInstanceSettingsActivity : AbstractSettingsActivityV2() {
    override fun newFragment(): PreferenceFragmentCompat {
        val instanceId = intent.getStringExtra(WidgetInstanceSettingsFragment.ARG_INSTANCE_ID).orEmpty()
        val typeId = intent.getStringExtra(WidgetInstanceSettingsFragment.ARG_TYPE_ID).orEmpty()
        return WidgetInstanceSettingsFragment.newInstance(instanceId, typeId)
    }

    companion object {
        fun startForWidget(context: Context, instance: WidgetInstance) {
            val intent = Intent(context, WidgetInstanceSettingsActivity::class.java)
            intent.putExtra(WidgetInstanceSettingsFragment.ARG_INSTANCE_ID, instance.instanceId)
            intent.putExtra(WidgetInstanceSettingsFragment.ARG_TYPE_ID, instance.typeId)
            context.startActivity(intent)
        }
    }
}
