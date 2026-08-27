package br.com.video2frames.video2frames_processing_service.domain.exception;

public class InvalidProcessingJobException extends RuntimeException {
    public InvalidProcessingJobException(String message) {
        super(message);
    }
}
