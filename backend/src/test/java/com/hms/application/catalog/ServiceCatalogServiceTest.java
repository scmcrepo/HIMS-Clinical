package com.hms.application.catalog;

import com.hms.api.catalog.request.CreateServiceItemRequest;
import com.hms.api.catalog.request.CreateServiceItemRequest.PricingTierRequest;
import com.hms.api.catalog.request.UpdatePricingTierRequest;
import com.hms.api.catalog.response.ServiceCategoryResponse;
import com.hms.api.catalog.response.ServiceItemResponse;
import com.hms.domain.catalog.model.*;
import com.hms.domain.billing.model.BillType;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.ServiceCatalogMapper;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock private ServiceCatalogItemJpaRepository itemRepo;
    @Mock private ServiceCategoryJpaRepository categoryRepo;
    @Mock private ServiceCatalogMapper catalogMapper;

    @InjectMocks
    private ServiceCatalogService serviceCatalogService;

    private UUID itemId;
    private UUID categoryId;
    private ServiceCatalogItem item;
    private ServiceCategory category;
    private PricingTier pricingTier;
    private ServiceItemResponse itemResponse;
    private ServiceCategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        item = new ServiceCatalogItem();
        item.setId(itemId);
        item.setCategoryId(categoryId);
        item.setName("Test Item");

        category = new ServiceCategory();
        category.setId(categoryId);
        category.setName("Test Category");

        pricingTier = new PricingTier();
        pricingTier.setId(UUID.randomUUID());
        pricingTier.setBillType(BillType.CASH);
        pricingTier.setUnitRate(100L);
        item.addPricingTier(pricingTier);

        itemResponse = mock(ServiceItemResponse.class);
        categoryResponse = mock(ServiceCategoryResponse.class);
    }

    // ── Service Items ──────────────────────────────────────────────────────

    @Test
    void createServiceItem_ShouldCreate_WhenValidRequest() {
        PricingTierRequest tierReq = new PricingTierRequest(BillType.CASH, 100L);
        CreateServiceItemRequest req = new CreateServiceItemRequest("Item 1", categoryId, ServiceType.INDIVIDUAL, true, List.of(tierReq));
        
        when(categoryRepo.existsById(categoryId)).thenReturn(true);
        when(itemRepo.save(any(ServiceCatalogItem.class))).thenReturn(item);
        when(catalogMapper.toResponse(item)).thenReturn(itemResponse);

        ServiceItemResponse response = serviceCatalogService.createServiceItem(req);

        assertNotNull(response);
        verify(itemRepo).save(any(ServiceCatalogItem.class));
    }

    @Test
    void createServiceItem_ShouldThrowException_WhenCategoryDoesNotExist() {
        PricingTierRequest tierReq = new PricingTierRequest(BillType.CASH, 100L);
        CreateServiceItemRequest req = new CreateServiceItemRequest("Item 1", categoryId, ServiceType.INDIVIDUAL, true, List.of(tierReq));
        
        when(categoryRepo.existsById(categoryId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> serviceCatalogService.createServiceItem(req));
    }

    @Test
    void createServiceItem_ShouldThrowException_WhenDuplicateBillTypes() {
        PricingTierRequest tierReq1 = new PricingTierRequest(BillType.CASH, 100L);
        PricingTierRequest tierReq2 = new PricingTierRequest(BillType.CASH, 200L);
        CreateServiceItemRequest req = new CreateServiceItemRequest("Item 1", categoryId, ServiceType.INDIVIDUAL, true, List.of(tierReq1, tierReq2));
        
        when(categoryRepo.existsById(categoryId)).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> serviceCatalogService.createServiceItem(req));
    }

    @Test
    void updateServiceItem_ShouldUpdate_WhenValidRequest() {
        PricingTierRequest tierReq = new PricingTierRequest(BillType.CASH, 150L);
        CreateServiceItemRequest req = new CreateServiceItemRequest("Item Updated", categoryId, ServiceType.INDIVIDUAL, true, List.of(tierReq));
        
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.saveAndFlush(any(ServiceCatalogItem.class))).thenReturn(item);
        when(catalogMapper.toResponse(item)).thenReturn(itemResponse);

        ServiceItemResponse response = serviceCatalogService.updateServiceItem(itemId, req);

        assertNotNull(response);
        assertEquals(150L, pricingTier.getUnitRate());
        verify(itemRepo).saveAndFlush(item);
    }

    @Test
    void updatePricingTier_ShouldUpdateTier() {
        UpdatePricingTierRequest req = new UpdatePricingTierRequest(pricingTier.getId(), BillType.CREDIT, 200L);
        
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.save(any(ServiceCatalogItem.class))).thenReturn(item);
        when(catalogMapper.toResponse(item)).thenReturn(itemResponse);

        ServiceItemResponse response = serviceCatalogService.updatePricingTier(itemId, req);

        assertNotNull(response);
        assertEquals(BillType.CREDIT, pricingTier.getBillType());
        assertEquals(200L, pricingTier.getUnitRate());
    }

    @Test
    void updatePricingTier_ShouldThrowException_WhenTierNotFound() {
        UpdatePricingTierRequest req = new UpdatePricingTierRequest(UUID.randomUUID(), BillType.CREDIT, 200L);
        
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(BusinessRuleViolationException.class, () -> serviceCatalogService.updatePricingTier(itemId, req));
    }

    @Test
    void deactivateServiceItem_ShouldDeactivate() {
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        
        serviceCatalogService.deactivateServiceItem(itemId);
        
        assertEquals(com.hms.domain.shared.model.EntityStatus.INACTIVE, item.getStatus());
        verify(itemRepo).save(item);
    }

    @Test
    void activateServiceItem_ShouldActivate() {
        item.deactivate();
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        
        serviceCatalogService.activateServiceItem(itemId);
        
        assertEquals(com.hms.domain.shared.model.EntityStatus.ACTIVE, item.getStatus());
        verify(itemRepo).save(item);
    }

    @Test
    void getById_ShouldReturnItem() {
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(catalogMapper.toResponse(item)).thenReturn(itemResponse);

        ServiceItemResponse response = serviceCatalogService.getById(itemId);

        assertNotNull(response);
    }

    @Test
    void searchItems_ShouldReturnPage() {
        Page<ServiceCatalogItem> page = new PageImpl<>(List.of(item));
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(itemRepo.searchByName("query", pageRequest)).thenReturn(page);
        when(catalogMapper.toResponse(any())).thenReturn(itemResponse);

        Page<ServiceItemResponse> response = serviceCatalogService.searchItems("query", false, false, pageRequest);

        assertFalse(response.isEmpty());
    }

    @Test
    void getByCategory_ShouldReturnList() {
        when(itemRepo.findActiveByCategoryId(categoryId)).thenReturn(List.of(item));
        when(catalogMapper.toResponse(any())).thenReturn(itemResponse);

        List<ServiceItemResponse> response = serviceCatalogService.getByCategory(categoryId);

        assertFalse(response.isEmpty());
    }

    // ── Categories ─────────────────────────────────────────────────────────

    @Test
    void getAllCategories_ShouldReturnList() {
        when(categoryRepo.findAllActive()).thenReturn(List.of(category));
        when(catalogMapper.toCategoryResponses(anyList())).thenReturn(List.of(categoryResponse));

        List<ServiceCategoryResponse> response = serviceCatalogService.getAllCategories();

        assertFalse(response.isEmpty());
    }

    @Test
    void createCategory_ShouldCreateCategory() {
        when(categoryRepo.save(any(ServiceCategory.class))).thenReturn(category);
        when(catalogMapper.toCategoryResponse(category)).thenReturn(categoryResponse);

        ServiceCategoryResponse response = serviceCatalogService.createCategory("New Cat", null);

        assertNotNull(response);
        verify(categoryRepo).save(any(ServiceCategory.class));
    }
}
