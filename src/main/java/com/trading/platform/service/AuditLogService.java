package com.trading.platform.service;

import com.trading.platform.entity.AuditLog;
import com.trading.platform.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

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

    public List<AuditLog> search(String operator,
                                 String action,
                                 String entityType,
                                 Long entityId) {

        Specification<AuditLog> specification =
                (root, query, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (operator != null && !operator.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        criteriaBuilder.lower(
                                                root.get("operator")
                                        ),
                                        operator.trim().toLowerCase()
                                )
                        );
                    }

                    if (action != null && !action.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        criteriaBuilder.upper(
                                                root.get("action")
                                        ),
                                        action.trim().toUpperCase()
                                )
                        );
                    }

                    if (entityType != null && !entityType.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        criteriaBuilder.upper(
                                                root.get("entityType")
                                        ),
                                        entityType.trim().toUpperCase()
                                )
                        );
                    }

                    if (entityId != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("entityId"),
                                        entityId
                                )
                        );
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        return auditLogRepository.findAll(
                specification,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private String toJson(Object data) {

        if (data == null) {
            return null;
        }

        try {
            return jsonMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert audit data to JSON", e
            );
        }
    }
}