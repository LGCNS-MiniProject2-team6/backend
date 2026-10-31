package com.recu.platform.config.support;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** 실제 Kafka KRaft 브로커. Bus 검증 스크립트에서만 실행하며 운영 배포하지 않는다. */
public final class LocalKafkaBroker {

  public static void main(String[] args) throws InterruptedException {
    var broker = new EmbeddedKafkaKraftBroker(1, 1, args[0]);
    broker.brokerProperties(Map.of("auto.create.topics.enable", "false"));
    broker.afterPropertiesSet();
    Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
    System.out.println("KAFKA_READY=" + broker.getBrokersAsString());
    System.out.flush();
    new CountDownLatch(1).await();
  }
}
