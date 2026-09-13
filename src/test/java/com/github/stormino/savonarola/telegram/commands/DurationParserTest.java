package com.github.stormino.savonarola.telegram.commands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @Test
    void convertsEachUnitToMinutes() {
        assertThat(DurationParser.toMinutes("10m")).isEqualTo(10);
        assertThat(DurationParser.toMinutes("2h")).isEqualTo(120);
        assertThat(DurationParser.toMinutes("1d")).isEqualTo(1440);
        assertThat(DurationParser.toMinutes(" 2H ")).isEqualTo(120);
    }

    @Test
    void rejectsMalformedSpecs() {
        assertThatThrownBy(() -> DurationParser.toMinutes("soon"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.toMinutes("10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.toMinutes("0m"))
                .hasMessageContaining("maggiore di zero");
    }

    @Test
    void refusesDurationsLongEnoughToActLikeABan() {
        assertThatThrownBy(() -> DurationParser.toMinutes("400d"))
                .hasMessageContaining("30 giorni");
    }
}
