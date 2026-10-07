package nodomain.freeyourgadget.gadgetbridge.model

import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator
import nodomain.freeyourgadget.gadgetbridge.util.sanitizeText

interface DeviceTextAdaptable<T> {
    fun transliterated(
        deviceSupport: DeviceSupport,
        transliterator: Transliterator?
    ): T

    fun transform(text: String?,
                  deviceSupport: DeviceSupport,
                  transliterator: Transliterator?): String? {
        val sanitized = sanitizeText(deviceSupport, text)
        return transliterator?.let { sanitized?.let(it::transliterate) } ?: sanitized
    }

    fun withRtlFix(): T

}