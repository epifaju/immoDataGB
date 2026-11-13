package com.immodata.gb.domain.valuation;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure median + IQR valuation (spec Bloc 5 § Calcul).
 *
 * <p>Empty-input convention: {@link #compute} throws {@link IllegalArgumentException}; the service checks for no
 * comparables first and maps to {@code VALUATION_NO_COMPARABLES}.
 */
public class ValuationCalculator {

    static final int MIN_RELIABLE_SAMPLE = 5;
    private static final int OUTLIER_FENCE_MIN_N = 4;
    private static final int CALC_SCALE = 10;
    private static final int PPS_OUTPUT_SCALE = 4;
    private static final BigDecimal FENCE_FACTOR = new BigDecimal("1.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private record Entry(ComparableFact fact, BigDecimal pps) {}

    public ValuationComputation compute(List<ComparableFact> candidates, BigDecimal targetAreaSqm) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        if (targetAreaSqm == null || targetAreaSqm.signum() <= 0) {
            throw new IllegalArgumentException("targetAreaSqm must be > 0");
        }

        List<Entry> entries = new ArrayList<>(candidates.size());
        for (ComparableFact fact : candidates) {
            if (fact.areaSqm() == null || fact.areaSqm().signum() <= 0) {
                throw new IllegalArgumentException("comparable area must be > 0: " + fact.id());
            }
            BigDecimal pps = BigDecimal.valueOf(fact.priceXof())
                    .divide(fact.areaSqm(), CALC_SCALE, RoundingMode.HALF_UP);
            entries.add(new Entry(fact, pps));
        }

        List<Entry> retained = entries;
        if (entries.size() >= OUTLIER_FENCE_MIN_N) {
            List<BigDecimal> sorted = sortedPps(entries);
            BigDecimal q1 = percentile(sorted, 0.25);
            BigDecimal q3 = percentile(sorted, 0.75);
            BigDecimal margin = q3.subtract(q1).multiply(FENCE_FACTOR);
            BigDecimal lowerFence = q1.subtract(margin);
            BigDecimal upperFence = q3.add(margin);
            retained = entries.stream()
                    .filter(e -> e.pps().compareTo(lowerFence) >= 0 && e.pps().compareTo(upperFence) <= 0)
                    .toList();
        }

        List<BigDecimal> sorted = sortedPps(retained);
        int m = sorted.size();
        BigDecimal median = median(sorted);
        BigDecimal low;
        BigDecimal high;
        if (m == 1) {
            low = median;
            high = median;
        } else if (m == 2) {
            low = sorted.get(0);
            high = sorted.get(1);
        } else {
            low = percentile(sorted, 0.25);
            high = percentile(sorted, 0.75);
        }

        int score = Math.min(100, m * 15);
        String warning = m < MIN_RELIABLE_SAMPLE
                ? "Amostra insuficiente (n=" + m + "). A estimativa é indicativa; recomenda-se prudência."
                : null;

        return new ValuationComputation(
                total(low, targetAreaSqm),
                total(median, targetAreaSqm),
                total(high, targetAreaSqm),
                low.setScale(PPS_OUTPUT_SCALE, RoundingMode.HALF_UP),
                median.setScale(PPS_OUTPUT_SCALE, RoundingMode.HALF_UP),
                high.setScale(PPS_OUTPUT_SCALE, RoundingMode.HALF_UP),
                m,
                entries.size() - m,
                score,
                ConfidenceLevel.fromScore(score),
                warning,
                retained.stream().map(Entry::fact).toList());
    }

    private static List<BigDecimal> sortedPps(List<Entry> entries) {
        return entries.stream().map(Entry::pps).sorted(Comparator.naturalOrder()).toList();
    }

    private static long total(BigDecimal pps, BigDecimal areaSqm) {
        return pps.multiply(areaSqm).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(TWO, CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** R-7 percentile: h = (n-1)*p, linear interpolation between floor(h) and ceil(h). */
    private static BigDecimal percentile(List<BigDecimal> sorted, double p) {
        int n = sorted.size();
        BigDecimal h = BigDecimal.valueOf(n - 1).multiply(BigDecimal.valueOf(p));
        int lo = h.setScale(0, RoundingMode.FLOOR).intValueExact();
        int hi = Math.min(lo + 1, n - 1);
        BigDecimal frac = h.subtract(BigDecimal.valueOf(lo));
        BigDecimal a = sorted.get(lo);
        BigDecimal b = sorted.get(hi);
        return a.add(b.subtract(a).multiply(frac)).setScale(CALC_SCALE, RoundingMode.HALF_UP);
    }
}
