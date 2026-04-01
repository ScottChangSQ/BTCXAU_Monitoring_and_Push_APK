/*
 * 异常交易图表标注构建器测试，确保异常记录会按当前 K 线时间桶聚合并映射强度。
 */
package com.binance.monitor.ui.chart;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbnormalAnnotationOverlayBuilderTest {
    private static final long BASE_TIME = 1_000_000L;

    @Test
    public void shouldGroupRecordsIntoSameCandleBucket() {
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, BASE_TIME + 300_000L),
                buildCandle(BASE_TIME + 300_000L, BASE_TIME + 600_000L)
        );
        List<AbnormalRecord> records = Arrays.asList(
                buildRecord("r1", BASE_TIME + 60_000L, BASE_TIME + 61_000L, 100d, "量能"),
                buildRecord("r2", BASE_TIME + 240_000L, BASE_TIME + 241_000L, 101d, "波动")
        );

        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> annotations =
                AbnormalAnnotationOverlayBuilder.build(records, candles);

        assertEquals(1, annotations.size());
        assertEquals(BASE_TIME, annotations.get(0).anchorTimeMs);
        assertEquals(2, annotations.get(0).count);
        assertTrue(annotations.get(0).label.contains("x2"));
    }

    @Test
    public void shouldIncreaseIntensityAndColorForDenserBuckets() {
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, BASE_TIME + 300_000L),
                buildCandle(BASE_TIME + 300_000L, BASE_TIME + 600_000L)
        );
        List<AbnormalRecord> records = Arrays.asList(
                buildRecord("r1", BASE_TIME + 60_000L, BASE_TIME + 61_000L, 100d, "单次"),
                buildRecord("r2", BASE_TIME + 320_000L, BASE_TIME + 321_000L, 101d, "双次"),
                buildRecord("r3", BASE_TIME + 340_000L, BASE_TIME + 341_000L, 101d, "双次"),
                buildRecord("r4", BASE_TIME + 360_000L, BASE_TIME + 361_000L, 101d, "双次"),
                buildRecord("r5", BASE_TIME + 380_000L, BASE_TIME + 381_000L, 101d, "双次"),
                buildRecord("r6", BASE_TIME + 400_000L, BASE_TIME + 401_000L, 101d, "双次"),
                buildRecord("r7", BASE_TIME + 420_000L, BASE_TIME + 421_000L, 101d, "双次")
        );

        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> annotations =
                AbnormalAnnotationOverlayBuilder.build(records, candles);

        assertEquals(2, annotations.size());
        assertEquals(1, annotations.get(0).count);
        assertEquals(6, annotations.get(1).count);
        assertTrue(annotations.get(1).intensity > annotations.get(0).intensity);
        assertTrue(annotations.get(1).color != annotations.get(0).color);
    }

    @Test
    public void shouldIgnoreRecordsOutsideVisibleCandles() {
        List<CandleEntry> candles = Collections.singletonList(buildCandle(BASE_TIME, BASE_TIME + 300_000L));
        List<AbnormalRecord> records = Arrays.asList(
                buildRecord("r1", BASE_TIME + 60_000L, BASE_TIME + 61_000L, 100d, "有效"),
                buildRecord("r2", BASE_TIME + 600_000L, BASE_TIME + 601_000L, 101d, "越界")
        );

        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> annotations =
                AbnormalAnnotationOverlayBuilder.build(records, candles);

        assertEquals(1, annotations.size());
        assertEquals(BASE_TIME, annotations.get(0).anchorTimeMs);
    }

    @Test
    public void shouldKeepAllVisibleBucketsWithoutTruncation() {
        List<CandleEntry> candles = new java.util.ArrayList<>();
        List<AbnormalRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            long openTime = BASE_TIME + i * 300_000L;
            candles.add(buildCandle(openTime, openTime + 300_000L));
            records.add(buildRecord("r" + i, openTime + 60_000L, openTime + 61_000L, 100d + i, "完整"));
        }

        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> annotations =
                AbnormalAnnotationOverlayBuilder.build(records, candles);

        assertEquals(200, annotations.size());
        assertEquals(BASE_TIME, annotations.get(0).anchorTimeMs);
        assertEquals(BASE_TIME + 199L * 300_000L, annotations.get(199).anchorTimeMs);
    }

    @Test
    public void shouldKeepLatestRecordWhenLastCandleCloseTimeIsLagging() {
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, BASE_TIME + 300_000L),
                buildCandle(BASE_TIME + 300_000L, BASE_TIME + 330_000L)
        );
        List<AbnormalRecord> records = Collections.singletonList(
                new AbnormalRecord(
                        "latest",
                        "BTCUSDT",
                        BASE_TIME + 450_000L,
                        BASE_TIME + 450_000L,
                        100d,
                        101d,
                        1d,
                        1d,
                        1d,
                        1d,
                        "最新"
                )
        );

        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> annotations =
                AbnormalAnnotationOverlayBuilder.build(records, candles);

        assertEquals(1, annotations.size());
        assertEquals(BASE_TIME + 300_000L, annotations.get(0).anchorTimeMs);
    }

    // 构造测试用 K 线，模拟图表当前窗口中的时间桶。
    private CandleEntry buildCandle(long openTime, long closeTime) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, 100d, 100d, 100d, 100d, 1d, 1d);
    }

    // 构造测试用异常记录，模拟服务器或本地识别出的异常事件。
    private AbnormalRecord buildRecord(String id, long timestamp, long closeTime, double closePrice, String summary) {
        return new AbnormalRecord(id, "BTCUSDT", timestamp, closeTime, closePrice - 1d, closePrice, 1d, 1d, 1d, 1d, summary);
    }
}
