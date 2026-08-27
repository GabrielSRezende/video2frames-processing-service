package br.com.video2frames.video2frames_processing_service.domain.model;

import br.com.video2frames.video2frames_processing_service.domain.exception.InvalidProcessingJobException;

import java.util.UUID;

/**
 * Este serviço não tem banco de dados (por desenho de arquitetura), então
 * "domínio" aqui é mais enxuto do que no auth-service/video-service: a
 * regra de negócio real é o próprio pipeline de transformação (baixar,
 * extrair, compactar, publicar). Ainda assim, a validação de que um job
 * é bem-formado é regra de negócio genuína, e vive aqui — não no use case.
 */
public final class VideoProcessingJob {

    private final UUID videoId;
    private final String ownerEmail;
    private final String videoKey;

    private VideoProcessingJob(UUID videoId, String ownerEmail, String videoKey) {
        this.videoId = videoId;
        this.ownerEmail = ownerEmail;
        this.videoKey = videoKey;
    }

    public static VideoProcessingJob of(UUID videoId, String ownerEmail, String videoKey) {
        if (videoId == null) {
            throw new InvalidProcessingJobException("videoId é obrigatório");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new InvalidProcessingJobException("ownerEmail é obrigatório");
        }
        if (videoKey == null || videoKey.isBlank()) {
            throw new InvalidProcessingJobException("videoKey é obrigatório");
        }
        return new VideoProcessingJob(videoId, ownerEmail, videoKey);
    }

    public UUID getVideoId() {
        return videoId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getVideoKey() {
        return videoKey;
    }
}
