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

/**
 * Poller manual (long-polling), mesmo padrão do video-service — ver o
 * README para a justificativa de não usar Spring Cloud AWS / @SqsListener.
 *
 * Processa até {@code app.processing.concurrency} vídeos em paralelo: cada
 * chamada busca até 10 mensagens (limite do SQS) e submete cada uma para um
 * thread pool de tamanho fixo, em vez de processar uma por vez. Isso é o que
 * permite processar múltiplos vídeos simultaneamente dentro de uma única
 * instância — o pipeline (download → ffmpeg → zip → upload → publish) já é
 * livre de estado compartilhado (arquivos temporários únicos por vídeo,
 * clientes AWS thread-safe), então paralelizar aqui é seguro sem mudar nada
 * no restante do código.
 *
 * Diferente dos pollers do video-service: aqui a mensagem é deletada logo
 * após chamar o use case, independente do resultado (sucesso ou falha),
 * porque o próprio ProcessVideoUseCase já captura toda falha internamente
 * e publica video-failed — ele nunca relança exceção. Só uma falha do
 * PRÓPRIO poller (ex: JSON malformado) deixaria a mensagem para retry.
 *
 * Além da concorrência dentro do processo, o serviço é stateless e segue o
 * padrão "competing consumers": rodar múltiplas réplicas deste serviço
 * (todas consumindo a mesma fila video-uploaded) escala o processamento
 * horizontalmente sem nenhuma mudança de código — ver
 * video2frames-infra-ops para como escalar via Docker Compose.
 */
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
                    UUID.fromString(payload.videoId()), payload.ownerEmail(), payload.videoKey()));

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
