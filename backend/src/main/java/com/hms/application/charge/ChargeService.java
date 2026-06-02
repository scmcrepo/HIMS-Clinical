package com.hms.application.charge;

import com.hms.domain.charge.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.charge.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public Charge createCharge(Charge req) {
        req.setStartDate(LocalDate.now());
        // If id provided → upsert (legacy behaviour: POST routes to update if id exists)
        if (req.getId() != null && chargeRepo.existsById(req.getId())) {
            return updateCharge(req.getId(), req);
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
        if (req.getEndDate() != null) {
            existing.setEndDate(req.getEndDate());
            existing.deactivate();
        } else {
            existing.setEndDate(null);
            existing.activate();
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
        return saved;
    }

    private void syncToServiceCatalog(Charge charge) {
        if (charge.getCategoryId() == null) return;

        // Try to find Category entity from Categories table
        com.hms.domain.shared.model.Category uiCategory = categoryRepo.findById(charge.getCategoryId()).orElse(null);
        if (uiCategory == null) return;
        
        String categoryName = uiCategory.getName();

        // Try to find corresponding ServiceCategory
        com.hms.domain.catalog.model.ServiceCategory serviceCat = serviceCategoryRepo.findByName(categoryName)
            .orElseGet(() -> {
                com.hms.domain.catalog.model.ServiceCategory newCat = new com.hms.domain.catalog.model.ServiceCategory();
                newCat.setName(categoryName);
                
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

        // Map ChargeType to ServiceType
        com.hms.domain.catalog.model.ServiceType mappedType = com.hms.domain.catalog.model.ServiceType.INDIVIDUAL;
        if (charge.getChargeType() == ChargeType.PACKAGE) {
            mappedType = com.hms.domain.catalog.model.ServiceType.PACKAGE;
        } else if (charge.getChargeType() == ChargeType.IP) {
            mappedType = com.hms.domain.catalog.model.ServiceType.INPATIENT;
        }
        sci.setServiceType(mappedType);
        sci.setRequiresOrder(false); // default

        // Update existing or add new pricing tiers
        List<com.hms.domain.catalog.model.PricingTier> existingTiers = new ArrayList<>(sci.getPricingTiers());
        charge.getTariffs().forEach(t -> {
            try {
                com.hms.domain.billing.model.BillType bt = com.hms.domain.billing.model.BillType.valueOf(t.getBillType().toUpperCase());
                java.util.Optional<com.hms.domain.catalog.model.PricingTier> existing = existingTiers.stream()
                        .filter(pt -> pt.getBillType() == bt).findFirst();
                if (existing.isPresent()) {
                    existing.get().setUnitRate(t.getRate());
                    existingTiers.remove(existing.get());
                } else {
                    com.hms.domain.catalog.model.PricingTier tier = new com.hms.domain.catalog.model.PricingTier();
                    tier.setBillType(bt);
                    tier.setUnitRate(t.getRate());
                    sci.addPricingTier(tier);
                }
            } catch (Exception ignored) {}
        });
        
        // Remove pricing tiers that are no longer present
        existingTiers.forEach(sci::removePricingTier);

        serviceCatalogItemRepo.save(sci);
    }

    @Transactional
    public void deleteCharge(UUID id) {
        Charge charge = chargeRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Charge", id));
        charge.retire(LocalDate.now());
        chargeRepo.save(charge);
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
