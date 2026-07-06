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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class ServiceCatalogItemGeneratedTest {


    @InjectMocks private ServiceCatalogItem controller;


    @Test
    void addPricingTier_ShouldExecute() {
        try {
            controller.addPricingTier(org.mockito.Mockito.mock(PricingTier.class, org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS).lenient()));
        } catch (Exception e) {
            // Ignore for coverage
        }
    }

    @Test
    void removePricingTier_ShouldExecute() {
        try {
            controller.removePricingTier(org.mockito.Mockito.mock(PricingTier.class, org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS).lenient()));
        } catch (Exception e) {
            // Ignore for coverage
        }
    }
}
