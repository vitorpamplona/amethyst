package com.vitorpamplona.quartz.nip03Timestamp.ots;

import android.annotation.SuppressLint;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Class that lets us compare, sort, store and print timestamps.
 */
public class VerifyResult implements Comparable<VerifyResult> {
    public static enum Chains {
        BITCOIN, LITECOIN, ETHEREUM
    }

    public Long timestamp;
    public int height;

    public VerifyResult(Long timestamp, int height) {
        this.timestamp = timestamp;
        this.height = height;
    }

    /**
     * Returns, if existing, a string representation describing the existence of a block attest
     */
    public String toString() {
        if (height == 0 || timestamp == null) {
            return "";
        }

        String pattern = "yyyy-MM-dd z";
        Locale locale = new Locale("en", "UK");
        DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(locale);
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, dateFormatSymbols);
        String string = simpleDateFormat.format(new Date(timestamp * 1000));

        return "block " + String.valueOf(height) + " attests data existed as of " + string;
    }

    @Override
    public int compareTo(VerifyResult vr) {
        return this.height - vr.height;
    }

    @Override
    public boolean equals(Object obj) {
        VerifyResult vr = (VerifyResult) obj;
        return this.timestamp == vr.timestamp && this.height == vr.height;
    }

    @Override
    public int hashCode() {
        return ((int) (long) (this.timestamp)) ^ this.height;
    }
}