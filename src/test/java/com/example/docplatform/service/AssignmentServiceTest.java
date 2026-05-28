package com.example.docplatform.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock ReportAssignmentMapper assignmentMapper;
    @Mock UserMapper userMapper;
    @Mock ReportTemplateRepository templateRepository;
    @InjectMocks AssignmentService assignmentService;

    @Test
    void assignmentStatus_hasPendingAndCompleted() {
        assertThat(AssignmentStatus.values()).containsExactlyInAnyOrder(
            AssignmentStatus.PENDING, AssignmentStatus.COMPLETED);
    }

    @Test
    void create_insertsAndReturnsAssignment() {
        when(assignmentMapper.insert(any(ReportAssignment.class))).thenReturn(1);

        ReportAssignment result = assignmentService.create(
            1L, 10L, new AssignmentRequest(20L, "tmpl-1", "Use Q1 figures"));

        assertThat(result.getTenantId()).isEqualTo(1L);
        assertThat(result.getCreatedBy()).isEqualTo(10L);
        assertThat(result.getAssigneeId()).isEqualTo(20L);
        assertThat(result.getTemplateId()).isEqualTo("tmpl-1");
        assertThat(result.getNotes()).isEqualTo("Use Q1 figures");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PENDING);
        verify(assignmentMapper).insert(any(ReportAssignment.class));
    }

    @Test
    void listMine_returnsPendingAssignmentsForUser() {
        ReportAssignment a = new ReportAssignment();
        a.setId(1L);
        a.setAssigneeId(20L);
        a.setTenantId(1L);
        a.setTemplateId("tmpl-1");
        a.setNotes("Use Q1");
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedAt(LocalDateTime.now());

        when(assignmentMapper.selectList(any())).thenReturn(List.of(a));

        ReportTemplate tmpl = new ReportTemplate();
        tmpl.setId("tmpl-1");
        tmpl.setName("Sales Report");
        when(templateRepository.findAllById(any())).thenReturn(List.of(tmpl));

        List<MyAssignmentResponse> result = assignmentService.listMine(20L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).templateName()).isEqualTo("Sales Report");
        assertThat(result.get(0).notes()).isEqualTo("Use Q1");
    }

    @Test
    void complete_updatesStatusAndDocumentId() {
        ReportAssignment a = new ReportAssignment();
        a.setId(5L);
        a.setTenantId(1L);
        a.setStatus(AssignmentStatus.PENDING);

        when(assignmentMapper.selectById(5L)).thenReturn(a);

        assignmentService.complete(5L, 1L, "doc-abc");

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(a.getDocumentId()).isEqualTo("doc-abc");
        assertThat(a.getCompletedAt()).isNotNull();
        verify(assignmentMapper).updateById(a);
    }

    @Test
    void complete_throwsWhenAssignmentNotInTenant() {
        ReportAssignment a = new ReportAssignment();
        a.setId(5L);
        a.setTenantId(99L); // different tenant

        when(assignmentMapper.selectById(5L)).thenReturn(a);

        assertThatThrownBy(() -> assignmentService.complete(5L, 1L, "doc-abc"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
