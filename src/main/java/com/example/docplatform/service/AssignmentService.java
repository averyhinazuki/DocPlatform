package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.dto.assignment.AssignmentResponse;
import com.example.docplatform.dto.assignment.MyAssignmentResponse;
import com.example.docplatform.entity.ReportAssignment;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.AssignmentStatus;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.ReportAssignmentMapper;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.ReportTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ReportAssignmentMapper assignmentMapper;
    private final UserMapper userMapper;
    private final ReportTemplateRepository templateRepository;

    public ReportAssignment create(Long tenantId, Long createdBy, AssignmentRequest req) {
        ReportAssignment a = new ReportAssignment();
        a.setTenantId(tenantId);
        a.setCreatedBy(createdBy);
        a.setAssigneeId(req.assigneeId());
        a.setTemplateId(req.templateId());
        a.setNotes(req.notes());
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedAt(LocalDateTime.now());
        assignmentMapper.insert(a);
        return a;
    }

    public List<AssignmentResponse> listByTenant(Long tenantId) {
        List<ReportAssignment> list = assignmentMapper.selectList(
            new LambdaQueryWrapper<ReportAssignment>()
                .eq(ReportAssignment::getTenantId, tenantId)
                .orderByDesc(ReportAssignment::getCreatedAt));
        if (list.isEmpty()) return List.of();

        Set<Long> userIds = list.stream().map(ReportAssignment::getAssigneeId).collect(Collectors.toSet());
        Map<Long, String> usernameById = userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        Set<String> tIds = list.stream().map(ReportAssignment::getTemplateId).collect(Collectors.toSet());
        Map<String, String> templateNameById = new HashMap<>();
        templateRepository.findAllById(tIds).forEach(t -> templateNameById.put(t.getId(), t.getName()));

        return list.stream().map(a -> new AssignmentResponse(
            a.getId(),
            usernameById.getOrDefault(a.getAssigneeId(), "unknown"),
            templateNameById.getOrDefault(a.getTemplateId(), "unknown"),
            a.getNotes(),
            a.getStatus(),
            a.getCreatedAt(),
            a.getCompletedAt(),
            a.getDocumentId()
        )).toList();
    }

    public List<MyAssignmentResponse> listMine(Long assigneeId, Long tenantId) {
        List<ReportAssignment> list = assignmentMapper.selectList(
            new LambdaQueryWrapper<ReportAssignment>()
                .eq(ReportAssignment::getAssigneeId, assigneeId)
                .eq(ReportAssignment::getTenantId, tenantId)
                .eq(ReportAssignment::getStatus, AssignmentStatus.PENDING)
                .orderByDesc(ReportAssignment::getCreatedAt));
        if (list.isEmpty()) return List.of();

        Set<String> tIds = list.stream().map(ReportAssignment::getTemplateId).collect(Collectors.toSet());
        Map<String, String> templateNameById = new HashMap<>();
        templateRepository.findAllById(tIds).forEach(t -> templateNameById.put(t.getId(), t.getName()));

        return list.stream().map(a -> new MyAssignmentResponse(
            a.getId(),
            a.getTemplateId(),
            templateNameById.getOrDefault(a.getTemplateId(), "unknown"),
            a.getNotes(),
            a.getCreatedAt()
        )).toList();
    }

    public void complete(Long assignmentId, Long tenantId, String documentId) {
        ReportAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !a.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Assignment " + assignmentId + " not found");
        }
        a.setStatus(AssignmentStatus.COMPLETED);
        a.setDocumentId(documentId);
        a.setCompletedAt(LocalDateTime.now());
        assignmentMapper.updateById(a);
    }
}
