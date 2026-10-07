package nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.protocol

import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoBackgroundVolume
import nodomain.freeyourgadget.gadgetbridge.service.devices.cardo.enums.CardoEnums

sealed interface CardoField<T> {
    val name: String
    val bitSize: Int
    fun decode(raw: Long): T
    fun encode(value: T): Long
}

data class BoolField(override val name: String) : CardoField<Boolean> {
    override val bitSize = 1
    override fun decode(raw: Long): Boolean = raw == 1L
    override fun encode(value: Boolean): Long = if (value) 1L else 0L
}

data class IntField(override val name: String, override val bitSize: Int) : CardoField<Int> {
    override fun decode(raw: Long): Int = raw.toInt()
    override fun encode(value: Int): Long = value.toLong()
}

class EnumField<T>(
    override val name: String,
    override val bitSize: Int,
    private val values: Array<T>,
    private val toOrdinalPayload: (T) -> Int
) : CardoField<T> where T : Enum<T> {
    override fun decode(raw: Long): T {
        val index = raw.toInt()
        require(index in values.indices) {
            "Value $index out of range for '$name' (enum ${values.firstOrNull()?.javaClass?.simpleName})"
        }
        return values[index]
    }
    override fun encode(value: T): Long = toOrdinalPayload(value).toLong()
}

inline fun <reified T> enumField(name: String, bitSize: Int): EnumField<T>
        where T : Enum<T>, T : CardoEnums =
    EnumField(name, bitSize, enumValues<T>()) { it.btPayload }

class EnumSetField<T>(
    override val name: String,
    override val bitSize: Int,
    private val values: Array<T>
) : CardoField<Set<T>> where T : Enum<T> {
    override fun decode(raw: Long): Set<T> =
        values.filterIndexed { index, _ -> (raw shr index) and 1L == 1L }.toSet()

    override fun encode(value: Set<T>): Long =
        value.fold(0L) { acc, v -> acc or (1L shl v.ordinal) }
}

inline fun <reified T : Enum<T>> enumSetField(name: String, bitSize: Int): EnumSetField<T> =
    EnumSetField(name, bitSize, enumValues<T>())

class CustomIndexEnumField<T>(
    override val name: String,
    override val bitSize: Int,
    private val decodeMap: Map<Int, T>,
    private val encodeMap: Map<T, Int>
) : CardoField<T> {
    override fun decode(raw: Long): T {
        val index = raw.toInt()
        return decodeMap[index]
            ?: throw IllegalArgumentException("Invalid index for '$name': $index")
    }
    override fun encode(value: T): Long =
        (encodeMap[value] ?: error("Missing encode mapping for '$name': $value")).toLong()
}

fun backgroundVolumeField(name: String, bitSize: Int): CustomIndexEnumField<CardoBackgroundVolume> {
    val values = CardoBackgroundVolume.values()
    val decodeMap = values.associateBy { it.customIndex }
    val encodeMap = values.associateWith { it.customIndex }
    return CustomIndexEnumField(name, bitSize, decodeMap, encodeMap)
}
