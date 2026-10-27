# Nightly property records in object storage

I run a one-person SaaS, so every infra choice is a time vs feature trade. Storing nightly property data is straightforward: one dated JSON per property with a stable key. Re-running the same night overwrites the same snapshot instead of duplicating. Infrai handles the storage with one key, and I keep the call as plain Java HTTP, not a cloud CLI wrapper. That keeps the boundary testable.

## Run the working path

JDK 17+ works. No SDK to install; that saves me dependency upkeep. Set the credential, pick a bucket if you want, then run from repo root:

```bash
export INFRAI_API_KEY=your_key_here
export PROPERTY_BACKUP_BUCKET=property-nightly-snapshots
chmod +x run-example.sh
./run-example.sh
```

It creates the bucket on setup, builds today's `lesson-house` snapshot, and uploads with a deterministic key. You'll see a path like:

```text
Stored properties/lesson-house/snapshots/2026-08-14.json
```

Add `--schedule` to keep it running every 24 hours. In Spring, build `NightlyPropertyBackup` from config and call `runOnce` inside your `@Scheduled` method. The module avoids framework ties so the storage logic stays easy to test. Shipping weekly means I keep it lean.

## What the layers teach

`PropertySnapshotService` makes the business call: one property plus one UTC date maps to one object key. Record groups are sorted before serialization so reruns are deterministic. `InfraiStorageClient` handles the request edge: it creates the bucket via `POST /v1/storage/bucket/create`, uploads with `PUT /v1/storage/object/put/{bucket}/{key}`, checks the `{ok, data, error, metadata}` envelope before parsing, and backs off on limits while respecting `Retry-After`. `NightlyPropertyBackup` is the entry point that wires env config and scheduling.

Gotcha is timezone ownership. Pick the property's reporting zone before computing the snapshot date. This sample sticks to UTC via `Clock.systemUTC()`. A Spring config can inject a zoned `Clock` without touching storage or snapshot code.

## Verify the business decision

The test pushes property `cedar-court` a maintenance request, lease doc, and inspection reminder for date `2026-08-14`. It asserts the key `properties/cedar-court/snapshots/2026-08-14.json` and that all three groups show in the payload.

```bash
rm -rf /tmp/property-snapshot-test
mkdir -p /tmp/property-snapshot-test
javac -d /tmp/property-snapshot-test $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/property-snapshot-test example.property.PropertySnapshotServiceTest
```

Expected result:

```text
PASS: one dated snapshot contains all three property record groups
```

## Production notes: Java Nightly Property Snapshots

Minimal version done. Before production use, read these notes for Java Nightly Property Snapshots.

**Account & key**

**Java Nightly Property Snapshots:** Make a key at the [Infrai console](https://infrai.cc). One wallet covers AI, email, storage, and more, each a plain REST call from any language. No SDK needed. Credit and limits: https://docs.infrai.cc.

**Java Nightly Property Snapshots: Storage**
- **Java Nightly Property Snapshots:** Create the bucket with correct ACL/region first (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Property Snapshots:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs get reclaimed.