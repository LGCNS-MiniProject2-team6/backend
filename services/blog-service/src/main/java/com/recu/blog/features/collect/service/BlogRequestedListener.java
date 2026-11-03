package com.recu.blog.features.collect.service;

import com.recu.blog.features.collect.event.*;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BlogRequestedListener {
  private final Validator validator;
  private final CollectedPostStore store;
  private final BlogEventPublisher publisher;

  public BlogRequestedListener(
      Validator validator, CollectedPostStore store, BlogEventPublisher publisher) {
    this.validator = validator;
    this.store = store;
    this.publisher = publisher;
  }

  @KafkaListener(
      id = "blogRequestedListener",
      topics = "blog-post.requested.v1",
      groupId = "blog-service",
      containerFactory = "blogEventListenerFactory",
      properties = "auto.offset.reset=earliest",
      autoStartup = "${recu.blog.events.listener-auto-startup:true}")
  public void consume(ConsumerRecord<String, BlogRequestedEvent> record) {
    var event = record.value();
    if (event == null || !validator.validate(event).isEmpty()) {
      throw new InvalidBlogEventException("Invalid requested envelope or data");
    }
    if (!event.data().url().equals(record.key()))
      throw new InvalidBlogEventException("Key must equal url");
    var saved = store.store(event.data());
    // The DB transaction is committed before publication. Replays publish the same created event.
    publisher.created(saved.data(), saved.collectedAt());
  }
}
