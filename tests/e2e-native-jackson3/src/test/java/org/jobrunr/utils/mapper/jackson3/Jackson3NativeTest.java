package org.jobrunr.utils.mapper.jackson3;

import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.states.EnqueuedState;
import org.jobrunr.jobs.states.FailedState;
import org.jobrunr.jobs.states.JobState;
import org.jobrunr.jobs.states.StateName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraalVM Native Image tests for Jackson 3 serialization/deserialization.
 * <p>
 * On JVM (HotSpot) all tests pass. On GraalVM Native Image, Jackson 3's MethodHandle-based
 * field setter converts {@code Boolean} to {@code Integer} when setting a primitive {@code boolean}
 * field, causing {@code IllegalArgumentException: Can not set boolean field ... to java.lang.Integer}.
 */
class Jackson3NativeTest {

    private Jackson3JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = new Jackson3JsonMapper();
    }

    @Test
    void testRoundTripFailedStateWithDoNotRetryTrue() {
        JobRunrException exception = JobRunrException.problematicConfigurationException("Problematic config");
        FailedState originalState = new FailedState("Job failed", exception);

        String json = jsonMapper.serialize(originalState);
        FailedState deserialized = jsonMapper.deserialize(json, FailedState.class);

        assertThat(deserialized.mustNotRetry()).isTrue();
    }

    @Test
    void testRoundTripFailedStateWithDoNotRetryFalse() {
        IllegalStateException exception = new IllegalStateException("Something went wrong");
        FailedState originalState = new FailedState("Job failed", exception);

        String json = jsonMapper.serialize(originalState);
        FailedState deserialized = jsonMapper.deserialize(json, FailedState.class);

        assertThat(deserialized.mustNotRetry()).isFalse();
    }

    @Test
    void testDeserializeFailedStateFromJsonWithDoNotRetryTrue() {
        String json = """
                {
                  "@class": "org.jobrunr.jobs.states.FailedState",
                  "state": "FAILED",
                  "createdAt": "2024-01-15T10:30:00Z",
                  "message": "Job failed",
                  "exceptionType": "org.jobrunr.JobRunrException",
                  "exceptionMessage": "Problematic config",
                  "stackTrace": "org.jobrunr.JobRunrException: Problematic config",
                  "doNotRetry": true
                }
                """;

        FailedState deserialized = jsonMapper.deserialize(json, FailedState.class);

        assertThat(deserialized.mustNotRetry()).isTrue();
        assertThat(deserialized.getMessage()).isEqualTo("Job failed");
        assertThat(deserialized.getExceptionType()).isEqualTo("org.jobrunr.JobRunrException");
    }

    @Test
    void testDeserializeFailedStateFromJsonWithDoNotRetryFalse() {
        String json = """
                {
                  "@class": "org.jobrunr.jobs.states.FailedState",
                  "state": "FAILED",
                  "createdAt": "2024-01-15T10:30:00Z",
                  "message": "Job failed",
                  "exceptionType": "java.lang.IllegalStateException",
                  "exceptionMessage": "Something went wrong",
                  "stackTrace": "java.lang.IllegalStateException: Something went wrong",
                  "doNotRetry": false
                }
                """;

        FailedState deserialized = jsonMapper.deserialize(json, FailedState.class);

        assertThat(deserialized.mustNotRetry()).isFalse();
    }

    @Test
    void testSerializedFailedStateContainsDoNotRetryField() {
        JobRunrException problematicException = JobRunrException.problematicConfigurationException("Problematic config");
        FailedState doNotRetryState = new FailedState("Job failed", problematicException);
        String doNotRetryJson = jsonMapper.serialize(doNotRetryState);
        assertThat(doNotRetryJson).contains("\"doNotRetry\":true");

        IllegalStateException normalException = new IllegalStateException("Normal failure");
        FailedState retryableState = new FailedState("Job failed", normalException);
        String retryableJson = jsonMapper.serialize(retryableState);
        assertThat(retryableJson).contains("\"doNotRetry\":false");
    }

    @Test
    void testRoundTripJobWithFailedStateDoNotRetryTrue() {
        var jobDetails = new JobDetails("org.example.MyService", null, "doWork", List.of(new JobParameter(String.class, "hello")));
        var failedState = new FailedState("Job failed", JobRunrException.problematicConfigurationException("Problematic config"));
        var jobHistory = new ArrayList<JobState>(List.of(new EnqueuedState(), failedState));
        var job = new Job(UUID.randomUUID(), 0, jobDetails, jobHistory, new ConcurrentHashMap<>());

        String json = jsonMapper.serialize(job);
        Job deserialized = jsonMapper.deserialize(json, Job.class);

        FailedState deserializedState = (FailedState) deserialized.getJobState();
        assertThat(deserializedState.mustNotRetry()).isTrue();
        assertThat(deserialized.getJobDetails().getClassName()).isEqualTo("org.example.MyService");
    }

    @Test
    void testRoundTripJobWithFailedStateDoNotRetryFalse() {
        var jobDetails = new JobDetails("org.example.MyService", null, "doWork", List.of(new JobParameter(String.class, "hello")));
        var failedState = new FailedState("Job failed", new IllegalStateException("Something went wrong"));
        var jobHistory = new ArrayList<JobState>(List.of(new EnqueuedState(), failedState));
        var job = new Job(UUID.randomUUID(), 0, jobDetails, jobHistory, new ConcurrentHashMap<>());

        String json = jsonMapper.serialize(job);
        Job deserialized = jsonMapper.deserialize(json, Job.class);

        FailedState deserializedState = (FailedState) deserialized.getJobState();
        assertThat(deserializedState.mustNotRetry()).isFalse();
    }
}
