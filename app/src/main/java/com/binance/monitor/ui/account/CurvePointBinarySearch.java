package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.CurvePoint;

import java.util.List;

final class CurvePointBinarySearch {

    private CurvePointBinarySearch() {
    }

    static int nearestCurvePointIndex(@Nullable List<CurvePoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    static int nearestDrawdownPointIndex(@Nullable List<CurveAnalyticsHelper.DrawdownPoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    static int nearestDailyReturnPointIndex(@Nullable List<CurveAnalyticsHelper.DailyReturnPoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    private static int nearestIndex(int size, long timestamp, @NonNull TimestampReader reader) {
        if (size <= 0) {
            return -1;
        }
        int left = 0;
        int right = size - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            long midTs = reader.timestampAt(mid);
            if (midTs < timestamp) {
                left = mid + 1;
            } else if (midTs > timestamp) {
                right = mid - 1;
            } else {
                return mid;
            }
        }
        if (left >= size) {
            return size - 1;
        }
        if (right < 0) {
            return 0;
        }
        long leftDiff = Math.abs(reader.timestampAt(left) - timestamp);
        long rightDiff = Math.abs(reader.timestampAt(right) - timestamp);
        return leftDiff < rightDiff ? left : right;
    }

    private interface TimestampReader {
        long timestampAt(int index);
    }
}
