package com.hms.application.dataapi;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataApiServiceTest {

    @Mock private SettingsRegistryImpl settings;
    @Mock private DataSource dataSource;
    
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData metaData;

    @InjectMocks
    private DataApiService dataApiService;

    @BeforeEach
    void setUp() throws Exception {
    }

    @Test
    void execute_ShouldReturnResults_WhenValidSelectQuery() throws Exception {
        String queryKey = "GET_PATIENTS";
        String rawSql = "SELECT * FROM patients WHERE branch_id = :branchId";
        Map<String, String> params = Map.of("branchId", "branch-123");

        when(settings.get("DATA_API", queryKey)).thenReturn(Optional.of(rawSql));
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(resultSet.getObject(1)).thenReturn(1L);
        when(metaData.getColumnLabel(2)).thenReturn("name");
        when(resultSet.getObject(2)).thenReturn("John");

        List<Map<String, Object>> result = dataApiService.execute(queryKey, params);

        assertEquals(1, result.size());
        assertEquals("John", result.get(0).get("name"));
        verify(preparedStatement).setString(1, "branch-123");
    }

    @Test
    void execute_ShouldThrowException_WhenQueryNotFound() {
        String queryKey = "UNKNOWN";
        when(settings.get("DATA_API", queryKey)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dataApiService.execute(queryKey, Map.of()));
    }

    @Test
    void execute_ShouldThrowException_WhenNotSelect() {
        String queryKey = "UPDATE_PATIENTS";
        when(settings.get("DATA_API", queryKey)).thenReturn(Optional.of("UPDATE patients SET name = 'Test'"));

        assertThrows(BusinessRuleViolationException.class, () -> dataApiService.execute(queryKey, Map.of()));
    }

    @Test
    void execute_ShouldThrowException_WhenMissingParam() {
        String queryKey = "GET_PATIENTS";
        String rawSql = "SELECT * FROM patients WHERE branch_id = :branchId";
        
        when(settings.get("DATA_API", queryKey)).thenReturn(Optional.of(rawSql));
        
        // Setup mock connection to avoid NPE if code proceeds incorrectly
        try {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        } catch(Exception ignored) {}

        assertThrows(BusinessRuleViolationException.class, () -> dataApiService.execute(queryKey, Map.of()));
    }

    @Test
    void registerQuery_ShouldSave_WhenValidSelect() {
        String key = "GET_USERS";
        String sql = "SELECT * FROM users";
        String desc = "Gets all users";

        dataApiService.registerQuery(key, sql, desc);

        verify(settings).save("DATA_API", key, sql);
        verify(settings).save("DATA_API_DESC", key, desc);
    }

    @Test
    void registerQuery_ShouldThrowException_WhenNotSelect() {
        assertThrows(BusinessRuleViolationException.class, () -> dataApiService.registerQuery("DEL", "DELETE FROM users", "Desc"));
    }
}
