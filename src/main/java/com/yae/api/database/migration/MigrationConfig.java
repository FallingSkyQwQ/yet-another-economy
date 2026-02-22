package com.yae.api.database.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * Configuration for the migration service.
 */
public final class MigrationConfig {
    
    private final boolean autoRun;
    private final String schemaHistoryTable;
    private final boolean validateChecksums;
    private final boolean allowOutOfOrder;
    private final int batchSize;
    private final boolean failOnWarning;
    private final boolean baselineOnMigrate;
    private final String baselineVersion;
    private final String baselineDescription;
    private final boolean skipDefaultResolvers;
    private final boolean cleanOnValidationError;
    
    private MigrationConfig(Builder builder) {
        this.autoRun = builder.autoRun;
        this.schemaHistoryTable = builder.schemaHistoryTable;
        this.validateChecksums = builder.validateChecksums;
        this.allowOutOfOrder = builder.allowOutOfOrder;
        this.batchSize = builder.batchSize;
        this.failOnWarning = builder.failOnWarning;
        this.baselineOnMigrate = builder.baselineOnMigrate;
        this.baselineVersion = builder.baselineVersion;
        this.baselineDescription = builder.baselineDescription;
        this.skipDefaultResolvers = builder.skipDefaultResolvers;
        this.cleanOnValidationError = builder.cleanOnValidationError;
    }
    
    /**
     * Creates a default migration configuration.
     * @return default configuration
     */
    @NotNull
    public static MigrationConfig defaultConfig() {
        return new Builder().build();
    }
    
    /**
     * Creates a builder for migration configuration.
     * @return new builder instance
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isAutoRun() { return autoRun; }
    public boolean isValidateChecksums() { return validateChecksums; }
    public boolean isAllowOutOfOrder() { return allowOutOfOrder; }
    public int getBatchSize() { return batchSize; }
    public boolean isFailOnWarning() { return failOnWarning; }
    public boolean isBaselineOnMigrate() { return baselineOnMigrate; }
    @Nullable
    public String getBaselineVersion() { return baselineVersion; }
    public boolean isSkipDefaultResolvers() { return skipDefaultResolvers; }
    public boolean isCleanOnValidationError() { return cleanOnValidationError; }
    
    @NotNull
    public String getSchemaHistoryTable() { return schemaHistoryTable; }
    
    /**
     * Gets the DDL for creating the schema history table based on database type.
     * @param databaseType the database type
     * @return DDL statement
     */
    @NotNull
    public String getSchemaHistoryTableDDL(@NotNull MigrationService.DatabaseType databaseType) {
        switch (databaseType) {
            case SQLITE:
                return getSqliteSchemaHistoryDDL();
            case MYSQL:
            case MARIADB:
                return getMySQLSchemaHistoryDDL();
            default:
                throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        }
    }
    
    @NotNull
    private String getSqliteSchemaHistoryDDL() {
        return "CREATE TABLE IF NOT EXISTS " + schemaHistoryTable + " (\n" +
               "    version TEXT PRIMARY KEY,\n" +
               "    description TEXT NOT NULL,\n" +
               "    checksum TEXT,\n" +
               "    execution_time INTEGER NOT NULL,\n" +
               "    success BOOLEAN NOT NULL,\n" +
               "    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL\n" +
               ");";
    }
    
    @NotNull
    private String getMySQLSchemaHistoryDDL() {
        return "CREATE TABLE IF NOT EXISTS " + schemaHistoryTable + " (\n" +
               "    version VARCHAR(255) PRIMARY KEY,\n" +
               "    description VARCHAR(255) NOT NULL,\n" +
               "    checksum VARCHAR(255),\n" +
               "    execution_time BIGINT NOT NULL,\n" +
               "    success BOOLEAN NOT NULL,\n" +
               "    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL\n" +
               ");";
    }
    
    @NotNull
    public String getBaslineDescription() { return baselineDescription; }
    
    /**
     * Builder for MigrationConfig
     */
    public static final class Builder {
        private boolean autoRun = true;
        private String schemaHistoryTable = "schema_history";
        private boolean validateChecksums = true;
        private boolean allowOutOfOrder = false;
        private int batchSize = 1;
        private boolean failOnWarning = false;
        private boolean baselineOnMigrate = false;
        private String baselineVersion = "0";
        private String baselineDescription = "<< Baseline >>";
        private boolean skipDefaultResolvers = false;
        private boolean cleanOnValidationError = false;
        
        private Builder() {}
        
        public Builder autoRun(boolean autoRun) {
            this.autoRun = autoRun;
            return this;
        }
        
        public Builder schemaHistoryTable(@NotNull String schemaHistoryTable) {
            this.schemaHistoryTable = Objects.requireNonNull(schemaHistoryTable, "schemaHistoryTable cannot be null");
            return this;
        }
        
        public Builder validateChecksums(boolean validateChecksums) {
            this.validateChecksums = validateChecksums;
            return this;
        }
        
        public Builder allowOutOfOrder(boolean allowOutOfOrder) {
            this.allowOutOfOrder = allowOutOfOrder;
            return this;
        }
        
        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder failOnWarning(boolean failOnWarning) {
            this.failOnWarning = failOnWarning;
            return this;
        }
        
        public Builder baselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
            return this;
        }
        
        public Builder baselineVersion(@NotNull String baselineVersion) {
            this.baselineVersion = Objects.requireNonNull(baselineVersion, "baselineVersion cannot be null");
            return this;
        }
        
        public Builder baselineDescription(@NotNull String baselineDescription) {
            this.baselineDescription = Objects.requireNonNull(baselineDescription, "baselineDescription cannot be null");
            return this;
        }
        
        public Builder skipDefaultResolvers(boolean skipDefaultResolvers) {
            this.skipDefaultResolvers = skipDefaultResolvers;
            return this;
        }
        
        public Builder cleanOnValidationError(boolean cleanOnValidationError) {
            this.cleanOnValidationError = cleanOnValidationError;
            return this;
        }
        
        @NotNull
        public MigrationConfig build() {
            return new MigrationConfig(this);
        }
    }
}
