package br.com.video2frames.video2frames_processing_service.application.dto;

import java.util.UUID;

public record ProcessVideoCommand(UUID videoId, String ownerEmail, String videoKey) {
}
