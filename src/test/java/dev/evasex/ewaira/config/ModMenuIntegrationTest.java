package dev.evasex.ewaira.config;

import dev.evasex.ewaira.integration.EwairaModMenu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModMenuIntegrationTest {
    @Test void factoryLoadsAgainstTheSelectedModMenuApiWithoutOpeningAScreen() {
        assertNotNull(new EwairaModMenu().getModConfigScreenFactory());
    }
}
