package br.com.video2frames.video2frames_processing_service.infrastructure.storage;

import br.com.video2frames.video2frames_processing_service.application.port.ZipUploadPort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.UUID;

@Component
public class S3ZipUploader implements ZipUploadPort {

    private final S3Client s3Client;
    private final String bucket;

    public S3ZipUploader(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String upload(UUID videoId, String ownerEmail, Path zipFile) {
        String key = "zips/%s/%s.zip".formatted(ownerEmail, videoId);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/zip").build(),
                    RequestBody.fromFile(zipFile));
            return key;
        } catch (RuntimeException e) {
            throw new VideoProcessingFailedException("Não foi possível enviar o arquivo .zip processado", e);
        }
    }
}
