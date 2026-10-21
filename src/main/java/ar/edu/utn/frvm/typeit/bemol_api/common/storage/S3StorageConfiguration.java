package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3StorageConfiguration {
  @Bean(destroyMethod = "close")
  public S3Client storageS3Client(@Value("${app.storage.s3.region}") final String region) {
    return S3Client.builder()
        .region(Region.of(region))
        .overrideConfiguration(
            configuration ->
                configuration
                    .apiCallTimeout(Duration.ofMinutes(2))
                    .apiCallAttemptTimeout(Duration.ofSeconds(30)))
        .build();
  }
}
