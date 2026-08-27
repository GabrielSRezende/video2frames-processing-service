package br.com.video2frames.video2frames_processing_service.application.usecase;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;
import br.com.video2frames.video2frames_processing_service.application.port.ArchivePort;
import br.com.video2frames.video2frames_processing_service.application.port.FrameExtractorPort;
import br.com.video2frames.video2frames_processing_service.application.port.ProcessingResultPublisherPort;
import br.com.video2frames.video2frames_processing_service.application.port.VideoDownloadPort;
import br.com.video2frames.video2frames_processing_service.application.port.ZipUploadPort;
import br.com.video2frames.video2frames_processing_service.domain.exception.InvalidProcessingJobException;
import br.com.video2frames.video2frames_processing_service.domain.exception.VideoProcessingFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessVideoUseCaseTest {

    @Mock
    private VideoDownloadPort videoDownloadPort;

    @Mock
    private FrameExtractorPort frameExtractorPort;

    @Mock
    private ArchivePort archivePort;

    @Mock
    private ZipUploadPort zipUploadPort;

    @Mock
    private ProcessingResultPublisherPort resultPublisherPort;

    private ProcessVideoUseCase useCase;

    private final UUID videoId = UUID.randomUUID();
    private final String ownerEmail = "gabriel@video2frames.com";
    private final String videoKey = "videos/original.mp4";

    private Path videoFile;
    private Path zipFile;

    @BeforeEach
    void setUp() {
        useCase = new ProcessVideoUseCase(
                videoDownloadPort, frameExtractorPort, archivePort, zipUploadPort, resultPublisherPort);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (videoFile != null) {
            Files.deleteIfExists(videoFile);
        }
        if (zipFile != null) {
            Files.deleteIfExists(zipFile);
        }
    }

    @Test
    void execute_quandoProcessamentoOcorreComSucesso_publicaVideoProcessedEventELimpaArquivosTemporarios()
            throws IOException {
        videoFile = Files.createTempFile("v2f-video-test-", ".mp4");
        zipFile = Files.createTempFile("v2f-zip-test-", ".zip");

        when(videoDownloadPort.download(videoKey)).thenReturn(videoFile);
        when(frameExtractorPort.extractFrames(eq(videoFile), any(Path.class))).thenReturn(7);
        when(archivePort.zip(any(Path.class), anyString())).thenReturn(zipFile);
        when(zipUploadPort.upload(eq(videoId), eq(ownerEmail), eq(zipFile))).thenReturn("zips/owner/video.zip");

        ArgumentCaptor<Path> framesDirCaptor = ArgumentCaptor.forClass(Path.class);

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        verify(frameExtractorPort).extractFrames(eq(videoFile), framesDirCaptor.capture());
        Path framesDir = framesDirCaptor.getValue();

        ArgumentCaptor<VideoProcessedEvent> eventCaptor = ArgumentCaptor.forClass(VideoProcessedEvent.class);
        verify(resultPublisherPort).publishProcessed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishFailed(any());

        VideoProcessedEvent event = eventCaptor.getValue();
        assertThat(event.videoId()).isEqualTo(videoId);
        assertThat(event.ownerEmail()).isEqualTo(ownerEmail);
        assertThat(event.zipKey()).isEqualTo("zips/owner/video.zip");
        assertThat(event.frameCount()).isEqualTo(7);

        assertThat(Files.exists(videoFile)).isFalse();
        assertThat(Files.exists(zipFile)).isFalse();
        assertThat(Files.exists(framesDir)).isFalse();
    }

    @Test
    void execute_quandoComandoInvalido_lancaInvalidProcessingJobExceptionSemPublicarEventos() {
        ProcessVideoCommand command = new ProcessVideoCommand(null, ownerEmail, videoKey);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidProcessingJobException.class);

        verifyNoInteractions(videoDownloadPort, frameExtractorPort, archivePort, zipUploadPort, resultPublisherPort);
    }

    @Test
    void execute_quandoDownloadLancaVideoProcessingFailedException_publicaVideoFailedEventComAMensagemDaFalha() {
        when(videoDownloadPort.download(videoKey))
                .thenThrow(new VideoProcessingFailedException("Não foi possível baixar o vídeo original"));

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        ArgumentCaptor<VideoFailedEvent> eventCaptor = ArgumentCaptor.forClass(VideoFailedEvent.class);
        verify(resultPublisherPort).publishFailed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishProcessed(any());
        verifyNoInteractions(frameExtractorPort, archivePort, zipUploadPort);

        VideoFailedEvent event = eventCaptor.getValue();
        assertThat(event.videoId()).isEqualTo(videoId);
        assertThat(event.ownerEmail()).isEqualTo(ownerEmail);
        assertThat(event.reason()).isEqualTo("Não foi possível baixar o vídeo original");
    }

    @Test
    void execute_quandoExtracaoDeFramesFalha_publicaVideoFailedEventELimpaOArquivoDeVideo() throws IOException {
        videoFile = Files.createTempFile("v2f-video-test-", ".mp4");
        when(videoDownloadPort.download(videoKey)).thenReturn(videoFile);
        when(frameExtractorPort.extractFrames(eq(videoFile), any(Path.class)))
                .thenThrow(new VideoProcessingFailedException("Nenhum frame foi extraído do vídeo"));

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        ArgumentCaptor<VideoFailedEvent> eventCaptor = ArgumentCaptor.forClass(VideoFailedEvent.class);
        verify(resultPublisherPort).publishFailed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishProcessed(any());
        verifyNoInteractions(archivePort, zipUploadPort);

        assertThat(eventCaptor.getValue().reason()).isEqualTo("Nenhum frame foi extraído do vídeo");
        assertThat(Files.exists(videoFile)).isFalse();
    }

    @Test
    void execute_quandoCompactacaoFalha_publicaVideoFailedEvent() throws IOException {
        videoFile = Files.createTempFile("v2f-video-test-", ".mp4");
        when(videoDownloadPort.download(videoKey)).thenReturn(videoFile);
        when(frameExtractorPort.extractFrames(eq(videoFile), any(Path.class))).thenReturn(3);
        when(archivePort.zip(any(Path.class), anyString()))
                .thenThrow(new VideoProcessingFailedException("Não foi possível compactar os frames extraídos"));

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        ArgumentCaptor<VideoFailedEvent> eventCaptor = ArgumentCaptor.forClass(VideoFailedEvent.class);
        verify(resultPublisherPort).publishFailed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishProcessed(any());
        verifyNoInteractions(zipUploadPort);

        assertThat(eventCaptor.getValue().reason()).isEqualTo("Não foi possível compactar os frames extraídos");
    }

    @Test
    void execute_quandoUploadFalha_publicaVideoFailedEventELimpaOArquivoZip() throws IOException {
        videoFile = Files.createTempFile("v2f-video-test-", ".mp4");
        zipFile = Files.createTempFile("v2f-zip-test-", ".zip");
        when(videoDownloadPort.download(videoKey)).thenReturn(videoFile);
        when(frameExtractorPort.extractFrames(eq(videoFile), any(Path.class))).thenReturn(3);
        when(archivePort.zip(any(Path.class), anyString())).thenReturn(zipFile);
        when(zipUploadPort.upload(eq(videoId), eq(ownerEmail), eq(zipFile)))
                .thenThrow(new VideoProcessingFailedException("Não foi possível enviar o arquivo .zip processado"));

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        ArgumentCaptor<VideoFailedEvent> eventCaptor = ArgumentCaptor.forClass(VideoFailedEvent.class);
        verify(resultPublisherPort).publishFailed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishProcessed(any());

        assertThat(eventCaptor.getValue().reason()).isEqualTo("Não foi possível enviar o arquivo .zip processado");
        assertThat(Files.exists(zipFile)).isFalse();
    }

    @Test
    void execute_quandoOcorreExcecaoInesperada_publicaVideoFailedEventComMensagemGenerica() {
        when(videoDownloadPort.download(videoKey)).thenThrow(new RuntimeException("erro totalmente inesperado"));

        useCase.execute(new ProcessVideoCommand(videoId, ownerEmail, videoKey));

        ArgumentCaptor<VideoFailedEvent> eventCaptor = ArgumentCaptor.forClass(VideoFailedEvent.class);
        verify(resultPublisherPort).publishFailed(eventCaptor.capture());
        verify(resultPublisherPort, never()).publishProcessed(any());

        VideoFailedEvent event = eventCaptor.getValue();
        assertThat(event.videoId()).isEqualTo(videoId);
        assertThat(event.reason()).isEqualTo("Falha inesperada no processamento");
    }
}
