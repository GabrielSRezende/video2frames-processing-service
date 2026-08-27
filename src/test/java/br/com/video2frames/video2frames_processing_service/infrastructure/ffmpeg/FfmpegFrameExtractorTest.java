package br.com.video2frames.video2frames_processing_service.infrastructure.ffmpeg;

import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegFrameExtractorTest {

    @Test
    void extractFrames_quandoBinarioDoFfmpegNaoExiste_lancaVideoProcessingFailedExceptionComIOExceptionComoCausa(
            @TempDir Path tempDir) {
        String binarioInexistente = "ffmpeg-binario-inexistente-" + UUID.randomUUID();
        FfmpegFrameExtractor extractor = new FfmpegFrameExtractor(binarioInexistente);

        Path videoFile = tempDir.resolve("video.mp4");
        Path outputDir = tempDir.resolve("frames");

        assertThatThrownBy(() -> extractor.extractFrames(videoFile, outputDir))
                .isInstanceOf(VideoProcessingFailedException.class)
                .hasMessageContaining("Não foi possível executar o FFmpeg")
                .hasCauseInstanceOf(IOException.class);
    }
}
