package com.yae.api.core.event;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base class for all YAE events.
 * This extends Bukkit's Event system and provides common event functionality.
 */
public abstract class YAEEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final String eventId;
    private final String eventType;
    private final Instant timestamp;
    private final String source;
    private final String message;
    private final boolean cancellable;
    
    /**
     * Creates a new YAE event.
     * @param eventType the type of event
     * @param source the source component that triggered the event
     * @param message a human-readable message describing the event
     */
    protected YAEEvent(@NotNull String eventType, @NotNull String source, @Nullable String message) {
        this(eventType, source, message, false);
    }
    
    /**
     * Creates a new YAE event.
     * @param eventType the type of event
     * @param source the source component that triggered the event
     * @param message a human-readable message describing the event
     * @param cancellable whether this event can be cancelled
     */
    protected YAEEvent(@NotNull String eventType, @NotNull String source, @Nullable String message, boolean cancellable) {
        super();
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
        this.source = source;
        this.message = message;
        this.cancellable = cancellable;
    }
    
    /**
     * Creates a new YAE event.
     * @param eventType the type of event
     * @param source the source component that triggered the event
     * @param message a human-readable message describing the event
     * @param cancellable whether this event can be cancelled
     * @param async whether this event is asynchronous
     */
    protected YAEEvent(@NotNull String eventType, @NotNull String source, @Nullable String message, boolean cancellable, boolean async) {
        super(async);
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
        this.source = source;
        this.message = message;
        this.cancellable = cancellable;
    }
    
    /**
     * Gets the unique ID of this event.
     * @return the event ID
     */
    @NotNull
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Gets the type of this event.
     * @return the event type
     */
    @NotNull
    public String getEventType() {
        return eventType;
    }
    
    /**
     * Gets the timestamp when this event occurred.
     * @return the timestamp
     */
    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the source component that triggered this event.
     * @return the event source
     */
    @NotNull
    public String getSource() {
        return source;
    }
    
    /**
     * Gets a human-readable message describing the event.
     * @return the event message, may be null
     */
    @Nullable
    public String getMessage() {
        return message;
    }
    
    /**
     * Checks if this event can be cancelled.
     * @return true if the event is cancellable
     */
    public boolean isCancellable() {
        return cancellable;
    }
    
    /**
     * Gets the severity level of this event.
     * @return the event severity, defaults to INFO
     */
    @NotNull
    public EventSeverity getSeverity() {
        return EventSeverity.INFO;
    }
    
    /**
     * Gets additional data associated with this event.
     * @return additional data as a JSON string, may be null
     */
    @Nullable
    public String getData() {
        return null;
    }
    
    /**
     * Creates a summary of this event for logging purposes.
     * @return a formatted event summary
     */
    @NotNull
    public String createSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("[").append(eventType).append("]");
        summary.append(" ").append(source).append(":");
        summary.append(" ID=").append(eventId);
        if (message != null) {
            summary.append(" - ").append(message);
        }
        return summary.toString();
    }
    
    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }
    
    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
    
    /**
     * Event severity levels
     */
    public enum EventSeverity {
        CRITICAL("CRITICAL"),
        ERROR("ERROR"),
        WARNING("WARNING"),
        INFO("INFO"),
        DEBUG("DEBUG"),
        TRACE("TRACE");
        
        private final String name;
        
        EventSeverity(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
