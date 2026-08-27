package br.com.video2frames.video2frames_processing_service.infrastructure.ffmpeg;

import br.com.video2frames.video2frames_processing_service.application.port.FrameExtractorPort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegFrameExtractor implements FrameExtractorPort {

    private static final long TIMEOUT_MINUTES = 15;

    private final String ffmpegBinaryPath;

    public FfmpegFrameExtractor(@Value("${app.ffmpeg.binary-path}") String ffmpegBinaryPath) {
        this.ffmpegBinaryPath = ffmpegBinaryPath;
    }

    @Override
    public int extractFrames(Path videoFile, Path outputDir) {
        List<String> command = List.of(
                ffmpegBinaryPath,
                "-i", videoFile.toAbsolutePath().toString(),
                "-vf", "fps=1",
                "-qscale:v", "2",
                outputDir.resolve("frame_%04d.jpg").toAbsolutePath().toString());

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new VideoProcessingFailedException("Tempo limite excedido ao extrair os frames do vídeo");
            }
            if (process.exitValue() != 0) {
                throw new VideoProcessingFailedException(
                        "FFmpeg falhou ao processar o vídeo (o arquivo pode estar corrompido ou em formato inválido)");
            }

            int frameCount = countFrames(outputDir);
            if (frameCount == 0) {
                throw new VideoProcessingFailedException("Nenhum frame foi extraído do vídeo");
            }
            return frameCount;

        } catch (IOException e) {
            throw new VideoProcessingFailedException(
                    "Não foi possível executar o FFmpeg — verifique se está instalado e no PATH", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProcessingFailedException("Extração de frames interrompida", e);
        }
    }

    private int countFrames(Path outputDir) throws IOException {
        try (var stream = Files.list(outputDir)) {
            return (int) stream.filter(path -> path.toString().endsWith(".jpg")).count();
        }
    }
}
