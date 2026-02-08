package com.teamcode.grove;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe state machine for managing Lemonlight device lifecycle.
 *
 * <p>Enforces valid state transitions and provides hooks for state change events.
 * This ensures the device is always in a known, valid state and prevents operations
 * from being performed in inappropriate states.
 *
 * <p><b>State Diagram:</b>
 * <pre>
 * UNINITIALIZED → INITIALIZING → READY
 *        ↓              ↓           ↓
 *    INITIALIZING ← ERROR ← ─ ─ ─ ─ ┘
 *        ↓              ↓
 *  DISCONNECTED ← ─ ─ ─ ┘
 * </pre>
 *
 * <p><b>States:</b>
 * <ul>
 *   <li>UNINITIALIZED - Device created but not initialized</li>
 *   <li>INITIALIZING - Initialization in progress</li>
 *   <li>READY - Device ready for operations</li>
 *   <li>ERROR - Error occurred, recovery possible</li>
 *   <li>DISCONNECTED - Device disconnected, requires re-initialization</li>
 * </ul>
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * DeviceStateMachine sm = new DeviceStateMachine();
 *
 * // Add state change listener
 * sm.addListener((from, to, reason) ->
 *     logger.info("State: {} -> {} ({})", from, to, reason)
 * );
 *
 * // Attempt transitions
 * if (sm.transition(State.INITIALIZING, "Starting init")) {
 *     // Perform initialization...
 *     if (success) {
 *         sm.transition(State.READY, "Init complete");
 *     } else {
 *         sm.transition(State.ERROR, "Init failed");
 *     }
 * }
 *
 * // Check state before operations
 * if (sm.isReady()) {
 *     // Perform device operations
 * }
 * }</pre>
 *
 * @see Lemonlight
 */
public class DeviceStateMachine {

    /**
     * Device lifecycle states.
     */
    public enum State {
        /** Device created but not initialized */
        UNINITIALIZED,

        /** Initialization in progress */
        INITIALIZING,

        /** Device ready for normal operations */
        READY,

        /** Error state, recovery possible via re-initialization */
        ERROR,

        /** Device disconnected, requires complete re-initialization */
        DISCONNECTED
    }

    private final AtomicReference<State> currentState;
    private final Map<State, Set<State>> allowedTransitions;
    private final List<StateChangeListener> listeners;
    private final AtomicReference<String> lastTransitionReason;
    private final AtomicReference<Long> lastTransitionTime;
    private final LemonlightLogger logger;

    /**
     * Creates a new state machine in UNINITIALIZED state.
     */
    public DeviceStateMachine() {
        this.currentState = new AtomicReference<>(State.UNINITIALIZED);
        this.listeners = new CopyOnWriteArrayList<>();
        this.lastTransitionReason = new AtomicReference<>("");
        this.lastTransitionTime = new AtomicReference<>(System.currentTimeMillis());
        this.logger = new LemonlightLogger(DeviceStateMachine.class);

        // Define valid state transitions
        this.allowedTransitions = new EnumMap<>(State.class);
        allowedTransitions.put(State.UNINITIALIZED, EnumSet.of(State.INITIALIZING));
        allowedTransitions.put(State.INITIALIZING, EnumSet.of(State.READY, State.ERROR, State.DISCONNECTED));
        allowedTransitions.put(State.READY, EnumSet.of(State.ERROR, State.DISCONNECTED));
        allowedTransitions.put(State.ERROR, EnumSet.of(State.INITIALIZING, State.DISCONNECTED));
        allowedTransitions.put(State.DISCONNECTED, EnumSet.of(State.INITIALIZING));

        logger.debug("State machine created in state: {}", State.UNINITIALIZED);
    }

    /**
     * Attempts to transition to a new state.
     *
     * <p>The transition will only succeed if:
     * <ul>
     *   <li>The new state is different from the current state</li>
     *   <li>The transition is allowed by the state machine rules</li>
     *   <li>No concurrent transition is in progress</li>
     * </ul>
     *
     * <p>If successful, all registered listeners will be notified.
     *
     * @param newState Target state
     * @param reason Human-readable reason for the transition
     * @return true if transition succeeded, false otherwise
     */
    public boolean transition(State newState, String reason) {
        if (newState == null) {
            throw new IllegalArgumentException("New state cannot be null");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be null or empty");
        }

        State current = currentState.get();

        // Already in target state
        if (current == newState) {
            logger.debug("Already in state {}, ignoring transition", newState);
            return true;
        }

        // Check if transition is allowed
        Set<State> allowed = allowedTransitions.get(current);
        if (allowed == null || !allowed.contains(newState)) {
            logger.error("Invalid state transition: {} -> {} (reason: {})",
                current, newState, reason);
            return false;
        }

        // Attempt atomic transition
        if (currentState.compareAndSet(current, newState)) {
            long timestamp = System.currentTimeMillis();
            lastTransitionReason.set(reason);
            lastTransitionTime.set(timestamp);

            logger.info("State transition: {} -> {} (reason: {})",
                current, newState, reason);

            // Notify listeners (non-blocking)
            notifyListeners(current, newState, reason);

            return true;
        }

        // CAS failed - concurrent transition occurred
        logger.warn("State transition {} -> {} failed due to concurrent modification",
            current, newState);
        return false;
    }

    /**
     * Gets the current state.
     *
     * @return Current state (never null)
     */
    public State getState() {
        return currentState.get();
    }

    /**
     * Checks if the device is in READY state.
     *
     * @return true if state is READY
     */
    public boolean isReady() {
        return currentState.get() == State.READY;
    }

    /**
     * Checks if the device is in ERROR state.
     *
     * @return true if state is ERROR
     */
    public boolean isError() {
        return currentState.get() == State.ERROR;
    }

    /**
     * Checks if the device is in DISCONNECTED state.
     *
     * @return true if state is DISCONNECTED
     */
    public boolean isDisconnected() {
        return currentState.get() == State.DISCONNECTED;
    }

    /**
     * Checks if the device is in INITIALIZING state.
     *
     * @return true if state is INITIALIZING
     */
    public boolean isInitializing() {
        return currentState.get() == State.INITIALIZING;
    }

    /**
     * Checks if the device is in UNINITIALIZED state.
     *
     * @return true if state is UNINITIALIZED
     */
    public boolean isUninitialized() {
        return currentState.get() == State.UNINITIALIZED;
    }

    /**
     * Checks if a specific transition would be valid from the current state.
     *
     * @param targetState Target state
     * @return true if transition is allowed
     */
    public boolean canTransitionTo(State targetState) {
        State current = currentState.get();
        if (current == targetState) {
            return true;
        }

        Set<State> allowed = allowedTransitions.get(current);
        return allowed != null && allowed.contains(targetState);
    }

    /**
     * Gets the reason for the last state transition.
     *
     * @return Last transition reason
     */
    public String getLastTransitionReason() {
        return lastTransitionReason.get();
    }

    /**
     * Gets the timestamp of the last state transition.
     *
     * @return Last transition timestamp in milliseconds
     */
    public long getLastTransitionTime() {
        return lastTransitionTime.get();
    }

    /**
     * Gets the time since the last state transition.
     *
     * @return Time in milliseconds since last transition
     */
    public long getTimeSinceLastTransition() {
        return System.currentTimeMillis() - lastTransitionTime.get();
    }

    /**
     * Adds a state change listener.
     *
     * <p>Listeners are called in the order they were added. If a listener
     * throws an exception, it will be logged but will not prevent other
     * listeners from being notified.
     *
     * @param listener Listener to add (must not be null)
     */
    public void addListener(StateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
        logger.debug("Added state change listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Removes a state change listener.
     *
     * @param listener Listener to remove
     * @return true if listener was removed, false if not found
     */
    public boolean removeListener(StateChangeListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            logger.debug("Removed state change listener: {}", listener.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Removes all state change listeners.
     */
    public void clearListeners() {
        int count = listeners.size();
        listeners.clear();
        logger.debug("Cleared {} state change listeners", count);
    }

    /**
     * Gets the number of registered listeners.
     *
     * @return Listener count
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Resets the state machine to UNINITIALIZED.
     *
     * <p><b>Warning:</b> This bypasses state transition validation and
     * should only be used for testing or emergency recovery.
     *
     * @param reason Reason for reset
     */
    public void reset(String reason) {
        State previous = currentState.getAndSet(State.UNINITIALIZED);
        lastTransitionReason.set("RESET: " + reason);
        lastTransitionTime.set(System.currentTimeMillis());

        logger.warn("State machine RESET: {} -> UNINITIALIZED (reason: {})",
            previous, reason);

        notifyListeners(previous, State.UNINITIALIZED, "RESET: " + reason);
    }

    /**
     * Gets a summary of the current state.
     *
     * @return State summary for telemetry display
     */
    public StateSummary getSummary() {
        return new StateSummary(
            currentState.get(),
            lastTransitionReason.get(),
            lastTransitionTime.get(),
            System.currentTimeMillis() - lastTransitionTime.get()
        );
    }

    /**
     * Notifies all listeners of a state change.
     *
     * @param from Previous state
     * @param to New state
     * @param reason Transition reason
     */
    private void notifyListeners(State from, State to, String reason) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChange(from, to, reason);
            } catch (Exception e) {
                logger.error("State listener {} failed: {}",
                    listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Listener interface for state change events.
     */
    @FunctionalInterface
    public interface StateChangeListener {
        /**
         * Called when state transitions.
         *
         * @param from Previous state
         * @param to New state
         * @param reason Transition reason
         */
        void onStateChange(State from, State to, String reason);
    }

    /**
     * Summary of current state for telemetry display.
     */
    public static class StateSummary {
        public final State state;
        public final String lastReason;
        public final long lastTransitionMs;
        public final long timeInStateMs;

        StateSummary(State state, String lastReason, long lastTransitionMs, long timeInStateMs) {
            this.state = state;
            this.lastReason = lastReason;
            this.lastTransitionMs = lastTransitionMs;
            this.timeInStateMs = timeInStateMs;
        }

        /**
         * Gets a formatted string for telemetry.
         *
         * @return Formatted summary
         */
        public String toTelemetryString() {
            return String.format("State: %s\nLast Transition: %s\nTime in State: %dms",
                state, lastReason, timeInStateMs);
        }

        @Override
        public String toString() {
            return String.format("StateSummary{state=%s, reason='%s', timeInState=%dms}",
                state, lastReason, timeInStateMs);
        }
    }

    @Override
    public String toString() {
        return String.format("DeviceStateMachine{state=%s, listeners=%d, lastReason='%s'}",
            currentState.get(), listeners.size(), lastTransitionReason.get());
    }
}
