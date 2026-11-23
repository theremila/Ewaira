package remila.sex.cirrus.config;

import remila.sex.cirrus.integration.CirrusModMenu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModMenuIntegrationTest {
    @Test void factoryLoadsAgainstTheSelectedModMenuApiWithoutOpeningAScreen() {
        assertNotNull(new CirrusModMenu().getModConfigScreenFactory());
    }
}
