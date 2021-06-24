package org.orca3.miniAutoML;

import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.orca3.miniAutoML.models.MemoryStore;

import static org.junit.Assert.assertEquals;

public class DataManagementServiceTest {
    @ClassRule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    public static final MemoryStore store = new MemoryStore();

    @BeforeClass
    public static void beforeClass() throws Exception {
        grpcCleanup.register(InProcessServerBuilder.forName("hostname").directExecutor()
                .addService(new DataManagementService(store))
                .build().start());
    }

    @After
    public void tearDown() {
        store.clear();
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
        DatasetVersionPointer reply = updateDataset(1, 2);
        assertEquals("1", reply.getDatasetId());
        assertEquals("2", reply.getCommitId());

        reply = updateDataset(1, 3);
        assertEquals("1", reply.getDatasetId());
        assertEquals("3", reply.getCommitId());

        reply = updateDataset(1, 4);
        assertEquals("1", reply.getDatasetId());
        assertEquals("4", reply.getCommitId());

        reply = updateDataset(1, 5);
        assertEquals("1", reply.getDatasetId());
        assertEquals("5", reply.getCommitId());
    }

    private DatasetVersionPointer createDataset(int id) {
        return blockingStub.createDataset(CreateDatasetRequest.newBuilder()
                .setName(String.format("test-%s", id))
                .setDescription("test dataset")
                .setDatasetType("image")
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/001", id))
                .build());
    }

    private DatasetVersionPointer updateDataset(int datasetId, int commitId) {
        return blockingStub.updateDataset(CreateCommitRequest.newBuilder()
                .setDatasetId(Integer.toString(datasetId))
                .setCommitMessage(String.format("commit %d", commitId))
                .setUri(String.format("s3://someBucket/somePath/someObject/%s/%s", datasetId, commitId))
                .build());
    }
}
