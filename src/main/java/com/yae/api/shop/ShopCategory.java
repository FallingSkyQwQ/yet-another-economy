package com.yae.api.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a shop category with all its properties.
 */
public class ShopCategory {
    
    private final String id;
    private final String displayName;
    private final String icon;
    private final List<String> description;
    private final String color;
    private final List<ShopItem> items;
    
    public ShopCategory(@NotNull String id,
                       @NotNull String displayName,
                       @NotNull String icon,
                       @Nullable List<String> description,
                       @NotNull String color,
                       @NotNull List<ShopItem> items) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.icon = Objects.requireNonNull(icon, "icon cannot be null");
        this.description = description != null ? List.copyOf(description) : List.of();
        this.color = Objects.requireNonNull(color, "color cannot be null");
        this.items = new ArrayList<>(items);
    }
    
    @NotNull
    public String getId() {
        return id;
    }
    
    @NotNull
    public String getDisplayName() {
        return displayName;
    }
    
    @NotNull
    public String getIcon() {
        return icon;
    }
    
    @NotNull
    public List<String> getDescription() {
        return description;
    }
    
    @NotNull
    public String getColor() {
        return color;
    }
    
    @NotNull
    public List<ShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }
    
    public void addItem(@NotNull ShopItem item) {
        Objects.requireNonNull(item, "item cannot be null");
        items.add(item);
    }
    
    public boolean removeItem(@NotNull ShopItem item) {
        Objects.requireNonNull(item, "item cannot be null");
        return items.remove(item);
    }
    
    public int getItemCount() {
        return items.size();
    }
    
    public boolean hasItems() {
        return !items.isEmpty();
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    /**
     * Gets the total stock of all items in this category.
     */
    public int getTotalStock() {
        return items.stream()
                   .mapToInt(ShopItem::getStock)
                   .sum();
    }
    
    /**
     * Gets the average price of all items in this category.
     */
    public double getAveragePrice() {
        if (items.isEmpty()) {
            return 0.0;
        }
        
        return items.stream()
                   .mapToDouble(ShopItem::getPrice)
                   .average()
                   .orElse(0.0);
    }
    
    /**
     * Gets the number of enabled items in this category.
     */
    public int getEnabledItemCount() {
        return (int) items.stream()
                         .filter(ShopItem::isEnabled)
                         .count();
    }
    
    /**
     * Search for items in this category by name or ID.
     */
    @NotNull
    public List<ShopItem> searchItems(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getItems();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        return items.stream()
                   .filter(item -> 
                       item.getDisplayName().toLowerCase().contains(lowerKeyword) ||
                       item.getId().toLowerCase().contains(lowerKeyword))
                   .toList();
    }
    
    /**
     * Gets items sorted by price.
     */
    @NotNull
    public List<ShopItem> getItemsSortedByPrice() {
        return items.stream()
                   .sorted(java.util.Comparator.comparingDouble(ShopItem::getPrice))
                   .toList();
    }
    
    /**
     * Gets items sorted by name.
     */
    @NotNull
    public List<ShopItem> getItemsSortedByName() {
        return items.stream()
                   .sorted(java.util.Comparator.comparing(ShopItem::getDisplayName))
                   .toList();
    }
    
    /**
     * Gets enabled items only.
     */
    @NotNull
    public List<ShopItem> getEnabledItems() {
        return items.stream()
                   .filter(ShopItem::isEnabled)
                   .toList();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShopCategory that = (ShopCategory) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("ShopCategory{id='%s', name='%s', items=%d, color='%s'}",
                           id, displayName, items.size(), color);
    }
}
