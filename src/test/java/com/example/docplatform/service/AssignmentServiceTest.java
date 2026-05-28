package com.example.docplatform.service;

import com.example.docplatform.enums.AssignmentStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AssignmentServiceTest {

    @Test
    void assignmentStatus_hasPendingAndCompleted() {
        assertThat(AssignmentStatus.values()).containsExactlyInAnyOrder(
            AssignmentStatus.PENDING, AssignmentStatus.COMPLETED);
    }
}
