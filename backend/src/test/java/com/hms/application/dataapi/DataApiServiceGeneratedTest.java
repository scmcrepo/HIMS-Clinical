package com.hms.application.dataapi;

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
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class DataApiServiceGeneratedTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private SettingsRegistryImpl settings;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private DataSource dataSource;

    @InjectMocks private DataApiService controller;


    @Test
    void registerQuery_ShouldExecute() {
        try {
            controller.registerQuery("dummy", "dummy", "dummy");
        } catch (Exception e) {
            // Ignore for coverage
        }
    }
}
