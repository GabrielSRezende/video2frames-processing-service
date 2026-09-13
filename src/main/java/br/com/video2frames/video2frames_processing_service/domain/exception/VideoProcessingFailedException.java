package br.com.video2frames.video2frames_processing_service.domain.exception;

public class VideoProcessingFailedException extends RuntimeException {
    public VideoProcessingFailedException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public VideoProcessingFailedException(String reason) {
        super(reason);
    }
}
