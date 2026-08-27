package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsQueueUrlsTest {

    @Mock
    private SqsClient sqsClient;

    private SqsQueueUrls queueUrls;

    @BeforeEach
    void setUp() {
        queueUrls = new SqsQueueUrls(sqsClient);
    }

    @Test
    void resolve_quandoChamadoPelaPrimeiraVez_consultaOSqsClient() {
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs.local/queue-a").build());

        String url = queueUrls.resolve("queue-a");

        assertThat(url).isEqualTo("https://sqs.local/queue-a");
        verify(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    @Test
    void resolve_quandoChamadoVariasVezesParaAMesmaFila_consultaOSqsClientApenasUmaVez() {
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs.local/queue-a").build());

        String first = queueUrls.resolve("queue-a");
        String second = queueUrls.resolve("queue-a");

        assertThat(first).isEqualTo(second);
        verify(sqsClient, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    @Test
    void resolve_quandoFilasDiferentes_consultaOSqsClientParaCadaUma() {
        when(sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("queue-a").build()))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs.local/queue-a").build());
        when(sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("queue-b").build()))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs.local/queue-b").build());

        assertThat(queueUrls.resolve("queue-a")).isEqualTo("https://sqs.local/queue-a");
        assertThat(queueUrls.resolve("queue-b")).isEqualTo("https://sqs.local/queue-b");
        verify(sqsClient, times(2)).getQueueUrl(any(GetQueueUrlRequest.class));
    }
}
