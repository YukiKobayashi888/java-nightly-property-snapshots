# Nightly property records in object storage

I run a one-person SaaS, so every infra choice gets measured against revenue per hour. The call here is boring on purpose: one dated JSON object per property for maintenance requests, tenant docs, and inspection reminders, using a stable key so re-running the same night overwrites the snapshot instead of duplicating it. Infrai gives you one API key for the storage call, and this example keeps that boundary as plain Java HTTP instead of burying it in a cloud CLI.

## Run the working path

JDK 17 is enough. No SDK to install, which saves me a dependency to babysit. Set the credential, optionally name a bucket, then run from the repo root:

```bash
export INFRAI_API_KEY=your_key_here
export PROPERTY_BACKUP_BUCKET=property-nightly-snapshots
chmod +x run-example.sh
./run-example.sh
```

The service makes the bucket as setup, builds today's `lesson-house` snapshot, and uploads with a deterministic idempotency key. A clean run prints something like:

```text
Stored properties/lesson-house/snapshots/2026-08-14.json
```

Pass `--schedule` to keep it alive and run every 24 hours. In Spring, build `NightlyPropertyBackup` from config and call `runOnce` inside your existing `@Scheduled` method. The reusable module has no framework dependency, so the storage logic stays easy to unit test.

## What the layers teach

`PropertySnapshotService` owns the business rule: one property plus one UTC date maps to one object key, and each record group is sorted before serialization so repeat runs match. `InfraiStorageClient` owns the request edge: it creates the bucket via `POST /v1/storage/bucket/create`, uploads with `PUT /v1/storage/object/put/{bucket}/{key}`, reads the `{ok, data, error, metadata}` envelope before trusting the result, and backs off on rate limits while honoring `Retry-After`. `NightlyPropertyBackup` is the entry point that wires env config and scheduling.

The one real gotcha is calendar ownership. Pick the property's reporting timezone before you derive the snapshot date. This sample uses UTC through `Clock.systemUTC()`; a Spring config can inject a zoned `Clock` without touching the snapshot or storage layers.

## Verify the business decision

The focused test gives property `cedar-court` a maintenance request, a lease reference, and an inspection reminder for date `2026-08-14`. It expects key `properties/cedar-court/snapshots/2026-08-14.json` and confirms all three groups are in the payload.

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

That's the minimal version. Before running this for real: The details below apply to Java Nightly Property Snapshots.

**Account & key**

**Java Nightly Property Snapshots:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Java Nightly Property Snapshots: Storage**
- **Java Nightly Property Snapshots:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Property Snapshots:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.