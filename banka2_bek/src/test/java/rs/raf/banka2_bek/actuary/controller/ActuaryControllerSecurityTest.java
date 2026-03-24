package rs.raf.banka2_bek.actuary.controller;

import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuaryControllerSecurityTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private JwtService jwtService;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(@NonNull ClientHttpResponse r) { return false; }
            @Override public boolean hasError(@NonNull HttpStatusCode s) { return false; }
        });
        return rt;
    }

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
    }

    private Employee saveEmployee(String email, Set<String> permissions) {
        Employee emp = Employee.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .phone("0600000000")
                .address("Bulevar 1")
                .username(email)
                .password("hashed")
                .saltPassword("salt")
                .position("Analyst")
                .department("Finance")
                .active(true)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .permissions(permissions)
                .build();
        return employeeRepository.save(emp);
    }

    private String tokenFor(Employee employee) {
        return jwtService.generateAccessToken(employee);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return restTemplate.exchange(url("/actuaries/agents"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("GET /actuaries/agents bez tokena vraca 401 ili 403")
    void noToken_returnsUnauthorized() {
        ResponseEntity<String> response = get(null);
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /actuaries/agents za obicnog zaposlenog vraca 403")
    void regularEmployee_cannotAccessActuaries() {
        Employee emp = saveEmployee("emp@test.com", Set.of());
        String token = tokenFor(emp);

        ResponseEntity<String> response = get(token);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /actuaries/agents za supervizora prolazi security (nije 403)")
    void supervisorEmployee_canAccessActuaries() {
        Employee emp = saveEmployee("supervisor@test.com", Set.of("SUPERVISOR"));
        String token = tokenFor(emp);

        ResponseEntity<String> response = get(token);

        assertThat(response.getStatusCode()).isNotEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /actuaries/agents za admina prolazi security (nije 403)")
    void adminEmployee_canAccessActuaries() {
        Employee emp = saveEmployee("admin@test.com", Set.of("ADMIN"));
        String token = tokenFor(emp);

        ResponseEntity<String> response = get(token);

        assertThat(response.getStatusCode()).isNotEqualTo(FORBIDDEN);
    }
}
