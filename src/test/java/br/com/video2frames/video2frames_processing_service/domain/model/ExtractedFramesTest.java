package br.com.video2frames.video2frames_processing_service.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractedFramesTest {

    @Test
    void constructor_quandoFrameCountPositivo_criaInstanciaComOsCamposInformados() {
        ExtractedFrames frames = new ExtractedFrames(5, "zips/video.zip");

        assertThat(frames.frameCount()).isEqualTo(5);
        assertThat(frames.zipKey()).isEqualTo("zips/video.zip");
    }

    @Test
    void constructor_quandoFrameCountZero_lancaIllegalArgumentException() {
        assertThatThrownBy(() -> new ExtractedFrames(0, "zips/video.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos 1 frame");
    }

    @Test
    void constructor_quandoFrameCountNegativo_lancaIllegalArgumentException() {
        assertThatThrownBy(() -> new ExtractedFrames(-1, "zips/video.zip"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
