package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;
import java.util.UUID;

public interface ZipUploadPort {

    /** Sobe o zip local e retorna a key final onde ficou armazenado. */
    String upload(UUID videoId, String ownerEmail, Path zipFile);
}
