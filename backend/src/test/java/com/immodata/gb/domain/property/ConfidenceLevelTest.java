package com.immodata.gb.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfidenceLevelTest {

    @ParameterizedTest
    @CsvSource({
        "0,LOW",
        "33,LOW",
        "34,MEDIUM",
        "66,MEDIUM",
        "67,HIGH",
        "100,HIGH"
    })
    void fromScoreMapsBands(int score, ConfidenceLevel expected) {
        assertEquals(expected, ConfidenceLevel.fromScore(score));
    }

    @Test
    void fromScoreCoversEveryScoreFrom0To100() {
        for (int score = 0; score <= 100; score++) {
            ConfidenceLevel expected = score <= 33
                    ? ConfidenceLevel.LOW
                    : score <= 66 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.HIGH;
            assertEquals(expected, ConfidenceLevel.fromScore(score), "score " + score);
        }
    }

    @Test
    void fromScoreRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.fromScore(-1));
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.fromScore(101));
    }
}
