package lineageos.weather.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.DecimalFormat;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;

public class TemperatureUtilsTest {

    @Test
    public void convertsBetweenUnits() {
        assertEquals(32d, TemperatureUtils.celsiusToFahrenheit(0d), 1e-9);
        assertEquals(0d, TemperatureUtils.fahrenheitToCelsius(32d), 1e-9);
        assertEquals(32d, TemperatureUtils.convert(0d, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT), 1e-9);
        assertEquals(0d, TemperatureUtils.convert(32d, TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS), 1e-9);
        assertEquals(20d, TemperatureUtils.convert(20d, TemperatureUnit.CELSIUS, TemperatureUnit.CELSIUS), 1e-9);
    }

    @Test
    public void formatsWithNoDecimalPlacesByDefault() {
        assertEquals("0°C", TemperatureUtils.formatTemperature(0d, TemperatureUnit.CELSIUS));
        assertEquals("100°F", TemperatureUtils.formatTemperature(100d, TemperatureUnit.FAHRENHEIT));
        // half-even rounding
        assertEquals("36°C", TemperatureUtils.formatTemperature(36.5d, TemperatureUnit.CELSIUS));
        assertEquals("38°C", TemperatureUtils.formatTemperature(37.5d, TemperatureUnit.CELSIUS));
        // negative zero is normalized to zero
        assertEquals("0°C", TemperatureUtils.formatTemperature(-0.4d, TemperatureUnit.CELSIUS));
        assertEquals("-", TemperatureUtils.formatTemperature(Double.NaN, TemperatureUnit.CELSIUS));
    }

    @Test
    public void normalizesNegativeZeroWithLocaleSpecificMinusSign() {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("fi", "FI"));
            assertEquals("0°C", TemperatureUtils.formatTemperature(-0.4d, TemperatureUnit.CELSIUS));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void formatsWithGivenFormatter() {
        assertEquals("36.5°C", TemperatureUtils.formatTemperature(36.5d, TemperatureUnit.CELSIUS, new DecimalFormat("0.0")));
        assertEquals("-0.0°C", TemperatureUtils.formatTemperature(-0.04d, TemperatureUnit.CELSIUS, new DecimalFormat("0.0")));
    }

    @Test
    public void formatsAndConverts() {
        assertEquals("32°F", TemperatureUtils.formatAndConvert(0d, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT));
        assertEquals("0°C", TemperatureUtils.formatAndConvert(32d, TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS));
        assertEquals("36.5°C", TemperatureUtils.formatAndConvert(36.5d, TemperatureUnit.CELSIUS, TemperatureUnit.CELSIUS, new DecimalFormat("0.0")));
        // caller-provided formatters are used verbatim ("-0.0" is kept)
        assertEquals("-0.0°C", TemperatureUtils.formatAndConvert(-0.04d, TemperatureUnit.CELSIUS, TemperatureUnit.CELSIUS, new DecimalFormat("0.0")));
        assertEquals("97.7°F", TemperatureUtils.formatAndConvert(36.5d, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT, new DecimalFormat("0.0")));
    }
}
