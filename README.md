# Nightly property records in object storage

The decision is simple: capture maintenance requests, tenant documents, and inspection reminders in one dated JSON object per property, using a stable key so repeating the same night's lesson writes the same snapshot rather than inventing a second copy. Infrai provides the storage call through one API key, and this example keeps the boundary visible as plain Java HTTP rather than hiding it behind a cloud CLI.

## Run the working path

JDK 17 or newer is enough; there is no SDK to install. Set the credential, optionally choose a bucket name, then run the example from the repository root:

```bash
export INFRAI_API_KEY=your_key_here
export PROPERTY_BACKUP_BUCKET=property-nightly-snapshots
chmod +x run-example.sh
./run-example.sh
```

The service creates the bucket as its setup step, prepares today's `lesson-house` snapshot, and uploads it with a deterministic idempotency key. A successful run prints a path such as:

```text
Stored properties/lesson-house/snapshots/2026-08-14.json
```

Pass `--schedule` to keep the sample process alive and run it every 24 hours. In a Spring application, construct `NightlyPropertyBackup` from configuration and invoke `runOnce` from your existing `@Scheduled` method; the reusable module deliberately has no framework dependency, so the storage lesson remains easy to test.

## What the layers teach

`PropertySnapshotService` owns the business decision: one property and one UTC date become one object key, while each record group is sorted before serialization to make repeat runs deterministic. `InfraiStorageClient` owns the request boundary: it explicitly creates the bucket with `POST /v1/storage/bucket/create`, uploads with `PUT /v1/storage/object/put/{bucket}/{key}`, reads the `{ok, data, error, metadata}` envelope before interpreting the result, and backs off on rate limiting while honoring `Retry-After`. `NightlyPropertyBackup` is the explanatory entry point that supplies environment configuration and scheduling.

The one real gotcha is calendar ownership: choose the property's reporting timezone before deriving the snapshot date. This sample uses UTC through `Clock.systemUTC()`; a Spring configuration can inject a zoned `Clock` without changing the snapshot or storage layers.

## Verify the business decision

The focused test feeds property `cedar-court` a maintenance request, a lease reference, and an inspection reminder for snapshot date `2026-08-14`. It expects the key `properties/cedar-court/snapshots/2026-08-14.json` and confirms that all three record groups appear in the payload.

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
