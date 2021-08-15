package org.orca3.miniAutoML.dataManagement.transformers;

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
import io.minio.errors.MinioException;
import org.orca3.miniAutoML.dataManagement.CommitInfo;
import org.orca3.miniAutoML.dataManagement.DatasetPart;
import org.orca3.miniAutoML.dataManagement.FileInfo;
import org.orca3.miniAutoML.dataManagement.SnapshotState;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.dataManagement.models.IntentText;
import org.orca3.miniAutoML.dataManagement.models.IntentTextCollection;
import org.orca3.miniAutoML.dataManagement.models.Label;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class IntentTextTransformer implements DatasetTransformer {

    public static final String EXAMPLES_FILE_NAME = "examples.csv";
    public static final String LABELS_FILE_NAME = "labels.csv";

    static List<IntentText> ingestRawInput(Reader reader) {
        MappingStrategy<IntentText> ms = new ColumnPositionMappingStrategy<>();
        ms.setType(IntentText.class);
        CsvToBean<IntentText> cb = new CsvToBeanBuilder<IntentText>(reader)
                .withMappingStrategy(ms)
                .withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();
        return cb.parse();
    }

    static IntentTextCollection packageRawInput(List<IntentText> rawInput) {
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
                String label = entry.getKey();
                String labelId = entry.getValue();
                if (!mergedLabels.containsKey(label)) {
                    mergedLabels.put(label, Integer.toString(++labelSeed));
                }
                remap.put(labelId, mergedLabels.get(label));
            }
            for (IntentText t : collection.getTexts()) {
                String[] oldLabelIds = t.getSplicedLabels();
                String[] newLabelIds = new String[oldLabelIds.length];
                for (int j = 0; j < oldLabelIds.length; j++) {
                    newLabelIds[j] = remap.get(oldLabelIds[j]);
                }
                mergedUtterances.add(new IntentText().utterance(t.getUtterance()).labels(newLabelIds));
            }
        }

        return new IntentTextCollection().texts(mergedUtterances)
                .labels(mergedLabels);
    }

    static IntentTextCollection fromFile(Reader labelReader, Reader examplesReader) {
        MappingStrategy<IntentText> examplesMs = new ColumnPositionMappingStrategy<>();
        examplesMs.setType(IntentText.class);
        CsvToBean<IntentText> examplesCb = new CsvToBeanBuilder<IntentText>(examplesReader)
                .withMappingStrategy(examplesMs)
                .withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true).build();

        MappingStrategy<Label> labelMs = new ColumnPositionMappingStrategy<>();
        labelMs.setType(Label.class);
        CsvToBean<Label> labelCb = new CsvToBeanBuilder<Label>(labelReader)
                .withMappingStrategy(labelMs)
                .withQuoteChar('"')
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

    public CommitInfo.Builder ingest(String ingestBucket, String ingestPath, String datasetId, String commitId, String bucketName, MinioClient minioClient) throws MinioException {
        try (Reader ingestReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                .bucket(ingestBucket).object(ingestPath).build()))) {
            List<IntentText> data = IntentTextTransformer.ingestRawInput(ingestReader);
            Writer labelsWriter = new StringWriter();
            Writer examplesWriter = new StringWriter();
            IntentTextCollection commitData = IntentTextTransformer.packageRawInput(data);
            IntentTextTransformer.toFile(commitData, labelsWriter, examplesWriter);
            String commitRoot = DatasetTransformer.getCommitRoot(datasetId, commitId);
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
            return CommitInfo.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(commitId)
                    .setCreatedAt(ISO_INSTANT.format(Instant.now()))
                    .putAllStatistics(commitData.stats())
                    .setPath(commitRoot);
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public VersionedSnapshot compress(List<DatasetPart> parts, String datasetId, String versionHash, String bucketName, MinioClient minioClient) throws MinioException {
        List<IntentTextCollection> collection = Lists.newArrayListWithCapacity(parts.size());
        // Download
        for (DatasetPart part : parts) {
            try (Reader labelsReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                    .bucket(part.getBucket())
                    .object(Paths.get(part.getPathPrefix(), LABELS_FILE_NAME).toString()).build()));
                 Reader examplesReader = new InputStreamReader(minioClient.getObject(GetObjectArgs.builder()
                         .bucket(bucketName)
                         .object(Paths.get(part.getPathPrefix(), EXAMPLES_FILE_NAME).toString()).build()))) {
                collection.add(IntentTextTransformer.fromFile(labelsReader, examplesReader));
            } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
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
        String versionHashRoot = DatasetTransformer.getVersionHashRoot(datasetId, versionHash);
        String mergedExamplesPath = Paths.get(versionHashRoot, EXAMPLES_FILE_NAME).toString();
        String mergedLabelsPath = Paths.get(versionHashRoot, LABELS_FILE_NAME).toString();
        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                    .object(mergedExamplesPath)
                    .stream(new ByteArrayInputStream(examplesWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                    .contentType("text/csv")
                    .build());
            minioClient.putObject(PutObjectArgs.builder().bucket(bucketName)
                    .object(mergedLabelsPath)
                    .stream(new ByteArrayInputStream(labelsWriter.toString().getBytes(StandardCharsets.UTF_8)), -1, 10485760)
                    .contentType("text/csv")
                    .build());
        } catch (InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return VersionedSnapshot.newBuilder()
                .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.READY)
                .addParts(FileInfo.newBuilder().setName(EXAMPLES_FILE_NAME).setPath(mergedExamplesPath).setBucket(bucketName).build())
                .addParts(FileInfo.newBuilder().setName(LABELS_FILE_NAME).setPath(mergedLabelsPath).setBucket(bucketName).build())
                .putAllStatistics(mergedCollection.stats())
                .build();
    }
}
