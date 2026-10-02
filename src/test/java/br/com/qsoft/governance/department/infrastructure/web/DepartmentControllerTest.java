package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.application.CreateDepartment;
import br.com.qsoft.governance.department.application.CreateDepartmentCommand;
import br.com.qsoft.governance.department.application.DeleteDepartment;
import br.com.qsoft.governance.department.application.GetDepartment;
import br.com.qsoft.governance.department.application.ListDepartments;
import br.com.qsoft.governance.department.application.UpdateDepartment;
import br.com.qsoft.governance.department.application.UpdateDepartmentCommand;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepartmentControllerTest {

    private CreateDepartment createDepartment;
    private UpdateDepartment updateDepartment;
    private DeleteDepartment deleteDepartment;
    private GetDepartment getDepartment;
    private ListDepartments listDepartments;
    private MockMvc mockMvc;

    private final TenantId tenantId = TenantId.random();

    @BeforeEach
    void setUp() {
        createDepartment = mock(CreateDepartment.class);
        updateDepartment = mock(UpdateDepartment.class);
        deleteDepartment = mock(DeleteDepartment.class);
        getDepartment = mock(GetDepartment.class);
        listDepartments = mock(ListDepartments.class);

        DepartmentController controller = new DepartmentController(
            createDepartment, updateDepartment, deleteDepartment, getDepartment, listDepartments);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new DepartmentApiExceptionHandler())
            .build();

        TenantContextHolder.set(new TenantContext(tenantId, null));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_listar_departamentos_sem_filtros() throws Exception {
        Department dept = Department.create(UUID.randomUUID(), tenantId, "Engenharia", "Depto de Eng", UUID.randomUUID(), new BigDecimal("10000.00"));
        when(listDepartments.list(tenantId, null, null)).thenReturn(List.of(dept));

        mockMvc.perform(get("/api/v1/departamentos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(dept.id().toString()))
            .andExpect(jsonPath("$[0].tenantId").value(tenantId.value().toString()))
            .andExpect(jsonPath("$[0].name").value("Engenharia"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(listDepartments).list(tenantId, null, null);
    }

    @Test
    void deve_listar_departamentos_com_filtros() throws Exception {
        Department dept = Department.create(UUID.randomUUID(), tenantId, "Engenharia", "Depto de Eng", UUID.randomUUID(), new BigDecimal("10000.00"));
        when(listDepartments.list(tenantId, "Eng", DepartmentStatus.ACTIVE)).thenReturn(List.of(dept));

        mockMvc.perform(get("/api/v1/departamentos")
                .param("search", "Eng")
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(dept.id().toString()))
            .andExpect(jsonPath("$[0].name").value("Engenharia"));

        verify(listDepartments).list(tenantId, "Eng", DepartmentStatus.ACTIVE);
    }

    @Test
    void deve_criar_departamento_com_sucesso() throws Exception {
        UUID leaderId = UUID.randomUUID();
        Department dept = Department.create(UUID.randomUUID(), tenantId, "Produtos", "Depto de Prod", leaderId, new BigDecimal("5000.00"));
        when(createDepartment.create(any(CreateDepartmentCommand.class))).thenReturn(dept);

        String payload = String.format("""
            {
              "name": "Produtos",
              "description": "Depto de Prod",
              "leaderUserId": "%s",
              "monthlyBudget": 5000.00
            }
            """, leaderId);

        mockMvc.perform(post("/api/v1/departamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(dept.id().toString()))
            .andExpect(jsonPath("$.name").value("Produtos"))
            .andExpect(jsonPath("$.monthlyBudget").value(5000.00));

        verify(createDepartment).create(any(CreateDepartmentCommand.class));
    }

    @Test
    void deve_retornar_bad_request_ao_criar_departamento_invalido() throws Exception {
        // Nome em branco e orçamento negativo
        String payload = """
            {
              "name": "",
              "monthlyBudget": -100.00
            }
            """;

        mockMvc.perform(post("/api/v1/departamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deve_buscar_departamento_por_id_com_sucesso() throws Exception {
        UUID id = UUID.randomUUID();
        Department dept = Department.create(id, tenantId, "Design", "Depto de UI/UX", null, new BigDecimal("3000.00"));
        when(getDepartment.get(tenantId, id)).thenReturn(dept);

        mockMvc.perform(get("/api/v1/departamentos/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Design"));

        verify(getDepartment).get(tenantId, id);
    }

    @Test
    void deve_atualizar_departamento_com_sucesso() throws Exception {
        UUID id = UUID.randomUUID();
        Department dept = Department.create(id, tenantId, "Design Atualizado", "Novo desc", null, new BigDecimal("4000.00"));
        when(updateDepartment.update(any(UpdateDepartmentCommand.class))).thenReturn(dept);

        String payload = """
            {
              "name": "Design Atualizado",
              "description": "Novo desc",
              "monthlyBudget": 4000.00
            }
            """;

        mockMvc.perform(put("/api/v1/departamentos/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Design Atualizado"));

        verify(updateDepartment).update(any(UpdateDepartmentCommand.class));
    }

    @Test
    void deve_deletar_departamento_com_sucesso() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(deleteDepartment).delete(tenantId, id);

        mockMvc.perform(delete("/api/v1/departamentos/" + id))
            .andExpect(status().isNoContent());

        verify(deleteDepartment).delete(tenantId, id);
    }

    @Test
    void deve_retornar_unauthorized_quando_contexto_de_tenant_estiver_ausente() throws Exception {
        TenantContextHolder.clear();

        mockMvc.perform(get("/api/v1/departamentos"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    // --- DTO Direct Tests ---

    @Test
    void deve_testar_department_request_e_response_records() {
        UUID leaderId = UUID.randomUUID();
        BigDecimal budget = new BigDecimal("7500.00");
        DepartmentRequest request = new DepartmentRequest("RH", "Recursos Humanos", leaderId, budget);

        assertThat(request.name()).isEqualTo("RH");
        assertThat(request.description()).isEqualTo("Recursos Humanos");
        assertThat(request.leaderUserId()).isEqualTo(leaderId);
        assertThat(request.monthlyBudget()).isEqualTo(budget);

        Department dept = Department.create(UUID.randomUUID(), tenantId, "RH", "Recursos Humanos", leaderId, budget);
        DepartmentResponse response = DepartmentResponse.from(dept);

        assertThat(response.id()).isEqualTo(dept.id());
        assertThat(response.tenantId()).isEqualTo(tenantId.value());
        assertThat(response.name()).isEqualTo("RH");
        assertThat(response.description()).isEqualTo("Recursos Humanos");
        assertThat(response.leaderUserId()).isEqualTo(leaderId);
        assertThat(response.monthlyBudget()).isEqualTo(budget);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(dept.createdAt());
        assertThat(response.updatedAt()).isEqualTo(dept.updatedAt());
    }
}
