package me.lewisblackburn.metabase.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOk() throws Exception {
        // Given the health endpoint is available.
        String endpoint = "/health";

        // When a client requests the health endpoint.
        ResultActions response = mockMvc.perform(get(endpoint));

        // Then the endpoint reports a successful response.
        response.andExpect(status().isOk()).andExpect(content().string("OK"));
    }
}
