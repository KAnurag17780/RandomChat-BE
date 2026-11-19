package dev.randomchat.platform.auth;

import dev.randomchat.platform.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the authentication API.
 * Tests registration, login, duplicate checks, and invalid credentials.
 * Requires Docker. Run with: mvn test -Pintegration-tests
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void register_success() {
        Map<String, String> request = Map.of(
                "email", "alice@example.com",
                "password", "SecurePass123!",
                "username", "alice"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("userId");
        assertThat(response.getBody()).containsEntry("username", "alice");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
    }

    @Test
    void register_duplicateEmail_returns409() {
        Map<String, String> request = Map.of(
                "email", "bob@example.com",
                "password", "SecurePass123!",
                "username", "bob_original"
        );
        restTemplate.postForEntity("/api/auth/register", request, Map.class);

        // Second registration with same email
        Map<String, String> duplicate = Map.of(
                "email", "bob@example.com",
                "password", "AnotherPass456!",
                "username", "bob_duplicate"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", duplicate, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_duplicateUsername_returns409() {
        restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "user1@example.com", "password", "Pass123!", "username", "sharedname"),
                Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "user2@example.com", "password", "Pass123!", "username", "sharedname"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void login_success() {
        // Register first
        restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "carol@example.com", "password", "MyPass789!", "username", "carol"),
                Map.class);

        // Login
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login",
                Map.of("email", "carol@example.com", "password", "MyPass789!"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("userId");
    }

    @Test
    void login_wrongPassword_returns401() {
        restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "dave@example.com", "password", "RealPass1!", "username", "dave"),
                Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login",
                Map.of("email", "dave@example.com", "password", "WrongPass!"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_emailNormalized_lowercased() {
        // Register with mixed-case email
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "Eve@Example.COM", "password", "Pass123!", "username", "eve"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Should fail because email is normalized to same lowercase
        ResponseEntity<Map> duplicate = restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "eve@example.com", "password", "Pass123!", "username", "eve2"),
                Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_invalidEmail_returns400() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register",
                Map.of("email", "not-an-email", "password", "Pass123!", "username", "testuser"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
