package org.orca3.miniAutoML.training.models;

import org.orca3.miniAutoML.training.TrainRequest;
import org.orca3.miniAutoML.training.TrainingJobMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryStore {
    public SortedMap<Integer, TrainingJobMetadata> jobQueue = new TreeMap<>();
    public Map<Integer, ExecutedTrainingJob> launchingList = new HashMap<>();
    public Map<Integer, ExecutedTrainingJob> runningList = new HashMap<>();
    public Map<Integer, ExecutedTrainingJob> finalizedJobs = new HashMap<>();
    AtomicInteger jobIdSeed = new AtomicInteger();

    public int offer(TrainRequest request) {
        int jobId = jobIdSeed.incrementAndGet();
        jobQueue.put(jobId, request.getMetadata());
        return jobId;
    }

    public int getQueuePosition(int jobId) {
        return jobQueue.headMap(jobId).size();
    }
}
