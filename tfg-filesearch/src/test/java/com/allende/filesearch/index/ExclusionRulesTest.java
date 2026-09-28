package com.allende.filesearch.index;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExclusionRulesTest {

    private final ExclusionRules rules = new ExclusionRules(List.of(
            "**/.git/**", "**/$RECYCLE.BIN/**", "**/~$*", "**/Thumbs.db", "**\\node_modules\\**"));

    @Test
    void excludesFoldersByName() {
        assertThat(rules.isExcludedFolder(Path.of("/exp/garcia/.git"))).isTrue();
        assertThat(rules.isExcludedFolder(Path.of("/d/$Recycle.Bin"))).isTrue();
        assertThat(rules.isExcludedFolder(Path.of("/app/node_modules"))).isTrue();
        assertThat(rules.isExcludedFolder(Path.of("/exp/garcia"))).isFalse();
    }

    @Test
    void excludesFilesByNameWithWildcards() {
        assertThat(rules.isExcludedFile(Path.of("/exp/~$demanda.docx"))).isTrue();
        assertThat(rules.isExcludedFile(Path.of("/exp/thumbs.db"))).isTrue();
        assertThat(rules.isExcludedFile(Path.of("/exp/demanda.docx"))).isFalse();
    }

    @Test
    void rejectsUnsupportedPatterns() {
        assertThatThrownBy(() -> new ExclusionRules(List.of("clientes/antiguos/**")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNoPatterns() {
        ExclusionRules none = new ExclusionRules(null);
        assertThat(none.isExcludedFolder(Path.of("/a/.git"))).isFalse();
    }
}
