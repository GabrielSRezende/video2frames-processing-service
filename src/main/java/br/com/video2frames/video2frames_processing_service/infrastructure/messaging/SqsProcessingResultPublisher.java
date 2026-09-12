package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;
import br.com.video2frames.video2frames_processing_service.application.port.ProcessingResultPublisherPort;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoFailedMessage;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoProcessedMessage;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Component
public class SqsProcessingResultPublisher implements ProcessingResultPublisherPort {

    private final SqsClient sqsClient;
    private final SqsQueueUrls queueUrls;

    private final String processedQueueName;
    private final String processedNotifQueueName;

    private final String failedQueueName;
    private final String failedNotifQueueName;

    private final Gson gson = new Gson();

    public SqsProcessingResultPublisher(
            SqsClient sqsClient,
            SqsQueueUrls queueUrls,
            @Value("${aws.sqs.video-processed-queue}") String processedQueueName,
            @Value("${aws.sqs.video-processed-notif-queue}") String processedNotifQueueName,
            @Value("${aws.sqs.video-failed-queue}") String failedQueueName,
            @Value("${aws.sqs.video-failed-notif-queue}") String failedNotifQueueName) {

        this.sqsClient = sqsClient;
        this.queueUrls = queueUrls;

        this.processedQueueName = processedQueueName;
        this.processedNotifQueueName = processedNotifQueueName;

        this.failedQueueName = failedQueueName;
        this.failedNotifQueueName = failedNotifQueueName;
    }

    @Override
    public void publishProcessed(VideoProcessedEvent event) {
        log.info("Publicando evento de sucesso para o vídeo {}", event.videoId());

        var message = new VideoProcessedMessage(
                event.videoId().toString(),
                event.ownerEmail(),
                event.zipKey(),
                event.frameCount()
        );

        var messageBody = gson.toJson(message);

        sendMessage(processedQueueName, messageBody);
        sendMessage(processedNotifQueueName, messageBody);
    }

    @Override
    public void publishFailed(VideoFailedEvent event) {
        log.info("Publicando evento de falha para o vídeo {}: {}", event.videoId(), event.reason());

        var message = new VideoFailedMessage(
                event.videoId().toString(),
                event.ownerEmail(),
                event.reason()
        );

        var messageBody = gson.toJson(message);

        sendMessage(failedQueueName, messageBody);
        sendMessage(failedNotifQueueName, messageBody);
    }

    private void sendMessage(String queueName, String messageBody) {
        try {
            sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrls.resolve(queueName))
                            .messageBody(messageBody)
                            .build()
            );
        } catch (RuntimeException e) {
            log.error("Falha ao publicar mensagem na fila {}", queueName, e);
            throw e;
        }
    }
}