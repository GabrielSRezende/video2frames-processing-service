package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.usecase.ProcessVideoUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoUploadedQueuePollerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SqsQueueUrls queueUrls;

    @Mock
    private ProcessVideoUseCase processVideoUseCase;

    private VideoUploadedQueuePoller poller;

    private final String queueUrl = "https://sqs.local/video-uploaded";

    @BeforeEach
    void setUp() {
        poller = new VideoUploadedQueuePoller(
                sqsClient, queueUrls, processVideoUseCase, "video-uploaded-queue", 10);
        when(queueUrls.resolve("video-uploaded-queue")).thenReturn(queueUrl);
    }

    @Test
    void poll_quandoNaoHaMensagens_naoExecutaOUseCaseNemDeletaMensagem() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(java.util.List.of()).build());

        poller.poll();

        verify(processVideoUseCase, never()).execute(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void poll_quandoMensagemValida_executaOUseCaseEDeletaAMensagem() {
        UUID videoId = UUID.randomUUID();
        String body = "{\"videoId\":\"" + videoId + "\",\"ownerEmail\":\"gabriel@video2frames.com\",\"videoKey\":\"videos/a.mp4\"}";
        Message message = Message.builder().body(body).receiptHandle("receipt-1").build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        poller.poll();

        ArgumentCaptor<ProcessVideoCommand> commandCaptor = ArgumentCaptor.forClass(ProcessVideoCommand.class);
        verify(processVideoUseCase).execute(commandCaptor.capture());

        ProcessVideoCommand command = commandCaptor.getValue();
        assertThat(command.videoId()).isEqualTo(videoId);
        assertThat(command.ownerEmail()).isEqualTo("gabriel@video2frames.com");
        assertThat(command.videoKey()).isEqualTo("videos/a.mp4");

        ArgumentCaptor<DeleteMessageRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().queueUrl()).isEqualTo(queueUrl);
        assertThat(deleteCaptor.getValue().receiptHandle()).isEqualTo("receipt-1");
    }

    @Test
    void poll_quandoMensagemComJsonInvalido_naoExecutaOUseCaseNemDeletaAMensagem() {
        Message message = Message.builder().body("isso-nao-e-json-valido").receiptHandle("receipt-2").build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        poller.poll();

        verify(processVideoUseCase, never()).execute(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void poll_quandoUseCaseLancaExcecao_naoDeletaAMensagem() {
        UUID videoId = UUID.randomUUID();
        String body = "{\"videoId\":\"" + videoId + "\",\"ownerEmail\":\"gabriel@video2frames.com\",\"videoKey\":\"videos/a.mp4\"}";
        Message message = Message.builder().body(body).receiptHandle("receipt-3").build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        org.mockito.Mockito.doThrow(new RuntimeException("falha inesperada"))
                .when(processVideoUseCase).execute(any());

        poller.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
