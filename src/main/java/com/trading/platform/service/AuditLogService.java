package com.trading.platform.service;

import com.trading.platform.entity.AuditLog;
import com.trading.platform.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           JsonMapper jsonMapper) {
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
    }

    public void log(String operator,
                    String action,
                    String entityType,
                    Long entityId,
                    Object beforeData,
                    Object afterData) {

        AuditLog auditLog = new AuditLog();

        auditLog.setOperator(operator);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setBeforeData(toJson(beforeData));
        auditLog.setAfterData(toJson(afterData));

        auditLogRepository.save(auditLog);
    }

    private String toJson(Object data) {

        if (data == null) {
            return null;
        }

        try {
            return jsonMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert audit data to JSON", e);
        }
    }
}