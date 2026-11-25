package io.github.mahoretushar.notification;

/** A message that can never succeed; it goes straight to the dead-letter topic without retries. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
