# YAE Core Service Architecture Documentation

## Overview

The YetAnotherEconomy (YAE) plugin follows a modular service-oriented architecture where functionality is organized into discrete, pluggable services. Each service is responsible for a specific aspect of the economy system and can be independently configured, enabled, or disabled.

## Core Architecture Components

### 1. ServiceType Enum
**Location**: `com.yae.api.core.ServiceType`

Defines all available service types in the system:
- **SPS** (Service Product Standard) - Service product standardization
- **SHOP** - Shop management service
- **SELL** - System selling service
- **MARKET** - Player-to-player trading service
- **BANK** - Banking service with interest
- **ORG** - Organization/guild management
- **RISK** - Risk assessment and management
- **LEDGER** - Transaction logging and auditing
- **CONFIG** - Configuration management
- **USER** - User data and permission management
- **ECONOMY** - Core economy functionality
- **COMMAND** - Command handling and registration
- **EVENT** - Event handling and processing
- **DATABASE** - Data persistence
- **CACHE** - Caching functionality
- **MESSAGING** - Message handling and localization

### 2. Service Interface
**Location**: `com.yae.api.core.Service`

Base interface that all services must implement:
- Service lifecycle management (initialize, reload, shutdown)
- Configuration management
- Health monitoring
- Dependency management
- Status reporting

### 3. YAECore Interface
**Location**: `com.yae.api.core.YAECore`

Main interface defining the core functionality:
- Service registration and management
- Configuration management
- Event bus and event handling
- Health and diagnostics
- Lifecycle management
- Logging utilities

### 4. YAECoreBase
**Location**: `com.yae.api.core.YAECoreBase`

Abstract base implementation providing:
- Thread-safe service registry using ConcurrentHashMap
- Event handling with CompletableFuture support
- Service lifecycle management
- Configuration management
- Health monitoring
- Logging utilities

### 5. YetAnotherEconomy (Main Plugin)
**Location**: `com.yae.YetAnotherEconomy`

Main plugin class that extends Bukkit's JavaPlugin and implements YAECore:
- Plugin lifecycle management
- Service initialization and shutdown
- Configuration loading
- Event system integration

## Event System

### YAEEvent Base Class
**Location**: `com.yae.api.core.event.YAEEvent`

Base class for all YAE events:
- Event ID and timestamp tracking
- Source identification
- Severity levels
- Summary generation

### Service Events
**Location**: `com.yae.api.core.event.ServiceEvent`, `com.yae.api.core.event.ServiceEventTypes`

Events for service lifecycle:
- ServiceRegisteredEvent
- ServiceUnregisteredEvent
- ServiceInitializedEvent
- ServiceStateChangedEvent
- ServiceReloadedEvent

### Economy Events
**Location**: `com.yae.api.core.event.EconomyEvent`

Events for economy operations:
- MoneyDepositEvent
- MoneyWithdrawEvent
- MoneyTransferEvent
- InsufficientFundsEvent

## Service Configuration

### ServiceConfig
**Location**: `com.yae.api.core.ServiceConfig`

Immutable configuration container with type-safe getters:
- Support for strings, integers, doubles, booleans
- Default value support
- Configuration validation
- Update operations returning new instances

## Design Principles

1. **Modularity**: Each service is self-contained and can function independently
2. **Extensibility**: New services can be added without modifying existing code
3. **Thread Safety**: All core components use thread-safe collections and proper synchronization
4. **Event-Driven**: Components communicate through events rather than direct coupling
5. **Configuration-Driven**: Services initialize from configuration, allowing runtime behavior changes
6. **Health Monitoring**: Built-in health checking and diagnostic capabilities
7. **Priority Management**: Services can define initialization and shutdown priorities

## Usage Patterns

### Service Registration
```java
YetAnotherEconomy plugin = YetAnotherEconomy.getInstance();
plugin.registerService(new MyCustomService());
```

### Configuration Management
```java
ServiceConfig config = ServiceConfig.empty()
    .with("key1", "value1")
    .with("key2", 123);
```

### Event Handling
```java
plugin.addEventHandler("money-transfer", event -> {
    MoneyTransferEvent transferEvent = (MoneyTransferEvent) event;
    // Handle the transfer
});
```

### Service Access
```java
BankService bankService = plugin.getService(ServiceType.BANK);
if (bankService != null && bankService.isEnabled()) {
    bankService.processTransaction(...);
}
```

## Package Structure

```
com.yae/
├── YetAnotherEconomy.java              # Main plugin class
└── api/
    └── core/
        ├── Service.java                  # Service interface
        ├── ServiceType.java              # Service type enum
        ├── ServiceConfig.java            # Configuration class
        ├── YAECore.java                  # Core interface
        ├── YAECoreBase.java              # Base implementation
        └── event/
            ├── YAEEvent.java             # Base event class
            ├── ServiceEvent.java         # Service event base
            ├── ServiceEventTypes.java    # Service event types
            └── EconomyEvent.java         # Economy event types
```

## Benefits

1. **Separation of Concerns**: Each service handles one specific aspect of the economy
2. **Maintainability**: Changes to one service don't affect others
3. **Testability**: Services can be tested in isolation
4. **Scalability**: New features can be added as new services
5. **Flexibility**: Services can be enabled/disabled as needed
6. **Observability**: Comprehensive event system and health monitoring

## Next Steps

This core architecture provides the foundation for implementing specific economy services:

1. **Implement Core Services**: Create concrete implementations for each service type
2. **Database Integration**: Add database services for data persistence
3. **Command System**: Implement command services for player interactions
4. **GUI System**: Create services for graphical user interfaces
5. **Integration Services**: Add support for external plugins (Vault, LuckPerms, etc.)

The architecture is designed to be extensible while maintaining clean separation of concerns and robust error handling.
