package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;

public interface FrameExtractorPort {

    /** Extrai os frames do vídeo em videoFile para dentro de outputDir. Retorna a quantidade extraída. */
    int extractFrames(Path videoFile, Path outputDir);
}
