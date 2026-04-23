package dev.streamline.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TopicNameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"my-topic", "topic.name", "topic_name", "TopicName123", "a", "a-b.c_d"})
    void validTopicNames(String topic) {
        assertDoesNotThrow(() -> TopicNameValidator.validate(topic));
    }

    @Test
    void nullTopicThrows() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate(null));
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    void emptyTopicThrows() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate(""));
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    void dotTopicThrows() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate("."));
        assertTrue(ex.getMessage().contains("\".\""));
    }

    @Test
    void dotDotTopicThrows() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate(".."));
        assertTrue(ex.getMessage().contains("\"..\""));
    }

    @Test
    void tooLongTopicThrows() {
        String longName = "a".repeat(TopicNameValidator.MAX_TOPIC_NAME_LENGTH + 1);
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate(longName));
        assertTrue(ex.getMessage().contains("250"));
    }

    @Test
    void maxLengthTopicIsValid() {
        String maxName = "a".repeat(TopicNameValidator.MAX_TOPIC_NAME_LENGTH);
        assertDoesNotThrow(() -> TopicNameValidator.validate(maxName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"topic name", "topic/name", "topic@name", "topic#name", "topic!name", "topic$name"})
    void invalidCharactersThrow(String topic) {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> TopicNameValidator.validate(topic));
        assertTrue(ex.getMessage().contains("invalid characters"));
    }
}
