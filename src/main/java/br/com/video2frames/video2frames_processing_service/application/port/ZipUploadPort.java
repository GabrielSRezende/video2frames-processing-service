package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;
import java.util.UUID;

public interface ZipUploadPort {

    String upload(UUID videoId, String ownerEmail, Path zipFile);
}
