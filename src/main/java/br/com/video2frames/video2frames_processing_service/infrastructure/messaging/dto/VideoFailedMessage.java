package br.com.video2frames.video2frames_processing_service.infrastructure.messaging.dto;

public record VideoFailedMessage(String videoId, String ownerEmail, String reason) {
}
