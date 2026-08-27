package br.com.video2frames.video2frames_processing_service.infrastructure.archive;

import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipFrameArchiverTest {

    private final ZipFrameArchiver archiver = new ZipFrameArchiver();

    private Path createdZip;

    @AfterEach
    void tearDown() throws IOException {
        if (createdZip != null) {
            Files.deleteIfExists(createdZip);
        }
    }

    @Test
    void zip_quandoDiretorioTemArquivos_criaZipComTodosOsArquivosEConteudoPreservado(@TempDir Path sourceDir)
            throws IOException {
        Files.writeString(sourceDir.resolve("frame_0001.jpg"), "conteudo-frame-1", StandardCharsets.UTF_8);
        Files.writeString(sourceDir.resolve("frame_0002.jpg"), "conteudo-frame-2", StandardCharsets.UTF_8);

        createdZip = archiver.zip(sourceDir, "video-frames.zip");

        assertThat(Files.exists(createdZip)).isTrue();

        Map<String, String> entries = readZipEntries(createdZip);
        assertThat(entries).hasSize(2);
        assertThat(entries.get("frame_0001.jpg")).isEqualTo("conteudo-frame-1");
        assertThat(entries.get("frame_0002.jpg")).isEqualTo("conteudo-frame-2");
    }

    @Test
    void zip_quandoDiretorioVazio_criaZipSemEntradas(@TempDir Path sourceDir) throws IOException {
        createdZip = archiver.zip(sourceDir, "vazio.zip");

        assertThat(Files.exists(createdZip)).isTrue();
        assertThat(readZipEntries(createdZip)).isEmpty();
    }

    @Test
    void zip_quandoDiretorioOrigemNaoExiste_lancaVideoProcessingFailedException(@TempDir Path tempDir) {
        Path inexistente = tempDir.resolve("nao-existe");

        assertThatThrownBy(() -> archiver.zip(inexistente, "frames.zip"))
                .isInstanceOf(VideoProcessingFailedException.class)
                .hasMessageContaining("compactar")
                .hasCauseInstanceOf(IOException.class);
    }

    private Map<String, String> readZipEntries(Path zipFile) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (var zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
