package com.trading.platform.dto;

import com.trading.platform.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String operator,
        String action,
        String entityType,
        Long entityId,
        String beforeData,
        String afterData,
        LocalDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getOperator(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getBeforeData(),
                auditLog.getAfterData(),
                auditLog.getCreatedAt()
        );
    }
}