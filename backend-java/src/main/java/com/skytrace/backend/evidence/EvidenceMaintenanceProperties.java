package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.evidence.maintenance")
public class EvidenceMaintenanceProperties {

    // 回填默认关闭，由管理员先观察历史数据量后再显式开启。
    private boolean hashBackfillEnabled = false;
    // 每轮只处理少量对象，避免回填流量挤占在线预览和上传。
    private int hashBackfillBatchSize = 25;
    // 失败对象经过退避时间后才会重新进入候选集合。
    private int hashBackfillRetryHours = 24;
    // 固定延迟从上一轮完成时开始计算，不会并发堆积批次。
    private long hashBackfillFixedDelayMs = 300_000L;
    // 应用启动后留出时间完成数据库、MinIO 和 Temporal 初始化。
    private long hashBackfillInitialDelayMs = 60_000L;

    // 自动物理清理具有不可逆副作用，因此默认关闭。
    private boolean cleanupEnabled = false;
    // 即使启用调度，默认也只演练并输出候选，不删除对象。
    private boolean cleanupDryRun = true;
    // 原件在归档和软删除都满 90 天后才允许进入清理候选。
    private int cleanupRetentionDays = 90;
    // 小批量清理可以限制单次归档校验、MinIO 删除和审计写入压力。
    private int cleanupBatchSize = 20;
    // 每天凌晨三点半检查一次，具体时区由应用统一配置决定。
    private String cleanupCron = "0 30 3 * * *";
    // 进程中断后，超过该时间的 PURGING 记录可以重新认领。
    private int cleanupStaleClaimHours = 6;

    public boolean isHashBackfillEnabled() {
        return hashBackfillEnabled;
    }

    public void setHashBackfillEnabled(boolean hashBackfillEnabled) {
        this.hashBackfillEnabled = hashBackfillEnabled;
    }

    public int getHashBackfillBatchSize() {
        return hashBackfillBatchSize;
    }

    public void setHashBackfillBatchSize(int hashBackfillBatchSize) {
        this.hashBackfillBatchSize = hashBackfillBatchSize;
    }

    public int getHashBackfillRetryHours() {
        return hashBackfillRetryHours;
    }

    public void setHashBackfillRetryHours(int hashBackfillRetryHours) {
        this.hashBackfillRetryHours = hashBackfillRetryHours;
    }

    public long getHashBackfillFixedDelayMs() {
        return hashBackfillFixedDelayMs;
    }

    public void setHashBackfillFixedDelayMs(long hashBackfillFixedDelayMs) {
        this.hashBackfillFixedDelayMs = hashBackfillFixedDelayMs;
    }

    public long getHashBackfillInitialDelayMs() {
        return hashBackfillInitialDelayMs;
    }

    public void setHashBackfillInitialDelayMs(long hashBackfillInitialDelayMs) {
        this.hashBackfillInitialDelayMs = hashBackfillInitialDelayMs;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public boolean isCleanupDryRun() {
        return cleanupDryRun;
    }

    public void setCleanupDryRun(boolean cleanupDryRun) {
        this.cleanupDryRun = cleanupDryRun;
    }

    public int getCleanupRetentionDays() {
        return cleanupRetentionDays;
    }

    public void setCleanupRetentionDays(int cleanupRetentionDays) {
        this.cleanupRetentionDays = cleanupRetentionDays;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public int getCleanupStaleClaimHours() {
        return cleanupStaleClaimHours;
    }

    public void setCleanupStaleClaimHours(int cleanupStaleClaimHours) {
        this.cleanupStaleClaimHours = cleanupStaleClaimHours;
    }
}
