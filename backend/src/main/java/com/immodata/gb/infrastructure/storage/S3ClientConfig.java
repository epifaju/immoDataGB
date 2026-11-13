package com.immodata.gb.infrastructure.storage;

import com.immodata.gb.infrastructure.config.StorageProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Client S3 path-style (MinIO). Actif seulement si {@code app.storage.endpoint} est renseigné. */
@Configuration
@ConditionalOnExpression("!'${app.storage.endpoint:}'.isBlank()")
public class S3ClientConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .forcePathStyle(true)
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    DocumentStorage documentStorage(S3Client s3Client, S3Presigner presigner) {
        return new S3DocumentStorage(s3Client, presigner);
    }

    private static StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}