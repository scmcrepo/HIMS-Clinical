package com.hms.application.smtp;

import com.hms.domain.smtp.model.SmtpConfig;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.smtp.SmtpConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpConfigServiceTest {

    @Mock private SmtpConfigRepository repo;

    @InjectMocks
    private SmtpConfigService smtpService;

    private UUID configId;
    private SmtpConfig config;

    @BeforeEach
    void setUp() {
        configId = UUID.randomUUID();
        config = new SmtpConfig();
        config.setId(configId);
        config.setSmtpHost("smtp.example.com");
        config.setSmtpPort(587);
        config.setUsername("test@example.com");
    }

    @Test
    void create_ShouldSaveConfig() {
        when(repo.save(any(SmtpConfig.class))).thenReturn(config);
        
        SmtpConfig result = smtpService.create(config);
        
        assertNotNull(result);
        verify(repo).save(config);
    }

    @Test
    void update_ShouldUpdateFieldsAndSave() {
        SmtpConfig updateReq = new SmtpConfig();
        updateReq.setSmtpHost("smtp.new.com");
        updateReq.setSmtpPort(465);
        updateReq.setUsername("new@example.com");
        updateReq.setPassword("newPass");

        when(repo.findById(configId)).thenReturn(Optional.of(config));
        when(repo.save(any(SmtpConfig.class))).thenReturn(config);

        SmtpConfig result = smtpService.update(configId, updateReq);

        assertNotNull(result);
        assertEquals("smtp.new.com", config.getSmtpHost());
        assertEquals("newPass", config.getPassword());
        verify(repo).save(config);
    }

    @Test
    void delete_ShouldSoftDelete() {
        when(repo.findById(configId)).thenReturn(Optional.of(config));
        when(repo.save(any(SmtpConfig.class))).thenReturn(config);

        smtpService.delete(configId);

        assertTrue(config.isDeleted());
        verify(repo).save(config);
    }

    @Test
    void findById_ShouldThrow_WhenNotFound() {
        when(repo.findById(configId)).thenReturn(Optional.empty());
        
        assertThrows(ResourceNotFoundException.class, () -> smtpService.findById(configId));
    }
}
