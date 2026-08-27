package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;

public interface FrameExtractorPort {

    int extractFrames(Path videoFile, Path outputDir);
}
