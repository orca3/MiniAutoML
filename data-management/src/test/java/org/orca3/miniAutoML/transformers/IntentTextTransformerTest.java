package org.orca3.miniAutoML.transformers;

import org.junit.Test;
import org.orca3.miniAutoML.models.IntentText;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

public class IntentTextTransformerTest {
    @Test
    public void testIntentTextTransformer() throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        List<IntentText> result;
        try (Reader r = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/intent-1.csv")))) {
            result = IntentTextTransformer.transform(r);
        }
        assertEquals(2, result.size());
        assertEquals("I am still waiting on my credit card?", result.get(0).getUtterance());
        assertArrayEquals(new String[]{"activate_my_card", "card_arrival"}, Arrays.stream(result.get(0).getLabels()).toArray(String[]::new));
        assertEquals("I couldn’t purchase gas in Costco", result.get(1).getUtterance());
        assertArrayEquals(new String[]{"card_not_working"}, Arrays.stream(result.get(1).getLabels()).toArray(String[]::new));
    }
}
