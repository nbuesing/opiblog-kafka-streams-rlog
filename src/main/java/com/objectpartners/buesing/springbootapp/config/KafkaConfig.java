package com.objectpartners.buesing.springbootapp.config;

import com.objectpartners.buesing.springbootapp.config.properties.KafkaStreamProperties;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableKafkaStreams
@Slf4j
public class KafkaConfig {

    private final KafkaStreamProperties kafkaStreamProperties;


    public KafkaConfig(final KafkaStreamProperties kafkaStreamProperties) {
        this.kafkaStreamProperties = kafkaStreamProperties;
    }

    @PostConstruct
    public void postConstruct() {
        log.info("KafkaConfig configured.");
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public StreamsConfig kStreamsConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put("schema.registry.url",kafkaStreamProperties.getSchemaRegistryUrl());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaStreamProperties.getBootstrapServers());
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, kafkaStreamProperties.getApplicationId());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class.getName());
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, ExceptionHandler.class);
        return new StreamsConfig(props);
    }

    @Bean
    public Serde<GenericRecord> valueSerde() {
        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", kafkaStreamProperties.getSchemaRegistryUrl());
        final Serde<GenericRecord> valueGenericAvroSerde = new GenericAvroSerde();
        valueGenericAvroSerde.configure(serdeConfig, false);
        return valueGenericAvroSerde;
    }

    /**
     * Create a KStream for the given topic.
     *
     * You must know how the data is serialized on the topic.  When using AVRO you have various options for domain
     * object to access the data.  Various Serde's know how to convert read the binary AVRO data into a domain structure
     * that can access the data.  For Streams that need little or not inspection of the message, utilizing
     * the GenericAvroSerde makes sense.
     *
     * Kafka Streams provides GenericAvroSerde and SpecificAvroSerde.
     */
    @Bean
    public KStream<String, GenericRecord> kStream(StreamsBuilder streamBuilder) {
        return streamBuilder
                // stream a topic deserializing the key as a string and deserializing the value as AVRO into a generic record object.
                .stream("commit.log", Consumed.with(Serdes.String(), valueSerde()))
                .peek((key, value) -> log.debug("kStream : key={}, value={}", key, value));
    }

    /**
     * Create a kTable
     *
     * kTables should be considered as a perminent data store.  When it comes to windowing, there are opportunties for
     * tables to have a be purged.  Windowing kTables have a retention period, but still timeframe of windows can
     * be a very long time, see UnlimitedWindows.of() for the support of windowing.
     *
     * However, when it comes to the commit log, the whole point is to capture windows of time with no response, so a
     * session window of period of time equals (or slightly greater) than the RESTful SLA seems to makes the most
     * sense.
     */
    @Bean
    public KTable<Windowed<String>, Integer> kTable(StreamsBuilder streamsBuilder) {

        return kStream(streamsBuilder)
                .peek((key, value) -> log.debug("kTable : key={}, value={}", key, value))
                // group records on the stream by the key, since the request/response utilize the same key,
                // this is the logical choice for grouping.
                .groupByKey()
                // window by a period of time equivalent to the RESTful SLA, 250ms, for understanding how windowing
                // works, be sure to checkout UnlimitedWindows.of() to understand that Kafka Streams handle large
                // windows, and windowing should be based on solving a problem, not worrying about resources.
                .windowedBy(SessionWindows.with(250L))
                // aggregate is what is used to combine the stream records that are being grouped.
                // the aggregator is rather simple, count the messages.
                .aggregate(
                        // initialize the value of the windowing to 0
                        () -> 0,
                        // for a given message merge, since we do not actually care of what is in the message (value)
                        // we just increment the counter of the aggregate.
                        (key, value, aggregate) -> {
                            log.debug("kTable - aggregator : key={}, aggregate={}", key, aggregate + 1);
                            return aggregate + 1;
                        },
                        // we need to merge two aggregates together.
                        (aggKey, aggOne, aggTwo) -> {
                            log.debug("kTable - merger : key={}, merge={}", aggKey, aggOne + aggTwo);
                            return aggOne + aggTwo;
                        },
                        // how the kTable is materialized.
                        Materialized.with(Serdes.String(), Serdes.Integer()));
    }




    @Bean
    public KStream<String, Integer> kStreamResponseMissing(StreamsBuilder streamBuilder) {
        return kTable(streamBuilder)
                // turn the kTable into a kStream where the windowing key is the key, since the windowing key is the
                // same session key we used for the commit log, it is a natural choice.
                .toStream((wk, v) -> wk.key())
                .peek((key, value) -> log.debug("kStreamResponseMissing (pre filter) : key={}, value={}", key, value))
                // only take those from the table who have a value of 1, which means that for the given session-window only
                // one record exists.  If the kTable has a value of 2, that means it has both a request and response
                // in the given session window, so it finished successfully; so that means the response is not missing.
                .filter((key, value) -> value == 1)
                .peek((key, value) -> log.debug("kStreamResponseMissing (post filter) : key={}, value={}", key, value));
    }


    /**
     * Combine the streams and write out the response to a topic.
     *
     * Spring makes it easy to reference beans w/out recreating them.  Calling kStream(streamBuilder) returns the
     * same spring bean no mater how many times you call it (provided you are using @Configuration).
     */
    @Bean
    public KStream<String, GenericRecord> kStreamReplay(StreamsBuilder streamBuilder) {

        return kStream(streamBuilder)
                .peek((key, value) -> log.debug("kStreamReplay (pre filter) : key={}, value={}", key, value))
                // we only want the requests, not the response, so we must filter based on content of the message (value)
                // we are utilizing the AVRO GenericRecord to obtain the action value.  a SpecificRecord AVRO could
                // have been utilized if so desired.
                .filter((key, value) -> "REQUEST".equals(value.get("action").toString())) // could use specific avro...
                .peek((key, value) -> log.debug("kStreamReplay (post filter) : key={}, value={}", key, value))
                .join(kStreamResponseMissing(streamBuilder),
                        (value1, value2) -> {
                            log.debug("kStreamReplay (join) value1={}, value2={}", value1, value2);
                            return value1;
                        },
                        JoinWindows.of(1000L), // picking a window larger than the session window of the kTable
                        Joined.with(Serdes.String(), valueSerde(), Serdes.Integer()))
                .peek((key, value) -> log.debug("kStreamReplay (post join) : key={}, value={}", key, value))
                // write the value out to another topic.  Since we are writing out the same key/value as the original
                // stream no Produced.with() serdes are needed.
                .through("commit.log.replay");
    }

    /**
     * Same result as kStreamReplay, but joining the streams in the opposite order.
     */
    @Bean
    public KStream<String, GenericRecord> kStreamReplay_v2(StreamsBuilder streamBuilder) {

        return kStreamResponseMissing(streamBuilder)
                .peek((key, value) -> log.debug("kStreamReplay_v2 (pre filter) : key={}, value={}", key, value))
                // we only want the requests, not the response, so we must filter based on content of the message (value)
                // we are utilizing the AVRO GenericRecord to obtain the action value.  a SpecificRecord AVRO could
                // have been utilized if so desired.
                .filter((key, value) -> value == 1)
                .peek((key, value) -> log.debug("kStreamReplay_v2 (post filter) : key={}, value={}", key, value))
                .join(kStream(streamBuilder).filter((key, value) -> "REQUEST".equals(value.get("action").toString())),
                        (value1, value2) -> {
                            log.debug("kStreamReplay_v2 (join) value1={}, value2={}", value1, value2);
                            return value2;
                        },
                        JoinWindows.of(1000L),
                        Joined.with(Serdes.String(), Serdes.Integer(), valueSerde()))
                .peek((key, value) -> log.debug("kStreamReplay_v2 (post join) : key={}, value={}", key, value))
                // write the value out to another topic.  Since we are writing out the same key/value as the original
                // stream no Produced.with() serdes are needed.
                .through("commit.log.replay2", Produced.with(Serdes.String(), valueSerde()));
    }
}
