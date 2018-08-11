package com.objectpartners.buesing.springbootapp.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@ComponentScan(
        basePackages = {"com.objectpartners.buesing.springbootapp"}
)
@EnableAsync
public class RootConfig {


//    @Autowired
//    private KafkaAdminClient adminClient;
//
//    @PostConstruct
//    public void postConstruct() {
//
//        List<NewTopic> topics = new ArrayList<>();
//
//        topics.add(new NewTopic("footest", 8, (short) 1));
//        topics.add(new NewTopic("commit.log", 1, (short) 1));
//        topics.add(new NewTopic("commit.log.failed", 1, (short) 1));
//        topics.add(new NewTopic("commit.log.replay", 1, (short) 1));
//
//        adminClient.createTopics(topics);
//    }
}
