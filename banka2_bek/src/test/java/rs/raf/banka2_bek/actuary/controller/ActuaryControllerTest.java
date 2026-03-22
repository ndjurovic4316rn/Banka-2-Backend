package rs.raf.banka2_bek.actuary.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.actuary.controller.exception_handler.ActuaryExceptionHandler;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.service.ActuaryService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ActuaryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ActuaryService actuaryService;

    @InjectMocks
    private ActuaryController actuaryController;

    private ActuaryInfoDto testAgentDto;
    private ActuaryInfoDto testAgentDto2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(actuaryController)
                .setControllerAdvice(new ActuaryExceptionHandler())
                .build();

        testAgentDto = new ActuaryInfoDto();
        testAgentDto.setId(1L);
        testAgentDto.setEmployeeId(10L);
        testAgentDto.setEmployeeName("Marko Markovic");
        testAgentDto.setEmployeeEmail("marko.markovic@banka.rs");
        testAgentDto.setEmployeePosition("Menadzer");
        testAgentDto.setActuaryType("AGENT");
        testAgentDto.setDailyLimit(new BigDecimal("100000.00"));
        testAgentDto.setUsedLimit(new BigDecimal("15000.00"));
        testAgentDto.setNeedApproval(false);

        testAgentDto2 = new ActuaryInfoDto();
        testAgentDto2.setId(2L);
        testAgentDto2.setEmployeeId(11L);
        testAgentDto2.setEmployeeName("Jelena Jovanovic");
        testAgentDto2.setEmployeeEmail("jelena.jovanovic@banka.rs");
        testAgentDto2.setEmployeePosition("Analiticar");
        testAgentDto2.setActuaryType("AGENT");
        testAgentDto2.setDailyLimit(new BigDecimal("50000.00"));
        testAgentDto2.setUsedLimit(BigDecimal.ZERO);
        testAgentDto2.setNeedApproval(true);
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/agents
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /actuaries/agents - 200 OK sa listom agenata")
    void getAgents_returnsList() throws Exception {
        when(actuaryService.getAgents(null, null, null, null))
                .thenReturn(List.of(testAgentDto, testAgentDto2));

        mockMvc.perform(get("/actuaries/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(10))
                .andExpect(jsonPath("$[0].employeeName").value("Marko Markovic"))
                .andExpect(jsonPath("$[0].employeeEmail").value("marko.markovic@banka.rs"))
                .andExpect(jsonPath("$[0].employeePosition").value("Menadzer"))
                .andExpect(jsonPath("$[0].actuaryType").value("AGENT"))
                .andExpect(jsonPath("$[0].dailyLimit").value(100000.00))
                .andExpect(jsonPath("$[0].usedLimit").value(15000.00))
                .andExpect(jsonPath("$[0].needApproval").value(false))
                .andExpect(jsonPath("$[1].employeeName").value("Jelena Jovanovic"))
                .andExpect(jsonPath("$[1].needApproval").value(true));

        verify(actuaryService).getAgents(null, null, null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents - 200 OK sa praznom listom")
    void getAgents_returnsEmptyList() throws Exception {
        when(actuaryService.getAgents(null, null, null, null))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/actuaries/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /actuaries/agents?email=marko - 200 OK filtrirano po email-u")
    void getAgents_filteredByEmail() throws Exception {
        when(actuaryService.getAgents(eq("marko"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("email", "marko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeEmail").value("marko.markovic@banka.rs"));

        verify(actuaryService).getAgents("marko", null, null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?firstName=Jelena - 200 OK filtrirano po imenu")
    void getAgents_filteredByFirstName() throws Exception {
        when(actuaryService.getAgents(isNull(), eq("Jelena"), isNull(), isNull()))
                .thenReturn(List.of(testAgentDto2));

        mockMvc.perform(get("/actuaries/agents")
                        .param("firstName", "Jelena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName").value("Jelena Jovanovic"));

        verify(actuaryService).getAgents(null, "Jelena", null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?lastName=Jovanovic - 200 OK filtrirano po prezimenu")
    void getAgents_filteredByLastName() throws Exception {
        when(actuaryService.getAgents(isNull(), isNull(), eq("Jovanovic"), isNull()))
                .thenReturn(List.of(testAgentDto2));

        mockMvc.perform(get("/actuaries/agents")
                        .param("lastName", "Jovanovic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName").value("Jelena Jovanovic"));

        verify(actuaryService).getAgents(null, null, "Jovanovic", null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?position=Menadzer - 200 OK filtrirano po poziciji")
    void getAgents_filteredByPosition() throws Exception {
        when(actuaryService.getAgents(isNull(), isNull(), isNull(), eq("Menadzer")))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("position", "Menadzer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeePosition").value("Menadzer"));

        verify(actuaryService).getAgents(null, null, null, "Menadzer");
    }

    @Test
    @DisplayName("GET /actuaries/agents?email=marko&firstName=Marko&position=Menadzer - 200 OK sa vise filtera")
    void getAgents_multipleFilters() throws Exception {
        when(actuaryService.getAgents(eq("marko"), eq("Marko"), isNull(), eq("Menadzer")))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("email", "marko")
                        .param("firstName", "Marko")
                        .param("position", "Menadzer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(actuaryService).getAgents("marko", "Marko", null, "Menadzer");
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/{employeeId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /actuaries/10 - 200 OK sa detaljima aktuara")
    void getActuaryInfo_returnsDto() throws Exception {
        when(actuaryService.getActuaryInfo(10L)).thenReturn(testAgentDto);

        mockMvc.perform(get("/actuaries/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.employeeId").value(10))
                .andExpect(jsonPath("$.employeeName").value("Marko Markovic"))
                .andExpect(jsonPath("$.employeeEmail").value("marko.markovic@banka.rs"))
                .andExpect(jsonPath("$.employeePosition").value("Menadzer"))
                .andExpect(jsonPath("$.actuaryType").value("AGENT"))
                .andExpect(jsonPath("$.dailyLimit").value(100000.00))
                .andExpect(jsonPath("$.usedLimit").value(15000.00))
                .andExpect(jsonPath("$.needApproval").value(false));

        verify(actuaryService).getActuaryInfo(10L);
    }

    @Test
    @DisplayName("GET /actuaries/999 - 404 kada zapis ne postoji")
    void getActuaryInfo_notFound_returns404() throws Exception {
        when(actuaryService.getActuaryInfo(999L))
                .thenThrow(new IllegalArgumentException(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));

        mockMvc.perform(get("/actuaries/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /actuaries/{employeeId}/limit
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /actuaries/10/limit - 200 OK (delegira u servis)")
    void updateAgentLimit_returnsOk() throws Exception {
        ActuaryInfoDto updatedDto = new ActuaryInfoDto();
        updatedDto.setId(1L);
        updatedDto.setEmployeeId(10L);
        updatedDto.setEmployeeName("Marko Markovic");
        updatedDto.setEmployeeEmail("marko.markovic@banka.rs");
        updatedDto.setActuaryType("AGENT");
        updatedDto.setDailyLimit(new BigDecimal("200000.00"));
        updatedDto.setUsedLimit(new BigDecimal("15000.00"));
        updatedDto.setNeedApproval(true);

        when(actuaryService.updateAgentLimit(eq(10L), any())).thenReturn(updatedDto);

        String payload = """
                {
                  "dailyLimit": 200000.00,
                  "needApproval": true
                }
                """;

        mockMvc.perform(patch("/actuaries/10/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(200000.00))
                .andExpect(jsonPath("$.needApproval").value(true));

        verify(actuaryService).updateAgentLimit(eq(10L), any());
    }

    @Test
    @DisplayName("PATCH /actuaries/999/limit - 404 kada agent ne postoji")
    void updateAgentLimit_notFound_returns404() throws Exception {
        when(actuaryService.updateAgentLimit(eq(999L), any()))
                .thenThrow(new IllegalArgumentException(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));

        String payload = """
                {
                  "dailyLimit": 200000.00
                }
                """;

        mockMvc.perform(patch("/actuaries/999/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /actuaries/10/limit - 403 kada caller nije supervizor")
    void updateAgentLimit_notSupervisor_returns403() throws Exception {
        when(actuaryService.updateAgentLimit(eq(10L), any()))
                .thenThrow(new IllegalStateException("Samo supervizor moze da menja limit agenta."));

        String payload = """
                {
                  "dailyLimit": 200000.00
                }
                """;

        mockMvc.perform(patch("/actuaries/10/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Samo supervizor moze da menja limit agenta."));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /actuaries/{employeeId}/reset-limit
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /actuaries/10/reset-limit - 200 OK")
    void resetUsedLimit_returnsOk() throws Exception {
        ActuaryInfoDto resetDto = new ActuaryInfoDto();
        resetDto.setId(1L);
        resetDto.setEmployeeId(10L);
        resetDto.setEmployeeName("Marko Markovic");
        resetDto.setActuaryType("AGENT");
        resetDto.setDailyLimit(new BigDecimal("100000.00"));
        resetDto.setUsedLimit(BigDecimal.ZERO);
        resetDto.setNeedApproval(false);

        when(actuaryService.resetUsedLimit(10L)).thenReturn(resetDto);

        mockMvc.perform(patch("/actuaries/10/reset-limit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedLimit").value(0))
                .andExpect(jsonPath("$.employeeName").value("Marko Markovic"));

        verify(actuaryService).resetUsedLimit(10L);
    }

    @Test
    @DisplayName("PATCH /actuaries/999/reset-limit - 404 kada agent ne postoji")
    void resetUsedLimit_notFound_returns404() throws Exception {
        when(actuaryService.resetUsedLimit(999L))
                .thenThrow(new IllegalArgumentException(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));

        mockMvc.perform(patch("/actuaries/999/reset-limit"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /actuaries/10/reset-limit - 403 kada caller nije supervizor")
    void resetUsedLimit_notSupervisor_returns403() throws Exception {
        when(actuaryService.resetUsedLimit(10L))
                .thenThrow(new IllegalStateException("Samo supervizor moze da resetuje limit."));

        mockMvc.perform(patch("/actuaries/10/reset-limit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Samo supervizor moze da resetuje limit."));
    }
}