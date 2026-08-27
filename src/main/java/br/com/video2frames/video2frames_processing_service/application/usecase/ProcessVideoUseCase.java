package br.com.video2frames.video2frames_processing_service.application.usecase;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;
import br.com.video2frames.video2frames_processing_service.application.port.ArchivePort;
import br.com.video2frames.video2frames_processing_service.application.port.FrameExtractorPort;
import br.com.video2frames.video2frames_processing_service.application.port.ProcessingResultPublisherPort;
import br.com.video2frames.video2frames_processing_service.application.port.VideoDownloadPort;
import br.com.video2frames.video2frames_processing_service.application.port.ZipUploadPort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import br.com.video2frames.video2frames_processing_service.domain.model.ExtractedFrames;
import br.com.video2frames.video2frames_processing_service.domain.model.VideoProcessingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Component
public class ProcessVideoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessVideoUseCase.class);

    private final VideoDownloadPort videoDownloadPort;
    private final FrameExtractorPort frameExtractorPort;
    private final ArchivePort archivePort;
    private final ZipUploadPort zipUploadPort;
    private final ProcessingResultPublisherPort resultPublisherPort;

    public ProcessVideoUseCase(
            VideoDownloadPort videoDownloadPort,
            FrameExtractorPort frameExtractorPort,
            ArchivePort archivePort,
            ZipUploadPort zipUploadPort,
            ProcessingResultPublisherPort resultPublisherPort) {
        this.videoDownloadPort = videoDownloadPort;
        this.frameExtractorPort = frameExtractorPort;
        this.archivePort = archivePort;
        this.zipUploadPort = zipUploadPort;
        this.resultPublisherPort = resultPublisherPort;
    }

    public void execute(ProcessVideoCommand command) {
        VideoProcessingJob job = VideoProcessingJob.of(command.videoId(), command.ownerEmail(), command.videoKey());

        Path videoFile = null;
        Path framesDir = null;
        Path zipFile = null;

        try {
            videoFile = videoDownloadPort.download(job.getVideoKey());

            framesDir = Files.createTempDirectory("v2f-frames-" + job.getVideoId());
            int frameCount = frameExtractorPort.extractFrames(videoFile, framesDir);

            zipFile = archivePort.zip(framesDir, job.getVideoId() + "-frames.zip");
            String zipKey = zipUploadPort.upload(job.getVideoId(), job.getOwnerEmail(), zipFile);

            ExtractedFrames result = new ExtractedFrames(frameCount, zipKey);
            resultPublisherPort.publishProcessed(
                    new VideoProcessedEvent(job.getVideoId(), job.getOwnerEmail(), result.zipKey(), result.frameCount()));

        } catch (VideoProcessingFailedException e) {
            log.warn("Falha ao processar vídeo {}: {}", job.getVideoId(), e.getMessage());
            resultPublisherPort.publishFailed(new VideoFailedEvent(job.getVideoId(), job.getOwnerEmail(), e.getMessage()));
        } catch (Exception e) {
            log.error("Falha inesperada ao processar vídeo {}", job.getVideoId(), e);
            resultPublisherPort.publishFailed(
                    new VideoFailedEvent(job.getVideoId(), job.getOwnerEmail(), "Falha inesperada no processamento"));
        } finally {
            cleanup(videoFile);
            cleanupDir(framesDir);
            cleanup(zipFile);
        }
    }

    private void cleanup(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Não foi possível remover arquivo temporário {}", file, e);
        }
    }

    private void cleanupDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            log.warn("Não foi possível remover diretório temporário {}", dir, e);
        }
    }
}
