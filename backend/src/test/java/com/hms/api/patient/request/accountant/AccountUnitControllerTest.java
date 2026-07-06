package com.hms.api.patient.request.accountant;

import com.hms.api.accountunit.AccountUnitController;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.AccountUnit;
import com.hms.infrastructure.persistence.accountunit.AccountUnitJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountUnitControllerTest {

    @Mock
    private AccountUnitJpaRepository repo;

    @InjectMocks
    private AccountUnitController controller;

    private AccountUnit accountUnit;

    @BeforeEach
    void setUp() {
        accountUnit = new AccountUnit();
        accountUnit.setId(UUID.randomUUID());
        accountUnit.setName("Finance");
    }

    @Test
    void getAll_ShouldReturnActiveAccountUnits() {

        when(repo.findAllActive()).thenReturn(List.of(accountUnit));

        ResponseEntity<ApiResponse<List<AccountUnit>>> response = controller.getAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().data().size());
        assertEquals("Finance", response.getBody().data().get(0).getName());

        verify(repo).findAllActive();
    }

    @Test
    void create_ShouldSaveAndReturnCreatedEntity() {

        when(repo.save(accountUnit)).thenReturn(accountUnit);

        ResponseEntity<ApiResponse<AccountUnit>> response = controller.create(accountUnit);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Finance", response.getBody().data().getName());

        verify(repo).save(accountUnit);
    }

    @Test
    void update_ShouldUpdateAndReturnEntity() {

        when(repo.save(accountUnit)).thenReturn(accountUnit);

        ResponseEntity<ApiResponse<AccountUnit>> response = controller.update(accountUnit);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Finance", response.getBody().data().getName());

        verify(repo).save(accountUnit);
    }

    @Test
    void getPaginated_ShouldReturnFirstPage() {

        AccountUnit second = new AccountUnit();
        second.setId(UUID.randomUUID());
        second.setName("HR");

        when(repo.findAllActive()).thenReturn(
                List.of(accountUnit, second));

        ResponseEntity<ApiResponse<Page<AccountUnit>>> response = controller.getPaginated(0, 1, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Page<AccountUnit> page = response.getBody().data();

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals("Finance", page.getContent().get(0).getName());

        verify(repo).findAllActive();
    }

    @Test
    void getPaginated_ShouldFilterByName() {

        AccountUnit second = new AccountUnit();
        second.setId(UUID.randomUUID());
        second.setName("HR");

        when(repo.findAllActive()).thenReturn(
                List.of(accountUnit, second));

        ResponseEntity<ApiResponse<Page<AccountUnit>>> response = controller.getPaginated(0, 10, "fin");

        Page<AccountUnit> page = response.getBody().data();

        assertEquals(1, page.getTotalElements());
        assertEquals("Finance", page.getContent().get(0).getName());

        verify(repo).findAllActive();
    }

    @Test
    void getPaginated_ShouldReturnEmptyWhenNoMatch() {

        when(repo.findAllActive()).thenReturn(List.of(accountUnit));

        ResponseEntity<ApiResponse<Page<AccountUnit>>> response = controller.getPaginated(0, 10, "xyz");

        Page<AccountUnit> page = response.getBody().data();

        assertEquals(0, page.getTotalElements());
        assertTrue(page.getContent().isEmpty());

        verify(repo).findAllActive();
    }
}