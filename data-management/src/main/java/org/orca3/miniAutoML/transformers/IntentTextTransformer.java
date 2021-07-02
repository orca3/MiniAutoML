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
import io.minio.DownloadObjectArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.orca3.miniAutoML.DatasetDetails;
import org.orca3.miniAutoML.DatasetPart;
import org.orca3.miniAutoML.models.IntentText;
import org.orca3.miniAutoML.models.IntentTextCollection;
import org.orca3.miniAutoML.models.Label;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
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

        for (int i = 0; i < collections.size(); i++) {
            Map<String, String> remap = Maps.newHashMap();
            for (Map.Entry<String, String> entry : collections.get(i).getLabels().entrySet()) {
                if (!mergedLabels.containsKey(entry.getValue())) {
                    mergedLabels.put(entry.getValue(), Integer.toString(++labelSeed));
                }
                remap.put(entry.getKey(), mergedLabels.get(entry.getValue()));
            }
            for (IntentText t : collections.get(i).getTexts()) {
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

    public static void compress(DatasetDetails details, String tmpdir, String jobId, String bucketName, MinioClient minioClient) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        List<IntentTextCollection> collection = Lists.newArrayListWithCapacity(details.getPartsList().size());
        // Download
        for (int i = 0; i < details.getPartsList().size(); i++) {
            DatasetPart part = details.getParts(i);
            URI uri = URI.create(part.getUri());
            String exampleFile = Paths.get(tmpdir, Integer.toString(i), EXAMPLES_FILE_NAME).toString();
            String labelFile = Paths.get(tmpdir, Integer.toString(i), LABELS_FILE_NAME).toString();
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(uri.getHost())
                    .object(Paths.get(uri.getPath(), EXAMPLES_FILE_NAME).toString())
                    .filename(exampleFile).build());
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(uri.getHost())
                    .object(Paths.get(uri.getPath(), LABELS_FILE_NAME).toString())
                    .filename(labelFile).build());
            try (Reader labelsReader = new FileReader(labelFile);
                 Reader examplesReader = new FileReader(exampleFile)) {
                collection.add(IntentTextTransformer.fromFile(labelsReader, examplesReader));
            }
        }
        // Merge locally
        IntentTextCollection mergedCollection = IntentTextTransformer.merge(collection);
        // Upload
        String mergedExamplesFile = Paths.get(tmpdir, "merged", EXAMPLES_FILE_NAME).toString();
        String mergedLabelsFile = Paths.get(tmpdir, "merged", LABELS_FILE_NAME).toString();
        try (Writer labelsWriter = new FileWriter(mergedLabelsFile);
             Writer examplesWriter = new FileWriter(mergedExamplesFile)) {
            IntentTextTransformer.toFile(mergedCollection, labelsWriter, examplesWriter);
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }
        minioClient.uploadObject(
                UploadObjectArgs.builder().bucket(bucketName)
                        .object(String.format("datasets/%s/examples.csv", jobId))
                        .filename(mergedExamplesFile).contentType("text/csv").build());
        minioClient.uploadObject(
                UploadObjectArgs.builder().bucket(bucketName)
                        .object(String.format("datasets/%s/labels.csv", jobId))
                        .filename(mergedLabelsFile).contentType("text/csv").build());
    }
}
