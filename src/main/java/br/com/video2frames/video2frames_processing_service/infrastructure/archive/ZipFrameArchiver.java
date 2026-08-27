package br.com.video2frames.video2frames_processing_service.infrastructure.archive;

import br.com.video2frames.video2frames_processing_service.application.port.ArchivePort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipFrameArchiver implements ArchivePort {

    @Override
    public Path zip(Path sourceDir, String zipFileName) {
        try {
            Path zipFile = Files.createTempFile("v2f-zip-", "-" + zipFileName);

            try (var out = new ZipOutputStream(Files.newOutputStream(zipFile));
                 var files = Files.list(sourceDir)) {

                for (Path file : files.sorted(Comparator.naturalOrder()).toList()) {
                    out.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }

            return zipFile;
        } catch (IOException e) {
            throw new VideoProcessingFailedException("Não foi possível compactar os frames extraídos", e);
        }
    }
}
