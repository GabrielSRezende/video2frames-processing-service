package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;

public interface VideoDownloadPort {

    /** Baixa o vídeo original pela key e retorna o caminho do arquivo local temporário. */
    Path download(String videoKey);
}
