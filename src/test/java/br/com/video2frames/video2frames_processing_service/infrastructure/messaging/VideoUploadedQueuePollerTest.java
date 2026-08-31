package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.usecase.ProcessVideoUseCase;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoUploadedQueuePollerTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;
    private static final long ASYNC_NEVER_TIMEOUT_MS = 500;

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
                sqsClient, queueUrls, processVideoUseCase, "video-uploaded-queue", 10, 2);
        when(queueUrls.resolve("video-uploaded-queue")).thenReturn(queueUrl);
    }

    @AfterEach
    void tearDown() {
        poller.shutdown();
    }

    @Test
    void poll_quandoNaoHaMensagens_naoExecutaOUseCaseNemDeletaMensagem() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(java.util.List.of()).build());

        poller.poll();

        verify(processVideoUseCase, timeout(ASYNC_NEVER_TIMEOUT_MS).times(0)).execute(any());
        verify(sqsClient, timeout(ASYNC_NEVER_TIMEOUT_MS).times(0)).deleteMessage(any(DeleteMessageRequest.class));
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
        verify(processVideoUseCase, timeout(ASYNC_TIMEOUT_MS)).execute(commandCaptor.capture());

        ProcessVideoCommand command = commandCaptor.getValue();
        assertThat(command.videoId()).isEqualTo(videoId);
        assertThat(command.ownerEmail()).isEqualTo("gabriel@video2frames.com");
        assertThat(command.videoKey()).isEqualTo("videos/a.mp4");

        ArgumentCaptor<DeleteMessageRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient, timeout(ASYNC_TIMEOUT_MS)).deleteMessage(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().queueUrl()).isEqualTo(queueUrl);
        assertThat(deleteCaptor.getValue().receiptHandle()).isEqualTo("receipt-1");
    }

    @Test
    void poll_quandoMensagemComJsonInvalido_naoExecutaOUseCaseNemDeletaAMensagem() {
        Message message = Message.builder().body("isso-nao-e-json-valido").receiptHandle("receipt-2").build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        poller.poll();

        verify(processVideoUseCase, timeout(ASYNC_NEVER_TIMEOUT_MS).times(0)).execute(any());
        verify(sqsClient, timeout(ASYNC_NEVER_TIMEOUT_MS).times(0)).deleteMessage(any(DeleteMessageRequest.class));
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

        verify(processVideoUseCase, timeout(ASYNC_TIMEOUT_MS)).execute(any());
        verify(sqsClient, timeout(ASYNC_NEVER_TIMEOUT_MS).times(0)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void poll_quandoVariasMensagens_processaEmParalelo() throws InterruptedException {
        int totalMensagens = 6;
        var mensagens = new java.util.ArrayList<Message>();
        for (int i = 0; i < totalMensagens; i++) {
            String body = "{\"videoId\":\"" + UUID.randomUUID()
                    + "\",\"ownerEmail\":\"gabriel@video2frames.com\",\"videoKey\":\"videos/a.mp4\"}";
            mensagens.add(Message.builder().body(body).receiptHandle("receipt-" + i).build());
        }

        var startLatch = new java.util.concurrent.CountDownLatch(1);
        var concurrentCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var maxObservedConcurrency = new java.util.concurrent.atomic.AtomicInteger(0);

        org.mockito.Mockito.doAnswer(invocation -> {
            int current = concurrentCount.incrementAndGet();
            maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
            startLatch.await();
            concurrentCount.decrementAndGet();
            return null;
        }).when(processVideoUseCase).execute(any());

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagens).build());

        poller.poll();

        Thread.sleep(300);
        startLatch.countDown();

        verify(processVideoUseCase, timeout(ASYNC_TIMEOUT_MS).times(totalMensagens)).execute(any());
        assertThat(maxObservedConcurrency.get())
                .as("mais de um vídeo deve ser processado ao mesmo tempo, respeitando o limite de concorrência configurado")
                .isGreaterThan(1)
                .isLessThanOrEqualTo(2);
    }
}
