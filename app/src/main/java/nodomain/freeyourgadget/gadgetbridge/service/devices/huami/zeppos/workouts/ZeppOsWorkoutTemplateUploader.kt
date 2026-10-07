package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.database.repository.WorkoutTemplateRepository
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.ZeppOsFileTransferService
import org.slf4j.LoggerFactory

/**
 * Sends a [WorkoutTemplate] to a Zepp OS device and updates the template's sync state.
 */
object ZeppOsWorkoutTemplateUploader {
    private val LOG = LoggerFactory.getLogger(ZeppOsWorkoutTemplateUploader::class.java)

    private const val URL = "sport://file_transfer?appId=7074120303&params={}"

    private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/\\p{Cntrl}]")

    private const val MAX_NAME_LENGTH = 64

    fun upload(
        context: Context,
        fileTransferService: ZeppOsFileTransferService,
        templateId: Long,
    ) {
        val template = WorkoutTemplateRepository.load(templateId)
        if (template == null) {
            LOG.error("Workout template {} not found", templateId)
            return
        }

        val sportType = ZeppOsWorkoutCodes.sportType(template.activityKind)
        if (sportType == null) {
            LOG.error("No Zepp OS sport mapping for {}", template.activityKind)
            WorkoutTemplateRepository.markSyncFailed(templateId, "Unknown sport type")
            return
        }

        // The id of the previous sync is reused, so that the watch replaces the template
        val remoteId = template.syncRemoteId?.toLongOrNull() ?: (System.currentTimeMillis() * 1000L)

        val payload = ZeppOsWorkoutTemplateEncoder.encode(template, remoteId)
        if (payload == null) {
            LOG.error("Failed to encode workout template {} for Zepp OS", templateId)
            WorkoutTemplateRepository.markSyncFailed(templateId, "JSON encoding failed")
            return
        }

        // The hash is taken before the upload, so an edit made during the upload shows as out of date
        val contentHash = WorkoutTemplateRepository.contentHash(template)
        WorkoutTemplateRepository.markSyncPending(templateId)

        val filename = filename(sportType, remoteId, template.name)
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)

        LOG.info("Uploading workout template {} as {} ({} bytes)", templateId, filename, bytes.size)

        fileTransferService.sendFile(URL, filename, bytes, false, object : ZeppOsFileTransferService.UploadCallback {
            override fun onFileUploadFinish(success: Boolean) {
                LOG.info("Finished workout template {} upload, success={}", templateId, success)
                if (success) {
                    WorkoutTemplateRepository.markSynced(templateId, remoteId.toString(), contentHash)
                } else {
                    WorkoutTemplateRepository.markSyncFailed(
                        templateId,
                        context.getString(R.string.workout_template_sync_failed_transfer)
                    )
                }
            }

            override fun onFileUploadProgress(progress: Int) {
                LOG.trace("Workout template {} upload progress: {}/{}", templateId, progress, bytes.size)
            }
        })
    }

    /**
     * The file name the watch expects, for example `training_52_1790193217000011_Strength Training.json`.
     */
    private fun filename(sportType: Int, remoteId: Long, name: String): String {
        val safeName = ILLEGAL_FILENAME_CHARS.replace(name, "")
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifEmpty { remoteId.toString() }
        return "training_${sportType}_${remoteId}_$safeName.json"
    }
}
