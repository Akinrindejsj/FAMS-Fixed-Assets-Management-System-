package com.example.fams.governance;

import com.example.fams.assets.Asset;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Records every change to an asset — who changed which field from what to what — by
 * comparing the asset's state when loaded with its state at update. It sees every save
 * path (screens, bulk actions, checkouts, workflows) without changes to those services.
 * Nothing here may throw: a failure is swallowed so the asset save itself is never affected.
 */
public class AssetChangeListener {

    private static final Logger log = LoggerFactory.getLogger(AssetChangeListener.class);

    /** Fields that are bookkeeping, generated, or too large to be useful in a change history. */
    private static final Set<String> IGNORED = Set.of(
            "id", "createdAt", "updatedAt", "imageUrl", "imagePublicId", "barcodeImageDataUri", "qrCodeImageDataUri");

    @PostLoad
    public void onLoad(Asset asset) {
        try {
            asset.setAuditSnapshot(snapshot(asset));
        } catch (Exception e) {
            log.debug("Could not snapshot asset on load: {}", e.getMessage());
        }
    }

    @PostPersist
    public void onCreate(Asset asset) {
        try {
            AssetChangeLog entry = entry(asset, AssetChangeLog.CREATED, "(asset record)", null, "Asset registered");
            AssetChangeRecorder.record(List.of(entry));
            asset.setAuditSnapshot(snapshot(asset));
        } catch (Exception e) {
            log.debug("Could not log asset creation: {}", e.getMessage());
        }
    }

    @PreUpdate
    public void onUpdate(Asset asset) {
        try {
            Map<String, String> before = asset.getAuditSnapshot();
            Map<String, String> after = snapshot(asset);
            if (before != null) {
                List<AssetChangeLog> entries = new ArrayList<>();
                for (Map.Entry<String, String> field : after.entrySet()) {
                    String old = before.get(field.getKey());
                    if (!Objects.equals(old, field.getValue())) {
                        entries.add(entry(asset, AssetChangeLog.UPDATED, field.getKey(), old, field.getValue()));
                    }
                }
                AssetChangeRecorder.record(entries);
            }
            asset.setAuditSnapshot(after);
        } catch (Exception e) {
            log.debug("Could not log asset update: {}", e.getMessage());
        }
    }

    private AssetChangeLog entry(Asset asset, String action, String field, String oldValue, String newValue) {
        AssetChangeLog entry = new AssetChangeLog();
        entry.setAssetId(asset.getId());
        entry.setAssetCode(asset.getAssetCode());
        entry.setAction(action);
        entry.setFieldName(field);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setChangedBy(currentUser());
        return entry;
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    /** Simple-valued fields of the asset (text, numbers, flags, dates, enums), as comparable strings. */
    static Map<String, String> snapshot(Asset asset) throws IllegalAccessException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : Asset.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                    || field.isAnnotationPresent(Transient.class) || IGNORED.contains(field.getName())) {
                continue;
            }
            Class<?> type = field.getType();
            boolean simple = type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type)
                    || type == Boolean.class || Temporal.class.isAssignableFrom(type) || type.isEnum();
            if (!simple) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(asset);
            String text;
            if (value == null) {
                text = null;
            } else if (value instanceof BigDecimal decimal) {
                text = decimal.stripTrailingZeros().toPlainString();
            } else {
                text = String.valueOf(value);
            }
            values.put(field.getName(), text);
        }
        return values;
    }
}
