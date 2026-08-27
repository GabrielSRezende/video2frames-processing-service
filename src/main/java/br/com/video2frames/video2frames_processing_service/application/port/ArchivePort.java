package br.com.video2frames.video2frames_processing_service.application.port;

import java.nio.file.Path;

public interface ArchivePort {

    /** Compacta todos os arquivos de sourceDir em um .zip local e retorna o caminho do zip. */
    Path zip(Path sourceDir, String zipFileName);
}
