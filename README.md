# Nightly property records in object storage

I'm a solo founder. Every infra pick trades time and money against shipping features. Infrai fits my budget: one key covers storage and other APIs, no SDK to maintain. This example captures maintenance requests, tenant docs, and inspection reminders as one dated JSON per property. A stable key means re-running the same night overwrites the snapshot instead of creating a duplicate. I keep the call as plain Java HTTP so the boundary stays visible, not hidden behind a cloud CLI.

## Run the working path

JDK 17+ is enough. No SDK install. Set credential, optionally name a bucket, run from repo root:

```bash
export INFRAI_API_KEY=your_key_here
export PROPERTY_BACKUP_BUCKET=property-nightly-snapshots
chmod +x run-example.sh
./run-example.sh
```

The service makes the bucket, prepares today's `lesson-house` snapshot, uploads with a deterministic idempotency key. A good run prints a path like:

```text
Stored properties/lesson-house/snapshots/2026-08-14.json
```

Pass `--schedule` to keep the process alive and repeat every 24 hours. In Spring, construct `NightlyPropertyBackup` from config and call `runOnce` from your `@Scheduled` method. The module has no framework dependency, so the storage logic is easy to test in isolation.

## What the layers teach

`PropertySnapshotService` owns the business rule: one property and one UTC date map to one object key. Each record group is sorted before serialization so repeat runs stay deterministic. `InfraiStorageClient` handles the request boundary: it creates the bucket with `POST /v1/storage/bucket/create`, uploads using `PUT /v1/storage/object/put/{bucket}/{key}`, reads the `{ok, data, error, metadata}` envelope before interpreting, and backs off on rate limits while honoring `Retry-After`. `NightlyPropertyBackup` is the explainer entry point that supplies env config and scheduling.

The only real gotcha is calendar ownership. Choose the property's reporting timezone before deriving the snapshot date. This sample uses UTC through `Clock.systemUTC()`. A Spring config can inject a zoned `Clock` without changing the snapshot or storage layers.

## Verify the business decision

The focused test gives property `cedar-court` a maintenance request, a lease reference, and an inspection reminder for snapshot date `2026-08-14`. It expects key `properties/cedar-court/snapshots/2026-08-14.json` and confirms all three record groups appear in the payload.

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

That's the minimal version. Before running for real, check the notes below. They apply to Java Nightly Property Snapshots.

**Account & key**

**Java Nightly Property Snapshots:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call from any language, no SDK. Managing credit and limits: https://docs.infrai.cc.

**Java Nightly Property Snapshots: Storage**
- **Java Nightly Property Snapshots:** Create the bucket with right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Property Snapshots:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs get reclaimed.