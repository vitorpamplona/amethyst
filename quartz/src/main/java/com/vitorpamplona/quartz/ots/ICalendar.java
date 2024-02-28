package com.vitorpamplona.quartz.ots;

import com.vitorpamplona.quartz.ots.exceptions.CommitmentNotFoundException;
import com.vitorpamplona.quartz.ots.exceptions.DeserializationException;
import com.vitorpamplona.quartz.ots.exceptions.ExceededSizeException;
import com.vitorpamplona.quartz.ots.exceptions.UrlException;

public interface ICalendar {
    Timestamp submit(byte[] digest)
            throws ExceededSizeException, UrlException, DeserializationException;

    Timestamp getTimestamp(byte[] commitment) throws DeserializationException, ExceededSizeException, CommitmentNotFoundException, UrlException;
}
