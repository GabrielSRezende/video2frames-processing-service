package br.com.video2frames.video2frames_processing_service.infrastructure.archive;

import br.com.video2frames.video2frames_processing_service.application.port.ArchivePort;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class ZipFrameArchiver implements ArchivePort {

    @Override
    public Path zip(Path sourceDir, String zipFileName) {
        try {
            Path zipFile = Files.createTempFile("v2f-zip-", "-" + zipFileName);
            int fileCount;

            try (var out = new ZipOutputStream(Files.newOutputStream(zipFile));
                 var files = Files.list(sourceDir)) {

                var sortedFiles = files.sorted(Comparator.naturalOrder()).toList();
                fileCount = sortedFiles.size();

                for (Path file : sortedFiles) {
                    out.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }

            log.info("Zip com {} frames gerado em {}", fileCount, zipFile);
            return zipFile;
        } catch (IOException e) {
            log.error("Não foi possível compactar os frames extraídos de {}", sourceDir, e);
            throw new VideoProcessingFailedException("Não foi possível compactar os frames extraídos", e);
        }
    }
}
