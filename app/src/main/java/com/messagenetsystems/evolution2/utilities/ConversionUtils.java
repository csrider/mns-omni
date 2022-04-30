package com.messagenetsystems.evolution2.utilities;

/* ConversionUtils
 * Conversion methods.
 *
 * Revisions:
 *  2019.11.12      Chris Rider     Created.
 *  2019.12.05      Chris Rider     Tweaks.
 *  2020.04.30      Chris Rider     Added percentage calculation methods to support AudioStaticUtils.
 *  2020.07.16      Chris Rider     Added hex conversion methods.
 */

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;


public class ConversionUtils {
    private final static String TAG = ConversionUtils.class.getSimpleName();

    // Constants...
    private final static char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();


    /*============================================================================================*/
    /* Conversion Methods */

    //TODO: formalize and polish this
    private String convertFloatToPercentString(final float floatToConvert) {
        return String.valueOf(Math.round(floatToConvert * 100)) + "%";
    }

    private int hexByteToInteger(byte hexByte) {
        return ((int) hexByte);
    }

    private String hexByteToIntegerString(byte hexByte) {
        return String.valueOf(hexByteToInteger(hexByte));
    }

    private String hexByteToBinaryString(byte hexByte) {
        final String TAGG = "hexByteToBinaryString: ";
        String ret = "0";
        String hexByteAsIntStr = hexByteToIntegerString(hexByte);
        ret = Integer.toBinaryString(Integer.parseInt(hexByteAsIntStr, 16));
        return ret;
    }

    public static String byteToHexString(byte b) {
        final String TAGG = "byteToHexString: ";
        String ret;

        ret = Integer.toHexString((int)b);

        return ret;
    }

    public static String byteArrayToHexString(byte[] bytes, @Nullable String delineator) {
        final String TAGG = "byteArrayToHexString: ";
        String ret;

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARS[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARS[v & 0x0F];
        }

        ret = new String(hexChars);

        if (delineator != null) {
            if (delineator.length() > 1) {
                Log.w(TAG, TAGG+"Only one-character delineators are supported. Omitting delineator altogether.");
            } else {
                Log.v(TAG, TAGG+"Hex Before: \""+ret+"\"");
                StringBuilder updatedRet = new StringBuilder();
                for (int i = 0; i < ret.length(); i++) {
                    if (i % 2 == 0) {
                        updatedRet.append(ret.charAt(i));
                        updatedRet.append(ret.charAt(i+1));
                    } else {
                        if (i < ret.length()-1) updatedRet.append(delineator);
                    }
                }

                // update what we will return
                ret = updatedRet.toString();
            }
        }

        Log.v(TAG, TAGG+"Returning:  \""+ret+"\"");
        return ret;
    }
    public static String byteArrayToHexString(byte[] bytes) {
        return byteArrayToHexString(bytes, null);
    }


    /*============================================================================================*/
    /* Calculation Methods */

    /** Calculate the percentage in decimal form that the specified value represents of the maximum value.
     * @param max   Maximum value to calculate with.
     * @param value The value for which you want percentage equivalent of maximum value.
     * @return The calculated percentage as raw floating point decimal value, or 0 if some problem.
     */
    public static float calculatePercentOfMax(int max, int value) {
        final String TAGG = "calculatePercentOfMax("+Integer.toString(max)+","+Integer.toString(value)+"): ";

        // Quick sanity check before we start...
        if (max == 0) {
            Log.w(TAG, TAGG+"Zero provided for max value will result in divide-by-zero problems, returning 0.");
            return (float) 0;
        }

        float ret;

        try {
            float maxAsFloat = (float) max;
            float valueAsFloat = (float) value;

            if (value <= 0 || value > max) {
                Log.w(TAG, TAGG+"Value may be unusual, so result may be as well.");
            }

            ret = valueAsFloat / maxAsFloat;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = (float) 0;
        }

        Log.v(TAG, TAGG+"Returning: \""+Float.toString(ret)+"\"");
        return ret;
    }

    /** Calculate the percentage that the specified value represents of the maximum value.  TODO: UNTESTED! May need a x100 multiplier somewhere?
     * @param max   Maximum value to calculate with.
     * @param value The value for which you want percentage equivalent of maximum value.
     * @param numberOfDecimalsDesired Number of decimal point precision you want returned.
     * @return The calculated percentage as a string value, or 0 if some problem.
     */
    public static String calculatePercentOfMax(int max, int value, int numberOfDecimalsDesired) {
        final String TAGG = "calculatePercentOfMax("+Integer.toString(max)+","+Integer.toString(value)+","+Integer.toString(numberOfDecimalsDesired)+"): ";

        String ret;

        try {
            float rawResult = calculatePercentOfMax(max, value);
            ret = String.format("%."+numberOfDecimalsDesired+"f", rawResult);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = "0";
        }

        Log.v(TAG, TAGG+"Returning: \""+ret+"\"");
        return ret;
    }

    /** Calculate the percentage that the specified value represents of the maximum value.
     * @param max   Maximum value to calculate with.
     * @param value The value for which you want percentage equivalent of maximum value.
     * @return The calculated percentage as a whole number integer, or 0 if some problem.
     */
    public static int calculatePercentOfMax_nearestInteger(int max, int value) {
        final String TAGG = "calculatePercentOfMax_nearestInteger("+Integer.toString(max)+","+Integer.toString(value)+"): ";

        int ret;

        try {
            ret = Math.round(calculatePercentOfMax(max, value)*100);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = 0;
        }

        Log.v(TAG, TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }

    /** Calculate the value of the provided percentage of provided value.
     * Ex. Max of 10, percentage of 20... result will be 2.
     * @param max       Maximum value to apply percentage to.
     * @param percent   Percentage (as a whole number integer) to calculate for.
     * @return The calculated value of the percentage of the max provided, or 0 if some problem.
     */
    public static int calculateValueOfPercentMax_nearestInteger(int max, int percent) {
        final String TAGG = "calculateValueOfPercentMax_nearestInteger("+Integer.toString(max)+","+Integer.toString(percent)+"): ";

        int ret;

        try {
            ret = Math.round( (float)percent/100 * max );
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = 0;
        }

        Log.v(TAG, TAGG+"Returning: "+Integer.toString(ret));
        return ret;
    }


}
