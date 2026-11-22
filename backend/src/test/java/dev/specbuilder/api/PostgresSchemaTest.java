package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migration and Hibernate's schema validation against real PostgreSQL, then
 * exercises the trickiest SQL path: deleting a project whose payloads refer to each other.
 * Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "specbuilder.seed-sample=false")
class PostgresSchemaTest extends ApiTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void sampleProjectRoundTripsOnPostgres() {
        Response sample = post("/api/projects/samples/order-service", null).expect(201);
        String p = sample.id();
        for (JsonNode e : get("/api/projects/" + p + "/endpoints").expect(200).json()) {
            get("/api/projects/" + p + "/endpoints/" + e.get("id").asString() + "/rules").expect(200);
        }
        String self = payload(p, "Folder", field("name", "STRING"));
        delete("/api/projects/" + p + "/payloads/" + self).expect(204);

        delete("/api/projects/" + p).expect(204);
        get("/api/projects/" + p).expect(404);
        assertThat(get("/api/projects").json().size()).isZero();
    }
}
