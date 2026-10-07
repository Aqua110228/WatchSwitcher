/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Modified by Gadgetbridge contributors: class renamed from WeatherUtils to TemperatureUtils,
 * adapted for general temperature conversion and formatting, and switched to
 * nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lineageos.weather.util;


import java.text.DecimalFormat;

import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;

/**
 * Helper class to perform operations and formatting of temperature data
 */
public class TemperatureUtils {

    /**
     * Converts a temperature expressed in degrees Celsius to degrees Fahrenheit
     * @param celsius temperature in Celsius
     * @return the temperature in degrees Fahrenheit
     */
    public static double celsiusToFahrenheit(double celsius) {
        return ((celsius * (9d/5d)) + 32d);
    }

    /**
     * Converts a temperature expressed in degrees Fahrenheit to degrees Celsius
     * @param fahrenheit temperature in Fahrenheit
     * @return the temperature in degrees Celsius
     */
    public static double fahrenheitToCelsius(double fahrenheit) {
        return  ((fahrenheit - 32d) * (5d/9d));
    }

    /**
     * Converts a temperature from one unit to another.
     * @param temperature the temperature value
     * @param from the unit the temperature is expressed in
     * @param to the unit to convert to
     * @return the temperature expressed in the target unit
     */
    public static double convert(final double temperature, final TemperatureUnit from, final TemperatureUnit to) {
        if (from == to) {
            return temperature;
        } else if (to == TemperatureUnit.FAHRENHEIT) {
            return celsiusToFahrenheit(temperature);
        } else {
            return fahrenheitToCelsius(temperature);
        }
    }

    /**
     * Returns a string representation of the temperature and unit supplied, with no decimal
     * places. The temperature value will be half-even rounded.
     * @param temperature the temperature value
     * @param unit the unit the temperature is expressed in
     * @return A string with the format XX&deg;F or XX&deg;C (where XX is the temperature)
     * or "-" if the temperature is NaN
     */
    public static String formatTemperature(final double temperature, final TemperatureUnit unit) {
        return formatTemperature(temperature, unit, new DecimalFormat("0"), true);
    }

    /**
     * Returns a string representation of the temperature and unit supplied. The temperature value
     * is formatted with the given formatter (the default is no decimal places, half-even rounded).
     * @param temperature the temperature value
     * @param unit the unit the temperature is expressed in
     * @param format the formatter used to render the numeric value
     * @return A string with the format XX&deg;F or XX&deg;C (where XX is the temperature)
     * or "-" if the temperature is NaN
     */
    public static String formatTemperature(final double temperature, final TemperatureUnit unit, final DecimalFormat format) {
        return formatTemperature(temperature, unit, format, false);
    }

    private static String formatTemperature(final double temperature, final TemperatureUnit unit, final DecimalFormat format, final boolean normalizeNegativeZero) {
        if (Double.isNaN(temperature)) return "-";

        String formatted = format.format(temperature);
        // The default integer formatting (like upstream) hides a rounded negative zero ("-0").
        // The minus sign character is locale-specific, so take it from the formatter itself.
        if (normalizeNegativeZero && formatted.equals(format.getDecimalFormatSymbols().getMinusSign() + "0")) {
            formatted = "0";
        }

        final StringBuilder sb = new StringBuilder()
                .append(formatted).append("\u00b0");
        if (unit == TemperatureUnit.CELSIUS) {
            sb.append("C");
        } else {
            sb.append("F");
        }
        return sb.toString();
    }

    /**
     * Converts a temperature to the target unit and returns its string representation with no
     * decimal places.
     * @param temperature the temperature value
     * @param from the unit the temperature is expressed in
     * @param to the unit to convert to
     * @return A string with the format XX&deg;F or XX&deg;C (where XX is the temperature)
     * or "-" if the temperature is NaN
     */
    public static String formatAndConvert(final double temperature, final TemperatureUnit from, final TemperatureUnit to) {
        return formatAndConvert(temperature, from, to, new DecimalFormat("0"));
    }

    /**
     * Converts a temperature to the target unit and returns its string representation. The
     * temperature value is formatted with the given formatter (the default is no decimal places,
     * half-even rounded).
     * @param temperature the temperature value
     * @param from the unit the temperature is expressed in
     * @param to the unit to convert to
     * @param format the formatter used to render the numeric value
     * @return A string with the format XX&deg;F or XX&deg;C (where XX is the temperature)
     * or "-" if the temperature is NaN
     */
    public static String formatAndConvert(final double temperature, final TemperatureUnit from, final TemperatureUnit to, final DecimalFormat format) {
        return formatTemperature(convert(temperature, from, to), to, format);
    }
}
