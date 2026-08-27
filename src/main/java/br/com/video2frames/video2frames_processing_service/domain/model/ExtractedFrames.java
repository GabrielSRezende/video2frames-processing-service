package br.com.video2frames.video2frames_processing_service.domain.model;

public record ExtractedFrames(int frameCount, String zipKey) {

    public ExtractedFrames {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Um vídeo processado precisa ter ao menos 1 frame extraído");
        }
    }
}
