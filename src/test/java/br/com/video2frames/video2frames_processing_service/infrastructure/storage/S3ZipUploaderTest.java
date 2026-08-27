package br.com.video2frames.video2frames_processing_service.infrastructure.storage;

import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ZipUploaderTest {

    @Mock
    private S3Client s3Client;

    private S3ZipUploader uploader;

    private final UUID videoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        uploader = new S3ZipUploader(s3Client, "video2frames-bucket");
    }

    @Test
    void upload_quandoSucesso_enviaOArquivoParaOS3EDevolveAKeyGerada(@TempDir Path tempDir) throws IOException {
        Path zipFile = Files.createFile(tempDir.resolve("frames.zip"));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = uploader.upload(videoId, "gabriel@video2frames.com", zipFile);

        assertThat(key).isEqualTo("zips/gabriel@video2frames.com/" + videoId + ".zip");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        org.mockito.Mockito.verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("video2frames-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("application/zip");
    }

    @Test
    void upload_quandoS3ClientLancaExcecao_lancaVideoProcessingFailedException(@TempDir Path tempDir)
            throws IOException {
        Path zipFile = Files.createFile(tempDir.resolve("frames.zip"));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("falha no upload").build());

        assertThatThrownBy(() -> uploader.upload(videoId, "gabriel@video2frames.com", zipFile))
                .isInstanceOf(VideoProcessingFailedException.class)
                .hasMessageContaining("Não foi possível enviar o arquivo .zip processado")
                .hasCauseInstanceOf(S3Exception.class);
    }
}
