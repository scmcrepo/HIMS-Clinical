package com.hms.application.report.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import com.hms.application.report.util.ReportScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class RevenueReportDataServiceGeneratedTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private JdbcTemplate jdbcTemplate;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private ReportScope scope;

    @InjectMocks private RevenueReportDataService controller;

}
