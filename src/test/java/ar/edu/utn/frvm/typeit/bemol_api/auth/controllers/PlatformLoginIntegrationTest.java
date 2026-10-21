package ar.edu.utn.frvm.typeit.bemol_api.auth.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@IntegrationTest
class PlatformLoginIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Should login platform admin with security filters enabled")
  void shouldLoginPlatformAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "admin@plataforma.com",
                      "password": "admin123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.email").value("admin@plataforma.com"))
        .andExpect(jsonPath("$.account.name").value("Administrador"))
        .andExpect(jsonPath("$.account.lastName").value("Plataforma"))
        .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty());
  }
}
