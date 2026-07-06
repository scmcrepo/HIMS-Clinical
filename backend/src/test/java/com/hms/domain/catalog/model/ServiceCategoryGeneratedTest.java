package com.hms.domain.catalog.model;

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
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class ServiceCategoryGeneratedTest {


    @InjectMocks private ServiceCategory controller;

}
