package example.property;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class NightlyPropertyBackup {
    private final InfraiStorageClient storage;
    private final PropertySnapshotService snapshots;
    private final Clock clock;
    private final String bucket;

    NightlyPropertyBackup(InfraiStorageClient storage, PropertySnapshotService snapshots, Clock clock, String bucket) {
        this.storage = storage;
        this.snapshots = snapshots;
        this.clock = clock;
        this.bucket = bucket;
    }

    public void runOnce(PropertySnapshotService.PropertyData data) throws Exception {
        storage.createBucket(bucket);
        PropertySnapshotService.Snapshot snapshot = snapshots.prepare(data, LocalDate.now(clock));
        storage.putSnapshot(bucket, snapshot);
        System.out.println("Stored " + snapshot.objectKey());
    }

    public static void main(String[] args) throws Exception {
        String bucket = System.getenv().getOrDefault("PROPERTY_BACKUP_BUCKET", "property-nightly-snapshots");
        NightlyPropertyBackup job = new NightlyPropertyBackup(new InfraiStorageClient(),
                new PropertySnapshotService(), Clock.systemUTC(), bucket);
        PropertySnapshotService.PropertyData lessonHouse = new PropertySnapshotService.PropertyData(
                "lesson-house", List.of(new PropertySnapshotService.MaintenanceRequest(
                        "MR-104", "2B", "Replace classroom-style desk lamp", "scheduled")),
                List.of(new PropertySnapshotService.TenantDocument(
                        "DOC-88", "2B", "lease", "leases/lesson-house/2B.pdf")),
                List.of(new PropertySnapshotService.InspectionReminder(
                        "INSP-17", "2B", LocalDate.now(ZoneOffset.UTC).plusDays(14))));
        if (args.length > 0 && args[0].equals("--schedule")) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try { job.runOnce(lessonHouse); } catch (Exception e) { e.printStackTrace(); }
            }, 0, 24, TimeUnit.HOURS);
        } else {
            job.runOnce(lessonHouse);
        }
    }
}
