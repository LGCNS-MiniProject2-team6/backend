package com.recu.blog.features.collect.config;

import com.recu.blog.features.collect.event.*;
import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.*;

/** Dedicated factories: domain events do not change the Config Bus serializer or consumer group. */
@Configuration
@EnableKafka
public class BlogEventKafkaConfiguration {
  public static final String REQUESTED = "blog-post.requested.v1";
  public static final String CREATED = "blog-post.created.v1";

  @Bean
  KafkaTemplate<String, Object> blogEventKafkaTemplate(
      KafkaProperties properties, com.fasterxml.jackson.databind.ObjectMapper mapper) {
    var config = new HashMap<>(properties.buildProducerProperties(null));
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    var serializers = new java.util.LinkedHashMap<Class<?>, Serializer<?>>();
    serializers.put(byte[].class, new ByteArraySerializer());
    serializers.put(
        Object.class,
        new JsonSerializer<>(
                mapper
                    .copy()
                    .disable(
                        com.fasterxml.jackson.databind.SerializationFeature
                            .WRITE_DATES_AS_TIMESTAMPS))
            .noTypeInfo());
    var values = new DelegatingByTypeSerializer(serializers, true);
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<>(config, new StringSerializer(), values));
  }

  @Bean
  DefaultErrorHandler blogEventErrorHandler(
      @Qualifier("blogEventKafkaTemplate") KafkaTemplate<String, Object> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) -> new TopicPartition(record.topic() + "-dlt", record.partition()));
    // Do not commit the source offset when DLT publication itself fails.
    recoverer.setFailIfSendResultIsError(true);
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(2.0);
    var handler = new DefaultErrorHandler(recoverer, backoff);
    handler.addNotRetryableExceptions(InvalidBlogEventException.class);
    return handler;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, BlogRequestedEvent> blogEventListenerFactory(
      KafkaProperties properties, @Qualifier("blogEventErrorHandler") DefaultErrorHandler handler) {
    var config = new HashMap<>(properties.buildConsumerProperties(null));
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    var values =
        new ErrorHandlingDeserializer<>(new JsonDeserializer<>(BlogRequestedEvent.class, false));
    var factory = new ConcurrentKafkaListenerContainerFactory<String, BlogRequestedEvent>();
    factory.setConsumerFactory(
        new DefaultKafkaConsumerFactory<>(
            config, new ErrorHandlingDeserializer<>(new StringDeserializer()), values));
    factory.setCommonErrorHandler(handler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
    factory.setMissingTopicsFatal(true);
    return factory;
  }
}
