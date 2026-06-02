package com.hms.application.catalog;

import com.hms.api.catalog.request.CreateServiceItemRequest;
import com.hms.api.catalog.request.UpdatePricingTierRequest;
import com.hms.api.catalog.response.ServiceCategoryResponse;
import com.hms.api.catalog.response.ServiceItemResponse;
import com.hms.domain.catalog.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.ServiceCatalogMapper;
import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceCatalogItemJpaRepository itemRepo;
    private final ServiceCategoryJpaRepository categoryRepo;
    private final ServiceCatalogMapper catalogMapper;

    // ── Service Items ──────────────────────────────────────────────────────

    @Transactional
    public ServiceItemResponse createServiceItem(CreateServiceItemRequest req) {
        if (!categoryRepo.existsById(req.categoryId())) {
            throw new ResourceNotFoundException("ServiceCategory", req.categoryId());
        }

        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setName(req.name());
        item.setCategoryId(req.categoryId());
        item.setServiceType(req.serviceType());
        item.setRequiresOrder(req.requiresOrder());
        
        // Prevent duplicate bill types in request
        long uniqueBillTypes = req.pricingTiers().stream().map(t -> t.billType()).distinct().count();
        if (uniqueBillTypes < req.pricingTiers().size()) {
            throw new BusinessRuleViolationException("Duplicate bill types are not allowed for a single service item");
        }

        req.pricingTiers().forEach(tierReq -> {
            PricingTier tier = new PricingTier();
            tier.setBillType(tierReq.billType());
            tier.setUnitRate(tierReq.unitRate());
            item.addPricingTier(tier);
        });

        return catalogMapper.toResponse(itemRepo.save(item));
    }

    @Transactional
    public ServiceItemResponse updateServiceItem(UUID itemId, CreateServiceItemRequest req) {
        ServiceCatalogItem item = findItemOrThrow(itemId);
        item.setName(req.name());
        item.setServiceType(req.serviceType());
        item.setRequiresOrder(req.requiresOrder());
        item.setCategoryId(req.categoryId());

        // Prevent duplicate bill types in request
        long uniqueBillTypes = req.pricingTiers().stream().map(t -> t.billType()).distinct().count();
        if (uniqueBillTypes < req.pricingTiers().size()) {
            throw new BusinessRuleViolationException("Duplicate bill types are not allowed for a single service item");
        }

        // Update pricing tiers: avoid clear() and handle potential existing duplicates in memory
        var incomingTiers = req.pricingTiers();
        var currentTiers = item.getPricingTiers();

        // 1. Map existing tiers by billType (handle duplicates if they somehow exist in the list)
        java.util.Map<com.hms.domain.billing.model.BillType, PricingTier> existingMap = new java.util.HashMap<>();
        for (PricingTier t : new java.util.ArrayList<>(currentTiers)) {
            if (!existingMap.containsKey(t.getBillType())) {
                existingMap.put(t.getBillType(), t);
            } else {
                // Remove redundant duplicates from the collection immediately
                item.removePricingTier(t);
            }
        }

        // 2. Process incoming tiers
        java.util.Set<com.hms.domain.billing.model.BillType> incomingBillTypes = new java.util.HashSet<>();
        incomingTiers.forEach(tierReq -> {
            incomingBillTypes.add(tierReq.billType());
            PricingTier existing = existingMap.get(tierReq.billType());
            
            if (existing != null) {
                existing.setUnitRate(tierReq.unitRate());
                existing.activate(); // Ensure it is active
            } else {
                PricingTier newTier = new PricingTier();
                newTier.setBillType(tierReq.billType());
                newTier.setUnitRate(tierReq.unitRate());
                item.addPricingTier(newTier);
            }
        });

        // 3. Remove tiers not in the incoming request
        currentTiers.removeIf(t -> !incomingBillTypes.contains(t.getBillType()));

        return catalogMapper.toResponse(itemRepo.saveAndFlush(item));
    }

    @Transactional
    public ServiceItemResponse updatePricingTier(UUID itemId, UpdatePricingTierRequest req) {
        ServiceCatalogItem item = findItemOrThrow(itemId);
        PricingTier tier = item.getPricingTiers().stream()
            .filter(t -> t.getId().equals(req.tierId()))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException(
                "PricingTier " + req.tierId() + " does not belong to item " + itemId));
        tier.setBillType(req.billType());
        tier.setUnitRate(req.unitRate());
        return catalogMapper.toResponse(itemRepo.save(item));
    }

    @Transactional
    public void deactivateServiceItem(UUID itemId) {
        ServiceCatalogItem item = findItemOrThrow(itemId);
        item.deactivate();
        itemRepo.save(item);
    }

    @Transactional
    public void activateServiceItem(UUID itemId) {
        ServiceCatalogItem item = findItemOrThrow(itemId);
        item.activate();
        itemRepo.save(item);
    }

    @Transactional(readOnly = true)
    public ServiceItemResponse getById(UUID itemId) {
        return catalogMapper.toResponse(findItemOrThrow(itemId));
    }

    @Transactional(readOnly = true)
    public Page<ServiceItemResponse> searchItems(String query, Pageable pageable) {
        return itemRepo.searchByName(query, pageable).map(catalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ServiceItemResponse> getByCategory(UUID categoryId) {
        return itemRepo.findActiveByCategoryId(categoryId).stream()
            .map(catalogMapper::toResponse).toList();
    }

    // ── Categories ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> getAllCategories() {
        return catalogMapper.toCategoryResponses(categoryRepo.findAllActive());
    }

    @Transactional
    public ServiceCategoryResponse createCategory(String name, ServiceCategoryType type) {
        ServiceCategory category = new ServiceCategory();
        category.setName(name);
        category.setCategoryType(type);
        return catalogMapper.toCategoryResponse(categoryRepo.save(category));
    }

    private ServiceCatalogItem findItemOrThrow(UUID id) {
        return itemRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ServiceCatalogItem", id));
    }
}
