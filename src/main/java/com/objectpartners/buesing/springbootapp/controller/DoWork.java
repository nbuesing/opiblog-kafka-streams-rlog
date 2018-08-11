package com.objectpartners.buesing.springbootapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DoWork {

    public void doWork(final String key, final String url, final long delay, final boolean forceFailure) {

        log.debug("DoWork : " + Thread.currentThread().getName());

        if (forceFailure) {
            throw new RuntimeException("forced-failure");
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.error("Forced Failure message={}", e.getMessage());
        }
    }
}
