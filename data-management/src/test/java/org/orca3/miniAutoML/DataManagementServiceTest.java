package org.orca3.miniAutoML;

import com.google.common.collect.Lists;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.orca3.miniAutoML.models.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class DataManagementServiceTest {
    @ClassRule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    public static final MemoryStore store = new MemoryStore();
    DataManagementServiceGrpc.DataManagementServiceBlockingStub blockingStub =
            DataManagementServiceGrpc.newBlockingStub(grpcCleanup.register(
                    InProcessChannelBuilder.forName("hostname").directExecutor().build()));

    @BeforeClass
    public static void beforeClass() throws Exception {
        Properties props = new Properties();
        props.load(DataManagementService.class.getClassLoader().getResourceAsStream("config-test.properties"));
        DataManagementService.Config config = new DataManagementService.Config(props);
        MinioClient minioClient = MinioClient.builder()
                .endpoint(config.minioHost)
                .credentials(config.minioAccessKey, config.minioSecretKey)
                .build();
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.minioBucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.minioBucketName).build());
        }
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
        DatasetVersionPointer reply = createDataset(1, Lists.newArrayList());
        assertEquals("1", reply.getDatasetId());
        assertEquals("1", reply.getCommitId());
    }

    @Test
    public void createDataset_repeatedCreation() throws Exception {
        createDataset(1, new ArrayList<>());
        createDataset(2, new ArrayList<>());
        DatasetVersionPointer reply = createDataset(3, Lists.newArrayList());
        assertEquals("3", reply.getDatasetId());
        assertEquals("1", reply.getCommitId());
    }

    @Test
    public void updateDataset_sequential() throws Exception {
        createDataset(1, new ArrayList<>());
        DatasetVersionPointer reply = updateDataset(1, 2, Lists.newArrayList(
                Tag.newBuilder().setTagKey("foo").setTagValue("bar1").build()
        ));
        assertEquals("1", reply.getDatasetId());
        assertEquals("2", reply.getCommitId());

        reply = updateDataset(1, 3, Lists.newArrayList(
                Tag.newBuilder().setTagKey("foo").setTagValue("bar1").build()
        ));
        assertEquals("1", reply.getDatasetId());
        assertEquals("3", reply.getCommitId());

        reply = updateDataset(1, 4, Lists.newArrayList(
                Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
        ));
        assertEquals("1", reply.getDatasetId());
        assertEquals("4", reply.getCommitId());

        reply = updateDataset(1, 5, Lists.newArrayList(
                Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
        ));
        assertEquals("1", reply.getDatasetId());
        assertEquals("5", reply.getCommitId());

        DatasetVersionHash fetchedDataset = blockingStub.fetchVersionedDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hashHg==", fetchedDataset.getVersionHash());

        fetchedDataset = blockingStub.fetchVersionedDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").addTags(
                        Tag.newBuilder().setTagKey("gibilish").setTagValue("gblish").build()
                ).build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hash", fetchedDataset.getVersionHash());

        fetchedDataset = blockingStub.fetchVersionedDataset(
                DatasetQuery.newBuilder().setDatasetId("1").setCommitId("4").addTags(
                        Tag.newBuilder().setTagKey("foo").setTagValue("bar2").build()
                ).build());
        assertEquals("1", fetchedDataset.getDatasetId());
        assertEquals("test-1", fetchedDataset.getName());
        assertEquals("hashEA==", fetchedDataset.getVersionHash());
    }

    private DatasetVersionPointer createDataset(int id, List<Tag> tags) {
        return blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName(String.format("test-%s", id))
                .setDescription("test dataset")
                .setDatasetType(DatasetType.GENERIC)
                .addAllTags(tags)
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/001", id))
                .build());
    }

    private DatasetVersionPointer updateDataset(int datasetId, int commitId, List<Tag> tags) {
        return blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(Integer.toString(datasetId))
                .setCommitMessage(String.format("commit %d", commitId))
                .addAllTags(tags)
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/%s", datasetId, commitId))
                .build());
    }
}
