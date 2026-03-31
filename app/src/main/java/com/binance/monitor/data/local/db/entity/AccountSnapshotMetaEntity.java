/*
 * 账户摘要实体，保存最近一次账户刷新后的元信息和曲线摘要。
 * 账户页冷启动恢复和运行时清理都依赖这张表。
 */
package com.binance.monitor.data.local.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "account_snapshot_meta")
public class AccountSnapshotMetaEntity {
    @PrimaryKey
    public int id;
    public boolean connected;
    public long updatedAt;
    public long fetchedAt;
    public String account = "";
    public String server = "";
    public String source = "";
    public String gateway = "";
    public String error = "";
    public String overviewMetricsJson = "[]";
    public String curveIndicatorsJson = "[]";
    public String statsMetricsJson = "[]";
    public String curvePointsJson = "[]";
}
