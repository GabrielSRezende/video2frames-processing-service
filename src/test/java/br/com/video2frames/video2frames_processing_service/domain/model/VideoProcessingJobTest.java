package br.com.video2frames.video2frames_processing_service.domain.model;

import br.com.video2frames.video2frames_processing_service.domain.exception.InvalidProcessingJobException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProcessingJobTest {

    @Test
    void of_comDadosValidos_criaJobComOsCamposInformados() {
        UUID videoId = UUID.randomUUID();

        VideoProcessingJob job = VideoProcessingJob.of(videoId, "gabriel@video2frames.com", "videos/abc.mp4");

        assertThat(job.getVideoId()).isEqualTo(videoId);
        assertThat(job.getOwnerEmail()).isEqualTo("gabriel@video2frames.com");
        assertThat(job.getVideoKey()).isEqualTo("videos/abc.mp4");
    }

    @Test
    void of_quandoVideoIdNulo_lancaInvalidProcessingJobException() {
        assertThatThrownBy(() -> VideoProcessingJob.of(null, "gabriel@video2frames.com", "videos/abc.mp4"))
                .isInstanceOf(InvalidProcessingJobException.class)
                .hasMessageContaining("videoId");
    }

    @Test
    void of_quandoOwnerEmailNulo_lancaInvalidProcessingJobException() {
        assertThatThrownBy(() -> VideoProcessingJob.of(UUID.randomUUID(), null, "videos/abc.mp4"))
                .isInstanceOf(InvalidProcessingJobException.class)
                .hasMessageContaining("ownerEmail");
    }

    @Test
    void of_quandoOwnerEmailEmBranco_lancaInvalidProcessingJobException() {
        assertThatThrownBy(() -> VideoProcessingJob.of(UUID.randomUUID(), "   ", "videos/abc.mp4"))
                .isInstanceOf(InvalidProcessingJobException.class)
                .hasMessageContaining("ownerEmail");
    }

    @Test
    void of_quandoVideoKeyNulo_lancaInvalidProcessingJobException() {
        assertThatThrownBy(() -> VideoProcessingJob.of(UUID.randomUUID(), "gabriel@video2frames.com", null))
                .isInstanceOf(InvalidProcessingJobException.class)
                .hasMessageContaining("videoKey");
    }

    @Test
    void of_quandoVideoKeyEmBranco_lancaInvalidProcessingJobException() {
        assertThatThrownBy(() -> VideoProcessingJob.of(UUID.randomUUID(), "gabriel@video2frames.com", "  "))
                .isInstanceOf(InvalidProcessingJobException.class)
                .hasMessageContaining("videoKey");
    }
}
