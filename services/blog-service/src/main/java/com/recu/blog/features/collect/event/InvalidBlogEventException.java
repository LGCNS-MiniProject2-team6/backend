package com.recu.blog.features.collect.event;

/** Invalid messages are sent straight to the existing DLT, without retrying. */
public class InvalidBlogEventException extends RuntimeException {
  public InvalidBlogEventException(String message) {
    super(message);
  }
}
