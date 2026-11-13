package com.immodata.gb.domain.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Golden fixtures (all facts have area 100 m², so price = pps * 100). R-7 percentiles: h = (n-1)*p, linear interpolation.
 *
 * <ol>
 *   <li>n=1: pps [200000], target 80 → 16 000 000 for low/median/high.
 *   <li>n=2: pps [100000, 140000], target 50 → low 5 000 000, median 6 000 000, high 7 000 000.
 *   <li>n=3 (no fence): [100000, 120000, 160000] → Q1 110000, median 120000, Q3 140000; target 100.
 *   <li>n=5 (no outlier): [100k..140k step 10k] → fence [80k,160k], Q1 110k, median 120k, Q3 130k; score 75.
 *   <li>n=6 (high outlier 500k): Q1 106250, Q3 118750, fence upper 137500 → 500k excluded; retained Q1 105k, med 110k, Q3 115k.
 *   <li>n=5 (low outlier 10k): fence [94k,110k] → 10k excluded; retained [100k,102k,104k,106k] → Q1 101500, med 103000, Q3 104500.
 *   <li>HALF_UP: single pps 100.05 × 10 m² = 1000.5 → 1001.
 * </ol>
 */
class ValuationCalculatorTest {

    private static final BigDecimal AREA_100 = new BigDecimal("100");
    private static final LocalDate OBSERVED = LocalDate.of(2026, 5, 1);

    private final ValuationCalculator calculator = new ValuationCalculator();

    private static ComparableFact fact(long priceXof) {
        return new ComparableFact(UUID.randomUUID(), "APARTMENT", priceXof, AREA_100, OBSERVED, 500.0);
    }

    /** Builds facts of area 100 m² from pps values (price = pps * 100). */
    private static List<ComparableFact> factsFromPps(long... ppsValues) {
        List<ComparableFact> facts = new ArrayList<>();
        for (long pps : ppsValues) {
            facts.add(fact(pps * 100));
        }
        return facts;
    }

    private static void assertTotals(ValuationComputation r, long low, long median, long high) {
        assertThat(r.rangeLow()).as("rangeLow").isEqualTo(low);
        assertThat(r.medianTotal()).as("medianTotal").isEqualTo(median);
        assertThat(r.rangeHigh()).as("rangeHigh").isEqualTo(high);
    }

    private static final String WARNING_FORMAT =
            "Amostra insuficiente (n=%d). A estimativa é indicativa; recomenda-se prudência.";

    @Test
    void fixture1_singleComparable_lowEqualsHighEqualsMedian() {
        ValuationComputation r = calculator.compute(factsFromPps(200_000), new BigDecimal("80"));

        assertTotals(r, 16_000_000L, 16_000_000L, 16_000_000L);
        assertThat(r.comparablesCount()).isEqualTo(1);
        assertThat(r.confidenceScore()).isEqualTo(15);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(r.warning()).isEqualTo(WARNING_FORMAT.formatted(1));
    }

    @Test
    void fixture2_twoComparables_medianIsAverageLowMinHighMax() {
        ValuationComputation r = calculator.compute(factsFromPps(140_000, 100_000), new BigDecimal("50"));

        assertTotals(r, 5_000_000L, 6_000_000L, 7_000_000L);
        assertThat(r.comparablesCount()).isEqualTo(2);
        assertThat(r.confidenceScore()).isEqualTo(30);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(r.warning()).isEqualTo(WARNING_FORMAT.formatted(2));
    }

    @Test
    void fixture3_threeComparables_noOutlierFence() {
        // 1_000_000 pps would be an outlier with n>=4 logic but must be retained with n<4.
        ValuationComputation noFence = calculator.compute(factsFromPps(100_000, 120_000, 1_000_000), AREA_100);
        assertThat(noFence.comparablesCount()).isEqualTo(3);
        assertThat(noFence.retainedFacts()).hasSize(3);

        ValuationComputation r = calculator.compute(factsFromPps(160_000, 100_000, 120_000), AREA_100);

        assertTotals(r, 11_000_000L, 12_000_000L, 14_000_000L);
        assertThat(r.comparablesCount()).isEqualTo(3);
        assertThat(r.confidenceScore()).isEqualTo(45);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(r.warning()).isEqualTo(WARNING_FORMAT.formatted(3));
    }

    @Test
    void fixture4_fiveComparables_noOutlier_noWarning() {
        ValuationComputation r =
                calculator.compute(factsFromPps(100_000, 110_000, 120_000, 130_000, 140_000), AREA_100);

        assertTotals(r, 11_000_000L, 12_000_000L, 13_000_000L);
        assertThat(r.comparablesCount()).isEqualTo(5);
        assertThat(r.confidenceScore()).isEqualTo(75);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(r.warning()).isNull();
    }

    @Test
    void fixture5_highOutlierExcluded() {
        List<ComparableFact> input = factsFromPps(100_000, 105_000, 110_000, 115_000, 120_000, 500_000);
        ComparableFact outlier = input.get(5);

        ValuationComputation r = calculator.compute(input, new BigDecimal("60"));

        assertTotals(r, 6_300_000L, 6_600_000L, 6_900_000L);
        assertThat(r.comparablesCount()).isEqualTo(5);
        assertThat(r.retainedFacts()).hasSize(5).doesNotContain(outlier);
        assertThat(r.confidenceScore()).isEqualTo(75);
        assertThat(r.warning()).isNull();
    }

    @Test
    void fixture6_lowOutlierExcluded_warningWithFourComparables() {
        List<ComparableFact> input = factsFromPps(10_000, 100_000, 102_000, 104_000, 106_000);
        ComparableFact outlier = input.get(0);

        ValuationComputation r = calculator.compute(input, AREA_100);

        assertTotals(r, 10_150_000L, 10_300_000L, 10_450_000L);
        assertThat(r.comparablesCount()).isEqualTo(4);
        assertThat(r.retainedFacts()).doesNotContain(outlier);
        assertThat(r.confidenceScore()).isEqualTo(60);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(r.warning()).isEqualTo(WARNING_FORMAT.formatted(4));
    }

    @Test
    void fixture7_totalsRoundHalfUp() {
        // pps = 10005 / 100 = 100.05 ; * 10 m² = 1000.5 → HALF_UP 1001
        ValuationComputation r = calculator.compute(List.of(fact(10_005)), new BigDecimal("10"));

        assertTotals(r, 1_001L, 1_001L, 1_001L);
    }

    @Test
    void ppsUsesEachFactArea() {
        ComparableFact a = new ComparableFact(UUID.randomUUID(), "HOUSE", 20_000_000L, new BigDecimal("50"), OBSERVED, 100);
        ComparableFact b = new ComparableFact(UUID.randomUUID(), "HOUSE", 36_000_000L, new BigDecimal("100"), OBSERVED, 200);

        ValuationComputation r = calculator.compute(List.of(a, b), new BigDecimal("100"));

        // pps 400000 and 360000 → median 380000
        assertTotals(r, 36_000_000L, 38_000_000L, 40_000_000L);
        assertThat(r.medianPps()).isEqualByComparingTo("380000");
    }

    @Test
    void scoreIsCappedAt100() {
        ValuationComputation r = calculator.compute(
                factsFromPps(100_000, 101_000, 102_000, 103_000, 104_000, 105_000, 106_000, 107_000), AREA_100);

        assertThat(r.comparablesCount()).isEqualTo(8);
        assertThat(r.confidenceScore()).isEqualTo(100);
        assertThat(r.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(r.warning()).isNull();
    }

    @Test
    void emptyCandidatesThrowIllegalArgument() {
        assertThatThrownBy(() -> calculator.compute(List.of(), AREA_100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidTargetAreaThrowsIllegalArgument() {
        assertThatThrownBy(() -> calculator.compute(factsFromPps(100_000), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.compute(factsFromPps(100_000), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
