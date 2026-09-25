package io.xion.application.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.xion.application.ContainerTeardown;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.domain.RestartPolicy;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches spawned container processes for exit. Honors {@code autoRemove} and restart policies.
 * <p>
 * v1 restart semantics: on restart-worthy exit, full teardown (including network detach) then
 * {@link StartContainerHandler} again — a new IP may be assigned.
 */
@ApplicationScoped
public class ContainerLifecycleWatcher {

    private static final Logger LOG = Logger.getLogger(ContainerLifecycleWatcher.class);

    private final ContainerStore store;
    private final ContainerTeardown teardown;
    private final ObjectMapper mapper;
    private final Instance<StartContainerHandler> startHandler;
    private final Instance<RemoveContainerHandler> removeHandler;

    private final Set<String> userStopped = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> failureCounts = new ConcurrentHashMap<>();

    @Inject
    public ContainerLifecycleWatcher(
            ContainerStore store,
            ContainerTeardown teardown,
            ObjectMapper mapper,
            Instance<StartContainerHandler> startHandler,
            Instance<RemoveContainerHandler> removeHandler) {
        this.store = store;
        this.teardown = teardown;
        this.mapper = mapper;
        this.startHandler = startHandler;
        this.removeHandler = removeHandler;
    }

    /** Mark that a user-initiated stop/rm is in progress so restart policies do not fire. */
    public void markUserStopped(String containerId) {
        userStopped.add(containerId);
    }

    /**
     * Register an {@link Process#onExit()} callback for a newly spawned container process.
     */
    public void onStart(String containerId, Process process) {
        userStopped.remove(containerId);
        process.onExit().thenAccept(p -> {
            try {
                onExit(containerId, p.exitValue());
            } catch (Exception e) {
                LOG.warnf(e, "Lifecycle callback failed for container %s", containerId);
            }
        });
    }

    void onExit(String containerId, int exitCode) {
        boolean user = userStopped.remove(containerId);
        Optional<ContainerRecord> opt = store.findById(containerId);
        if (opt.isEmpty()) {
            teardown.shutdown(containerId, null, false);
            return;
        }
        ContainerRecord record = opt.get();
        boolean autoRemove = readAutoRemove(record);
        RestartPolicy policy = readRestartPolicy(record);

        if (autoRemove) {
            if (!removeHandler.isUnsatisfied()) {
                try {
                    removeHandler.get().handle(new RemoveContainerCommand(containerId, true));
                } catch (Exception e) {
                    LOG.warnf(e, "autoRemove failed for %s; falling back to teardown", containerId);
                    teardown.shutdown(containerId, record.name(), true);
                    store.delete(containerId);
                }
            } else {
                teardown.shutdown(containerId, record.name(), true);
                store.delete(containerId);
            }
            failureCounts.remove(containerId);
            return;
        }

        int failureCount = 0;
        if (exitCode != 0) {
            failureCount = failureCounts.merge(containerId, 1, Integer::sum);
        } else {
            failureCounts.remove(containerId);
        }

        // Always clear process/proxy; v1 also detaches network (new IP on restart is OK).
        teardown.shutdown(containerId, record.name(), true);
        store.update(record
                .withStatus(ContainerStatus.STOPPED)
                .withPid(null)
                .withStoppedAt(Instant.now()));

        if (policy.shouldRestart(exitCode, failureCount, user) && !startHandler.isUnsatisfied()) {
            LOG.infof("Restarting container %s (policy=%s exit=%d failures=%d)",
                    containerId, policy.wire(), exitCode, failureCount);
            try {
                startHandler.get().handle(new StartContainerCommand(containerId));
            } catch (Exception e) {
                LOG.warnf(e, "Restart failed for container %s", containerId);
            }
        }
    }

    private boolean readAutoRemove(ContainerRecord record) {
        try {
            JsonNode node = mapper.readTree(record.profileJson());
            return node.path("autoRemove").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private RestartPolicy readRestartPolicy(ContainerRecord record) {
        try {
            JsonNode node = mapper.readTree(record.profileJson());
            if (node.hasNonNull("restartPolicy")) {
                return RestartPolicy.parse(node.get("restartPolicy").asText());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return RestartPolicy.NO;
    }
}
