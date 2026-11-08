package ru.sfustudio.dobryshkaf;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int S3_PORT = 8333;

    // Один контейнер на все тестовые контексты: SeaweedFS отвечает по S3 только секунд через 15 после старта
    private static final GenericContainer<?> S3 = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:3.97"))
            .withCopyFileToContainer(MountableFile.forHostPath("docker/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-s3", "-dir=/data", "-s3.config=/etc/seaweedfs/s3.json")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        S3.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    DynamicPropertyRegistrar s3Properties() {
        return registry -> registry.add("app.storage.endpoint",
                () -> "http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(S3_PORT)));
    }
}
