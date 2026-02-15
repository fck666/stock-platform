package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.api.dto.*;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
/**
 * Service for calculating technical indicators.
 * Computes MA, MACD, KDJ, and handles time-frame aggregation (weekly, monthly, etc.).
 */
public class IndicatorsService {
    private final MarketRepository marketRepository;

    public IndicatorsService(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    /**
     * Compute indicators for a given symbol and interval.
     * Fetches raw daily bars from DB, aggregates them if needed, and then computes requested indicators.
     *
     * @param canonicalSymbol Stock symbol
     * @param interval Interval string (1d, 1w, 1m, 1q, 1y)
     * @param start Start date
     * @param end End date
     * @param maPeriods List of periods for Simple Moving Average (e.g. [20, 50, 200])
     * @param includeMacd Whether to compute MACD (12, 26, 9)
     * @param includeKdj Whether to compute KDJ (9, 3, 3)
     * @return DTO containing all computed points
     */
    public IndicatorsResponseDto getIndicators(
            String canonicalSymbol,
            String interval,
            LocalDate start,
            LocalDate end,
            List<Integer> maPeriods,
            boolean includeMacd,
            boolean includeKdj
    ) {
        LocalDate effectiveEnd = end != null ? end : LocalDate.now().minusDays(1);
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusYears(2);
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("start must be <= end");
        }

        // 1. Determine fetch range. We need extra history for indicators to stabilize (warm-up period).
        // e.g. for MA200, we need at least 200 days prior to start date.
        int maxMa = maPeriods.stream().max(Integer::compareTo).orElse(0);
        int lookbackDays = Math.max(1200, maxMa * 3);
        LocalDate fetchStart = effectiveStart.minusDays(lookbackDays);

        // 2. Fetch daily bars and aggregate if interval > 1d
        List<BarDto> dailyBars = marketRepository.getBarsBySymbol(canonicalSymbol, "1d", fetchStart, effectiveEnd);
        List<BarDto> bars = aggregateIfNeeded(dailyBars, interval);

        // 3. Prepare arrays for calculation
        List<LocalDate> dates = new ArrayList<>(bars.size());
        double[] close = new double[bars.size()];
        double[] high = new double[bars.size()];
        double[] low = new double[bars.size()];

        for (int i = 0; i < bars.size(); i++) {
            BarDto b = bars.get(i);
            dates.add(b.date());
            close[i] = toDouble(b.close());
            high[i] = toDouble(b.high());
            low[i] = toDouble(b.low());
        }

        // 4. Calculate SMAs
        Map<Integer, double[]> maMap = new HashMap<>();
        for (Integer p : maPeriods) {
            if (p != null && p > 0) {
                maMap.put(p, sma(close, p));
            }
        }

        // 5. Calculate MACD (standard settings: 12, 26, 9)
        double[] dif = null;
        double[] dea = null;
        double[] hist = null;
        if (includeMacd) {
            double[] ema12 = ema(close, 12);
            double[] ema26 = ema(close, 26);
            dif = new double[close.length];
            for (int i = 0; i < close.length; i++) {
                dif[i] = ema12[i] - ema26[i];
            }
            dea = ema(dif, 9);
            hist = new double[close.length];
            for (int i = 0; i < close.length; i++) {
                hist[i] = (dif[i] - dea[i]) * 2.0;
            }
        }

        // 6. Calculate KDJ (standard settings: 9, 3, 3)
        double[] k = null;
        double[] d = null;
        double[] j = null;
        if (includeKdj) {
            double[][] kdj = kdj(high, low, close, 9, 3, 3);
            k = kdj[0];
            d = kdj[1];
            j = kdj[2];
        }

        List<IndicatorPointDto> out = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            LocalDate dt = dates.get(i);
            if (dt.isBefore(effectiveStart) || dt.isAfter(effectiveEnd)) continue;

            Map<Integer, BigDecimal> maOut = null;
            if (!maMap.isEmpty()) {
                maOut = new TreeMap<>();
                for (Map.Entry<Integer, double[]> e : maMap.entrySet()) {
                    double v = e.getValue()[i];
                    if (!Double.isNaN(v)) {
                        maOut.put(e.getKey(), bd(v));
                    } else {
                        maOut.put(e.getKey(), null);
                    }
                }
            }

            MacdDto macd = null;
            if (includeMacd && dif != null && dea != null && hist != null) {
                macd = new MacdDto(bd(dif[i]), bd(dea[i]), bd(hist[i]));
            }

            KdjDto kdjDto = null;
            if (includeKdj && k != null && d != null && j != null) {
                kdjDto = new KdjDto(bd(k[i]), bd(d[i]), bd(j[i]));
            }

            out.add(new IndicatorPointDto(dt, maOut, macd, kdjDto));
        }

        return new IndicatorsResponseDto(interval, out);
    }

    private static double toDouble(BigDecimal v) {
        if (v == null) return Double.NaN;
        return v.doubleValue();
    }

    private static BigDecimal bd(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static double[] sma(double[] values, int period) {
        double[] out = new double[values.length];
        Arrays.fill(out, Double.NaN);
        if (period <= 0) return out;
        double sum = 0.0;
        int cnt = 0;
        for (int i = 0; i < values.length; i++) {
            double v = values[i];
            if (!Double.isNaN(v)) {
                sum += v;
                cnt += 1;
            }
            if (i >= period) {
                double prev = values[i - period];
                if (!Double.isNaN(prev)) {
                    sum -= prev;
                    cnt -= 1;
                }
            }
            if (i >= period - 1 && cnt == period) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    private static double[] ema(double[] values, int period) {
        double[] out = new double[values.length];
        Arrays.fill(out, Double.NaN);
        if (values.length == 0) return out;
        double alpha = 2.0 / (period + 1.0);
        double prev = Double.NaN;
        for (int i = 0; i < values.length; i++) {
            double v = values[i];
            if (Double.isNaN(v)) {
                out[i] = prev;
                continue;
            }
            if (Double.isNaN(prev)) {
                prev = v;
            } else {
                prev = alpha * v + (1.0 - alpha) * prev;
            }
            out[i] = prev;
        }
        return out;
    }

    private static double[][] kdj(double[] high, double[] low, double[] close, int n, int kPeriod, int dPeriod) {
        int len = close.length;
        double[] k = new double[len];
        double[] d = new double[len];
        double[] j = new double[len];

        double prevK = 50.0;
        double prevD = 50.0;

        for (int i = 0; i < len; i++) {
            int from = Math.max(0, i - n + 1);
            double llv = Double.POSITIVE_INFINITY;
            double hhv = Double.NEGATIVE_INFINITY;
            for (int t = from; t <= i; t++) {
                double lo = low[t];
                double hi = high[t];
                if (!Double.isNaN(lo)) llv = Math.min(llv, lo);
                if (!Double.isNaN(hi)) hhv = Math.max(hhv, hi);
            }
            double rsv;
            if (!Double.isFinite(llv) || !Double.isFinite(hhv) || hhv - llv <= 0.0 || Double.isNaN(close[i])) {
                rsv = 50.0;
            } else {
                rsv = (close[i] - llv) / (hhv - llv) * 100.0;
            }

            double kAlpha = 1.0 / kPeriod;
            double dAlpha = 1.0 / dPeriod;
            double curK = (1.0 - kAlpha) * prevK + kAlpha * rsv;
            double curD = (1.0 - dAlpha) * prevD + dAlpha * curK;
            double curJ = 3.0 * curK - 2.0 * curD;

            k[i] = curK;
            d[i] = curD;
            j[i] = curJ;
            prevK = curK;
            prevD = curD;
        }

        return new double[][]{k, d, j};
    }

    private static List<BarDto> aggregateIfNeeded(List<BarDto> daily, String interval) {
        if (interval == null || interval.equalsIgnoreCase("1d")) return daily;
        String iv = interval.toLowerCase(Locale.ROOT);
        if (iv.equals("1w")) return aggregateWeekly(daily);
        if (iv.equals("1m")) return aggregateMonthly(daily);
        if (iv.equals("1q")) return aggregateQuarterly(daily);
        if (iv.equals("1y")) return aggregateYearly(daily);
        throw new IllegalArgumentException("interval must be one of: 1d, 1w, 1m, 1q, 1y");
    }

    private static List<BarDto> aggregateWeekly(List<BarDto> daily) {
        WeekFields wf = WeekFields.ISO;
        Map<String, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            LocalDate d = b.date();
            int wy = d.get(wf.weekBasedYear());
            int ww = d.get(wf.weekOfWeekBasedYear());
            String key = wy + "-" + ww;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateMonthly(List<BarDto> daily) {
        Map<YearMonth, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            YearMonth ym = YearMonth.from(b.date());
            groups.computeIfAbsent(ym, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateQuarterly(List<BarDto> daily) {
        Map<String, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            LocalDate d = b.date();
            int q = (d.getMonthValue() - 1) / 3 + 1;
            String key = d.getYear() + "-Q" + q;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateYearly(List<BarDto> daily) {
        Map<Integer, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            groups.computeIfAbsent(b.date().getYear(), k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateGroups(Collection<List<BarDto>> groups) {
        List<BarDto> out = new ArrayList<>();
        for (List<BarDto> g : groups) {
            if (g.isEmpty()) continue;
            g.sort(Comparator.comparing(BarDto::date));
            BarDto first = g.get(0);
            BarDto last = g.get(g.size() - 1);
            BigDecimal open = first.open();
            BigDecimal close = last.close();
            BigDecimal high = null;
            BigDecimal low = null;
            long vol = 0;
            boolean hasVol = false;
            for (BarDto b : g) {
                if (b.high() != null) {
                    high = high == null ? b.high() : high.max(b.high());
                }
                if (b.low() != null) {
                    low = low == null ? b.low() : low.min(b.low());
                }
                if (b.volume() != null) {
                    vol += b.volume();
                    hasVol = true;
                }
            }
            out.add(new BarDto(last.date(), open, high, low, close, hasVol ? vol : null));
        }
        out.sort(Comparator.comparing(BarDto::date));
        return out;
    }
}

