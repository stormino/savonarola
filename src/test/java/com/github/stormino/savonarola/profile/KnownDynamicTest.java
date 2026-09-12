package com.github.stormino.savonarola.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnownDynamicTest {

    private static KnownDynamic from(DynamicSource source) {
        return new KnownDynamic(1L, 2L, "pattern", source);
    }

    @Test
    void inferenceMayOverwriteInference() {
        assertThat(from(DynamicSource.BOT_INFERRED).supersededBy(DynamicSource.BOT_INFERRED)).isTrue();
    }

    @Test
    void inferenceMayNotOverwriteAnAdminAnnotation() {
        assertThat(from(DynamicSource.ADMIN_ANNOTATED).supersededBy(DynamicSource.BOT_INFERRED))
                .isFalse();
    }

    @Test
    void anAdminMayOverwriteAnything() {
        assertThat(from(DynamicSource.ADMIN_ANNOTATED).supersededBy(DynamicSource.ADMIN_ANNOTATED))
                .isTrue();
        assertThat(from(DynamicSource.BOT_INFERRED).supersededBy(DynamicSource.ADMIN_ANNOTATED))
                .isTrue();
    }
}
