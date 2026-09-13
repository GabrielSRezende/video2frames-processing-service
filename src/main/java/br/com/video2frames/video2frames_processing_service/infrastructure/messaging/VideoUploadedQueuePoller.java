package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.usecase.ProcessVideoUseCase;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoUploadedMessage;
import com.google.gson.Gson;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VideoUploadedQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadedQueuePoller.class);
    private static final int SQS_MAX_MESSAGES_PER_RECEIVE = 10;

    private final SqsClient sqsClient;
    private final SqsQueueUrls queueUrls;
    private final ProcessVideoUseCase processVideoUseCase;
    private final String queueName;
    private final int waitTimeSeconds;
    private final Gson gson = new Gson();
    private final ExecutorService executor;

    public VideoUploadedQueuePoller(
            SqsClient sqsClient,
            SqsQueueUrls queueUrls,
            ProcessVideoUseCase processVideoUseCase,
            @Value("${aws.sqs.video-uploaded-queue}") String queueName,
            @Value("${aws.sqs.poll-wait-time-seconds}") int waitTimeSeconds,
            @Value("${app.processing.concurrency}") int concurrency) {
        this.sqsClient = sqsClient;
        this.queueUrls = queueUrls;
        this.processVideoUseCase = processVideoUseCase;
        this.queueName = queueName;
        this.waitTimeSeconds = waitTimeSeconds;
        this.executor = Executors.newFixedThreadPool(concurrency, namedThreadFactory());
        log.info("Pool de processamento de vídeos configurado com concorrência {}", concurrency);
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        String queueUrl = queueUrls.resolve(queueName);

        var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(SQS_MAX_MESSAGES_PER_RECEIVE)
                .waitTimeSeconds(waitTimeSeconds)
                .build());

        for (Message message : response.messages()) {
            executor.submit(() -> processMessage(queueUrl, message));
        }
    }

    private void processMessage(String queueUrl, Message message) {
        try {
            var payload = gson.fromJson(message.body(), VideoUploadedMessage.class);

            processVideoUseCase.execute(new ProcessVideoCommand(
                    UUID.fromString(payload.videoId()), payload.ownerEmail(), payload.videoKey(), payload.fileName()));

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.error("Falha ao interpretar mensagem de video-uploaded, deixando para nova tentativa", e);
        }
    }

    private static java.util.concurrent.ThreadFactory namedThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "video-processor-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Encerrando o pool de processamento à força após 30s de espera");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
