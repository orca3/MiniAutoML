package org.orca3.miniAutoML.transformers;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.MappingStrategy;
import org.orca3.miniAutoML.models.IntentText;

import java.io.Reader;
import java.util.List;

public class IntentTextTransformer {

    public static List<IntentText> transform(Reader reader) {
        MappingStrategy<IntentText> ms = new ColumnPositionMappingStrategy<>();
        ms.setType(IntentText.class);
        CsvToBean<IntentText> cb = new CsvToBeanBuilder<IntentText>(reader)
                .withMappingStrategy(ms)
                .withIgnoreQuotations(true).withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();
        return cb.parse();
    }
}
