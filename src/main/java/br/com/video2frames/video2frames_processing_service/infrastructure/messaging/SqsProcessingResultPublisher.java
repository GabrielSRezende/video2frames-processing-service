package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;
import br.com.video2frames.video2frames_processing_service.application.port.ProcessingResultPublisherPort;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoFailedMessage;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoProcessedMessage;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class SqsProcessingResultPublisher implements ProcessingResultPublisherPort {

    private final SqsClient sqsClient;
    private final SqsQueueUrls queueUrls;
    private final String processedQueueName;
    private final String failedQueueName;
    private final Gson gson = new Gson();

    public SqsProcessingResultPublisher(
            SqsClient sqsClient,
            SqsQueueUrls queueUrls,
            @Value("${aws.sqs.video-processed-queue}") String processedQueueName,
            @Value("${aws.sqs.video-failed-queue}") String failedQueueName) {
        this.sqsClient = sqsClient;
        this.queueUrls = queueUrls;
        this.processedQueueName = processedQueueName;
        this.failedQueueName = failedQueueName;
    }

    @Override
    public void publishProcessed(VideoProcessedEvent event) {
        var message = new VideoProcessedMessage(
                event.videoId().toString(), event.ownerEmail(), event.zipKey(), event.frameCount());
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrls.resolve(processedQueueName))
                .messageBody(gson.toJson(message))
                .build());
    }

    @Override
    public void publishFailed(VideoFailedEvent event) {
        var message = new VideoFailedMessage(event.videoId().toString(), event.ownerEmail(), event.reason());
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrls.resolve(failedQueueName))
                .messageBody(gson.toJson(message))
                .build());
    }
}
