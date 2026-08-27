package br.com.video2frames.video2frames_processing_service.application.port;

import br.com.video2frames.video2frames_processing_service.application.dto.VideoFailedEvent;
import br.com.video2frames.video2frames_processing_service.application.dto.VideoProcessedEvent;

public interface ProcessingResultPublisherPort {

    void publishProcessed(VideoProcessedEvent event);

    void publishFailed(VideoFailedEvent event);
}
