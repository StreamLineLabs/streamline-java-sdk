package dev.streamline.client;

import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {

    private ConnectionPool pool;

    @BeforeEach
    void setUp() {
        StreamlineConfig config = new StreamlineConfig(
            "localhost:9092",
            ProducerConfig.defaults(),
            ConsumerConfig.defaults(),
            4, 30000, 30000
        );
        pool = new ConnectionPool(config);
    }

    @Test
    void testAcquireConnection() {
        ConnectionPool.Connection conn = pool.acquire();

        assertNotNull(conn);
        assertEquals("localhost:9092", conn.getServer());
    }

    @Test
    void testReleaseConnection() {
        ConnectionPool.Connection conn = pool.acquire();
        assertDoesNotThrow(() -> pool.release(conn));
    }

    @Test
    void testIsHealthy() {
        assertTrue(pool.isHealthy());
    }

    @Test
    void testClosePool() {
        pool.close();
        assertFalse(pool.isHealthy());
    }

    @Test
    void testAcquireAfterClose() {
        pool.close();
        assertThrows(StreamlineException.class, () -> pool.acquire());
    }

    @Test
    void testCloseIdempotent() {
        pool.close();
        assertDoesNotThrow(() -> pool.close());
        assertFalse(pool.isHealthy());
    }

    @Test
    void testConnectionGetServer() {
        ConnectionPool.Connection conn = new ConnectionPool.Connection("broker1:9092");
        assertEquals("broker1:9092", conn.getServer());
    }
}
