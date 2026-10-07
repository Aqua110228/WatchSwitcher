package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Bundle
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NavigationDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setupPreferences()
    }

    private fun setupPreferences() {
        setPreferencesFromResource(R.xml.debug_preferences_navigation, null)

        onClick(PREF_DEBUG_NAVIGATION_SEND) { sendNavigationInfoSpec() }
        onClick(PREF_DEBUG_NAVIGATION_RESET) { resetPreferences() }

        setInputTypeNumber(PREF_DEBUG_NAVIGATION_TOTALTIMETODESTINATION)
        setInputTypeNumber(PREF_DEBUG_NAVIGATION_COMPLETIONPERCENT)

        setListPreferenceEntries(PREF_DEBUG_NAVIGATION_NEXTACTION, ACTIONS.keys.toTypedArray())
    }

    private fun sendNavigationInfoSpec() {
        val sharedPreferences = preferenceManager.sharedPreferences!!

        val navigationInfoSpec = NavigationInfoSpec()
        navigationInfoSpec.instruction = sharedPreferences.getString(PREF_DEBUG_NAVIGATION_INSTRUCTION, "Turn left onto Main Street")
        navigationInfoSpec.nextAction = ACTIONS[sharedPreferences.getString(PREF_DEBUG_NAVIGATION_NEXTACTION, "ACTION_TURN_LEFT")] ?: 0
        navigationInfoSpec.distanceToTurn = sharedPreferences.getString(PREF_DEBUG_NAVIGATION_DISTANCETOTURN, "100m")
        navigationInfoSpec.distanceToTarget = sharedPreferences.getString(PREF_DEBUG_NAVIGATION_DISTANCETOTARGET, "2.5km")
        navigationInfoSpec.totalTimeToDestination = sharedPreferences.getString(PREF_DEBUG_NAVIGATION_TOTALTIMETODESTINATION, "600")?.toIntOrNull()
        // Set the ETA after totalTimeToDestination, which also sets the ETA
        sharedPreferences.getString(PREF_DEBUG_NAVIGATION_ETA, null)?.takeIf { it.isNotBlank() }?.let {
            navigationInfoSpec.ETA = it
        }
        navigationInfoSpec.completionPercent = sharedPreferences.getString(PREF_DEBUG_NAVIGATION_COMPLETIONPERCENT, "25")?.toIntOrNull() ?: 0

        LOG.debug("Sending {}", navigationInfoSpec)

        runOnDebugDevices("Send NavigationInfoSpec") {
            GBApplication.deviceService(it).onSetNavigationInfo(navigationInfoSpec)
        }
    }

    private fun resetPreferences() {
        preferenceScreen.removeAll()

        preferenceManager.sharedPreferences!!.edit(true) {
            remove(PREF_DEBUG_NAVIGATION_INSTRUCTION)
            remove(PREF_DEBUG_NAVIGATION_NEXTACTION)
            remove(PREF_DEBUG_NAVIGATION_DISTANCETOTURN)
            remove(PREF_DEBUG_NAVIGATION_DISTANCETOTARGET)
            remove(PREF_DEBUG_NAVIGATION_TOTALTIMETODESTINATION)
            remove(PREF_DEBUG_NAVIGATION_ETA)
            remove(PREF_DEBUG_NAVIGATION_COMPLETIONPERCENT)
        }

        // Reload the preference screen to reflect the changes
        setupPreferences()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(NavigationDebugFragment::class.java)

        private val ACTIONS: Map<String, Int> = linkedMapOf(
            "ACTION_CONTINUE" to NavigationInfoSpec.ACTION_CONTINUE,
            "ACTION_TURN_LEFT" to NavigationInfoSpec.ACTION_TURN_LEFT,
            "ACTION_TURN_LEFT_SLIGHTLY" to NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY,
            "ACTION_TURN_LEFT_SHARPLY" to NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY,
            "ACTION_TURN_RIGHT" to NavigationInfoSpec.ACTION_TURN_RIGHT,
            "ACTION_TURN_RIGHT_SLIGHTLY" to NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY,
            "ACTION_TURN_RIGHT_SHARPLY" to NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY,
            "ACTION_KEEP_LEFT" to NavigationInfoSpec.ACTION_KEEP_LEFT,
            "ACTION_KEEP_RIGHT" to NavigationInfoSpec.ACTION_KEEP_RIGHT,
            "ACTION_UTURN_LEFT" to NavigationInfoSpec.ACTION_UTURN_LEFT,
            "ACTION_UTURN_RIGHT" to NavigationInfoSpec.ACTION_UTURN_RIGHT,
            "ACTION_OFFROUTE" to NavigationInfoSpec.ACTION_OFFROUTE,
            "ACTION_ROUNDABOUT_RIGHT" to NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT,
            "ACTION_ROUNDABOUT_LEFT" to NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT,
            "ACTION_ROUNDABOUT_STRAIGHT" to NavigationInfoSpec.ACTION_ROUNDABOUT_STRAIGHT,
            "ACTION_ROUNDABOUT_UTURN" to NavigationInfoSpec.ACTION_ROUNDABOUT_UTURN,
            "ACTION_FINISH" to NavigationInfoSpec.ACTION_FINISH,
            "ACTION_MERGE" to NavigationInfoSpec.ACTION_MERGE,
        )

        private const val PREF_DEBUG_NAVIGATION_SEND = "pref_debug_navigation_send"
        private const val PREF_DEBUG_NAVIGATION_RESET = "pref_debug_navigation_reset"
        private const val PREF_DEBUG_NAVIGATION_INSTRUCTION = "pref_debug_navigation_instruction"
        private const val PREF_DEBUG_NAVIGATION_NEXTACTION = "pref_debug_navigation_nextAction"
        private const val PREF_DEBUG_NAVIGATION_DISTANCETOTURN = "pref_debug_navigation_distanceToTurn"
        private const val PREF_DEBUG_NAVIGATION_DISTANCETOTARGET = "pref_debug_navigation_distanceToTarget"
        private const val PREF_DEBUG_NAVIGATION_TOTALTIMETODESTINATION = "pref_debug_navigation_totalTimeToDestination"
        private const val PREF_DEBUG_NAVIGATION_ETA = "pref_debug_navigation_eta"
        private const val PREF_DEBUG_NAVIGATION_COMPLETIONPERCENT = "pref_debug_navigation_completionPercent"
    }
}
