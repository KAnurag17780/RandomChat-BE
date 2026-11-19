package dev.randomchat.platform;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Shared Testcontainers PostgreSQL container for all integration tests.
 * <p>
 * Declared as a static field so the container is shared across test classes
 * in the same JVM run, reducing startup overhead.
 */
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("randomchat_test")
                    .withUsername("test")
                    .withPassword("test");
}
