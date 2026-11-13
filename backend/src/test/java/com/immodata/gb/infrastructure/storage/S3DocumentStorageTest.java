package com.immodata.gb.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3DocumentStorageTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner presigner = S3Presigner.builder()
            .endpointOverride(URI.create("http://localhost:9000"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
            .serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .checksumValidationEnabled(false)
                    .build())
            .build();
    private final S3DocumentStorage storage = new S3DocumentStorage(s3Client, presigner);

    @Test
    void putCallsS3WithBucketKeyContentTypeAndLength() {
        byte[] bytes = "hello".getBytes();

        storage.put("docs", "org/prop/doc/file.pdf", new ByteArrayInputStream(bytes), bytes.length, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), ArgumentMatchers.any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("docs");
        assertThat(request.getValue().key()).isEqualTo("org/prop/doc/file.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().contentLength()).isEqualTo(5L);
    }

    @Test
    void putPropagatesSdkFailure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("down").build());

        assertThatThrownBy(() -> storage.put("docs", "k", new ByteArrayInputStream(new byte[1]), 1, "image/png"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    void presignGetReturnsPathStyleUrlWithTtl() {
        URI uri = storage.presignGet("docs", "org/prop/doc/file.pdf", Duration.ofMinutes(15));

        assertThat(uri).isNotNull();
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPath()).isEqualTo("/docs/org/prop/doc/file.pdf");
        assertThat(uri.getQuery()).contains("X-Amz-Expires=900").contains("X-Amz-Signature=");
        assertThat(uri.getQuery().toLowerCase()).doesNotContain("checksum");
    }
}
