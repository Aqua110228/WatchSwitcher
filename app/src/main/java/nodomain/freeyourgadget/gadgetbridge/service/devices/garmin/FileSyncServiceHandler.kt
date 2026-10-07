/*  Copyright (C) 2025-2026 José Rebelo, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin

import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileSyncService
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType.FILETYPE
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.FileDownloadedDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.util.protobuf.buildWith
import org.slf4j.LoggerFactory

class FileSyncServiceHandler(val deviceSupport: GarminSupport) {
    private var nextPageId: Int? = null
    private var cursorId: Int? = null

    fun handle(fileSyncService: GdiFileSyncService.FileSyncService): GdiFileSyncService.FileSyncService? {
        return when {
            fileSyncService.hasNewFileNotification() -> handleNewFileNotification(fileSyncService.newFileNotification)
            fileSyncService.hasFileListResponse() -> handleFileListResponse(fileSyncService.fileListResponse)
            fileSyncService.hasFileResponse() -> handleFileResponse(fileSyncService.fileResponse)
            fileSyncService.hasFileModifiedFlags() -> handleFileModifiedFlags(fileSyncService.fileModifiedFlags)
            fileSyncService.hasTransferStatusRequest() -> handleTransferStatusRequest(fileSyncService.transferStatusRequest)
            fileSyncService.hasFileUpdateNotification() -> handleFileUpdateNotification(fileSyncService.fileUpdateNotification)
            fileSyncService.hasStartSyncNotification() -> handleStartSyncNotification(fileSyncService.startSyncNotification)
            else -> {
                LOG.warn("Unhandled file sync service: {}", fileSyncService)
                return null
            }
        }
    }

    private fun handleStartSyncNotification(startSyncNotification: GdiFileSyncService.StartSyncNotification): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got start sync notification: {}", startSyncNotification)
        deviceSupport.onFetchRecordedData(RecordedDataTypes.TYPE_ALL)
        return null
    }


    private fun handleFileUpdateNotification(fileUpdateNotification: GdiFileSyncService.FileUpdateNotification): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got file updated flags: {}", fileUpdateNotification)
        // no action required
        return null
    }

    private fun handleFileModifiedFlags(fileModifiedFlags: GdiFileSyncService.FileModifiedFlags): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got file modified flags: {}", fileModifiedFlags)
        // no action required
        return null
    }

    private fun handleTransferStatusRequest(transferStatusRequest: GdiFileSyncService.TransferStatusRequest): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got transfer status request: {}", transferStatusRequest)
        val response = GdiFileSyncService.TransferStatusResponse.newBuilder().buildWith {
            unk1 = 1
        }
        return GdiFileSyncService.FileSyncService.newBuilder().buildWith {
            transferStatusResponse = response
        }
    }

    private fun handleNewFileNotification(newFileNotification: GdiFileSyncService.NewFileNotification): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got new file notification: {}", newFileNotification)

        for (file in newFileNotification.fileList) {
            if (!file.hasType()) {
                LOG.warn("New file has no type: {}", file)
                continue
            }

            conditionallyDownload(file, null)
        }
        return null
    }

    private fun handleFileResponse(fileResponse: GdiFileSyncService.FileResponse): GdiFileSyncService.FileSyncService? {
        LOG.debug("Got file response: {}", fileResponse)

        if (fileResponse.status != 0) {
            LOG.warn("File download failed with status {}", fileResponse.status)
            // Signal to the support class that the download failed so it can also continue to the next one
            val fileDownloadedDeviceEvent = FileDownloadedDeviceEvent()
            fileDownloadedDeviceEvent.success = false
            deviceSupport.evaluateGBDeviceEvent(fileDownloadedDeviceEvent)
        } else {
            deviceSupport.downloadFileFromServiceV2(fileResponse.handle)
        }

        return null
    }

    private fun handleFileListResponse(fileListResponse: GdiFileSyncService.FileListResponse): GdiFileSyncService.FileSyncService? {
        LOG.debug(
            "Handling file list response with status={}, files={}, cursorId={}, nextPageId={}",
            if (fileListResponse.hasStatus()) fileListResponse.status else null,
            fileListResponse.fileList.size,
            if (fileListResponse.hasCursorId()) fileListResponse.cursorId else null,
            if (fileListResponse.hasNextPageId()) fileListResponse.nextPageId else null,
        )

        val fetchUnknownFiles = deviceSupport.devicePrefs.fetchUnknownFiles

        // Only the first entry for a type seems to contain the type name, so keep track of them
        val nameMap: MutableMap<Int?, String?> = HashMap()
        for (file in fileListResponse.fileList) {
            if (!file.hasType()) {
                LOG.warn("Ignoring listed file without type information: {}", file)
                continue
            }

            if (file.type.hasNameId() && file.type.hasName()) {
                nameMap.put(file.type.nameId, file.type.name)
            }

            var typeName = if (file.type.hasName()) {
                file.type.name
            } else if (file.type.hasNameId()) {
                nameMap[file.type.nameId]
            } else {
                null
            }

            conditionallyDownload(file, typeName)
        }

        // #5461 - some watches to not send the next page ID
        // however, from previous logs, it always seems to match the max seen across all sent items, so attempt
        // to fall back to that as a workaround so we can fetch the subsequent files
        nextPageId = fileListResponse.nextPageId

        // The device sets cursor_id when there are more items pending for *this same* listing
        // request. Keep pulling pages within this cursor immediately instead of stopping after
        // one page - otherwise anything past the first ~100 items (which can easily be all SPORTS
        // backlog) is never seen.
        cursorId = if (fileListResponse.hasCursorId()) fileListResponse.cursorId else null
        if (cursorId != null) {
            deviceSupport.sendProtobufRequest(
                "continue file list",
                Smart.newBuilder().setFileSyncService(requestFileList()).build()
            )
        }

        return null
    }

    fun requestFileList(): GdiFileSyncService.FileSyncService {
        LOG.debug("Requesting file list starting at page {} (cursorId={})", nextPageId, cursorId)

        val fileListRequestBuilder = GdiFileSyncService.FileListRequest.newBuilder().apply {
            // Exclusion flags? If we omit this, it sends back already synced files going back months.
            addFlags1(GdiFileSyncService.FileId.newBuilder().setId1(FLAGS_SYNCED).setId2(FLAGS_SYNCED).build())
            addFlags2(GdiFileSyncService.FileId.newBuilder().setId1(FLAGS_SYNCED).setId2(FLAGS_SYNCED).build())
        }

        val currentcursorId = cursorId
        if (currentcursorId != null) {
            fileListRequestBuilder.cursorId = currentcursorId
        } else {
            nextPageId?.let { fileListRequestBuilder.startPageId = it }
        }

        return GdiFileSyncService.FileSyncService.newBuilder().buildWith {
            fileListRequest = fileListRequestBuilder.build()
        }
    }

    fun requestFile(fileToRequest: GdiFileSyncService.File): GdiFileSyncService.FileSyncService {
        LOG.debug(
            "Requesting file: {}/{} ({})",
            fileToRequest.id.id1,
            fileToRequest.id.id2,
            fileToRequest.type.name
        )
        return GdiFileSyncService.FileSyncService.newBuilder().buildWith {
            fileRequest = GdiFileSyncService.FileRequest.newBuilder().buildWith {
                file = fileToRequest
                unk2 = 24
                unk3 = 0
                unk4 = 0
                unk5 = 15
            }
        }
    }

    fun markSynced(syncFile: GdiFileSyncService.File): GdiFileSyncService.FileSyncService? {
        val fetchUnknownFiles = deviceSupport.devicePrefs.fetchUnknownFiles
        if (fetchUnknownFiles) {
            // Since some of the unknown files are not really supposed to be marked as synced (eg. settings, courses, locations)
            // let's avoid sending the command if it's not a file that we process
            if (syncFile.type.name == null) {
                LOG.warn("Will not mark {}/{} as synced - unknown type", syncFile.id.id1, syncFile.id.id2)
                return null
            }
            val fileType = FileType.FILETYPE.findByTypeName(syncFile.type.name);
            if (fileType == null || !fileType.pull) {
                LOG.warn(
                    "Will not mark {}/{} ({}) as synced - not a file to process",
                    syncFile.id.id1,
                    syncFile.id.id2,
                    syncFile.type.name
                )
                return null
            }
        }

        return GdiFileSyncService.FileSyncService.newBuilder().buildWith {
            fileSetFlags = GdiFileSyncService.FileSetFlags.newBuilder().buildWith {
                file = syncFile.id
                setFlags = GdiFileSyncService.FileId.newBuilder().setId1(FLAGS_SYNCED).setId2(FLAGS_SYNCED).build()
            }
        }
    }

    private fun conditionallyDownload(file: GdiFileSyncService.File, rawTypeName: String?) {
        var computedName: String? = null;
        if (rawTypeName == null || rawTypeName.length < 1) {
            if (file.hasType() && file.type.hasName()) {
                computedName = file.type.name;
            } else if (file.hasType() && file.type.hasCode()) {
                if (file.type.code == 3) {
                    computedName = FILETYPE.DEVICE_XML.name
                } else {
                    // generate a name for fetchUnknownFiles
                    computedName = "TYPE_" + file.type.code;
                }
            } else {
                LOG.warn("Ignoring file with no type name: {}", file)
                return
            }
        } else {
            computedName = rawTypeName;
        }

        val fileType = FileType.FILETYPE.findByTypeName(computedName)
        val typeName = if (fileType != null) {
            if (fileType.typeName != null) {
                fileType.typeName
            } else {
                fileType.name
            }
        } else {
            computedName
        }

        if (!deviceSupport.devicePrefs.fetchUnknownFiles) {
            if (fileType == null || !fileType.pull) {
                LOG.warn("Ignoring file: {} {}", typeName, file)
                return
            }
        }

        // ensure the to-be-downloaded file has a good type.name
        // used by a later processing stage for non-FIT files
        val actualFile: GdiFileSyncService.File
        if (!file.hasType() || !file.type.hasName() || !typeName.contentEquals(file.type.name)) {
            val builder = file.toBuilder()
            builder.setType(GdiFileSyncService.FileType.newBuilder().setName(typeName).build())
            actualFile = builder.build();
        } else {
            actualFile = file
        }

        LOG.debug(
            "Adding file to download: {}/{} ({})",
            actualFile.id.id1,
            actualFile.id.id2,
            actualFile.type.name
        )
        deviceSupport.addFileToDownloadList(actualFile)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(FileSyncServiceHandler::class.java)

        private const val FLAGS_SYNCED = 42405L
    }
}
