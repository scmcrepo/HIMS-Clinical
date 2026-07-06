package com.hms.api.stock;

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
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.inventory.InventoryManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class StockControllerGeneratedTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private InventoryManagementService inventoryService;

    @InjectMocks private StockController controller;

}
