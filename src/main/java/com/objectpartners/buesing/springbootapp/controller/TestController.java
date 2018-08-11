package com.objectpartners.buesing.springbootapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping(value = {"/api/v1/commit"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class TestController {

    private static final String PREFIX = RandomStringUtils.randomAlphabetic(2);

    private static AtomicInteger atomicInteger = new AtomicInteger();


    @Autowired
    private ReplayLogService replayLogService;

    @Autowired
    private DoWork doWork;

    @PostMapping(value = "")
    public ResponseEntity<Map> post(
            @RequestParam(defaultValue = "0") Long delay,
            @RequestParam(defaultValue = "false") boolean forceFailure,
            @RequestBody String value,
            HttpServletRequest request) {

        log.debug("Test Controller : " + Thread.currentThread().getName());

        // actual code should use a UUID or some unique identifer, for demo readablity using an atomic integer.
        final String key = "ID_" + PREFIX + "_" + atomicInteger.incrementAndGet();

        log.debug("key={}, delay={}", key, delay);

        replayLogService.start(key, request.getRequestURI(), value);

        doWork.doWork(key, request.getRequestURI(), delay, forceFailure);

        final Map<String, Object> map = new HashMap<>();
        map.put("key", key);

        replayLogService.end(key, request.getRequestURI(), map);

        return new ResponseEntity<>(map, HttpStatus.OK);
    }

}
