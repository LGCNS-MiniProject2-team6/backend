package com.recu.user.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public KafkaTemplate<String, Object> userEventKafkaTemplate(
      KafkaProperties properties, ObjectMapper mapper) {
    Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
    config.put(ProducerConfig.ACKS_CONFIG, "all");

    // byte[]와 JsonNode/일반 객체 모두 처리 가능한 DelegatingByTypeSerializer 구성 (DLT 전송 호환)
    Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
    serializers.put(byte[].class, new ByteArraySerializer());
    serializers.put(
        Object.class,
        new JsonSerializer<>(mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
            .noTypeInfo());
    DelegatingByTypeSerializer values = new DelegatingByTypeSerializer(serializers, true);

    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<>(config, new StringSerializer(), values));
  }

  @Bean
  public ConsumerFactory<String, Object> kafkaConsumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

    // Deserializer with error handling
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

    // JsonDeserializer 설정 (모든 패키지 허용 및 type header 무시 옵션 호환)
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.fasterxml.jackson.databind.JsonNode");

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public DefaultErrorHandler kafkaErrorHandler(
      @Qualifier("userEventKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
    // 1s, 2s, 4s 3회 재시도 후 DLT(-dlt) 이동
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxAttempts(3);

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, error) -> new TopicPartition(record.topic() + "-dlt", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

    // 역직렬화 실패 예외 및 IllegalArgumentException 등 비재시도 예외 추가 (즉시 DLT 이동)
    errorHandler.addNotRetryableExceptions(
        org.apache.kafka.common.errors.SerializationException.class,
        org.springframework.kafka.support.serializer.DeserializationException.class,
        IllegalArgumentException.class);

    return errorHandler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
      ConsumerFactory<String, Object> kafkaConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(kafkaConsumerFactory);
    factory.setCommonErrorHandler(kafkaErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
    return factory;
  }
}
