package dev.streamline.spring;

import dev.streamline.client.Streamline;
import dev.streamline.client.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link StreamlineListenerContainerProcessor}.
 */
class StreamlineListenerContainerProcessorTest {

    private Streamline streamline;
    private StreamlineListenerContainerProcessor processor;

    @BeforeEach
    void setUp() {
        streamline = mock(Streamline.class);
        processor = new StreamlineListenerContainerProcessor(streamline);
    }

    @Test
    void detectsAnnotatedMethods() {
        var bean = new AnnotatedBean();
        processor.postProcessAfterInitialization(bean, "annotatedBean");

        assertThat(processor.getContainerCount()).isEqualTo(1);
    }

    @Test
    void detectsMultipleAnnotatedMethods() {
        var bean = new MultiListenerBean();
        processor.postProcessAfterInitialization(bean, "multiListenerBean");

        assertThat(processor.getContainerCount()).isEqualTo(2);
    }

    @Test
    void ignoresBeansWithoutAnnotation() {
        var bean = new PlainBean();
        processor.postProcessAfterInitialization(bean, "plainBean");

        assertThat(processor.getContainerCount()).isEqualTo(0);
    }

    @Test
    void returnsSameBeanInstance() {
        var bean = new AnnotatedBean();
        Object result = processor.postProcessAfterInitialization(bean, "annotatedBean");

        assertThat(result).isSameAs(bean);
    }

    @Test
    void rejectsMethodWithZeroParameters() {
        var bean = new ZeroParamBean();

        assertThatThrownBy(() ->
            processor.postProcessAfterInitialization(bean, "zeroParamBean"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must accept at least one parameter");
    }

    @Test
    void isNotRunningBeforeStart() {
        assertThat(processor.isRunning()).isFalse();
    }

    @Test
    void isAutoStartupReturnsTrue() {
        assertThat(processor.isAutoStartup()).isTrue();
    }

    @Test
    void startWithNoContainersDoesNothing() {
        processor.start();
        assertThat(processor.isRunning()).isFalse();
    }

    @Test
    void stopWhenNotRunningIsNoOp() {
        processor.stop();
        assertThat(processor.isRunning()).isFalse();
    }

    // --- Test helper beans ---

    static class AnnotatedBean {
        @StreamlineListener(topics = "test-topic", groupId = "test-group")
        public void handle(ConsumerRecord<String, String> record) {
            // no-op
        }
    }

    static class MultiListenerBean {
        @StreamlineListener(topics = "topic-a")
        public void handleA(ConsumerRecord<String, String> record) {
            // no-op
        }

        @StreamlineListener(topics = "topic-b", groupId = "group-b")
        public void handleB(ConsumerRecord<String, String> record) {
            // no-op
        }
    }

    static class PlainBean {
        public void notAListener(String value) {
            // no-op
        }
    }

    static class ZeroParamBean {
        @StreamlineListener(topics = "bad-topic")
        public void badListener() {
            // no-op
        }
    }
}
