package com.hms.application.charge;

import com.hms.domain.charge.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.charge.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeJpaRepository chargeRepo;
    private final TariffJpaRepository tariffRepo;
    private final com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository serviceCatalogItemRepo;
    private final com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository serviceCategoryRepo;
    private final com.hms.infrastructure.persistence.category.CategoryJpaRepository categoryRepo;
    private final com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository diagTemplateRepo;

    @Transactional
    public Charge createCharge(Charge req) {
        req.setStartDate(LocalDate.now());
        // If id provided → upsert (legacy behaviour: POST routes to update if id exists)
        if (req.getId() != null && chargeRepo.existsById(req.getId())) {
            return updateCharge(req.getId(), req);
        }
        
        UUID tenantId = req.getTenantId() != null ? req.getTenantId() : TenantContext.require();
        UUID branchId = req.getBranchId() != null ? req.getBranchId() : BranchContext.get();
        List<Charge> existingCharges = chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(tenantId, branchId, req.getName().trim());
        if (!existingCharges.isEmpty()) {
            throw new BusinessRuleViolationException("Charge with name '" + req.getName().trim() + "' already exists in this branch.");
        }
        if (req.getTariffs() != null) {
            List<Tariff> orig = new ArrayList<>(req.getTariffs());
            req.getTariffs().clear();
            orig.forEach(req::addTariff);
        }
        if (req.getPackageCharges() != null) {
            Set<Packages> orig = new HashSet<>(req.getPackageCharges());
            req.getPackageCharges().clear();
            orig.forEach(req::addPackageCharge);
        }
        Charge saved = chargeRepo.save(req);
        syncToServiceCatalog(saved);
        return saved;
    }

    /**
     * C2.7 Versioning: if bills use current tariffs and rate changed
     * → create new charge, retire old one.
     * If only name/category changed → update in place.
     */
    @Transactional
    public Charge updateCharge(UUID id, Charge req) {
        Charge existing = chargeRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Charge", id));

        if (!existing.getName().equalsIgnoreCase(req.getName().trim())) {
            UUID tenantId = existing.getTenantId() != null ? existing.getTenantId() : TenantContext.require();
            UUID branchId = existing.getBranchId() != null ? existing.getBranchId() : BranchContext.get();
            List<Charge> collision = chargeRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(tenantId, branchId, req.getName().trim());
            if (collision.stream().anyMatch(c -> !c.getId().equals(id))) {
                throw new BusinessRuleViolationException("Charge with name '" + req.getName().trim() + "' already exists in this branch.");
            }
        }

        boolean ratesChanged = existing.getTariffs().stream().anyMatch(t -> {
            return req.getTariffs().stream()
                .filter(rt -> rt.getBillType().equals(t.getBillType())
                           && Objects.equals(rt.getPayorId(), t.getPayorId()))
                .anyMatch(rt -> rt.getRate() != t.getRate());
        });

        boolean billsUseCharge = tariffRepo.countBillUsage(id) > 0;

        if (billsUseCharge && ratesChanged) {
            // Version: retire old, create new
            existing.retire(LocalDate.now());
            chargeRepo.save(existing);
            syncToServiceCatalog(existing);  // deactivate old ServiceCatalogItem
            Charge newCharge = new Charge();
            newCharge.setName(req.getName());
            newCharge.setCategoryId(req.getCategoryId());
            newCharge.setChargeType(req.getChargeType());
            newCharge.setQuantitative(req.getQuantitative());
            newCharge.setStartDate(LocalDate.now());
            req.getTariffs().forEach(t -> {
                Tariff nt = new Tariff();
                nt.setBillType(t.getBillType()); nt.setRate(t.getRate()); nt.setPayorId(t.getPayorId());
                newCharge.addTariff(nt);
            });
            if (req.getPackageCharges() != null) {
                req.getPackageCharges().forEach(pc -> {
                    Packages npc = new Packages();
                    npc.setSubCharge(pc.getSubCharge());
                    npc.setCategoryId(pc.getCategoryId());
                    npc.setQuantity(pc.getQuantity());
                    npc.setAmount(pc.getAmount());
                    npc.setMode(pc.isMode());
                    newCharge.addPackageCharge(npc);
                });
            }
            Charge saved = chargeRepo.save(newCharge);
            syncToServiceCatalog(saved);
            return saved;
        }

        // Safe update in place
        existing.setName(req.getName());
        existing.setCategoryId(req.getCategoryId());
        existing.setChargeType(req.getChargeType());
        existing.setQuantitative(req.getQuantitative());
        if (req.getStatus() == com.hms.domain.shared.model.EntityStatus.INACTIVE || req.getEndDate() != null) {
            existing.deactivate();
            if (existing.getEndDate() == null) {
                existing.setEndDate(req.getEndDate() != null ? req.getEndDate() : LocalDate.now());
            }
        } else {
            existing.activate();
            existing.setEndDate(null);
        }
        if (!billsUseCharge) {
            existing.getTariffs().clear();
            req.getTariffs().forEach(existing::addTariff);
        }
        existing.getPackageCharges().clear();
        if (req.getPackageCharges() != null) {
            req.getPackageCharges().forEach(pc -> {
                Packages npc = new Packages();
                npc.setSubCharge(pc.getSubCharge());
                npc.setCategoryId(pc.getCategoryId());
                npc.setQuantity(pc.getQuantity());
                npc.setAmount(pc.getAmount());
                npc.setMode(pc.isMode());
                existing.addPackageCharge(npc);
            });
        }
        Charge saved = chargeRepo.save(existing);
        syncToServiceCatalog(saved);
        syncDiagnosticTemplateStatus(saved);
        return saved;
    }

    private void syncToServiceCatalog(Charge charge) {
        if (charge.getCategoryId() == null) return;

        // Try to find Category entity from Categories table
        com.hms.domain.shared.model.Category uiCategory = categoryRepo.findById(charge.getCategoryId()).orElse(null);
        if (uiCategory == null) return;
        
        String categoryName = uiCategory.getName();

        UUID tenantId = charge.getTenantId() != null ? charge.getTenantId() : TenantContext.require();

        // Try to find corresponding ServiceCategory
        com.hms.domain.catalog.model.ServiceCategory serviceCat = serviceCategoryRepo.findByNameAndTenantIdIgnoreCaseNative(categoryName, tenantId)
            .orElseGet(() -> {
                com.hms.domain.catalog.model.ServiceCategory newCat = new com.hms.domain.catalog.model.ServiceCategory();
                newCat.setName(categoryName);
                newCat.setTenantId(tenantId);
                newCat.setBranchId(null); // ServiceCategory is tenant-wide
                
                // Map from UI Category to ServiceCategoryType
                if (uiCategory.getChargeCategoryType() != null) {
                    switch (uiCategory.getChargeCategoryType()) {
                        case DIAGNOSTICS: newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.DIAGNOSTICS); break;
                        case CONSULTATION: newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.CONSULTATION); break;
                        case ROOM_CHARGE: newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.ROOM_CHARGE); break;
                        case SURGERY: newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.SURGERY); break;
                        default: newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.OTHER); break;
                    }
                } else {
                    newCat.setCategoryType(com.hms.domain.catalog.model.ServiceCategoryType.OTHER);
                }
                
                return serviceCategoryRepo.save(newCat);
            });

        com.hms.domain.catalog.model.ServiceCatalogItem sci = serviceCatalogItemRepo.findById(charge.getId())
            .orElseGet(() -> {
                com.hms.domain.catalog.model.ServiceCatalogItem item = new com.hms.domain.catalog.model.ServiceCatalogItem();
                item.setId(charge.getId());
                return item;
            });

        sci.setName(charge.getName());
        sci.setCategoryId(serviceCat.getId());
        sci.setTenantId(charge.getTenantId());
        sci.setBranchId(charge.getBranchId()); // ServiceCatalogItem is branch-scoped

        // Map ChargeType to ServiceType
        com.hms.domain.catalog.model.ServiceType mappedType = com.hms.domain.catalog.model.ServiceType.INDIVIDUAL;
        if (charge.getChargeType() == ChargeType.PACKAGE) {
            mappedType = com.hms.domain.catalog.model.ServiceType.PACKAGE;
        } else if (charge.getChargeType() == ChargeType.IP) {
            mappedType = com.hms.domain.catalog.model.ServiceType.INPATIENT;
        }
        sci.setServiceType(mappedType);
        sci.setRequiresOrder(false); // default

        // Group tariffs by BillType to avoid duplicate pricing tiers
        Map<com.hms.domain.billing.model.BillType, Tariff> bestTariffs = new HashMap<>();
        charge.getTariffs().forEach(t -> {
            try {
                com.hms.domain.billing.model.BillType bt = com.hms.domain.billing.model.BillType.valueOf(t.getBillType().toUpperCase());
                Tariff existingBest = bestTariffs.get(bt);
                if (existingBest == null) {
                    bestTariffs.put(bt, t);
                } else {
                    // Prefer the default tariff (where payorId is null)
                    if (existingBest.getPayorId() != null && t.getPayorId() == null) {
                        bestTariffs.put(bt, t);
                    }
                }
            } catch (Exception ignored) {}
        });

        // Update existing or add new pricing tiers
        List<com.hms.domain.catalog.model.PricingTier> existingTiers = new ArrayList<>(sci.getPricingTiers());
        bestTariffs.forEach((bt, t) -> {
            java.util.Optional<com.hms.domain.catalog.model.PricingTier> existing = existingTiers.stream()
                    .filter(pt -> pt.getBillType() == bt).findFirst();
            if (existing.isPresent()) {
                existing.get().setUnitRate(t.getRate());
                existingTiers.remove(existing.get());
            } else {
                com.hms.domain.catalog.model.PricingTier tier = new com.hms.domain.catalog.model.PricingTier();
                tier.setBillType(bt);
                tier.setUnitRate(t.getRate());
                tier.setTenantId(charge.getTenantId());
                tier.setBranchId(null); // PricingTier is tenant-wide
                sci.addPricingTier(tier);
            }
        });
        
        // Remove pricing tiers that are no longer present
        existingTiers.forEach(sci::removePricingTier);

        sci.setStatus(charge.getStatus());

        serviceCatalogItemRepo.save(sci);

        // Link any pre-existing diagnostic templates that had NULL or mismatched chargeId
        try {
            List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates =
                diagTemplateRepo.findByTenantIdAndBranchIdAndNameIgnoreCase(tenantId, charge.getBranchId(), charge.getName().trim());
            for (com.hms.domain.diagnostic.model.DiagnosticTemplate dt : templates) {
                if (dt.getChargeId() == null || !dt.getChargeId().equals(charge.getId())) {
                    dt.setChargeId(charge.getId());
                    diagTemplateRepo.save(dt);
                }
            }
        } catch (Exception e) {
            // Ignore template sync errors to avoid blocking core charge operations
        }
    }

    /**
     * When a charge status changes (ACTIVE ↔ INACTIVE), also update the status
     * of any linked DiagnosticTemplate(s) so they appear/disappear from the
     * test-catalog search used in Diagnostic Orders.
     */
    private void syncDiagnosticTemplateStatus(Charge charge) {
        List<com.hms.domain.diagnostic.model.DiagnosticTemplate> linkedTemplates =
            diagTemplateRepo.findByChargeIdAll(charge.getId());
        for (com.hms.domain.diagnostic.model.DiagnosticTemplate dt : linkedTemplates) {
            if (dt.getStatus() != charge.getStatus()) {
                dt.setStatus(charge.getStatus());
                diagTemplateRepo.save(dt);
            }
        }
    }

    @Transactional
    public void deleteCharge(UUID id) {
        Charge charge = chargeRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Charge", id));
        charge.retire(LocalDate.now());
        Charge saved = chargeRepo.save(charge);
        syncToServiceCatalog(saved);
        syncDiagnosticTemplateStatus(saved);
    }

    @Transactional(readOnly = true)
    public Charge getById(UUID id) {
        return chargeRepo.findByIdWithTariffs(id)
            .orElseThrow(() -> new ResourceNotFoundException("Charge", id));
    }

    @Transactional(readOnly = true)
    public List<Charge> search(String name) {
        if (name == null || name.isBlank()) return chargeRepo.findAllActiveWithTariffs();
        return chargeRepo.searchByName(name.trim());
    }

    @Transactional(readOnly = true)
    public List<Charge> searchAll(String name) {
        if (name == null || name.isBlank()) return chargeRepo.findAllNotDeletedOrdered();
        return chargeRepo.searchAllNotDeletedOrdered(name.trim());
    }

    @Transactional(readOnly = true)
    public List<Charge> getByCategory(UUID categoryId) {
        return chargeRepo.findByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Charge> getByIds(List<UUID> ids) {
        return chargeRepo.findAllByIdIn(ids);
    }

    @Transactional(readOnly = true)
    public String validateDelete(UUID id) {
        if (tariffRepo.countBillUsage(id) > 0) {
            return "This charge is used in active bills and cannot be deleted.";
        }
        return null;
    }
}
