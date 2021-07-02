package org.orca3.miniAutoML.transformers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.Test;
import org.orca3.miniAutoML.models.IntentText;
import org.orca3.miniAutoML.models.IntentTextCollection;
import org.orca3.miniAutoML.models.Label;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IntentTextTransformerTest {
    ClassLoader cl = getClass().getClassLoader();

    @Test
    public void testTransform() throws IOException {
        List<IntentText> result;
        try (Reader r = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/intent-1.csv")))) {
            result = IntentTextTransformer.transform(r);
        }
        assertEquals(2, result.size());
        assertEquals("I am still waiting on my credit card?", result.get(0).getUtterance());
        assertArrayEquals(new String[]{"activate_my_card", "card_arrival"}, result.get(0).getSplicedLabels());
        assertEquals("I couldn’t purchase gas in Costco", result.get(1).getUtterance());
        assertArrayEquals(new String[]{"card_not_working"}, result.get(1).getSplicedLabels());
    }

    @Test
    public void testRepackage() throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        List<IntentText> texts;
        try (Reader r = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/intent-1.csv")))) {
            texts = IntentTextTransformer.transform(r);
        }
        IntentTextCollection result = IntentTextTransformer.repackage(texts);
        assertEquals(3, result.getLabels().size());
        assertEquals("1", result.getLabels().get("activate_my_card"));
        assertEquals("2", result.getLabels().get("card_arrival"));
        assertEquals("3", result.getLabels().get("card_not_working"));
        assertEquals(2, result.getTexts().size());
        assertEquals("I am still waiting on my credit card?", result.getTexts().get(0).getUtterance());
        assertArrayEquals(new String[]{"1", "2"}, result.getTexts().get(0).getSplicedLabels());
        assertEquals("I couldn’t purchase gas in Costco", result.getTexts().get(1).getUtterance());
        assertArrayEquals(new String[]{"3"}, result.getTexts().get(1).getSplicedLabels());
    }

    @Test
    public void testMerge() {
        IntentTextCollection a = new IntentTextCollection()
                .labels(ImmutableMap.<String, String>builder()
                        .put("1", "foo")
                        .put("2", "bar")
                        .build())
                .texts(ImmutableList.<IntentText>builder()
                        .add(new IntentText().labels("1").utterance("sample 1"))
                        .add(new IntentText().labels("1").utterance("sample 2"))
                        .add(new IntentText().labels("1").utterance("sample 3"))
                        .add(new IntentText().labels("2").utterance("sample 4"))
                        .add(new IntentText().labels("2").utterance("sample 5"))
                        .add(new IntentText().labels("1;2").utterance("sample 6"))
                        .build());
        IntentTextCollection b = new IntentTextCollection()
                .labels(ImmutableMap.<String, String>builder()
                        .put("1", "bar")
                        .put("2", "foo")
                        .put("3", "cho")
                        .build())
                .texts(ImmutableList.<IntentText>builder()
                        .add(new IntentText().labels("1").utterance("sample 7"))
                        .add(new IntentText().labels("2").utterance("sample 8"))
                        .add(new IntentText().labels("3").utterance("sample 9"))
                        .add(new IntentText().labels("3;2").utterance("sample 10"))
                        .add(new IntentText().labels("2;1;3").utterance("sample 11"))
                        .add(new IntentText().labels("1;2").utterance("sample 12"))
                        .build());
        IntentTextCollection result = IntentTextTransformer.merge(Lists.newArrayList(a, b));
        assertArrayEquals(ImmutableList.<Label>builder()
                        .add(new Label().index("2").label("bar"))
                        .add(new Label().index("1").label("foo"))
                        .add(new Label().index("3").label("cho"))
                        .build().toArray(Label[]::new),
                result.getLabels().entrySet().stream()
                        .map(e -> new Label().index(e.getKey()).label(e.getValue()))
                        .toArray(Label[]::new));
        assertArrayEquals(ImmutableList.<IntentText>builder()
                        .add(new IntentText().labels("1").utterance("sample 1"))
                        .add(new IntentText().labels("1").utterance("sample 2"))
                        .add(new IntentText().labels("1").utterance("sample 3"))
                        .add(new IntentText().labels("2").utterance("sample 4"))
                        .add(new IntentText().labels("2").utterance("sample 5"))
                        .add(new IntentText().labels("1;2").utterance("sample 6"))
                        .add(new IntentText().labels("2").utterance("sample 7"))
                        .add(new IntentText().labels("1").utterance("sample 8"))
                        .add(new IntentText().labels("3").utterance("sample 9"))
                        .add(new IntentText().labels("3;1").utterance("sample 10"))
                        .add(new IntentText().labels("1;2;3").utterance("sample 11"))
                        .add(new IntentText().labels("2;1").utterance("sample 12"))
                        .build().toArray(IntentText[]::new),
                result.getTexts().toArray(IntentText[]::new));
    }

    @Test
    public void testFromFile() throws IOException {
        IntentTextCollection result;
        try (Reader labelReader = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/labels-1.csv")));
             Reader exampleReader = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/examples-1.csv")))) {
            result = IntentTextTransformer.fromFile(labelReader, exampleReader);
        }
        assertTestIntentTextCollection(result);
    }

    @Test
    public void testToFile() throws IOException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        IntentTextCollection data;
        try (Reader labelReader = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/labels-1.csv")));
             Reader exampleReader = new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream("datasets/examples-1.csv")))) {
            data = IntentTextTransformer.fromFile(labelReader, exampleReader);
        }
        Writer labelsWriter = new StringWriter();
        Writer examplesWriter = new StringWriter();
        IntentTextTransformer.toFile(data, labelsWriter, examplesWriter);

        // Read back and test
        Reader labelReader = new StringReader(labelsWriter.toString());
        Reader exampleReader = new StringReader(examplesWriter.toString());
        IntentTextCollection result = IntentTextTransformer.fromFile(labelReader, exampleReader);
        assertTestIntentTextCollection(result);
    }

    private void assertTestIntentTextCollection(IntentTextCollection result) {
        assertEquals(3, result.getLabels().size());
        assertEquals("activate_my_card", result.getLabels().get("0"));
        assertEquals("card_arrival", result.getLabels().get("1"));
        assertEquals("card_not_working", result.getLabels().get("2"));
        assertEquals(2, result.getTexts().size());
        assertEquals("I am still waiting on my credit card?", result.getTexts().get(0).getUtterance());
        assertArrayEquals(new String[]{"0", "1"}, result.getTexts().get(0).getSplicedLabels());
        assertEquals("I couldn’t purchase gas in Costco", result.getTexts().get(1).getUtterance());
        assertArrayEquals(new String[]{"2"}, result.getTexts().get(1).getSplicedLabels());
    }
}
