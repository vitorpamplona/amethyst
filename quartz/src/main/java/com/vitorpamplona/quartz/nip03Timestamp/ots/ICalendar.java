package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.CommitmentNotFoundException;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.ExceededSizeException;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException;

public interface ICalendar {
    Timestamp submit(byte[] digest)
            throws ExceededSizeException, UrlException, DeserializationException;

    Timestamp getTimestamp(byte[] commitment) throws DeserializationException, ExceededSizeException, CommitmentNotFoundException, UrlException;
}
