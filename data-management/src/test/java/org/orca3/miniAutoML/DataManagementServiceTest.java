package org.orca3.miniAutoML;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.orca3.miniAutoML.models.MemoryStore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.orca3.miniAutoML.transformers.IntentTextTransformer.EXAMPLES_FILE_NAME;
import static org.orca3.miniAutoML.transformers.IntentTextTransformer.LABELS_FILE_NAME;

public class DataManagementServiceTest {
    static final ClassLoader cl = DataManagementServiceTest.class.getClassLoader();

    static {
        Properties props = new Properties();
        try {
            props.load(DataManagementService.class.getClassLoader().getResourceAsStream("config-test.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config = new DataManagementService.Config(props);

    }

    @ClassRule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    public static final DockerImageName MINIO_IMAGE = DockerImageName.parse("minio/minio");
    public static final DataManagementService.Config config;

    @ClassRule
    public static GenericContainer<?> minio =
            new GenericContainer<>(MINIO_IMAGE).withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", config.minioAccessKey)
                    .withEnv("MINIO_ROOT_PASSWORD", config.minioSecretKey)
                    .withCommand("server", "/data");

    public static final MemoryStore store = new MemoryStore();
    public static MinioClient minioClient;
    public static String minioBucketName;
    DataManagementServiceGrpc.DataManagementServiceBlockingStub blockingStub =
            DataManagementServiceGrpc.newBlockingStub(grpcCleanup.register(
                    InProcessChannelBuilder.forName("hostname").directExecutor().build()));

    @BeforeClass
    public static void beforeClass() throws Exception {
        Properties props = new Properties();
        props.load(DataManagementService.class.getClassLoader().getResourceAsStream("config-test.properties"));
        DataManagementService.Config config = new DataManagementService.Config(props);
        minioClient = MinioClient.builder()
                .endpoint(String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000)))
                .credentials(config.minioAccessKey, config.minioSecretKey)
                .build();
        minioBucketName = config.minioBucketName;
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.minioBucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.minioBucketName).build());
        }
        for (String fileName : ImmutableList.of("foo", "bar", "coo", "dzz", "ell"))
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName).object("ingest/genericDatasets/" + fileName)
                    .stream(cl.getResourceAsStream("genericDatasets/" + fileName), -1, 5 * 1024 * 1024).build());

        grpcCleanup.register(InProcessServerBuilder.forName("hostname").directExecutor()
                .addService(new DataManagementService(store, minioClient, config))
                .build().start());
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void createDataset_simpleCreation() throws Exception {
        String path = "ingest/genericDatasets/foo";
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioBucketName).object(path)
                .stream(cl.getResourceAsStream("genericDatasets/foo"), -1, 5 * 1024 * 1024).build());
        DatasetSummary reply = blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName("test-1").setDescription("test dataset").setDatasetType(DatasetType.GENERIC)
                .setBucket(minioBucketName).setPath(path).build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(1, reply.getCommitsCount());
    }

    @Test
    public void createDataset_repeatedCreation() throws Exception {
        DatasetSummary reply = null;
        for (int i = 0; i < 3; i++) {
            reply = blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                    .setName("test-1").setDescription("test dataset").setDatasetType(DatasetType.GENERIC)
                    .setBucket(minioBucketName).setPath("ingest/genericDatasets/foo").build());
        }
        assertEquals("3", reply.getDatasetId());
        assertEquals(1, reply.getCommitsCount());
    }

    @Test
    public void updateDataset_sequential() throws Exception {
        DatasetSummary reply = blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName("test-1")
                .setDescription("test dataset")
                .setDatasetType(DatasetType.GENERIC)
                .setBucket(minioBucketName).setPath("ingest/genericDatasets/foo").build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(1, reply.getCommitsCount());

        reply = blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(reply.getDatasetId())
                .addAllTags(Lists.newArrayList(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar1").build()
                ))
                .setBucket(minioBucketName).setPath("ingest/genericDatasets/bar")
                .build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(2, reply.getCommitsCount());

        reply = blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(reply.getDatasetId())
                .addAllTags(Lists.newArrayList(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar1").build()
                ))
                .setBucket(minioBucketName).setPath("ingest/genericDatasets/coo")
                .build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(3, reply.getCommitsCount());

        reply = blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(reply.getDatasetId())
                .addAllTags(Lists.newArrayList(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
                ))
                .setBucket(minioBucketName).setPath("ingest/genericDatasets/dzz")
                .build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(4, reply.getCommitsCount());

        reply = blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(reply.getDatasetId())
                .addAllTags(Lists.newArrayList(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
                ))
                .setBucket(minioBucketName).setPath("ingest/genericDatasets/ell")
                .build());
        assertEquals("1", reply.getDatasetId());
        assertEquals(5, reply.getCommitsCount());

        DatasetVersionHash fetchedDataset = blockingStub.prepareTrainingDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hashHg==", fetchedDataset.getVersionHash());

        fetchedDataset = blockingStub.prepareTrainingDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").addTags(
                        Tag.newBuilder().setTagKey("gibilish").setTagValue("gblish").build()
                ).build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hash", fetchedDataset.getVersionHash());

        fetchedDataset = blockingStub.prepareTrainingDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").addTags(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
                ).build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hashEA==", fetchedDataset.getVersionHash());
    }

    @Test
    public void datasetSnapshot() throws Exception {
        for (String f : ImmutableList.of("test.csv", "train.csv", "validation.csv")) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object("ingest/" + f)
                    .stream(cl.getResourceAsStream("datasets/" + f), -1, 5 * 1024 * 1024).build());
        }
        String datasetId = blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName("Dataset Snapshot Test")
                .setDescription("test dataset")
                .setDatasetType(DatasetType.TEXT_INTENT)
                .setBucket(minioBucketName)
                .setPath("ingest/train.csv")
                .build()).getDatasetId();
        blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(datasetId)
                .setCommitMessage("test data")
                .setBucket(minioBucketName)
                .setPath("ingest/test.csv")
                .build());
        blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(datasetId)
                .setCommitMessage("validation data")
                .setBucket(minioBucketName)
                .setPath("ingest/validation.csv")
                .build());
        DatasetVersionHash fetchedDatasetVersionHash = blockingStub.prepareTrainingDataset(
                DatasetQuery.newBuilder().setDatasetId(datasetId).build());
        String versionHash = fetchedDatasetVersionHash.getVersionHash();
        int iteration = 0;
        VersionHashDataset result = blockingStub.fetchTrainingDataset(VersionHashQuery.newBuilder()
                .setDatasetId(datasetId).setVersionHash(versionHash).build());
        while (result.getState() == SnapshotState.RUNNING && ++iteration < 20) {
            Thread.sleep(1000);
            result = blockingStub.fetchTrainingDataset(VersionHashQuery.newBuilder()
                    .setDatasetId(datasetId).setVersionHash(versionHash).build());
        }
        assertEquals(SnapshotState.READY, result.getState());
        assertEquals(2, result.getPartsCount());
        assertEquals(EXAMPLES_FILE_NAME, result.getParts(0).getName());
        assertEquals(minioBucketName, result.getParts(0).getBucket());
        assertEquals("versionedDatasets/1/hashDg==/examples.csv", result.getParts(0).getPath());
        assertEquals(LABELS_FILE_NAME, result.getParts(1).getName());
        assertEquals(minioBucketName, result.getParts(1).getBucket());
        assertEquals("versionedDatasets/1/hashDg==/labels.csv", result.getParts(1).getPath());
    }
}
