package io.github.xiaoailazy.coexistree.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for span_events collection mode.
 *
 * Modes:
 * - keypoints (default): only record milestone events
 * - batched: accumulate delta batches in time windows
 * - full: every chunk persisted (debug only)
 */
@Component
@ConfigurationProperties(prefix = "observability.span-events")
public class SpanEventCollectionConfig {

    /** keypoints | batched | full */
    private String mode = "keypoints";

    /** Batch flush interval in ms (batched mode) */
    private long batchWindowMs = 3000;

    /** Max characters per delta batch (batched mode) */
    private int batchMaxChars = 500;

    /** Persist failed runs in full mode for debugging */
    private boolean persistFailedRunsFull = true;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public long getBatchWindowMs() { return batchWindowMs; }
    public void setBatchWindowMs(long batchWindowMs) { this.batchWindowMs = batchWindowMs; }
    public int getBatchMaxChars() { return batchMaxChars; }
    public void setBatchMaxChars(int batchMaxChars) { this.batchMaxChars = batchMaxChars; }
    public boolean isPersistFailedRunsFull() { return persistFailedRunsFull; }
    public void setPersistFailedRunsFull(boolean persistFailedRunsFull) { this.persistFailedRunsFull = persistFailedRunsFull; }
}
