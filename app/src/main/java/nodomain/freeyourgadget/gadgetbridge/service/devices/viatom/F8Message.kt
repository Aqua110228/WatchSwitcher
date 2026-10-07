package nodomain.freeyourgadget.gadgetbridge.service.devices.viatom

import kotlinx.serialization.Serializable
import nodomain.freeyourgadget.gadgetbridge.service.devices.viatom.UserData.Sex
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

sealed class F8Message {
    abstract val messageType: Int


    data object DeviceInfo : F8Message() {
        override val messageType = 0xA1

        //This message contains some kind of information about the device, but we don't have enough data points to understand them
        override fun decode(buffer: ByteBuffer): Any {

            //It might be that the payload structure is different than int, byte, int
            val unkInt = buffer.getInt()
            val unk = buffer.get().toInt() and 0xFF
            val unkInt2 = buffer.getInt()
            if ((unkInt != 863049251) or (unk != 0) or (unkInt2 != 402721304))
                LOG.info(
                    "Please report these values to the project: DeviceInfo: {},{},{}",
                    unkInt,
                    unk,
                    unkInt2
                )
            return this
        }
    }

    data object UnstableWeight : F8Message() {
        override val messageType = 0xA2

        override fun decode(cfg: F8Support.ScaleConfig, buffer: ByteBuffer): WeightRecord {
            val stable = buffer.get().toInt() and 0xFF
            val weightRawBytes = buffer.getInt()
            return WeightRecord(
                time = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                weightKind = if (stable == 3) WeightRecord.WeightKind.stable_measuring_impedances else WeightRecord.WeightKind.unstable,
                weightKg = (weightRawBytes and 0x3FFFF) / (cfg.rawWeightToGramsCoefficient * 100.0), // The scale always returns the weight in grams, no matter which unit is chosen
                bfaType = (weightRawBytes shr 24) and 0xFF
            )
        }
    }

    data object StableWeight : F8Message() {
        override val messageType = 0xA3

        override fun decode(cfg: F8Support.ScaleConfig, buffer: ByteBuffer): WeightRecord {
            return decodeWeight(
                cfg,
                buffer,
                ts = Instant.now().atZone(ZoneId.systemDefault()).toEpochSecond()
            )
        }
    }

    data object WeightHistory : F8Message() {
        override val messageType = 0xA4

        override fun decode(cfg: F8Support.ScaleConfig, buffer: ByteBuffer): WeightRecord {
            val ts = buffer.getInt()
            return decodeWeight(cfg, buffer, ts.toLong())
        }
    }

    fun decodeWeight(cfg: F8Support.ScaleConfig, buffer: ByteBuffer, ts: Long): WeightRecord {
        val weightRawBytes = buffer.getInt()
        val unk = buffer.get().toInt() and 0xFF //separator?
        if (unk != 0)
            LOG.info(
                "Please report these values to the project: WeightHistory: {}",
                GB.hexdump(buffer.array())
            )

        val impedances: MutableList<ImpedanceReading> = ArrayList()
        for (i in 0..<10) {
            val impValue = buffer.getShort().toInt() and 0xFFFF
            impedances.add(
                ImpedanceReading(
                    bodySection = ImpedanceReading.BodySection.entries[i % 5],
                    frequencyKHz = (if (i < 5) 20.0 else 100.0),
                    rawImpedance = impValue / cfg.rawImpedanceToOhmsCoefficient
                )
            )
        }

        return WeightRecord(
            time = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            weightKind = WeightRecord.WeightKind.full,
            weightKg = (weightRawBytes and 0x3FFFF) / (cfg.rawWeightToGramsCoefficient * 100.0), // The scale always returns the weight in grams, no matter which unit is chosen
            bfaType = (weightRawBytes shr 24) and 0xFF,
            impedances as List<ImpedanceReading>?
        )
    }

    data object ReplyCommand : F8Message() {
        override val messageType = 0xB0

        override fun decode(buffer: ByteBuffer) {

            val replyPackage = buffer.get().toInt() and 0XFF
            val status = buffer.get().toInt() and 0xFF

            LOG.info("ReplyCommand. Index: {}, status: {}", replyPackage, status)
        }

        override fun encodePayload(p: PayloadData): ByteArray {
            return when (p) {
                is PayloadData.PayloadReplyTo -> encodePayload(p.sequence)
                else -> error(LOG.error("Unsupported type: ${p::class.simpleName}"))
            }
        }

        fun encodePayload(p: Int): ByteArray {
            val buffer = ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN)
            buffer.put(messageType.toByte())
            buffer.put(p.toByte())
            buffer.put(0) //status OK?!?
            return buffer.array()
        }
    }

    data object UserInfo : F8Message() {
        override val messageType = 0xB1

        override fun decode(buffer: ByteBuffer): DecodedUserData {
            val ts = buffer.getInt()
            val utcOffsetMinutes = buffer.getShort().toInt()
            val userIndex = buffer.get().toInt()
            val height = buffer.get().toInt() and 0xFF
            val weight = buffer.getShort().toInt() and 0xFFFF
            val ageSex = buffer.get().toInt() and 0xff
            val features = buffer.get().toInt() and 0xff
            val targetWeight = buffer.getShort().toInt() and 0xFFFF

            val userData = UserData(
                UserBasicData(
                    index = userIndex,
                    height = height,
                    weightDecagrams = weight,
                    featureFlagsMask = features
                ),
                age = ageSex and 0x7F,
                targetWeightDecagrams = targetWeight,
                personType = UserData.PersonType.STANDARD,
                sex = (if ((ageSex and 0x80) == 0x80) Sex.MALE else Sex.FEMALE)
            )

            val instant = Instant.ofEpochSecond(ts.toLong())
            val offset = ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60)

            return DecodedUserData(
                time = ZonedDateTime.ofInstant(instant, offset),
                userData
            )
        }

        override fun encodePayload(p: PayloadData): ByteArray {
            return when (p) {
                is PayloadData.PayloadUserData -> encodePayload(p.user)
                is PayloadData.PayloadDecodedUserData -> encodePayload(p.user.userData, p.user.time)
                else -> error(LOG.error("Unsupported type: ${p::class.simpleName}"))
            }
        }

        fun encodePayload(userData: UserData): ByteArray {
            val now = LocalDateTime.now().atZone(ZoneId.systemDefault())

            return encodePayload(userData, now)
        }


        fun encodePayload(userData: UserData, ts: ZonedDateTime): ByteArray {
            val weightInt = userData.userBasicData.weightDecagrams
            val targetWeightInt = userData.targetWeightDecagrams
            val ageSex = userData.age or (if (userData.sex == Sex.MALE) 0x80 else 0x00)

            val buffer = ByteBuffer.allocate(15).order(ByteOrder.BIG_ENDIAN)
            buffer.put(messageType.toByte())
            buffer.putInt(ts.toEpochSecond().toInt())
            buffer.putShort((ts.offset.totalSeconds / 60).toShort())
            buffer.put(userData.userBasicData.index.toByte())
            buffer.put(userData.userBasicData.height.toByte())
            buffer.putShort(weightInt.toShort())
            buffer.put(ageSex.toByte())
            buffer.put(
                UserBasicData.FeatureFlag.toBitmask(userData.userBasicData.getFeatureFlags())
                    .toByte()
            )
            buffer.putShort(targetWeightInt.toShort())
            return buffer.array()
        }

    }

    data object UserInfoList : F8Message() {
        override val messageType = 0xB2

        override fun decode(buffer: ByteBuffer): List<UserBasicData> {
            val userCount = buffer.get().toInt() and 0xFF

            return List(userCount) { userIndex ->
                UserBasicData(
                    index = userIndex,
                    height = buffer.get().toInt() and 0xff,
                    weightDecagrams = buffer.getShort().toInt() and 0xFFFF,
                    featureFlagsMask = buffer.get().toInt() and 0xff
                )
            }

        }

        override fun encodePayload(p: PayloadData): ByteArray {
            return when (p) {
                is PayloadData.PayloadUserBasicData -> encodePayload(p.user)
                is PayloadData.PayloadUserDataList -> encodePayload(p.users)
                else -> error(LOG.error("Unsupported type: ${p::class.simpleName}"))
            }
        }

        fun encodePayload(p: List<UserData>): ByteArray {
            val buffer = ByteBuffer.allocate(2 + p.size * 4).order(ByteOrder.BIG_ENDIAN)

            buffer.put(messageType.toByte())
            buffer.put(p.size.toByte())
            p.forEach {
                buffer.put(it.userBasicData.height.toByte())
                buffer.putShort(it.userBasicData.weightDecagrams.toShort())
                buffer.put(
                    UserBasicData.FeatureFlag.toBitmask(it.userBasicData.getFeatureFlags()).toByte()
                )
            }
            return buffer.array()
        }

        fun encodePayload(p: UserBasicData): ByteArray {
            val buffer = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
            buffer.put(messageType.toByte())
            buffer.put(1)
            buffer.put(p.height.toByte())
            buffer.putShort(p.weightDecagrams.toShort())
            buffer.put(UserBasicData.FeatureFlag.toBitmask(p.getFeatureFlags()).toByte())
            return buffer.array()
        }
    }

    open fun encodePayload(p: PayloadData): ByteArray {
        throw IllegalAccessException("encodePayload() not implemented")
    }

    open fun decode(buffer: ByteBuffer): Any? {
        throw IllegalAccessException("decode() not implemented")
    }

    open fun decode(cfg: F8Support.ScaleConfig, buffer: ByteBuffer): Any? {
        throw IllegalAccessException("decode() not implemented")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(F8Message::class.java)

        fun decode(cfg: F8Support.ScaleConfig, buffer: ByteBuffer): Any? {
            return when (val code = buffer.get().toInt() and 0xFF) {
                0xA1 -> DeviceInfo.decode(buffer)
                0xA2 -> UnstableWeight.decode(cfg, buffer)
                0xA3 -> StableWeight.decode(cfg, buffer)
                0xA4 -> WeightHistory.decode(cfg, buffer)

                // Outgoing messages, decoding is useful mostly for testing
                0xB0 -> ReplyCommand.decode(buffer)
                0xB1 -> UserInfo.decode(buffer)
                0xB2 -> UserInfoList.decode(buffer)
                else -> {
                    LOG.error("Message type unknown: 0x{}", code.toHexString())
                    null
                }
            }
        }
    }
}

sealed class PayloadData {
    data class PayloadUserBasicData(val user: UserBasicData) : PayloadData()
    data class PayloadUserDataList(val users: List<UserData>) : PayloadData()
    data class PayloadUserData(val user: UserData) : PayloadData()
    data class PayloadDecodedUserData(val user: DecodedUserData) : PayloadData()
    data class PayloadReplyTo(val sequence: Int) : PayloadData()
}

data class WeightRecord(
    val time: LocalDateTime,
    val weightKind: WeightKind,
    val weightKg: Double,
    val bfaType: Int, //unknown, label seen in logs
    val impedances: List<ImpedanceReading>? = null
) {
    val timeEpochMillis: Long
        get() = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    enum class WeightKind {
        unstable,
        stable_measuring_impedances,
        full
    }
}

data class ImpedanceReading(
    val bodySection: BodySection,
    val frequencyKHz: Double,
    val rawImpedance: Double
) {
    enum class BodySection {
        TORSO,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG,
        UNKNOWN
    }
}

@Serializable
data class UserBasicData(
    val index: Int,
    val height: Int,
    val weightDecagrams: Int,
    val featureFlagsMask: Int
) {
    fun getFeatureFlags(): Set<FeatureFlag> = FeatureFlag.fromBitmask(featureFlagsMask)

    enum class FeatureFlag(val bit: Int) {
        IMPEDANCE(0x01),
        BALANCE(0x02),
        HEART_RATE(0x04),
        GRAVITY(0x08);

        companion object {
            fun fromBitmask(mask: Int): Set<FeatureFlag> {
                return entries.filter { (mask and it.bit) != 0 }.toSet()
            }

            fun toBitmask(flags: Set<FeatureFlag>): Int {
                return flags.fold(0) { acc, flag -> acc or flag.bit }
            }
        }
    }
}

@Serializable
data class UserData(
    val userBasicData: UserBasicData,
    val age: Int,
    val targetWeightDecagrams: Int,
    val personType: PersonType,
    val sex: Sex
) {
    enum class PersonType {
        STANDARD,
        ATHLETE
    }

    enum class Sex {
        FEMALE,
        MALE
    }
}

//only for testing
data class DecodedUserData(
    val time: ZonedDateTime,
    val userData: UserData
)
