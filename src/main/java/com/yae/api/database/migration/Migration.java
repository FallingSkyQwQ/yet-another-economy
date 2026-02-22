package com.yae.api.database.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a database migration with version control support.
 */
public final class Migration implements Comparable<Migration> {
    
    private final String version;
    private final String description;
    private final String upScript;
    private final String downScript;
    private final int priority;
    private final boolean isBaseline;
    
    private Migration(@NotNull Builder builder) {
        this.version = builder.version;
        this.description = builder.description;
        this.upScript = builder.upScript;
        this.downScript = builder.downScript;
        this.priority = builder.priority;
        this.isBaseline = builder.isBaseline;
    }
    
    /**
     * Gets the version of this migration.
     * @return the migration version
     */
    @NotNull
    public String getVersion() {
        return version;
    }
    
    /**
     * Gets the description of this migration.
     * @return the migration description
     */
    @NotNull
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the SQL script for applying this migration (up direction).
     * @return the up script
     */
    @NotNull
    public String getUpScript() {
        return upScript;
    }
    
    /**
     * Gets the SQL script for rolling back this migration (down direction).
     * @return the down script, may be null for baseline migrations
     */
    @Nullable
    public String getDownScript() {
        return downScript;
    }
    
    /**
     * Gets the priority of this migration.
     * @return the migration priority
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * Checks if this is a baseline migration.
     * @return true if this is a baseline migration
     */
    public boolean isBaseline() {
        return isBaseline;
    }
    
    /**
     * Creates a new migration builder.
     * @return a new builder
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public int compareTo(@NotNull Migration other) {
        int priorityComparison = Integer.compare(this.priority, other.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        return this.version.compareTo(other.version);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Migration migration = (Migration) obj;
        return version.equals(migration.version);
    }
    
    @Override
    public int hashCode() {
        return version.hashCode();
    }
    
    @Override
    public String toString() {
        return "Migration{" +
               "version='" + version + '\'' +
               ", description='" + description + '\'' +
               ", priority=" + priority +
               ", isBaseline=" + isBaseline +
               '}';
    }
    
    /**
     * Builder class for creating Migration instances.
     */
    public static final class Builder {
        private String version;
        private String description;
        private String upScript;
        private String downScript;
        private int priority = 0;
        private boolean isBaseline = false;
        
        private Builder() {}
        
        /**
         * Sets the migration version.
         * @param version the migration version
         * @return this builder
         */
        @NotNull
        public Builder version(@NotNull String version) {
            this.version = version;
            return this;
        }
        
        /**
         * Sets the migration description.
         * @param description the migration description
         * @return this builder
         */
        @NotNull
        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Sets the migration SQL script for up direction.
         * @param upScript the up script
         * @return this builder
         */
        @NotNull
        public Builder upScript(@NotNull String upScript) {
            this.upScript = upScript;
            return this;
        }
        
        /**
         * Sets the migration SQL script for down direction.
         * @param downScript the down script
         * @return this builder
         */
        @NotNull
        public Builder downScript(@Nullable String downScript) {
            this.downScript = downScript;
            return this;
        }
        
        /**
         * Sets the migration priority.
         * @param priority the migration priority
         * @return this builder
         */
        @NotNull
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Sets whether this is a baseline migration.
         * @param baseline true if this is a baseline migration
         * @return this builder
         */
        @NotNull
        public Builder baseline(boolean baseline) {
            this.isBaseline = baseline;
            return this;
        }
        
        /**
         * Builds the migration instance.
         * @return the migration
         * @throws IllegalStateException if required fields are missing
         */
        @NotNull
        public Migration build() {
            if (version == null || version.trim().isEmpty()) {
                throw new IllegalStateException("Migration version cannot be empty");
            }
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalStateException("Migration description cannot be empty");
            }
            if (upScript == null || upScript.trim().isEmpty()) {
                throw new IllegalStateException("Migration up script cannot be empty");
            }
            return new Migration(this);
        }
    }
}
