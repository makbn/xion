package io.xion.infrastructure.resources;

import io.xion.domain.ResourceLimits;

/**
 * Applies resource limits around child spawn (setrlimit / taskpolicy on Darwin).
 */
public interface ResourceGovernor {

    /**
     * Apply limits before/around spawning the child process.
     * Implementations may mutate the current process or wrap the spawn command.
     */
    void apply(ResourceLimits limits);

    /**
     * Optionally wrap a command list with macOS {@code taskpolicy} when --cpus is set.
     */
    default java.util.List<String> wrapCommand(java.util.List<String> command, ResourceLimits limits) {
        return command;
    }
}
