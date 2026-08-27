package br.com.video2frames.video2frames_processing_service.domain.exception;

/**
 * Exceção "guarda-chuva" para qualquer falha do pipeline de processamento.
 * O use case captura esta exceção (e só esta) para decidir que o job
 * falhou e publicar o evento video-failed com a mensagem informada — o
 * "reason" é pensado para ser exibido ao usuário final no Angular, então
 * cada estágio (download, ffmpeg, zip, upload) deve lançar com uma
 * mensagem clara.
 */
public class VideoProcessingFailedException extends RuntimeException {
    public VideoProcessingFailedException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public VideoProcessingFailedException(String reason) {
        super(reason);
    }
}
