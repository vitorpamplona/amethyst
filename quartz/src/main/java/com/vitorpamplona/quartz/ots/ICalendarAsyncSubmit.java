package com.vitorpamplona.quartz.ots;

import java.util.Optional;
import java.util.concurrent.Callable;

public interface ICalendarAsyncSubmit extends Callable<Optional<Timestamp>> {
    @Override
    Optional<Timestamp> call() throws Exception;
}
