# RBAC Flow Document — HIMS Vitalsoft

## 1. Architecture Overview

| Layer | Technology | Role |
|---|---|---|
| **Models (JPA)** | Spring 4 / Hibernate 4 | `User`, `Role`, `Feature` entities |
| **Data Access** | Hibernate DAOs | `UserDaoImpl` (implements `UserDetailsService`), `RoleDaoImpl`, `FeatureDaoImpl` |
| **Security Config** | Spring Security 4 | URL filtering, form login, session management |
| **Method Security** | `@PreAuthorize` + custom `PermissionEvaluator` | Feature-level authorization on controller methods |
| **Runtime Cache** | `SecurityContent` | In-memory `featureKey → Set<roleName>` map |
| **AOP Cache Refresh** | AspectJ | Rebuilds cache after role CRUD |
| **Frontend** | AngularJS 1.x | Client-side feature-gated UI hiding |

## 2. Data Model

### 2.1 User (`users` table)

`User` implements `UserDetails`. Linked to roles via join table `user_roles`.

```java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(name = "user_roles",
    joinColumns = @JoinColumn(name = "user"),
    inverseJoinColumns = @JoinColumn(name = "role"))
private Set<Role> roles;

public Set<GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    authorities.addAll(roles);
    return authorities;
}
```

Additional user attributes:
- `userRights` — JSON column for fine-grained clinical note permissions
- `deptView` — enum: `AllRecord` / `OnlyHisDepartmentRecord` / `OnlyHisRecord`
- `patientView` — enum: `DepartmentWise` / `ConsultantWise`

### 2.2 Role (`roles` table)

`Role` implements `GrantedAuthority`. `getAuthority()` delegates to `getName()`.

```java
public class Role implements GrantedAuthority {
    private String name;                          // e.g. ROLE_ADMIN, ROLE_DOCTOR
    private Integer modNo;                        // links to license module
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_features")
    private Set<Feature> features;                // permissions granted to this role
}
```

**Seed roles:** `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_USER`, `ROLE_LAB`, `ROLE_BILLING`, `ROLE_RECEPTION`, `ROLE_DOCTOR`, `ROLE_RADIOLOGY`, `ROLE_STOCK`, `ROLE_SALES`, `ROLE_NURSE`.

### 2.3 Feature (`features` table)

Unique permission keys that represent specific application capabilities.

```java
public class Feature extends BaseModel {
    private String name;           // human-readable e.g. "Settings Users"
    private String featureKey;     // unique key e.g. SETTINGS_USERS, REGISTRATION
    private Integer modNo;         // license module number
    @ElementCollection
    private Set<Module> modules;   // e.g. RECEPTION, BILLING, ADMIN, DIAGNOSTICS...
}
```

**Feature key examples:**
- `REGISTRATION`, `PATIENT_EDIT`, `PATIENT_SEARCH`, `PATIENT_PROFILE`
- `OUT_PATIENT`, `IN_PATIENT`, `DISCHARGE_SUMMARY_ONLY`
- `PATIENT_BILLS`, `IP_AUTOMATED_ORDERS`, `IP_AUTOMATED_OTHER_CHARGE`
- `LAB_REPORT`, `RADIOLOGY`
- `BEDMANAGEMENT`, `OT_SCHEDULE`, `MARKETING`, `MEDICAL_RECORD`
- `SETTINGS_USERS`, `SETTINGS_ROLE`, `SETTINGS_CHARGES`, `SETTINGS_ITEM`
- `SETTINGS_DEPARTMENT`, `SETTINGS_CONSULTANT`, `SETTINGS_CONFIGURATION`
- `PAYMENT`, `SALES`, `APPOINTMENT_BOOK`, `CHECKIN`
- `UPDATE_CLINICAL_TRAIL`, `SETTINGS_ACCOUNTUNIT`, `SETTINGS_DATAQUERY`, etc.

### 2.4 Relationship Summary

```
User ──M:N──► Role ──M:N──► Feature
  │                          │
  └── accountUnits           └── modules
  └── departments
  └── consultant
  └── userRights (JSON)
```

## 3. Authentication Flow

```
┌──────────┐   POST /session    ┌──────────────────┐
│  Login    │ ─────────────────► │   SecurityConfig  │
│  Page     │   (UserName/      │  (Spring Security) │
│           │    Password)       └────────┬──────────┘
└──────────┘                              │
                                          ▼
                            ┌────────────────────────┐
                            │  DaoAuthenticationProvider│
                            │  + BCryptPasswordEncoder │
                            └────────┬────────────────┘
                                     │
                                     ▼
                            ┌────────────────────────┐
                            │  UserDaoImpl            │
                            │  .loadUserByUsername()  │
                            │  (Hibernate criteria)   │
                            │  - fetches user + roles │
                            │  - fetches departments  │
                            │  - fetches consultant   │
                            └────────┬────────────────┘
                                     │
                          ┌──────────┴──────────┐
                          │                     │
                          ▼                     ▼
              ┌──────────────────┐   ┌──────────────────┐
              │ Authentication   │   │ Authentication   │
              │ Success          │   │ Failure          │
              │ - set 15min      │   │ - HTTP 401       │
              │   session timeout│   │ - JSON message   │
              │ - cookie "VSSID" │   └──────────────────┘
              └──────────────────┘
```

**Key points:**
- `UserDaoImpl.loadUserByUsername()` uses `AliasToNestedMapResultTransformer` to build a nested User object with all associations in a single query.
- `AuthenticationSuccess` sets `MAX_INACTIVE_TIME = 900` seconds (15 minutes).
- `SessionExpirationFilter` checks session validity on every request (except login/assets), returns HTTP 401 + redirect if expired.

## 4. Authorization Flow (Runtime)

### 4.1 Startup: Building the Permission Cache

On application startup, `SecurityConfig.securityContent()` creates `SecurityContent`:

```java
public SecurityContent(FeatureDao featureDao, RoleDao roleDao, String[] activeProfiles) {
    // calls getModNos() which returns all modules 1-32 (license bypass)
    initializeFeatureRoles();
}
```

`initializeFeatureRoles()` builds an in-memory `HashMap<String, Set<String>>`:

```
featureRoles["SETTINGS_USERS"] = {"ROLE_ADMIN", "ROLE_SUPER_ADMIN"}
featureRoles["REGISTRATION"]   = {"ROLE_RECEPTION", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"}
featureRoles["OUT_PATIENT"]    = {"ROLE_DOCTOR", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"}
...
```

**Algorithm:**
1. Get all licensed module numbers (modules 1-32 — license validation bypassed)
2. Load all Features for those module numbers from the database
3. Load all Roles with their `@ManyToMany(fetch = EAGER)` Feature collections
4. For each Feature, collect all role names that include it

This map is `synchronized` and can be rebuilt at runtime via `SecurityAspect`.

### 4.2 Request-Time Authorization

Protected controller methods use `@PreAuthorize("hasPermission('FEATURE_KEY', '')")`:

```java
@PreAuthorize("hasPermission('SETTINGS_ROLE', '')")
@RequestMapping(method = RequestMethod.POST)
public ResponseEntity<Object> createRole(@Valid @RequestBody RoleDto roleDto, ...) { ... }
```

**Full flow through `CustomPermissionEvaluator.hasPermission()`:**

```
1. Extract user's role names from Authentication object
   Set<String> roles = AuthorityUtils.authorityListToSet(auth.getAuthorities())
   e.g. {"ROLE_ADMIN", "ROLE_USER"}

2. Look up permitted roles from cache
   Set<String> permittedRoles = securityContent.getFeatureRoles().get("SETTINGS_ROLE")
   e.g. {"ROLE_ADMIN", "ROLE_SUPER_ADMIN"}

3. If permittedRoles is null  → DENY (unlicensed feature)

4. If permittedRoles is empty → DENY (no roles configured)

5. For each of user's roles:
   if permittedRoles.contains(role) → ALLOW ✓

6. If no match → DENY ✗
   Logs: "NAME[username] IP[x.x.x.x] : Not permitted to access Feature [FEATURE_KEY]"
```

### 4.3 Programmatic Permission Check

For non-controller code, `PermissionUtil` provides the same logic:

```java
@Component
public class PermissionUtil {
    public boolean hasPermission(String featureName) {
        Set<String> permittedRoles = securityContent.getFeatureRoles().get(featureName);
        if (permittedRoles == null || permittedRoles.isEmpty()) return false;
        Set<String> roles = getUserRoles();
        for (String role : roles)
            if (permittedRoles.contains(role)) return true;
        return false;
    }
}
```

### 4.4 URL-Level Security

`SecurityConfig.configure(HttpSecurity)`:
- `/login`, `/eRegister`, `/patient/eRegister/**` → `permitAll()`
- All other URLs → `authenticated()`
- CSRF disabled
- Form login at `/session` (POST), default success URL `/`
- Logout at `/logout`, deletes `VSSID` cookie

### 4.5 OR-based Authorization

Multiple features can be OR'd together in a single `@PreAuthorize`:

```java
@PreAuthorize("hasPermission('PATIENT_BILLS', '') OR hasPermission('IP_AUTOMATED_OTHER_CHARGE', '')")
@PreAuthorize("hasPermission('LAB_REPORT', '') OR hasPermission('RADIOLOGY', '')")
@PreAuthorize("hasPermission('SETTINGS_ITEM', '') OR hasPermission('MEDICAL_RECORD', '')")
```

## 5. Cache Synchronization (AOP)

### 5.1 SecurityAspect (AspectJ)

When roles are created/updated/deleted, the runtime cache must be refreshed:

```java
@Aspect
public class SecurityAspect {
    @AfterReturning(
        pointcut = "execution(* com.ssb.vitalsoft.service.impl.RoleManagerImpl.save*(..)) ||
                    execution(* com.ssb.vitalsoft.service.impl.RoleManagerImpl.update*(..)) ||
                    execution(* com.ssb.vitalsoft.service.impl.RoleManagerImpl.remove*(..))",
        returning = "result")
    public void afterReturning(Object result) {
        securityContent.initializeFeatureRoles();  // rebuilds the cache
    }
}
```

Registered in `AspectConfig.java`:
```java
@Bean
public SecurityAspect securityAspect() {
    return new SecurityAspect();
}
```

### 5.2 UserSecurityAdvice (Spring AOP)

A `MethodBeforeAdvice` + `AfterReturningAdvice` on `UserManager` operations:

```
before(method, args, target):
  - If modifying another user and NOT admin → throw AccessDeniedException
  - If modifying self and trying to change roles → throw AccessDeniedException
  - If anonymous (signup) → allow

afterReturning(returnValue, method, args, target):
  - If modified user is the current user → refresh SecurityContext
    (creates new UsernamePasswordAuthenticationToken with updated authorities)
```

Registered in `WebConfig.java`:
```java
@Bean
public UserSecurityAdvice userSecurityAdvice() {
    return new UserSecurityAdvice();
}
```

## 6. Super Admin Protection

`CommonUtil.hasSARole()` checks if a user has `ROLE_SUPER_ADMIN`:

```java
public static boolean hasSARole(User user) {
    return hasSARole(user.getRoles());
}
public static boolean hasSARole(Collection<Role> roles) {
    for (Role role : roles)
        if (isSARole(role)) return true;
    return false;
}
```

Used in:
- `UserManagerImpl.getAllUsers()` — SA users are hidden from non-SA users
- `UserManagerImpl.getAllRoles()` — SA role is hidden from non-SA users
- Frontend role/user selectors — SA role is excluded

## 7. Frontend Enforcement (AngularJS)

### 7.1 Loading User Features

On page load, `main.js` calls:

```javascript
$http.get('/user/loggedInUser').success(function(data) {
    var roles = loggedInUser.roles;
    angular.forEach(roles, function(role) {
        angular.forEach(role.features, function(feature) {
            $scope.features[feature.featureKey] = true;
        });
    });
});
```

This builds `$scope.features` as a map:
```json
{ "SETTINGS_USERS": true, "REGISTRATION": true, "PATIENT_BILLS": true, ... }
```

### 7.2 Feature-Gated UI

Menu items use `ng-if="features.FEATURE_KEY"`:

```html
<!-- setting/index.html -->
<li ng-if="features.SETTINGS_USERS"><a ui-sref="settings.user">Users</a></li>
<li ng-if="features.SETTINGS_ROLE"><a ui-sref="settings.role">Roles</a></li>
<li ng-if="features.SETTINGS_CHARGES"><a ui-sref="settings.charges">Charges</a></li>
<li ng-if="features.SETTINGS_CONFIGURATION"><a ui-sref="settings.configuration">Configuration</a></li>
<li ng-if="features.SETTINGS_DEPARTMENT"><a ui-sref="settings.department">Department</a></li>
<li ng-if="features.SETTINGS_CONSULTANT"><a ui-sref="settings.consultant">Consultants</a></li>
<li ng-if="features.SETTINGS_ITEM"><a ui-sref="settings.item">Item</a></li>
<li ng-if="features.SETTINGS_USERS"><a ui-sref="settings.user">Users</a></li>
```

### 7.3 Module-Based Feature Loading

When a user clicks a module:

```javascript
$scope.getFeatures = function(module) {
    $http.get('/feature/getFeaturesByCurrentUser?module=' + module).success(function(data) {
        $scope.features = data;      // overwrites with module-specific features
        setUpModuleView(module);      // routes to module landing page
    });
};
```

The backend `FeatureServiceImpl.getFeaturesByCurrentUser()` filters features by module and checks against the current user's roles.

### 7.4 Role-Based Dashboard Routing

```javascript
if (roles.indexOf('ROLE_SUPER_ADMIN') != -1)  dashboardPath = '/reports';
else if (roles.indexOf('ROLE_DOCTOR') != -1)  dashboardPath = '/outpatient';
else if (roles.indexOf('ROLE_LAB') != -1)     dashboardPath = '/diagnostics';
else if (roles.indexOf('ROLE_RADIOLOGY') != -1) dashboardPath = '/radiology';
else if (roles.indexOf('ROLE_RECEPTION') != -1) dashboardPath = '/patient';
else if (roles.indexOf('ROLE_BILLING') != -1)  dashboardPath = '/search';
else if (roles.indexOf('ROLE_ADMIN') != -1)    dashboardPath = '/module' or '/reports';
```

## 8. Complete End-to-End Example

**Scenario:** A doctor accesses Out Patient page.

```
1. Login: POST /session (username="dr.smith", password="***")

2. SecurityConfig → DaoAuthenticationProvider
   → UserDaoImpl.loadUserByUsername("dr.smith")
   → Returns User with roles [ROLE_DOCTOR]
   → AuthenticationSuccess: set 15min session, redirect to /

3. Angular loads → GET /user/loggedInUser
   → Returns User + roles[ROLE_DOCTOR] + features
   → Frontend builds $scope.features map
   → role = ROLE_DOCTOR → dashboardPath = '/outpatient'
   → Route changes to /outpatient

4. VisitController API called:
   @PreAuthorize("hasPermission('OUT_PATIENT', '')")

5. CustomPermissionEvaluator.hasPermission():
   - User's roles: {ROLE_DOCTOR}
   - featureRoles["OUT_PATIENT"] = {ROLE_DOCTOR, ROLE_ADMIN, ROLE_SUPER_ADMIN}
   - "ROLE_DOCTOR" ∈ permitted → ALLOW ✓

6. Page loads successfully
```

**Scenario:** A receptionist tries the same endpoint:

```
5. CustomPermissionEvaluator.hasPermission():
   - User's roles: {ROLE_RECEPTION}
   - featureRoles["OUT_PATIENT"] = {ROLE_DOCTOR, ROLE_ADMIN, ROLE_SUPER_ADMIN}
   - "ROLE_RECEPTION" ∉ permitted → DENY ✗
   - Logs: "NAME[reception1] IP[192.168.x.x] : Not permitted to access Feature [OUT_PATIENT]"
   → HTTP 403 Forbidden
```

## 9. Security Configuration Files

| File | Path | Purpose |
|---|---|---|
| `SecurityConfig.java` | `config/SecurityConfig.java` | URL security rules, form login, session config |
| `MethodSecurityConfig.java` | `config/MethodSecurityConfig.java` | Enables `@PreAuthorize`, registers `CustomPermissionEvaluator` |
| `CustomPermissionEvaluator.java` | `config/CustomPermissionEvaluator.java` | Core authorization logic using feature→roles cache |
| `SecurityContent.java` | `config/SecurityContent.java` | Runtime feature→roles in-memory cache, built at startup |
| `SessionExpirationfilter.java` | `config/SessionExpirationfilter.java` | Validates session on every request |
| `AuthenticationSuccess.java` | `config/AuthenticationSuccess.java` | Sets session timeout post-login |
| `AuthenticationFailure.java` | `config/AuthenticationFailure.java` | Returns 401 JSON on login failure |
| `WebConfig.java` | `config/WebConfig.java` | Registers `UserSecurityAdvice` |
| `AspectConfig.java` | `config/AspectConfig.java` | Registers AOP aspects |
| `SecurityAspect.java` | `aspect/SecurityAspect.java` | Refreshes cache after role changes |
| `UserSecurityAdvice.java` | `aspect/UserSecurityAdvice.java` | Prevents unauthorized user modifications |
| `PermissionUtil.java` | `util/PermissionUtil.java` | Programmatic permission check for non-controller code |
| `CommonUtil.java` | `util/CommonUtil.java` | `hasSARole()` utility |
| `Constants.java` | `constants/Constants.java` | Role name constants |

## 10. Key Design Decisions

1. **Feature-based, not action-based** — Permissions are domain features (e.g. `SETTINGS_USERS`) rather than CRUD actions. Multiple controller methods share the same feature gate.

2. **Roles group features** — A role is simply a named collection of features. There is no role hierarchy or inheritance. A user can have multiple roles, gaining all their features.

3. **Dual enforcement** — Frontend hides inaccessible UI elements (cosmetic/UX), backend enforces actual authorization via `@PreAuthorize` + custom `PermissionEvaluator`.

4. **Eager caching** — Feature→role mapping is cached in memory at startup (`SecurityContent`) and refreshed via AOP on role changes. This avoids DB lookups on every request.

5. **License bypass** — `SecurityContent.getModNos()` returns all module numbers 1-32, effectively bypassing real license-based feature gating.

6. **External dependency** — `SecurityContent` extends `com.ssb.security.ModuleContent` from an external JAR, suggesting a shared organizational security/licensing framework.

7. **Frontend limitations** — Frontend RBAC is only UI hiding; a malicious user could access REST endpoints directly, but the server-side `@PreAuthorize` will block unauthorized access.
