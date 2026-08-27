package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsProcessingResultPublisherTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SqsQueueUrls queueUrls;

    private SqsProcessingResultPublisher publisher;

    private final UUID videoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        publisher = new SqsProcessingResultPublisher(
                sqsClient, queueUrls,
                "video-processed-queue", "video-processed-notif-queue",
                "video-failed-queue", "video-failed-notif-queue");
    }

    @Test
    void publishProcessed_enviaAMensagemParaAFilaPrincipalEParaAFilaDeNotificacao() {
        when(queueUrls.resolve("video-processed-queue")).thenReturn("https://sqs.local/processed");
        when(queueUrls.resolve("video-processed-notif-queue")).thenReturn("https://sqs.local/processed-notif");

        publisher.publishProcessed(new VideoProcessedEvent(videoId, "gabriel@video2frames.com", "zips/a.zip", 10));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient, times(2)).sendMessage(captor.capture());

        List<SendMessageRequest> requests = captor.getAllValues();
        assertThat(requests).extracting(SendMessageRequest::queueUrl)
                .containsExactlyInAnyOrder("https://sqs.local/processed", "https://sqs.local/processed-notif");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.messageBody()).contains(videoId.toString());
            assertThat(request.messageBody()).contains("gabriel@video2frames.com");
            assertThat(request.messageBody()).contains("zips/a.zip");
            assertThat(request.messageBody()).contains("10");
        });
    }

    @Test
    void publishFailed_enviaAMensagemParaAFilaPrincipalEParaAFilaDeNotificacao() {
        when(queueUrls.resolve("video-failed-queue")).thenReturn("https://sqs.local/failed");
        when(queueUrls.resolve("video-failed-notif-queue")).thenReturn("https://sqs.local/failed-notif");

        publisher.publishFailed(new VideoFailedEvent(videoId, "gabriel@video2frames.com", "erro ao processar"));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient, times(2)).sendMessage(captor.capture());

        List<SendMessageRequest> requests = captor.getAllValues();
        assertThat(requests).extracting(SendMessageRequest::queueUrl)
                .containsExactlyInAnyOrder("https://sqs.local/failed", "https://sqs.local/failed-notif");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.messageBody()).contains(videoId.toString());
            assertThat(request.messageBody()).contains("erro ao processar");
        });
    }
}
