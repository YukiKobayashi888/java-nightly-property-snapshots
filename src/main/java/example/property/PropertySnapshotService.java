package example.property;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class PropertySnapshotService {
    public record MaintenanceRequest(String id, String unit, String summary, String status) {}
    public record TenantDocument(String id, String unit, String kind, String storageReference) {}
    public record InspectionReminder(String id, String unit, LocalDate dueOn) {}
    public record PropertyData(String propertyId, List<MaintenanceRequest> maintenanceRequests,
                               List<TenantDocument> tenantDocuments,
                               List<InspectionReminder> inspectionReminders) {}
    public record Snapshot(String objectKey, byte[] content) {}

    public Snapshot prepare(PropertyData data, LocalDate snapshotDate) {
        String key = "properties/" + segment(data.propertyId()) + "/snapshots/" + snapshotDate + ".json";
        String json = "{\"property_id\":" + quote(data.propertyId())
                + ",\"snapshot_date\":" + quote(snapshotDate.toString())
                + ",\"maintenance_requests\":" + maintenanceJson(data.maintenanceRequests())
                + ",\"tenant_documents\":" + documentsJson(data.tenantDocuments())
                + ",\"inspection_reminders\":" + remindersJson(data.inspectionReminders()) + "}";
        return new Snapshot(key, json.getBytes(StandardCharsets.UTF_8));
    }

    private static String maintenanceJson(List<MaintenanceRequest> values) {
        return values.stream().sorted(Comparator.comparing(MaintenanceRequest::id))
                .map(v -> "{\"id\":" + quote(v.id()) + ",\"unit\":" + quote(v.unit())
                        + ",\"summary\":" + quote(v.summary()) + ",\"status\":" + quote(v.status()) + "}")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private static String documentsJson(List<TenantDocument> values) {
        return values.stream().sorted(Comparator.comparing(TenantDocument::id))
                .map(v -> "{\"id\":" + quote(v.id()) + ",\"unit\":" + quote(v.unit())
                        + ",\"kind\":" + quote(v.kind()) + ",\"storage_reference\":" + quote(v.storageReference()) + "}")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private static String remindersJson(List<InspectionReminder> values) {
        return values.stream().sorted(Comparator.comparing(InspectionReminder::id))
                .map(v -> "{\"id\":" + quote(v.id()) + ",\"unit\":" + quote(v.unit())
                        + ",\"due_on\":" + quote(v.dueOn().toString()) + "}")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private static String segment(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("propertyId must be a safe path segment");
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
