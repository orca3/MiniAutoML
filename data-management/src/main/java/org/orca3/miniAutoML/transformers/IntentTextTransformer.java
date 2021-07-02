package org.orca3.miniAutoML.transformers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.MappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.orca3.miniAutoML.DatasetPart;
import org.orca3.miniAutoML.models.IntentText;
import org.orca3.miniAutoML.models.IntentTextCollection;
import org.orca3.miniAutoML.models.Label;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IntentTextTransformer {

    public static final String EXAMPLES_FILE_NAME = "examples.csv";
    public static final String LABELS_FILE_NAME = "labels.csv";

    static List<IntentText> transform(Reader reader) {
        MappingStrategy<IntentText> ms = new ColumnPositionMappingStrategy<>();
        ms.setType(IntentText.class);
        CsvToBean<IntentText> cb = new CsvToBeanBuilder<IntentText>(reader)
                .withMappingStrategy(ms)
                .withIgnoreQuotations(true).withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();
        return cb.parse();
    }

    static IntentTextCollection repackage(List<IntentText> rawInput) {
        Map<String, String> indexedLabels = new HashMap<>();
        int labelCount = 0;
        List<IntentText> repackagedIntentText = new ArrayList<>();
        for (IntentText t : rawInput) {
            IntentText d = new IntentText().utterance(t.getUtterance());
            String[] labels = t.getSplicedLabels();
            String[] labelIndexes = new String[labels.length];
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i];
                if (indexedLabels.containsKey(label)) {
                    labelIndexes[i] = indexedLabels.get(label);
                } else {
                    labelIndexes[i] = Integer.toString(++labelCount);
                    indexedLabels.put(label, labelIndexes[i]);
                }
            }
            d.labels(labelIndexes);
            repackagedIntentText.add(d);
        }
        return new IntentTextCollection().labels(indexedLabels)
                .texts(repackagedIntentText);
    }

    static IntentTextCollection merge(List<IntentTextCollection> collections) {
        if (collections.isEmpty()) {
            return new IntentTextCollection();
        }
        if (collections.size() == 1) {
            return collections.get(0);
        }
        List<IntentText> mergedUtterances = Lists.newArrayList();
        Map<String, String> mergedLabels = Maps.newHashMap();
        int labelSeed = 0;

        for (IntentTextCollection collection : collections) {
            Map<String, String> remap = Maps.newHashMap();
            for (Map.Entry<String, String> entry : collection.getLabels().entrySet()) {
                if (!mergedLabels.containsKey(entry.getValue())) {
                    mergedLabels.put(entry.getValue(), Integer.toString(++labelSeed));
                }
                remap.put(entry.getKey(), mergedLabels.get(entry.getValue()));
            }
            for (IntentText t : collection.getTexts()) {
                String[] oldLabels = t.getSplicedLabels();
                String[] newLabels = new String[oldLabels.length];
                for (int j = 0; j < oldLabels.length; j++) {
                    newLabels[j] = remap.get(oldLabels[j]);
                }
                mergedUtterances.add(new IntentText().utterance(t.getUtterance()).labels(newLabels));
            }
        }

        ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
        mergedLabels.forEach((k, v) -> b.put(v, k));
        return new IntentTextCollection().texts(mergedUtterances)
                .labels(b.build());
    }

    static IntentTextCollection fromFile(Reader labelReader, Reader examplesReader) {
        MappingStrategy<IntentText> examplesMs = new ColumnPositionMappingStrategy<>();
        examplesMs.setType(IntentText.class);
        CsvToBean<IntentText> examplesCb = new CsvToBeanBuilder<IntentText>(examplesReader)
                .withMappingStrategy(examplesMs)
                .withIgnoreQuotations(true).withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();

        MappingStrategy<Label> labelMs = new ColumnPositionMappingStrategy<>();
        labelMs.setType(Label.class);
        CsvToBean<Label> labelCb = new CsvToBeanBuilder<Label>(labelReader)
                .withMappingStrategy(labelMs)
                .withIgnoreQuotations(true).withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();

        return new IntentTextCollection().texts(examplesCb.parse())
                .labels(labelCb.parse().stream()
                        .collect(Collectors.toMap(Label::getIndex, Label::getLabel)));
    }

    static void toFile(IntentTextCollection data, Writer labelWriter, Writer exampleWriter) throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        MappingStrategy<IntentText> ms = new ColumnPositionMappingStrategy<>();
        ms.setType(IntentText.class);

        StatefulBeanToCsv<IntentText> beanWriter =
                new StatefulBeanToCsvBuilder<IntentText>(exampleWriter)
                        .withMappingStrategy(ms).build();

        // Write list to StatefulBeanToCsv object
        beanWriter.write(data.getTexts());

        MappingStrategy<Label> labelMs = new ColumnPositionMappingStrategy<>();
        labelMs.setType(Label.class);

        StatefulBeanToCsv<Label> labelBeanWriter =
                new StatefulBeanToCsvBuilder<Label>(labelWriter)
                        .withMappingStrategy(labelMs).build();
        labelBeanWriter.write(data.getLabels().entrySet().stream()
                .map(entry -> new Label().index(entry.getKey()).label(entry.getValue()))
                .collect(Collectors.toList()));
    }

    public static String ingest(String ingestUri, String datasetId, String commitId, String bucketName, MinioClient minioClient) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        URI uri = URI.create(ingestUri);
        try (Reader ingestReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName).object(uri.getPath()).build()))) {
            List<IntentText> data = IntentTextTransformer.transform(ingestReader);
            Writer labelsWriter = new StringWriter();
            Writer examplesWriter = new StringWriter();
            IntentTextTransformer.toFile(IntentTextTransformer.repackage(data), labelsWriter, examplesWriter);
            String commitRoot = Paths.get("dataset", datasetId, "commit", commitId).toString();
            minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                    .object(Paths.get(commitRoot, LABELS_FILE_NAME).toString())
                    .stream(new ByteArrayInputStream(labelsWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                    .contentType("text/csv")
                    .build());
            minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                    .object(Paths.get(commitRoot, EXAMPLES_FILE_NAME).toString())
                    .stream(new ByteArrayInputStream(examplesWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                    .contentType("text/csv")
                    .build());
            return commitRoot;
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }
    }

    public static void compress(List<DatasetPart> parts, String jobId, String bucketName, MinioClient minioClient) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        List<IntentTextCollection> collection = Lists.newArrayListWithCapacity(parts.size());
        // Download
        for (DatasetPart part : parts) {
            URI uri = URI.create(part.getUri());
            try (Reader labelsReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(Paths.get(uri.getPath(), LABELS_FILE_NAME).toString()).build()));
                 Reader examplesReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                         .bucket(bucketName)
                         .object(Paths.get(uri.getPath(), EXAMPLES_FILE_NAME).toString()).build()))) {
                collection.add(IntentTextTransformer.fromFile(labelsReader, examplesReader));
            }
        }
        // Merge locally
        IntentTextCollection mergedCollection = IntentTextTransformer.merge(collection);
        // Upload
        Writer labelsWriter = new StringWriter();
        Writer examplesWriter = new StringWriter();
        try {
            IntentTextTransformer.toFile(mergedCollection, labelsWriter, examplesWriter);
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }
        minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                .object(Paths.get("datasetView", jobId, EXAMPLES_FILE_NAME).toString())
                .stream(new ByteArrayInputStream(labelsWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                .contentType("text/csv")
                .build());
        minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                .object(Paths.get("datasetView", jobId, LABELS_FILE_NAME).toString())
                .stream(new ByteArrayInputStream(examplesWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                .contentType("text/csv")
                .build());
    }
}
