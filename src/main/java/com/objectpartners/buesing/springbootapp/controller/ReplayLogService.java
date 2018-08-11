package com.objectpartners.buesing.springbootapp.controller;

import com.objectpartners.buesing.avro.CommitLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.objectpartners.buesing.avro.Action;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ReplayLogService {

    private static final String COMMIT_LOG_TOPIC = "commit.log";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    public void start(final String key, final String url, final String value) {
        kafkaTemplate.send(COMMIT_LOG_TOPIC, key, start(url, value));
    }

    public void end(final String key, final String url, final Map<String, Object> map) {
        kafkaTemplate.send(COMMIT_LOG_TOPIC, key, end(url, toString(map)));
    }


    private String toString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return "<cannot deserialize>";
        }
    }

    private CommitLog start(final String url, final String body) {
        CommitLog.Builder builder = CommitLog.newBuilder();

        builder.setUrl(url);
        builder.setBody(body);
        builder.setAction(Action.REQUEST);
        builder.setHeaders(new HashMap<>());

        return builder.build();
    }

    private CommitLog end(final String url, final String body) {
        CommitLog.Builder builder = CommitLog.newBuilder();

        builder.setUrl(url);
        builder.setBody(body);
        builder.setAction(Action.RESPONSE);
        builder.setStatus(HttpStatus.OK.value());
        builder.setHeaders(new HashMap<>());

        return builder.build();
    }
}
