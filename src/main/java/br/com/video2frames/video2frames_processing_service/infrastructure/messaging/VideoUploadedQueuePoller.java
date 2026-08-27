package br.com.video2frames.video2frames_processing_service.infrastructure.messaging;

import br.com.video2frames.video2frames_processing_service.application.dto.ProcessVideoCommand;
import br.com.video2frames.video2frames_processing_service.application.usecase.ProcessVideoUseCase;
import br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto.VideoUploadedMessage;
import com.google.gson.Gson;
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

/**
 * Poller manual (long-polling), mesmo padrão do video-service — ver o
 * README para a justificativa de não usar Spring Cloud AWS / @SqsListener.
 *
 * Diferente dos pollers do video-service: aqui a mensagem é deletada logo
 * após chamar o use case, independente do resultado (sucesso ou falha),
 * porque o próprio ProcessVideoUseCase já captura toda falha internamente
 * e publica video-failed — ele nunca relança exceção. Só uma falha do
 * PRÓPRIO poller (ex: JSON malformado) deixaria a mensagem para retry.
 */
@Component
public class VideoUploadedQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadedQueuePoller.class);

    private final SqsClient sqsClient;
    private final SqsQueueUrls queueUrls;
    private final ProcessVideoUseCase processVideoUseCase;
    private final String queueName;
    private final int waitTimeSeconds;
    private final Gson gson = new Gson();

    public VideoUploadedQueuePoller(
            SqsClient sqsClient,
            SqsQueueUrls queueUrls,
            ProcessVideoUseCase processVideoUseCase,
            @Value("${aws.sqs.video-uploaded-queue}") String queueName,
            @Value("${aws.sqs.poll-wait-time-seconds}") int waitTimeSeconds) {
        this.sqsClient = sqsClient;
        this.queueUrls = queueUrls;
        this.processVideoUseCase = processVideoUseCase;
        this.queueName = queueName;
        this.waitTimeSeconds = waitTimeSeconds;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        String queueUrl = queueUrls.resolve(queueName);

        var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(waitTimeSeconds)
                .build());

        for (Message message : response.messages()) {
            try {
                var payload = gson.fromJson(message.body(), VideoUploadedMessage.class);

                processVideoUseCase.execute(new ProcessVideoCommand(
                        UUID.fromString(payload.videoId()), payload.ownerEmail(), payload.videoKey()));

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            } catch (Exception e) {
                log.error("Falha ao interpretar mensagem de video-uploaded, deixando para nova tentativa", e);
            }
        }
    }
}
