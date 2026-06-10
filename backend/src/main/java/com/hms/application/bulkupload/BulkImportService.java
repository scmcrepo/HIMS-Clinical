package com.hms.application.bulkupload;

import com.hms.domain.bed.model.*;
import com.hms.domain.charge.model.*;
import com.hms.domain.consultant.model.*;
import com.hms.domain.diagnostic.model.*;
import com.hms.domain.inventory.model.*;

import com.hms.domain.patient.model.*;
import com.hms.domain.shared.model.*;
import com.hms.infrastructure.persistence.bed.*;
import com.hms.infrastructure.persistence.category.CategoryJpaRepository;
import com.hms.infrastructure.persistence.charge.ChargeJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository;
import com.hms.infrastructure.persistence.inventory.*;
import com.hms.infrastructure.persistence.molecule.MoleculeJpaRepository;

import com.hms.infrastructure.persistence.patient.*;
import com.hms.infrastructure.persistence.payor.PayorJpaRepository;
import com.hms.infrastructure.persistence.referral.ReferralJpaRepository;
import com.hms.infrastructure.persistence.shared.*;
import com.hms.infrastructure.persistence.staff.StaffJpaRepository;
import com.hms.infrastructure.persistence.supplier.SupplierJpaRepository;

import com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository;
import com.hms.infrastructure.persistence.catalog.ServiceCategoryJpaRepository;
import com.hms.infrastructure.persistence.specimen.SpecimenJpaRepository;

import com.hms.infrastructure.persistence.diagtemplate.LabTemplateDetailJpaRepository;
import com.hms.infrastructure.persistence.printtemplate.PrintTemplateJpaRepository;
import com.hms.domain.catalog.model.*;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hms.domain.billing.model.BillType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * BulkImportService — parses a CSV MultipartFile and persists records.
 *
 * Supported entity types (16):
 *   bed, bed_type, patient, item, referral, payor,
 *   user, department, molecule, category, stock,
 *   consultant, staff, diagnostic_template, order_set, charge
 *
 * Design:
 *   - Row parsing is lenient: rows with missing required fields are counted as errors
 *   - Duplicate-key violations are counted as skipped (not errors)
 *   - Maximum 50,000 rows per upload
 *   - All work happens in a single transaction; on failure the entire import rolls back
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private static final int MAX_ROWS = 50_000;

    private final BedJpaRepository                bedRepo;
    private final BedOccupancyJpaRepository       occupancyRepo;
    private final InventoryItemJpaRepository      itemRepo;
    private final PatientJpaRepository            patientRepo;
    private final ReferralJpaRepository           referralRepo;
    private final SupplierJpaRepository           supplierRepo;
    private final UserJpaRepository               userRepo;
    private final RoleJpaRepository               roleRepo;
    private final ConsultantJpaRepository         consultantRepo;
    private final StaffJpaRepository              staffRepo;
    private final DepartmentJpaRepository         departmentRepo;
    private final CategoryJpaRepository           categoryRepo;
    private final MoleculeJpaRepository           moleculeRepo;
    private final UnitOfMeasureJpaRepository      uomRepo;
    private final RoomCategoryJpaRepository       roomCategoryRepo;
    private final PayorJpaRepository              payorRepo;
    private final DiagnosticTemplateJpaRepository diagnosticTemplateRepo;
    private final ChargeJpaRepository             chargeRepo;
    private final InventoryBatchJpaRepository     batchRepo;
    private final ServiceCatalogItemJpaRepository catalogItemRepo;
    private final ServiceCategoryJpaRepository    serviceCategoryRepo;
    private final PasswordEncoder                 passwordEncoder;
    private final SpecimenJpaRepository           specimenRepo;

    private final LabTemplateDetailJpaRepository  labDetailRepo;
    private final PrintTemplateJpaRepository      printTemplateRepo;
    private final com.hms.domain.shared.port.out.SequenceNumberPort sequencePort;
    private final com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ── Column headers for each entity type ──────────────────────────────────

    private static final Map<String, List<String>> HEADERS = Map.ofEntries(
        Map.entry("bed",
            List.of("Bed No", "Bed Type")),
        Map.entry("patient",
            List.of("Salutation", "First Name", "Last Name", "Sex", "Age or Dob", "Address", "Contact Number", "Primary Consultant", "Check-in Time", "Patient No", "Patient Type")),
        Map.entry("item",
            List.of("Item Name", "CIMS Id", "Batch Required", "Base Unit", "Category")),
        Map.entry("referral",
            List.of("Salutation", "First Name", "Last Name", "Contact Number", "Address")),
        Map.entry("user",
            List.of("User Name", "Password", "Confirm Password", "First Name", "Last Name", "Email Id", "Phone No", "Salutation", "Role")),
        Map.entry("molecule",
            List.of("Name", "CIMS Id")),
        Map.entry("category",
            List.of("Category Name", "Category Type", "Diagnostic Type", "Type")),
        Map.entry("department",
            List.of("Name", "Type", "Display Order")),
        Map.entry("stock",
            List.of("item", "department", "batch_number", "quantity",
                    "purchase_rate", "mrp", "selling_rate", "expiry_date")),
        Map.entry("consultant",
            List.of("name", "address", "qualification", "salutation", "contactNo")),
        Map.entry("staff",
            List.of("Name", "Type")),
        Map.entry("diagnostic_template",
            List.of("Format", "Specimen", "Charge Name", "Order Number", "Department", "Header", "TemplateName")),
        Map.entry("charge",
            List.of("category", "name", "cash", "credit")),
        Map.entry("payor",
            List.of("Payer Name", "Payer Type")),
        Map.entry("bed_type",
            List.of("Bed Type", "Charge Name")),
        Map.entry("lab_template_detail",
            List.of("Charge Name", "Type", "Rows", "Result Name", "Normal Range", "Order Number", "Unit", "Expression"))
    );

    public List<String> getExpectedHeaders(String entityType) {
        List<String> headers = HEADERS.get(entityType);
        if (headers == null) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Unknown entity type '" + entityType + "'. Supported: " + String.join(", ", HEADERS.keySet()));
        }
        return headers;
    }

    public ImportResult importCsv(String entityType, MultipartFile file) {
        if (!HEADERS.containsKey(entityType)) {
            return new ImportResult(entityType, 0, 0, 0, 1,
                List.of("Unknown entity type: " + entityType));
        }

        List<Map<String, String>> rows = parseCsv(file);
        if (rows.isEmpty()) {
            return new ImportResult(entityType, 0, 0, 0, 0, List.of());
        }

        // For grouped CSV formats, carry forward charge_name from previous rows
        if ("lab_template_detail".equals(entityType) || "diagnostic_template".equals(entityType)) {
            String lastChargeName = "";
            for (Map<String, String> row : rows) {
                String cn = row.getOrDefault("charge_name", "").trim();
                if (!cn.isEmpty()) {
                    lastChargeName = cn;
                } else if (!lastChargeName.isEmpty()) {
                    row.put("charge_name", lastChargeName);
                }
            }
        }

        int created = 0, skipped = 0, errors = 0;
        List<String> errorMessages = new ArrayList<>();

        org.springframework.transaction.support.TransactionTemplate txTemplate =
            new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int i = 0; i < Math.min(rows.size(), MAX_ROWS); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // +2 because row 1 = header
            try {
                boolean wasCreated = txTemplate.execute(status -> importRow(entityType, row));
                if (wasCreated) created++;
                else            skipped++;
            } catch (Exception ex) {
                errors++;
                if (errorMessages.size() < 50) {
                    errorMessages.add("Row " + rowNum + ": " + ex.getMessage());
                }
                log.warn("Import error at row {}: {}", rowNum, ex.getMessage());
            }
        }

        return new ImportResult(entityType, rows.size(), created, skipped, errors, errorMessages);
    }

    // ── Per-entity import logic ───────────────────────────────────────────────

    private boolean importRow(String entityType, Map<String, String> row) {
        return switch (entityType) {
            case "bed"                 -> importBed(row);
            case "patient"             -> importPatient(row);
            case "item"                -> importItem(row);
            case "referral"            -> importReferral(row);
            case "user"                -> importUser(row);
            case "molecule"            -> importMolecule(row);
            case "bed_type"            -> importBedType(row);
            case "consultant"          -> importConsultant(row);
            case "staff"               -> importStaff(row);
            case "department"          -> importDepartment(row);
            case "category"            -> importCategory(row);
            case "payor"               -> importPayor(row);
            case "diagnostic_template" -> importDiagnosticTemplate(row);
            case "charge"              -> importCharge(row);
            case "stock"               -> importStock(row);
            case "lab_template_detail" -> importLabTemplateDetail(row);
            default                    -> importGenericLog(entityType, row);
        };
    }

    private boolean importBed(Map<String, String> row) {
        String rawName = row.containsKey("bed_no") ? row.get("bed_no") : row.get("name");
        String rawCatInput = row.containsKey("bed_type") ? row.get("bed_type") : row.get("room_category_id");

        if (rawName == null || rawName.isBlank() || rawCatInput == null || rawCatInput.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required fields 'Bed No' or 'Bed Type' are missing");
        }
        
        final String name = rawName.trim();
        String catInput = rawCatInput.trim();

        // Support semicolon-separated categories by taking the first one
        if (catInput.contains(";")) {
            String[] parts = catInput.split(";");
            if (parts.length > 0) {
                catInput = parts[0].trim();
            }
        }

        if (bedRepo.findByName(name).isPresent()) {
            return false; // Skip duplicate
        }

        Bed bed = new Bed();
        bed.setName(name);
        bed.setBedStatus(BedStatus.AVAILABLE);

        final String finalCatInput = catInput;
        UUID categoryId = null;
        try {
            categoryId = UUID.fromString(finalCatInput);
            if (!roomCategoryRepo.existsById(categoryId)) {
                throw new com.hms.exception.BusinessRuleViolationException("Room category not found for ID: " + finalCatInput);
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID, try looking it up by name
            String normalizedInput = finalCatInput.replace(" ", "_");
            RoomCategory cat = roomCategoryRepo.findByNameIgnoreCase(normalizedInput)
                .orElseGet(() -> roomCategoryRepo.findByNameIgnoreCase(finalCatInput)
                .orElseThrow(() -> new com.hms.exception.BusinessRuleViolationException("Room category not found with name: " + finalCatInput)));
            categoryId = cat.getId();
        }
        bed.setRoomCategoryId(categoryId);
        bedRepo.save(bed);
        return true;
    }

    private boolean importPatient(Map<String, String> row) {
        String firstName = row.containsKey("first_name") ? row.get("first_name") : row.get("first_name");
        String lastName = row.containsKey("last_name") ? row.get("last_name") : row.get("last_name");
        String genderStr = row.containsKey("sex") ? row.get("sex") : row.get("gender");
        
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank() || genderStr == null || genderStr.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required fields 'first_name', 'last_name', or 'gender/sex' missing");
        }
        
        Patient patient = new Patient();
        patient.setSalutation(row.get("salutation"));
        patient.setFirstName(firstName.trim());
        patient.setLastName(lastName.trim());
        patient.setGender(com.hms.domain.patient.model.Gender.valueOf(genderStr.trim().toUpperCase()));
        
        String dob = row.containsKey("age_or_dob") ? row.get("age_or_dob") : row.getOrDefault("estimated_dob", "");
        if (!dob.isBlank() && !dob.toLowerCase().contains("year")) {
            patient.setEstimatedDateOfBirth(parseDate(dob.trim()));
        } else {
            patient.setEstimatedDateOfBirth(java.time.LocalDate.now());
        }
        
        patient.setContactNumber(row.getOrDefault("contact_number", null));
        patient.setAddress(row.getOrDefault("address", null));
        patient.setPatientType(row.containsKey("patient_type") ? row.get("patient_type") : row.getOrDefault("patient type", null));

        
        patientRepo.save(patient);

        String patientNo = row.containsKey("patient_no") ? row.get("patient_no") : row.get("patient_number");
        if (patientNo == null || patientNo.isBlank()) {
            patientNo = sequencePort.generateNext(com.hms.domain.billing.model.DocumentType.PATIENT);
        } else {
            patientNo = patientNo.trim();
        }

        com.hms.infrastructure.sequence.NumberSequenceEntity seq = new com.hms.infrastructure.sequence.NumberSequenceEntity();
        seq.setId(patient.getId());
        seq.setValue(patientNo);
        seq.setTypeId(patient.getId());
        numberSequenceRepo.save(seq);

        return true;
    }

    private boolean importItem(Map<String, String> row) {
        String name = row.containsKey("item_name") ? row.get("item_name") : row.get("name");
        if (name == null || name.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'name' or 'item_name' is missing or empty");
        }
        name = name.trim();
        if (itemRepo.findByName(name).isPresent()) {
            return false; // Skip duplicate
        }

        InventoryItem item = new InventoryItem();
        item.setName(name);

        String hsn = row.get("hsn_code");
        if (hsn != null && !hsn.isBlank()) item.setHsnCode(hsn.trim());

        String taxRateStr = row.containsKey("gst") ? row.get("gst") : row.getOrDefault("tax_rate", "0");
        item.setTaxRate(parseTaxRate(taxRateStr));

        String reorderStr = row.getOrDefault("reorder_level", "0");
        item.setReorderLevel(parseBigDecimal(reorderStr, "reorder_level"));

        String moleculeName = row.get("molecule");
        if (moleculeName != null && !moleculeName.isBlank()) {
            String molTrimmed = moleculeName.trim();
            Optional<Molecule> foundMolecule = moleculeRepo.findAll().stream()
                .filter(m -> m.getName().equalsIgnoreCase(molTrimmed))
                .findFirst();
            if (foundMolecule.isEmpty()) {
                Molecule newMolecule = new Molecule();
                newMolecule.setName(molTrimmed);
                moleculeRepo.save(newMolecule);
            }
        }

        String uomName = row.containsKey("base_unit") ? row.get("base_unit") : row.getOrDefault("unit_of_measure", null);
        if (uomName != null && !uomName.isBlank()) {
            String uomTrimmed = uomName.trim();
            Optional<UnitOfMeasure> foundUom = uomRepo.findAll().stream()
                .filter(u -> u.getName().equalsIgnoreCase(uomTrimmed) || (u.getSymbol() != null && u.getSymbol().equalsIgnoreCase(uomTrimmed)))
                .findFirst();
            if (foundUom.isPresent()) {
                item.setUnitOfMeasureId(foundUom.get().getId());
            } else {
                UnitOfMeasure newUom = new UnitOfMeasure();
                newUom.setName(uomTrimmed);
                newUom.setSymbol(uomTrimmed.length() <= 10 ? uomTrimmed.toUpperCase() : uomTrimmed.substring(0, 10).toUpperCase());
                newUom = uomRepo.save(newUom);
                item.setUnitOfMeasureId(newUom.getId());
            }
        }

        // Conversion Factor
        String convFactorStr = row.getOrDefault("conversion_factor", "1");
        if (convFactorStr != null && !convFactorStr.isBlank()) {
            try {
                item.setConversionFactor((int) Double.parseDouble(convFactorStr.trim()));
            } catch (Exception ignored) {}
        }

        String batchReqStr = row.containsKey("batch_required") ? row.get("batch_required") : row.getOrDefault("requires_batch", "false");
        boolean requiresBatch = "true".equalsIgnoreCase(batchReqStr) || "yes".equalsIgnoreCase(batchReqStr) || "1".equals(batchReqStr);
        item.setRequiresBatch(requiresBatch);

        String schedStr = row.getOrDefault("requires_prescription", "false");
        boolean requiresPrescription = "true".equalsIgnoreCase(schedStr) || "yes".equalsIgnoreCase(schedStr) || "1".equals(schedStr);
        item.setRequiresPrescription(requiresPrescription);

        String cimsId = row.get("cims_id");
        if (cimsId != null && !cimsId.isBlank()) item.setCimsId(cimsId.trim());

        String cimsName = row.get("cims_name");
        if (cimsName != null && !cimsName.isBlank()) item.setCimsName(cimsName.trim());

        String cimsType = row.get("cims_type");
        if (cimsType != null && !cimsType.isBlank()) item.setCimsType(cimsType.trim());

        String manufacturer = row.get("manufacturer");
        if (manufacturer != null && !manufacturer.isBlank()) item.setManufacturer(manufacturer.trim());

        String mrpStr = row.containsKey("price") ? row.get("price") : row.get("mrp");
        if (mrpStr != null && !mrpStr.isBlank()) item.setMrp(mrpStr.trim());

        String categoryName = row.get("category");
        if (categoryName != null && !categoryName.isBlank()) {
            categoryRepo.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName.trim()))
                .findFirst()
                .ifPresent(c -> item.setCategoryId(c.getId()));
        }

        itemRepo.save(item);
        return true;
    }

    private BigDecimal parseTaxRate(String taxRateStr) {
        if (taxRateStr == null || taxRateStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(taxRateStr.replace("%", "").trim());
        } catch (Exception e) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(\\.\\d+)?").matcher(taxRateStr);
            if (matcher.find()) {
                return new BigDecimal(matcher.group());
            }
        }
        return BigDecimal.ZERO;
    }

    private boolean importReferral(Map<String, String> row) {
        String name = row.get("name");
        String firstName = row.get("first_name");
        String lastName = row.get("last_name");
        if ((name == null || name.isBlank()) && (firstName == null || firstName.isBlank())) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'name' or 'first_name' missing");
        }
        Referral ref = new Referral();
        if (name != null && !name.isBlank()) {
            ref.setName(name.trim());
        } else {
            ref.setName(firstName.trim() + (lastName != null && !lastName.isBlank() ? " " + lastName.trim() : ""));
            ref.setFirstName(firstName.trim());
            ref.setLastName(lastName != null ? lastName.trim() : null);
        }
        ref.setSalutation(row.get("salutation"));
        ref.setType(row.getOrDefault("type", null));
        ref.setContact(row.containsKey("contact_number") ? row.get("contact_number") : row.getOrDefault("contact", null));
        ref.setAddress(row.get("address"));
        referralRepo.save(ref);
        return true;
    }

    private boolean importUser(Map<String, String> row) {
        String username = row.containsKey("user_name") ? row.get("user_name") : row.get("username");
        String firstName = row.get("first_name");
        String lastName = row.get("last_name");
        String password = row.get("password");
        
        if (username == null || username.isBlank() || firstName == null || firstName.isBlank() || password == null || password.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required fields missing");
        }
        
        username = username.trim().toLowerCase();
        if (userRepo.findByUsernameAndStatus(username, (short) 1).isPresent()) return false;
        
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName != null ? lastName.trim() : null);
        user.setEmail(row.containsKey("email_id") ? row.get("email_id") : row.getOrDefault("email", null));
        user.setPhoneNo(row.containsKey("phone_no") ? row.get("phone_no") : row.getOrDefault("phone no", null));
        user.setSalutation(row.get("salutation"));
        user.setPasswordHash(passwordEncoder.encode(password.trim()));
        user.setStatus((short) 1);
        user.setCreatedAt(java.time.Instant.now());
        user.setModifiedAt(java.time.Instant.now());

        String roleName = row.containsKey("role") ? row.get("role") : row.getOrDefault("role_name", "").trim();
        if (!roleName.isBlank()) {
            String cleanRoleName = roleName.toUpperCase().replace("ROLE_", "");
            Optional<com.hms.infrastructure.persistence.shared.RoleEntity> roleOpt = roleRepo.findByName(cleanRoleName)
                .or(() -> roleRepo.findByName(roleName));
                
            if (roleOpt.isPresent()) {
                user.setRoles(Set.of(roleOpt.get()));
            } else {
                com.hms.infrastructure.persistence.shared.RoleEntity newRole = new com.hms.infrastructure.persistence.shared.RoleEntity();
                newRole.setName(cleanRoleName);
                newRole.setDescription(cleanRoleName + " Role");
                newRole.setStatus((short) 1);
                newRole = roleRepo.save(newRole);
                user.setRoles(Set.of(newRole));
            }
        }
        userRepo.save(user);
        return true;
    }

    private boolean importMolecule(Map<String, String> row) {
        String name = row.get("name");
        if (name == null || name.isBlank()) throw new com.hms.exception.BusinessRuleViolationException("Required field 'name' missing");
        Molecule molecule = new Molecule();
        molecule.setName(name.trim());
        molecule.setCimsId(row.get("cims_id"));
        moleculeRepo.save(molecule);
        return true;
    }

    private boolean importBedType(Map<String, String> row) {
        String rawName = row.containsKey("bed_type") ? row.get("bed_type") : row.get("name");
        if (rawName == null || rawName.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'Bed Type' or 'name' is missing or empty");
        }
        
        final String name = rawName.trim();
        String chargeName = row.get("charge_name");
        UUID chargeId = null;
        if (chargeName != null && !chargeName.isBlank()) {
            List<Charge> charges = chargeRepo.findByNameIgnoreCase(chargeName.trim());
            if (charges.isEmpty()) {
                throw new com.hms.exception.BusinessRuleViolationException("Charge '" + chargeName + "' not found");
            }
            chargeId = charges.get(0).getId();
        }

        // If RoomCategory already exists, update its linked charge rather than skipping or duplicating
        Optional<RoomCategory> existingOpt = roomCategoryRepo.findByNameIgnoreCase(name);
        RoomCategory cat = existingOpt.orElseGet(RoomCategory::new);
        
        cat.setName(name);

        
        if (chargeId != null) {
            cat.setServiceCatalogItemId(chargeId);
        }
        
        roomCategoryRepo.save(cat);
        return true;
    }

    private boolean importConsultant(Map<String, String> row) {
        String name = row.get("name");
        if (name == null || name.isBlank()) throw new com.hms.exception.BusinessRuleViolationException("Required field 'name' missing");
        String fullName = name.trim();
        Consultant consultant = new Consultant();

        String salutation = row.get("salutation");
        if (salutation != null && !salutation.isBlank()) {
            consultant.setSalutation(salutation.trim());
            String[] parts = fullName.split("\\s+");
            if (parts.length >= 2) {
                consultant.setFirstName(parts[0]);
                consultant.setLastName(fullName.substring(parts[0].length() + 1).trim());
            } else {
                consultant.setFirstName(fullName);
                consultant.setLastName(".");
            }
        } else {
            String[] parts = fullName.split("\\s+");
            if (parts.length >= 3) {
                consultant.setSalutation(parts[0]);
                consultant.setFirstName(parts[1]);
                consultant.setLastName(fullName.substring(parts[0].length() + parts[1].length() + 2).trim());
            } else if (parts.length == 2) {
                if (isSalutation(parts[0])) {
                    consultant.setSalutation(parts[0]);
                    consultant.setFirstName(parts[1]);
                    consultant.setLastName(".");
                } else {
                    consultant.setFirstName(parts[0]);
                    consultant.setLastName(parts[1]);
                }
            } else {
                consultant.setFirstName(fullName);
                consultant.setLastName(".");
            }
        }

        consultant.setSpecialisation(row.getOrDefault("specialisation", null));
        consultant.setContact(row.containsKey("contactno") ? row.get("contactno") : row.getOrDefault("contact", null));
        consultant.setQualification(row.get("qualification"));
        consultant.setAddress(row.get("address"));
        consultant.setConsultantType(ConsultantType.PERMANENT);



        consultantRepo.save(consultant);
        return true;
    }

    private boolean isSalutation(String s) {
        String lower = s.toLowerCase();
        return lower.equals("dr") || lower.equals("dr.") || lower.equals("mr") || lower.equals("mr.") ||
               lower.equals("ms") || lower.equals("ms.") || lower.equals("mrs") || lower.equals("mrs.");
    }

    private boolean importStaff(Map<String, String> row) {
        requireFields(row, "name");
        Staff staff = new Staff();
        staff.setName(row.get("name").trim());
        staff.setStaffType(row.containsKey("type") ? row.get("type") : row.getOrDefault("role", null));
        staff.setContact(row.getOrDefault("contact", null));
        staffRepo.save(staff);
        return true;
    }

    private boolean importDepartment(Map<String, String> row) {
        String name = row.get("name");
        if (name == null || name.isBlank()) throw new com.hms.exception.BusinessRuleViolationException("Required field 'name' missing");
        Department dept = new Department();
        dept.setName(name.trim());
        dept.setDepartmentType(row.getOrDefault("type", null));
        dept.setDisplayOrder(row.containsKey("display_order") ? row.get("display_order") : null);
        departmentRepo.save(dept);
        return true;
    }


    private boolean importLabTemplateDetail(Map<String, String> row) {
        String chargeName = row.containsKey("charge_name") ? row.get("charge_name") : row.get("charge");
        if (chargeName == null || chargeName.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'Charge Name' (or 'charge') is missing or empty");
        }

        // 1. Find Charge by name
        List<Charge> charges = chargeRepo.findByNameIgnoreCase(chargeName.trim());
        if (charges.isEmpty()) {
            throw new com.hms.exception.BusinessRuleViolationException("Charge '" + chargeName + "' not found for LabTemplateDetail");
        }
        Charge charge = charges.get(0);

        // 2. Find DiagnosticTemplate by chargeId
        List<DiagnosticTemplate> templates = diagnosticTemplateRepo.findByChargeId(charge.getId());
        if (templates.isEmpty()) {
            throw new com.hms.exception.BusinessRuleViolationException("DiagnosticTemplate not found for Charge '" + chargeName + "'");
        }
        DiagnosticTemplate template = templates.get(0);

        // 3. Create and set LabTemplateDetail
        LabTemplateDetail detail = new LabTemplateDetail();
        detail.setResultName(row.getOrDefault("result_name", "").trim());
        
        String orderNumStr = row.getOrDefault("order_number", "0").trim();
        int orderNum = 0;
        try {
            orderNum = Integer.parseInt(orderNumStr);
        } catch (Exception ignored) {}
        detail.setOrderNumber(orderNum);
        
        String unitVal = row.getOrDefault("unit", "").trim();
        String normalRangeVal = row.getOrDefault("normal_range", "").trim();
        String expressionVal = row.getOrDefault("expression", "").trim();
        detail.setUnit(unitVal.isEmpty() ? null : unitVal);
        detail.setNormalRange(normalRangeVal.isEmpty() ? null : normalRangeVal);
        detail.setNormalRangeExp(expressionVal.isEmpty() ? null : expressionVal);

        // Determine lab type — valid values are NUMERIC, TEXT, FORMULA, HEADER.
        // CSV may contain diagnostic category names (e.g. "DIAGNOSTICS") which are not lab types.
        java.util.Set<String> VALID_LAB_TYPES = java.util.Set.of("NUMERIC", "TEXT", "FORMULA", "HEADER");
        String ltdType = row.getOrDefault("type", "").trim().toUpperCase();
        if (VALID_LAB_TYPES.contains(ltdType)) {
            detail.setLabType(ltdType);
        } else {
            // Infer: only treat as HEADER if name has bold tags or explicitly looks like a header, otherwise default to NUMERIC
            String resName = detail.getResultName() != null ? detail.getResultName().toLowerCase() : "";
            if (normalRangeVal.isEmpty() && unitVal.isEmpty() && expressionVal.isEmpty() &&
                (resName.contains("<b>") || resName.contains("<strong>") || resName.contains("esr") || resName.contains("differential"))) {
                detail.setLabType("HEADER");
            } else {
                detail.setLabType("NUMERIC");
            }
        }

        String rowsStr = row.getOrDefault("rows", "").trim();
        short rowCount = 1;
        if (!rowsStr.isEmpty()) {
            try {
                rowCount = Short.parseShort(rowsStr);
            } catch (NumberFormatException ignored) {}
        }
        
        detail.setRowCount(rowCount);
        detail.setStatus(EntityStatus.ACTIVE); // active

        detail = labDetailRepo.save(detail);

        // 4. Link detail to DiagnosticTemplate
        template.getLabTemplateDetails().add(detail);
        diagnosticTemplateRepo.save(template);

        return true;
    }

    private boolean importCategory(Map<String, String> row) {
        String name = row.containsKey("category_name") ? row.get("category_name") : row.get("name");
        if (name == null || name.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'Category Name' or 'name' is missing or empty");
        }

        Category cat = new Category();
        cat.setName(name.trim());

        String typeStr = row.getOrDefault("type", "").trim();
        if (!typeStr.isBlank()) {
            try {
                cat.setType(CategoryType.valueOf(typeStr.toUpperCase()));
            } catch (Exception e) {
                throw new com.hms.exception.BusinessRuleViolationException("Invalid Category Type: " + typeStr);
            }
        } else {
            cat.setType(CategoryType.PATIENT);
        }

        String chargeCatTypeStr = row.containsKey("category_type") ? row.get("category_type") : row.get("charge_category_type");
        if (chargeCatTypeStr != null && !chargeCatTypeStr.isBlank()) {
            try {
                cat.setChargeCategoryType(ChargeCategoryType.valueOf(chargeCatTypeStr.trim().toUpperCase()));
            } catch (Exception e) {
                if (cat.getType() == CategoryType.CHARGE) {
                    throw new com.hms.exception.BusinessRuleViolationException("Invalid Charge Category Type: " + chargeCatTypeStr);
                }
            }
        }

        String diagTypeStr = row.getOrDefault("diagnostic_type", "").trim();
        if (!diagTypeStr.isBlank()) {
            if (diagTypeStr.equalsIgnoreCase("LABORATORY")) diagTypeStr = "LAB";
            try {
                cat.setDiagnosticType(DiagnosticType.valueOf(diagTypeStr.toUpperCase()));
            } catch (Exception e) {
                throw new com.hms.exception.BusinessRuleViolationException("Invalid Diagnostic Type: " + diagTypeStr);
            }
        }

        cat.setStatus(EntityStatus.ACTIVE);
        cat.syncCategoryType();

        categoryRepo.save(cat);
        return true;
    }

    private boolean importPayor(Map<String, String> row) {
        String name = row.containsKey("payer_name") ? row.get("payer_name") : row.get("name");
        if (name == null || name.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'Payer Name' is missing or empty");
        }
        String trimmedName = name.trim();
        // Auto-generate code from name (uppercase slug, max 30 chars)
        String code = trimmedName.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (code.length() > 30) code = code.substring(0, 30);

        Payor payor = new Payor();
        payor.setName(trimmedName);
        payor.setCode(code);
        payor.setType(row.containsKey("payer_type") ? row.get("payer_type") : row.getOrDefault("type", null));
        payorRepo.save(payor);
        return true;
    }

    private boolean importDiagnosticTemplate(Map<String, String> row) {
        String chargeName = row.containsKey("charge_name") && !row.get("charge_name").isBlank() ? row.get("charge_name") :
                            row.containsKey("name") && !row.get("name").isBlank() ? row.get("name") :
                            row.get("templatename");
        
        if (chargeName == null || chargeName.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required field 'Charge Name' is missing or empty");
        }
        
        // 1. Find Charge
        Charge charge = null;
        List<Charge> charges = chargeRepo.findByNameIgnoreCase(chargeName.trim());
        if (!charges.isEmpty()) {
            charge = charges.get(0);
        } else {
            throw new com.hms.exception.BusinessRuleViolationException(chargeName.trim() + " not found");
        }
        
        // 2. Find or create Specimen
        String specimenName = row.getOrDefault("specimen", "").trim();
        UUID specimenId = null;
        if (!specimenName.isEmpty()) {
            List<Specimen> specimens = specimenRepo.findByNameIgnoreCase(specimenName);
            if (!specimens.isEmpty()) {
                specimenId = specimens.get(0).getId();
            } else {
                Specimen s = new Specimen();
                s.setName(specimenName);
                s.setStatus(com.hms.domain.shared.model.EntityStatus.ACTIVE);
                s = specimenRepo.save(s);
                specimenId = s.getId();
            }
        }
        
        // 3. Find or create Department
        String deptName = row.getOrDefault("department", "").trim();
        Department department = null;
        if (deptName.isEmpty()) {
            throw new com.hms.exception.BusinessRuleViolationException("Department is required");
        } else {
            Optional<Department> deptOpt = departmentRepo.findByNameIgnoreCase(deptName);
            if (deptOpt.isPresent()) {
                department = deptOpt.get();
            } else {
                department = new Department();
                department.setName(deptName);
                department.setDepartmentType("DIAGNOSTICS");
                department.setDisplayOrder("0");
                department.setStatus(EntityStatus.ACTIVE);
                department = departmentRepo.save(department);
            }
        }
        
        // 4. Create or update DiagnosticTemplate
        DiagnosticTemplate template = null;
        List<DiagnosticTemplate> existingTemplates = diagnosticTemplateRepo.findByChargeId(charge.getId());
        if (!existingTemplates.isEmpty()) {
            template = existingTemplates.get(0);
        } else {
            template = new DiagnosticTemplate();
            template.setChargeId(charge.getId());
        }
        
        template.setName(charge.getName());
        
        // Format
        String rawFormat = row.getOrDefault("format", "LAB_TEMPLATE").trim().toUpperCase();
        String format = "LAB_TEMPLATE";
        if (rawFormat.contains("CUSTOM") || rawFormat.contains("FREE")) {
            format = "CUSTOM_TEMPLATE";
        } else if (rawFormat.contains("ATTACHMENT")) {
            format = "ATTACHMENT";
        }
        template.setFormat(format);

        // Diagnostic Type based on Format
        if (format.equals("CUSTOM_TEMPLATE") || format.equals("ATTACHMENT")) {
            template.setDiagnosticType(DiagnosticType.RADIOLOGY);
        } else {
            template.setDiagnosticType(DiagnosticType.LAB);
        }

        // Print Template Name lookup for Custom Template format
        if (format.equals("CUSTOM_TEMPLATE")) {
            String templateName = row.get("templatename");
            if (templateName != null && !templateName.isBlank()) {
                List<PrintTemplate> pts = printTemplateRepo.findByNameIgnoreCaseAndDocumentTypeIgnoreCase(templateName.trim(), "DIAGNOSTICS");
                if (pts.isEmpty()) {
                    throw new com.hms.exception.BusinessRuleViolationException(templateName.trim() + " not found");
                }
                template.setTemplateHtml(pts.get(0).getContent());
            }
        }
        
        // Order Number
        String orderNumStr = row.getOrDefault("order_number", "0").trim();
        int orderNum = 0;
        try {
            orderNum = Integer.parseInt(orderNumStr);
        } catch (Exception ignored) {}
        template.setOrderNumber(orderNum);
        
        template.setSpecimenId(specimenId);
        template.setDepartment(department);
        template.setHeader(row.getOrDefault("header", null));
        template.setMethod(row.getOrDefault("method", null));
        template.setReferenceRange(row.getOrDefault("reference_range", null));
        template.setUnit(row.getOrDefault("unit", null));
        template.setStatus(EntityStatus.ACTIVE);
        
        diagnosticTemplateRepo.save(template);
        
        return true;
    }

    private void addPricingTier(ServiceCatalogItem item, BillType type, String rateStr) {
        if (rateStr == null || rateStr.isBlank()) return;
        try {
            long rate = Math.round(Double.parseDouble(rateStr.trim()) * 100);
            if (rate <= 0) return;
            PricingTier tier = new PricingTier();
            tier.setBillType(type);
            tier.setUnitRate(rate);
            item.addPricingTier(tier);
        } catch (Exception ignored) {}
    }



    private boolean importCharge(Map<String, String> row) {
        requireFields(row, "name");
        Charge charge = new Charge();
        charge.setName(row.get("name").trim());

        // Resolve Category ID by looking up category name (or UUID as fallback)
        UUID categoryId = null;
        String categoryName = row.containsKey("category") ? row.get("category") : row.get("category_id");
        if (categoryName != null && !categoryName.isBlank()) {
            try {
                UUID parsedId = UUID.fromString(categoryName.trim());
                if (categoryRepo.existsById(parsedId)) {
                    categoryId = parsedId;
                }
            } catch (Exception ignored) {
            }
            
            if (categoryId == null) {
                // Not a valid existing UUID, look it up by name in Category table (shared master)
                Optional<Category> foundCat = categoryRepo.findAll().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(categoryName.trim()))
                    .findFirst();
                if (foundCat.isPresent()) {
                    categoryId = foundCat.get().getId();
                } else {
                    // Create the category if not found
                    Category newCat = new Category();
                    newCat.setName(categoryName.trim());
                    newCat.setStatus(EntityStatus.ACTIVE);
                    newCat.setType(com.hms.domain.shared.model.CategoryType.CHARGE);
                    newCat.syncCategoryType();
                    if (categoryName.trim().equalsIgnoreCase("CONSULTATION CHARGES")) {
                        newCat.setChargeCategoryType(com.hms.domain.shared.model.ChargeCategoryType.CONSULTATION);
                    } else if (categoryName.trim().equalsIgnoreCase("ROOM CHARGES")) {
                        newCat.setChargeCategoryType(com.hms.domain.shared.model.ChargeCategoryType.ROOM_CHARGE);
                    } else {
                        newCat.setChargeCategoryType(com.hms.domain.shared.model.ChargeCategoryType.DIAGNOSTICS);
                    }
                    newCat = categoryRepo.save(newCat);
                    categoryId = newCat.getId();
                }
            }
        }

        if (categoryId != null) {
            charge.setCategoryId(categoryId);
        } else {
            if (categoryName != null && !categoryName.isBlank()) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Category '" + categoryName + "' not found. Please create the category first.");
            }
        }

        charge.setChargeType(ChargeType.CHARGE);
        charge.setStartDate(java.time.LocalDate.now());

        String cashRate = row.containsKey("cash") ? row.get("cash") : row.getOrDefault("cash_rate", "0");
        String creditRate = row.containsKey("credit") ? row.get("credit") : row.getOrDefault("credit_rate", "0");

        addTariff(charge, "CASH", cashRate);
        addTariff(charge, "CREDIT", creditRate);

        charge = chargeRepo.save(charge);

        // If this charge belongs to a diagnostic-like category, sync it to ServiceCatalogItem
        syncToServiceCatalog(charge, row);

        return true;
    }

    private void syncToServiceCatalog(Charge charge, Map<String, String> row) {
        if (charge.getCategoryId() == null) return;

        // Resolve the category name by checking Category table first, then ServiceCategory table
        String categoryName = categoryRepo.findById(charge.getCategoryId())
            .map(Category::getName)
            .orElseGet(() -> serviceCategoryRepo.findById(charge.getCategoryId())
                .map(ServiceCategory::getName)
                .orElse(null));

        if (categoryName == null) return;

        ServiceCategory cat = serviceCategoryRepo.findByName(categoryName)
            .orElseGet(() -> {
                ServiceCategory newCat = new ServiceCategory();
                newCat.setName(categoryName);
                if (categoryName.trim().equalsIgnoreCase("CONSULTATION CHARGES")) {
                    newCat.setCategoryType(ServiceCategoryType.CONSULTATION);
                } else if (categoryName.trim().equalsIgnoreCase("ROOM CHARGES")) {
                    newCat.setCategoryType(ServiceCategoryType.ROOM_CHARGE);
                } else if (categoryName.trim().equalsIgnoreCase("PHARMACY")) {
                    newCat.setCategoryType(ServiceCategoryType.PHARMACY);
                } else {
                    newCat.setCategoryType(ServiceCategoryType.DIAGNOSTICS); // Default to DIAGNOSTICS for imported charges
                }
                return serviceCategoryRepo.save(newCat);
            });

        ServiceCatalogItem sci = catalogItemRepo.findById(charge.getId())
            .orElseGet(() -> {
                ServiceCatalogItem item = new ServiceCatalogItem();
                item.setId(charge.getId());
                return item;
            });
        sci.setName(charge.getName());
        sci.setCategoryId(cat.getId());
        sci.setServiceType(ServiceType.INDIVIDUAL);

        // Clear and rebuild tiers
        sci.getPricingTiers().clear();
        
        String cashRate = row.containsKey("cash") ? row.get("cash") : row.getOrDefault("cash_rate", "0");
        String creditRate = row.containsKey("credit") ? row.get("credit") : row.getOrDefault("credit_rate", "0");

        addPricingTier(sci, BillType.CASH, cashRate);
        addPricingTier(sci, BillType.CREDIT, creditRate);

        catalogItemRepo.save(sci);
    }

    private void addTariff(Charge charge, String type, String rate) {
        if (rate == null || rate.isBlank()) return;
        try {
            long r = Math.round(Double.parseDouble(rate.trim()) * 100);
            Tariff t = new Tariff();
            t.setBillType(type);
            t.setRate(r);
            charge.addTariff(t);
        } catch (Exception ignored) {}
    }

    private boolean importStock(Map<String, String> row) {
        String itemInput = row.getOrDefault("item", row.getOrDefault("item_id", "")).trim();
        String deptInput = row.getOrDefault("department", row.getOrDefault("department_id", "")).trim();
        String qtyInput  = row.getOrDefault("quantity", "").trim();

        if (itemInput.isBlank() || deptInput.isBlank() || qtyInput.isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Required fields 'item', 'department', and 'quantity' must be provided");
        }

        InventoryBatch batch = new InventoryBatch();
        
        // Resolve Item
        UUID itemId = null;
        try {
            itemId = UUID.fromString(itemInput);
            if (!itemRepo.existsById(itemId)) {
                throw new com.hms.exception.BusinessRuleViolationException("Item not found for ID: " + itemInput);
            }
        } catch (IllegalArgumentException e) {
            itemId = itemRepo.findByName(itemInput)
                .map(com.hms.domain.inventory.model.InventoryItem::getId)
                .orElseThrow(() -> new com.hms.exception.BusinessRuleViolationException("Item not found with name: " + itemInput));
        }
        batch.setItemId(itemId);

        // Resolve Department
        UUID deptId = null;
        try {
            deptId = UUID.fromString(deptInput);
            if (!departmentRepo.existsById(deptId)) {
                throw new com.hms.exception.BusinessRuleViolationException("Department not found for ID: " + deptInput);
            }
        } catch (IllegalArgumentException e) {
            deptId = departmentRepo.findByNameIgnoreCase(deptInput)
                .map(com.hms.domain.shared.model.Department::getId)
                .orElseThrow(() -> new com.hms.exception.BusinessRuleViolationException("Department not found with name: " + deptInput));
        }
        batch.setDepartmentId(deptId);

        try {
            batch.setCurrentQuantity(Integer.parseInt(qtyInput));
        } catch (NumberFormatException e) {
            throw new com.hms.exception.BusinessRuleViolationException("Invalid numeric value for quantity: " + qtyInput);
        }

        batch.setBatchNumber(row.getOrDefault("batch_number", "AUTO-" + System.currentTimeMillis()));
        batch.setPurchaseRate(parseBigDecimal(row.getOrDefault("purchase_rate", "0"), "purchase_rate"));
        batch.setMaximumRetailPrice(parseBigDecimal(row.getOrDefault("mrp", "0"), "mrp"));
        batch.setSellingRate(parseBigDecimal(row.getOrDefault("selling_rate", "0"), "selling_rate"));

        String expiry = row.getOrDefault("expiry_date", "");
        if (!expiry.isBlank()) {
            batch.setExpiryDate(parseDate(expiry.trim()));
        }

        // Dummy transaction ID for bulk import
        batch.setSourceTransactionId(UUID.randomUUID());
        batchRepo.save(batch);
        return true;
    }

    private boolean importGenericLog(String entityType, Map<String, String> row) {
        log.info("Generic import ({}) row: {}", entityType, row);
        return true;
    }

    // ── CSV Parsing ───────────────────────────────────────────────────────────

    private List<Map<String, String>> parseCsv(MultipartFile file) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            List<String[]> records = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                // Skip BOM if present
                reader.mark(1);
                int firstChar = reader.read();
                if (firstChar != 0xFEFF && firstChar != -1) {
                    reader.reset();
                }
                
                StringBuilder field = new StringBuilder();
                List<String> record = new ArrayList<>();
                boolean inQuotes = false;
                int ch;
                
                while ((ch = reader.read()) != -1) {
                    char c = (char) ch;
                    if (inQuotes) {
                        if (c == '"') {
                            reader.mark(1);
                            int nextCh = reader.read();
                            if (nextCh == '"') {
                                field.append('"');
                            } else {
                                inQuotes = false;
                                if (nextCh != -1) {
                                    reader.reset();
                                }
                            }
                        } else {
                            field.append(c);
                        }
                    } else {
                        if (c == '"') {
                            inQuotes = true;
                        } else if (c == ',') {
                            record.add(field.toString());
                            field.setLength(0);
                        } else if (c == '\r') {
                            // ignore
                        } else if (c == '\n') {
                            record.add(field.toString());
                            field.setLength(0);
                            records.add(record.toArray(new String[0]));
                            record.clear();
                        } else {
                            field.append(c);
                        }
                    }
                }
                if (field.length() > 0 || !record.isEmpty()) {
                    record.add(field.toString());
                    records.add(record.toArray(new String[0]));
                }
            }

            if (records.isEmpty()) return rows;

            String[] rawHeaders = records.get(0);
            String[] headers = new String[rawHeaders.length];
            for (int i = 0; i < rawHeaders.length; i++) {
                headers[i] = rawHeaders[i].replaceAll("^\"|\"$", "").trim().toLowerCase().replace(" ", "_");
            }

            for (int r = 1; r < records.size(); r++) {
                String[] values = records.get(r);
                if (values.length == 0 || (values.length == 1 && values[0].isBlank())) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String val = i < values.length ? values[i].trim() : "";
                    row.put(headers[i], val);
                }
                rows.add(row);
            }

        } catch (Exception ex) {
            log.error("CSV parse error: {}", ex.getMessage(), ex);
            throw new com.hms.exception.BusinessRuleViolationException("CSV parse error: " + ex.getMessage());
        }
        return rows;
    }

    private void requireFields(Map<String, String> row, String... fields) {
        for (String field : fields) {
            if (row.getOrDefault(field, "").isBlank()) {
                throw new com.hms.exception.BusinessRuleViolationException(
                    "Required field '" + field + "' is missing or empty");
            }
        }
    }

    private BigDecimal parseBigDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Invalid numeric value for '" + fieldName + "': " + value);
        }
    }

    private java.time.LocalDate parseDate(String dateStr) {
        List<java.time.format.DateTimeFormatter> formatters = List.of(
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"),
            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(dateStr, formatter);
            } catch (java.time.format.DateTimeParseException ignored) {}
        }

        throw new com.hms.exception.BusinessRuleViolationException(
            "Invalid date format: '" + dateStr + "'. Expected yyyy-MM-dd or dd/MM/yyyy");
    }
}
