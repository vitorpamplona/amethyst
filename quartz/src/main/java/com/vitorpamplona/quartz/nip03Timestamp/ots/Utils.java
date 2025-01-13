package com.vitorpamplona.quartz.nip03Timestamp.ots;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Utility functions for (mostly) manipulating byte arrays.
 */
public class Utils {
    /**
     * Fills a byte array with the given byte value.
     *
     * @param array the array to fill
     * @param value the value to fill the array with
     */
    public static void arrayFill(byte[] array, byte value) {
        for (int i = 0; i < array.length; i++) {
            array[i] = value;
        }
    }

    /**
     * Returns the first value that is not null. If all objects are null, then it returns null.
     *
     * @param items the array of Ts
     * @param <T> This describes my type parameter
     * @return the first value that is not null. If all objects are null, then it returns null.
     * @deprecated Not used by Java OpenTimestamps itself, and doesn't offer much useful functionality.
     */
    @Deprecated
    public static <T> T coalesce(T... items) {
        for (T i : items) {
            if (i != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * Returns a copy of the byte array argument, or null if the byte array argument is null.
     *
     * @param data the array of bytes to copy
     * @return the copied byte array
     */
    public static byte[] arraysCopy(byte[] data) {
        if (data == null) {
            return null;
        }

        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);

        return copy;
    }

    /**
     * Returns a byte array which is the result of concatenating the two passed in byte arrays.
     * None of the passed in arrays may be null.
     *
     * @param array1 the first array of bytes
     * @param array2 the second array of bytes
     * @return a copy of array1 and array2 concatenated together
     */
    public static byte[] arraysConcat(byte[] array1, byte[] array2) {
        byte[] array1and2 = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, array1and2, 0, array1.length);
        System.arraycopy(array2, 0, array1and2, array1.length, array2.length);

        return array1and2;
    }

    /**
     * Returns a given length array of random bytes.
     *
     * @param length the requested length of the byte array
     * @return a given length array of random bytes
     * @throws NoSuchAlgorithmException for Java 8 implementations
     */
    public static byte[] randBytes(int length) throws NoSuchAlgorithmException {
        //Java 6 & 7:
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);

        // Java 8 (even more secure):
        // SecureRandom.getInstanceStrong().nextBytes(bytes);

        return bytes;
    }

    /**
     * Returns a reversed copy of the passed in byte array.
     *
     * @param array the byte array to reverse
     * @return a copy of the byte array, reversed
     */
    public static byte[] arrayReverse(byte[] array) {
        byte[] reversedArray = new byte[array.length];

        for (int i = array.length - 1, j = 0; i >= 0; i--, j++) {
            reversedArray[j] = array[i];
        }

        return reversedArray;
    }

    /**
     * Compares two byte arrays.
     *
     * @param left the left byte array to compare with
     * @param right the right byte array to compare with
     * @return 0 if the arrays are identical, negative if left &lt; right, positive if left &gt; right
     */
    public static int compare(byte[] left, byte[] right) {
        for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
            int a = (left[i] & 0xff);
            int b = (right[j] & 0xff);

            if (a != b) {
                return a - b;
            }
        }

        return left.length - right.length;
    }

    /**
     * Returns a HEX representation of the passed in byte array.
     *
     * @param bytes the byte array to convert into a string of HEX
     * @return a string representation of the byte array
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }

    /**
     * Converts a HEX representation to its byte array representation. The passed in string must not be null.
     *
     * @param s a string in HEX format to be converted to a corresponding byte array
     * @return the byte array representation of the string in HEX format
     * @throws IllegalArgumentException if the passed in HEX string can't be converted to a byte array
     */
    public static byte[] hexToBytes(String s) throws IllegalArgumentException {
        int len = s.length();

        if (len % 2 == 1) {
            throw new IllegalArgumentException();
        }

        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            if ((Character.digit(s.charAt(i), 16) == -1) || (Character.digit(s.charAt(i + 1), 16) == -1)) {
                throw new IllegalArgumentException();
            }

            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }

    /**
     * Returns a string with the first letter uppercase.
     *
     * @param string the string to get its first character converted to uppercase
     * @return the string, with its first character converted to uppercase
     */
    public static String toUpperFirstLetter(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
    }

    // TODO: This is not the way to do logging. Fix later, possibly with slf4j annotation. Need to read up on the subject.
    public static Logger getLogger(String name) {
        Logger log = Logger.getLogger(name);
        ConsoleHandler handler = new ConsoleHandler();

        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord lr) {
                return lr.getMessage() + "\r\n";
            }
        });

        log.setUseParentHandlers(false);
        log.addHandler(handler);

        return log;
    }
}