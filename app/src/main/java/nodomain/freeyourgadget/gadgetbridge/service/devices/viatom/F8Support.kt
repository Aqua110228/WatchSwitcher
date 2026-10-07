package nodomain.freeyourgadget.gadgetbridge.service.devices.viatom

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.GenericImpedanceSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.viatom.F8WeightSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.F8WeightSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericImpedanceSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile
import nodomain.freeyourgadget.gadgetbridge.service.devices.viatom.UserData.Sex
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.withTransaction
import org.jetbrains.annotations.TestOnly
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.Period
import java.util.UUID


open class F8Support : AbstractBTLESingleDeviceSupport(LOG) {
    private val deviceInfoProfile: DeviceInfoProfile<F8Support>

    private val packetManager = BlePacketManager()
    private val cfg: ScaleConfig
    lateinit var activeUser: UserData

    enum class WeightUnit {
        KG,
        LB,
        ST_LB,
        ;

        companion object {
            fun fromGadgetbridgeWeightUnit(gadgetbridgeWU: nodomain.freeyourgadget.gadgetbridge.model.WeightUnit): WeightUnit =
                when (gadgetbridgeWU) {
                    nodomain.freeyourgadget.gadgetbridge.model.WeightUnit.KILOGRAM -> KG
                    nodomain.freeyourgadget.gadgetbridge.model.WeightUnit.POUND -> LB
                    nodomain.freeyourgadget.gadgetbridge.model.WeightUnit.STONE -> ST_LB
                    nodomain.freeyourgadget.gadgetbridge.model.WeightUnit.JIN -> {
                        GB.toast("Unsupported weight unit", Toast.LENGTH_LONG, GB.WARN)
                        KG
                    }
                }
        }
    }

    data class ScaleConfig(
        val rawWeightToGramsCoefficient: Double,
        val rawImpedanceToOhmsCoefficient: Double,
        val weightUnit: WeightUnit
    )

    init {
        addSupportedService(SERVICE_UUID)

        //TODO: should ST_LB be used for imperial units?!? Should a device settings be added?
        cfg = ScaleConfig(
            10.0,
            10.0,
            WeightUnit.fromGadgetbridgeWeightUnit(GBApplication.getPrefs().weightUnit)
        )

        val mListener = IntentListener { intent: Intent? ->
            intent?.action?.let { action ->
                when (action) {
                    DeviceInfoProfile.ACTION_DEVICE_INFO -> {
                        handleDeviceInfo(intent.getParcelableCompat<DeviceInfo>(DeviceInfoProfile.EXTRA_DEVICE_INFO)!!)
                    }
                }
            }
        }

        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION)
        deviceInfoProfile = DeviceInfoProfile<F8Support>(this)
        deviceInfoProfile.addListener(mListener)
        addSupportedProfile(deviceInfoProfile)
    }

    private fun handleDeviceInfo(deviceInfo: DeviceInfo) {
        LOG.debug("Device info: {}", deviceInfo)

        val versionCmd = GBDeviceEventVersionInfo()

        if (deviceInfo.hardwareRevision != null) {
            versionCmd.hwVersion = deviceInfo.hardwareRevision
        }

        if (deviceInfo.firmwareRevision != null) {
            versionCmd.fwVersion = deviceInfo.firmwareRevision
            versionCmd.fwVersion2 = deviceInfo.softwareRevision
        } else if (deviceInfo.softwareRevision != null) {
            versionCmd.fwVersion = deviceInfo.softwareRevision
        }

        handleGBDeviceEvent(versionCmd)

        if (deviceInfo.manufacturerName != null) {
            handleGBDeviceEvent(
                GBDeviceEventUpdateDeviceInfo(
                    "MANUFACTURER: ",
                    deviceInfo.manufacturerName
                )
            )
        }

        if (deviceInfo.modelNumber != null) {
            handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("MODEL: ", deviceInfo.modelNumber))
        }

        if (deviceInfo.serialNumber != null) {
            handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("SERIAL: ", deviceInfo.serialNumber))
        }
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)

        packetManager.resetAll()
        deviceInfoProfile.requestDeviceInfo(builder)


        builder.notify(UUID_CHARACTERISTICS_F8WEIGHT, true)
        builder.notify(UUID_CHARACTERISTICS_F8DEVICE, true)

        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        if (characteristic.uuid == UUID_CHARACTERISTICS_F8WEIGHT) {
            processPacket(value, false)
            return true
        }
        if (characteristic.uuid == UUID_CHARACTERISTICS_F8DEVICE) {
            processPacket(value, true)
            return true
        }

        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true
        }

        LOG.warn("Unhandled characteristic changed: {} {}", characteristic.uuid, GB.hexdump(value))
        return false
    }

    override fun onSendConfiguration(config: String) {
        // every setting is about users ATM, so just blindly send them
        sendUsers()
    }

    override fun useAutoConnect(): Boolean {
        return true
    }

    @TestOnly
    fun processPacket(packet: ByteArray): Any? {
        return processPacket(packet, false)
    }

    fun processPacket(packet: ByteArray, reply: Boolean): Any? {
        when (val result = packetManager.assemblePacketToData(packet)) {
            is ReassemblyResult.Complete -> {
                val ret = processReceivedData(result.data)
                if (reply)
                    sendReply(result.sequenceNumber)
                when (ret) {
                    is F8Message.DeviceInfo -> {
                        sendUsers()
                    }

                    is WeightRecord -> {
                        LOG.info("Received unstable Weight Record: {}", ret)

                        if (ret.weightKind == WeightRecord.WeightKind.full) {
                            LOG.info("Received Weight Record: {}", ret)

                            try {
                                GBApplication.acquireDB().use { db ->
                                    val f8WeightSampleProvider =
                                        F8WeightSampleProvider(device, db.daoSession)
                                    val genericImpedanceSampleProvider =
                                        GenericImpedanceSampleProvider(device, db.daoSession)
                                    val userId = DBHelper.getUser(db.daoSession).id
                                    val deviceId = DBHelper.getDevice(device, db.daoSession).id

                                    val weightSample = F8WeightSample(
                                        ret.timeEpochMillis,
                                        deviceId!!,
                                        userId!!,
                                        ret.weightKg.toFloat(),
                                        ret.bfaType,
                                        activeUser.userBasicData.index,
                                        activeUser.userBasicData.height,
                                        activeUser.age,
                                        activeUser.personType.ordinal,
                                        activeUser.sex.ordinal
                                    )

                                    f8WeightSampleProvider.addSample(weightSample)

                                    val impedanceSamples = ret.impedances?.map {
                                        GenericImpedanceSample(
                                            ret.timeEpochMillis,
                                            deviceId,
                                            userId,
                                            it.bodySection.ordinal,
                                            (it.frequencyKHz * 1000).toInt(),
                                            it.rawImpedance.toFloat()
                                        )
                                    }.orEmpty()

                                    genericImpedanceSampleProvider.addSamples(impedanceSamples)
                                }
                            } catch (e: Exception) {
                                LOG.error("saveWeightInfo - error", e)
                            }
                        }
                    }
                }
                packetManager.reset(result.sequenceNumber)
                return ret
            }

            is ReassemblyResult.Incomplete -> {
                LOG.debug("Sequence ${result.sequenceNumber}: ${result.received}/${result.expected} packets")
            }
        }
        return null
    }

    private fun sendReply(sequenceNumber: Int) {
        val packets =
            packetManager.splitDataInPackets(
                F8Message.ReplyCommand.encodePayload(
                    PayloadData.PayloadReplyTo(sequenceNumber)
                ), cfg
            )

        withTransaction("reply_$sequenceNumber") { builder ->
            packets.forEach { packet ->
                builder.write(
                    CHARACTERISTICS_WRITE_UUID,
                    *packet
                )
            }
        }
    }

    private fun buildUserDevicePreferenceKey(id: Int, pref: String): String {
        return "u${id}_scale_user_${pref}"
    }

    private fun sendUsers() {

        val activeUserIdx = devicePrefs.getInt("active_user", 1)
        LOG.info("Active user id: {}", activeUserIdx)
        val userList = buildList {
            for (i in 1..2) {
                val user = UserData(
                    UserBasicData(
                        index = i,
                        height = devicePrefs.getInt(
                            buildUserDevicePreferenceKey(i, "height_cm"),
                            ActivityUser.defaultUserHeightCm
                        ),
                        weightDecagrams = 100 * devicePrefs.getInt(
                            buildUserDevicePreferenceKey(
                                i,
                                "weight_kg"
                            ), ActivityUser.defaultUserWeightKg
                        ),
                        featureFlagsMask = UserBasicData.FeatureFlag.IMPEDANCE.bit
                    ),
                    age = Period.between(
                        devicePrefs.getLocalDate(
                            buildUserDevicePreferenceKey(i, "date_of_birth"),
                            ActivityUser.defaultUserDateOfBirth
                        ),
                        LocalDate.now()
                    ).years,
                    targetWeightDecagrams = 100 * devicePrefs.getInt(
                        buildUserDevicePreferenceKey(
                            i,
                            "goal_weight_kg"
                        ), ActivityUser.defaultUserGoalWeightKg
                    ),
                    personType = UserData.PersonType.STANDARD,
                    sex = if (ActivityUser.GENDER_MALE != devicePrefs.getInt(
                            buildUserDevicePreferenceKey(i, "gender"),
                            ActivityUser.defaultUserGender
                        )
                    ) Sex.FEMALE else Sex.MALE
                )

                add(user)

                if (activeUserIdx == i) {
                    activeUser = user
                    val packets =
                        packetManager.splitDataInPackets(
                            F8Message.UserInfo.encodePayload(user),
                            cfg
                        )
                    withTransaction("send user ${i}") { builder ->
                        packets.forEach { packet ->
                            builder.write(
                                CHARACTERISTICS_WRITE_UUID,
                                *packet
                            )
                        }
                    }
                }
            }
        }

        LOG.debug("Users: {}", userList)

        withTransaction("send user list") { builder ->
            packetManager.splitDataInPackets(
                F8Message.UserInfoList.encodePayload(
                    PayloadData.PayloadUserDataList(userList)
                ), cfg
            ).forEach { packet ->
                builder.write(
                    CHARACTERISTICS_WRITE_UUID,
                    *packet
                )
            }
        }
    }

    fun processReceivedData(data: ByteArray): Any? {
        require(data.isNotEmpty()) { LOG.error("Payload is empty") }

        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.BIG_ENDIAN)
        val processed = F8Message.decode(cfg, buffer)

//        LOG.debug(processed.toString())

        return processed
    }

    @TestOnly
    fun encodeTest(t: Any?, seqNum: Int): List<ByteArray> {
        return encodeTest(t, cfg, seqNum)
    }

    @TestOnly
    fun encodeTest(t: Any?, cfg: ScaleConfig, seqNum: Int): List<ByteArray> {

        if (t != null) {
            when (t) {
                is DecodedUserData -> return packetManager.splitDataInPackets(
                    F8Message.UserInfo.encodePayload(
                        PayloadData.PayloadDecodedUserData(t)
                    ), cfg, seqNum
                )
            }
        }
        return emptyList()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(F8Support::class.java)

        private val SERVICE_UUID: UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTICS_WRITE_UUID =
            UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTICS_F8WEIGHT: UUID =
            UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTICS_F8DEVICE: UUID =
            UUID.fromString("0000ffb3-0000-1000-8000-00805f9b34fb")

    }


}
