package com.allende.filesearch.utils;

import com.allende.filesearch.model.Config;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void theLauncherCanPointToABundledTesseract() {
        Config config = new Config();
        ConfigLoader.applyEnvironment(config, Map.of(ConfigLoader.TESSERACT_PATH_ENV, "C:\\FileSearch\\resources\\tesseract"));
        assertThat(config.getOcr().getTesseractPath()).isEqualTo("C:\\FileSearch\\resources\\tesseract");
    }

    @Test
    void keepsTheConfiguredPathWhenTheVariableIsEmpty() {
        Config config = new Config();
        config.getOcr().setTesseractPath("/opt/tesseract");
        ConfigLoader.applyEnvironment(config, Map.of(ConfigLoader.TESSERACT_PATH_ENV, " "));
        ConfigLoader.applyEnvironment(config, Map.of());
        assertThat(config.getOcr().getTesseractPath()).isEqualTo("/opt/tesseract");
    }
}
