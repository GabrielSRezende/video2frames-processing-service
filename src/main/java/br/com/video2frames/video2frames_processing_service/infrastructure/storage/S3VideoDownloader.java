package br.com.video2frames.video2frames_processing_service.infrastructure.storage;

import br.com.video2frames.video2frames_processing_service.application.port.VideoDownloadPort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class S3VideoDownloader implements VideoDownloadPort {

    private final S3Client s3Client;
    private final String bucket;

    public S3VideoDownloader(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public Path download(String videoKey) {
        try {
            Path tempFile = Files.createTempFile("v2f-video-", suffix(videoKey));

            try (var objectStream = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(videoKey).build())) {
                Files.copy(objectStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile;
        } catch (IOException | RuntimeException e) {
            throw new VideoProcessingFailedException("Não foi possível baixar o vídeo original", e);
        }
    }

    private String suffix(String key) {
        int dotIndex = key.lastIndexOf('.');
        return dotIndex >= 0 ? key.substring(dotIndex) : ".mp4";
    }
}
