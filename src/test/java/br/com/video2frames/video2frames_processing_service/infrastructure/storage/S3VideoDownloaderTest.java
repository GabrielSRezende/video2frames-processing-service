package br.com.video2frames.video2frames_processing_service.infrastructure.storage;

import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3VideoDownloaderTest {

    @Mock
    private S3Client s3Client;

    private S3VideoDownloader downloader;

    private Path downloadedFile;

    @BeforeEach
    void setUp() {
        downloader = new S3VideoDownloader(s3Client, "video2frames-bucket");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (downloadedFile != null) {
            Files.deleteIfExists(downloadedFile);
        }
    }

    @Test
    void download_quandoObjetoExisteNoS3_salvaOConteudoEmArquivoLocalTemporario() throws IOException {
        byte[] content = "conteudo-do-video".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream(content));

        downloadedFile = downloader.download("videos/original.mp4");

        assertThat(Files.exists(downloadedFile)).isTrue();
        assertThat(downloadedFile.toString()).endsWith(".mp4");
        assertThat(Files.readAllBytes(downloadedFile)).isEqualTo(content);
    }

    @Test
    void download_quandoKeySemExtensao_usaSufixoMp4PorPadrao() throws IOException {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseInputStream("conteudo".getBytes(StandardCharsets.UTF_8)));

        downloadedFile = downloader.download("videos/sem-extensao");

        assertThat(downloadedFile.toString()).endsWith(".mp4");
    }

    @Test
    void download_quandoS3ClientLancaExcecao_lancaVideoProcessingFailedException() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("chave não encontrada").build());

        assertThatThrownBy(() -> downloader.download("videos/nao-existe.mp4"))
                .isInstanceOf(VideoProcessingFailedException.class)
                .hasMessageContaining("Não foi possível baixar o vídeo original")
                .hasCauseInstanceOf(NoSuchKeyException.class);
    }

    private ResponseInputStream<GetObjectResponse> responseInputStream(byte[] content) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content)));
    }
}
