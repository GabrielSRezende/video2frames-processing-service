package br.com.video2frames.video2frames_processing_service.domain.model;

import br.com.video2frames.video2frames_processing_service.domain.exception.InvalidProcessingJobException;

import java.util.UUID;

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
