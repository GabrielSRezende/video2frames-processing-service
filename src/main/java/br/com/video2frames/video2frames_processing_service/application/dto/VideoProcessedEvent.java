package br.com.video2frames.video2frames_processing_service.application.dto;

import java.util.UUID;

public record VideoProcessedEvent(UUID videoId, String ownerEmail, String zipKey, int frameCount) {
}
