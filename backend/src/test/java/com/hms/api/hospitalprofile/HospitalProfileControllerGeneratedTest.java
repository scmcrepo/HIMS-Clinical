package com.hms.api.hospitalprofile;

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
import com.hms.api.shared.ApiResponse;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.AttachmentType;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class HospitalProfileControllerGeneratedTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private SettingsRegistryImpl settingsRegistry;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private AttachmentService attachmentService;

    @InjectMocks private HospitalProfileController controller;

}
