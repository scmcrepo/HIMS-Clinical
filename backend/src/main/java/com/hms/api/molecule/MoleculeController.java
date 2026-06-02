package com.hms.api.molecule;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.Molecule;
import com.hms.infrastructure.persistence.molecule.MoleculeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping({"/molecule", "/molecules"}) @RequiredArgsConstructor
public class MoleculeController {
    private final MoleculeJpaRepository repo;
    @GetMapping("/getMoleculesByName")
    public ResponseEntity<ApiResponse<List<Molecule>>> searchByName(@RequestParam(name = "name", defaultValue="") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.searchByName(name)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Molecule>>> getAll(
            @RequestParam(name = "start", defaultValue="0") int start, @RequestParam(name = "limit", defaultValue="20") int limit,
            @RequestParam(name = "value", required=false) String value) {
        Pageable p = PageRequest.of(start / Math.max(limit,1), Math.max(limit,1));
        Page<Molecule> page = (value != null && !value.isBlank())
            ? repo.searchByNamePaged(value, p)
            : repo.findAllActivePaged(p);
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<Molecule>> create(@RequestBody Molecule req) {
        // BUG FIX: legacy returned "Department created successfully" — we return correct message
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Molecule created successfully", repo.save(req)));
    }
    @PutMapping
    public ResponseEntity<ApiResponse<Molecule>> update(@RequestBody Molecule req) {
        // BUG FIX: legacy returned "Department updated successfully"
        return ResponseEntity.ok(ApiResponse.ok("Molecule updated successfully", repo.save(req)));
    }
}
