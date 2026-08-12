package com.skytrace.backend.evidence.domain;

public enum EvidenceArchiveStatus {
    ACTIVE,
    ARCHIVED,
    // 清理执行器已独占认领该记录，其他实例不能重复处理。
    PURGING,
    // MinIO 原件和派生对象已删除，数据库元数据作为审计墓碑保留。
    PURGED
}
