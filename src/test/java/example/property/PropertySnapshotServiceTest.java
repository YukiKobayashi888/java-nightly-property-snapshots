package example.property;

import java.time.LocalDate;
import java.util.List;

public final class PropertySnapshotServiceTest {
    public static void main(String[] args) {
        PropertySnapshotService service = new PropertySnapshotService();
        PropertySnapshotService.PropertyData data = new PropertySnapshotService.PropertyData(
                "cedar-court",
                List.of(new PropertySnapshotService.MaintenanceRequest("MR-2", "3A", "Window latch", "open")),
                List.of(new PropertySnapshotService.TenantDocument("DOC-1", "3A", "lease", "leases/3A.pdf")),
                List.of(new PropertySnapshotService.InspectionReminder("INSP-4", "3A", LocalDate.parse("2026-08-20"))));
        PropertySnapshotService.Snapshot snapshot = service.prepare(data, LocalDate.parse("2026-08-14"));
        String json = new String(snapshot.content(), java.nio.charset.StandardCharsets.UTF_8);
        require(snapshot.objectKey().equals("properties/cedar-court/snapshots/2026-08-14.json"), "stable nightly key");
        require(json.contains("\"maintenance_requests\":[{\"id\":\"MR-2\""), "maintenance included");
        require(json.contains("\"tenant_documents\":[{\"id\":\"DOC-1\""), "documents included");
        require(json.contains("\"inspection_reminders\":[{\"id\":\"INSP-4\""), "reminders included");
        System.out.println("PASS: one dated snapshot contains all three property record groups");
    }

    private static void require(boolean condition, String decision) {
        if (!condition) throw new AssertionError(decision);
    }
}
