package org.orca3.miniAutoML;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class DataManagementServiceTest {
    @SuppressWarnings("rawtypes")
    @ClassRule
    public static GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:6"))
            .withExposedPorts(6379);

    @ClassRule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    private static Jedis jedis;

    @BeforeClass
    public static void beforeClass() throws Exception {
        jedis = new Jedis(redis.getHost(), redis.getFirstMappedPort());
        grpcCleanup.register(InProcessServerBuilder.forName("hostname").directExecutor()
                .addService(new DataManagementService(jedis))
                .build().start());
    }

    @After
    public void tearDown() {
        jedis.flushAll();
    }

    DataManagementServiceGrpc.DataManagementServiceBlockingStub blockingStub =
            DataManagementServiceGrpc.newBlockingStub(grpcCleanup.register(
                    InProcessChannelBuilder.forName("hostname").directExecutor().build()));

    @Test
    public void createDataset_simpleCreation() throws Exception {
        DatasetVersionPointer reply = createDataset(1);
        assertEquals("1", reply.getDatasetId());
        assertEquals("1", reply.getCommitId());
    }

    @Test
    public void createDataset_repeatedCreation() throws Exception {
        createDataset(1);
        createDataset(2);
        DatasetVersionPointer reply = createDataset(3);
        assertEquals("3", reply.getDatasetId());
        assertEquals("1", reply.getCommitId());
    }

    @Test
    public void updateDataset_sequential() throws Exception {
        createDataset(1);
        DatasetVersionPointer reply = updateDataset(1, 1, CommitType.APPEND);
        assertEquals("1", reply.getDatasetId());
        assertEquals("2", reply.getCommitId());

        reply = updateDataset(1, 2, CommitType.APPEND);
        assertEquals("1", reply.getDatasetId());
        assertEquals("3", reply.getCommitId());

        reply = updateDataset(1, 3, CommitType.OVERWRITE);
        assertEquals("1", reply.getDatasetId());
        assertEquals("4", reply.getCommitId());

        reply = updateDataset(1, 4, CommitType.APPEND);
        assertEquals("1", reply.getDatasetId());
        assertEquals("5", reply.getCommitId());
    }

    @Test
    public void updateDataset_rejectWrongParent() {
        createDataset(1);
        DatasetVersionPointer reply = updateDataset(1, 1, CommitType.APPEND);
        assertEquals("1", reply.getDatasetId());
        assertEquals("2", reply.getCommitId());

        reply = updateDataset(1, 2, CommitType.APPEND);
        assertEquals("1", reply.getDatasetId());
        assertEquals("3", reply.getCommitId());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                updateDataset(1, 2, CommitType.OVERWRITE));
        assertEquals(Status.INVALID_ARGUMENT.getCode(), e.getStatus().getCode());
    }

    private DatasetVersionPointer createDataset(int id) {
        return blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName(String.format("test-%s", id))
                .setDescription("test dataset")
                .setDatasetType("image")
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/001", id))
                .build());
    }

    private DatasetVersionPointer updateDataset(int datasetId, int parentCommitId, CommitType commitType) {
        return blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(Integer.toString(datasetId))
                .setParentCommitId(Integer.toString(parentCommitId))
                .setCommitMessage(String.format("commit %d", parentCommitId + 1))
                .setCommitType(commitType)
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/%s", datasetId, parentCommitId + 1))
                .build());
    }
}
