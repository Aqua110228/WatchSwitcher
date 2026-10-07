package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemWorkoutSportPickerBinding
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

/**
 * One row per [ActivityKind], with its icon and label.
 */
class SportPickerAdapter(context: Context, sports: List<ActivityKind>) :
    ArrayAdapter<ActivityKind>(context, R.layout.item_workout_sport_picker, sports) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemWorkoutSportPickerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ).also {
                it.root.tag = it
            }
        } else {
            convertView.tag as ItemWorkoutSportPickerBinding
        }
        val activityKind = getItem(position)!!
        binding.workoutSportPickerIcon.setImageResource(activityKind.icon)
        binding.workoutSportPickerLabel.text = activityKind.getLabel(context)
        return binding.root
    }
}
