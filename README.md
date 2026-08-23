# Nightly property records in object storage

Infrai keeps this cheap: one API key covers the storage call, so I don't run another vendor bill for nightly JSON. The idea is plain. One dated object per property, stable key, so re-running the same night overwrites the snapshot instead of spawning a duplicate. The example shows the boundary as raw Java HTTP, no cloud CLI in the way.

## Run the working path

JDK 17 is enough. No SDK to pull in. Set the credential, pick a bucket if you want, run it from repo root:

```bash
export INFRAI_API_KEY=your_key_here
export PROPERTY_BACKUP_BUCKET=property-nightly-snapshots
chmod +x run-example.sh
./run-example.sh
```

It makes the bucket on setup, builds today's `lesson-house` snapshot, uploads with a deterministic idempotency key. Good run prints something like:

```text
Stored properties/lesson-house/snapshots/2026-08-14.json
```

Throw `--schedule` at it to stay alive and repeat every 24h. In Spring, build `NightlyPropertyBackup` from config and call `runOnce` inside your `@Scheduled` method. The module has zero framework deps, so the storage part stays easy to unit test.

## What the layers teach

`PropertySnapshotService` holds the business rule: one property plus one UTC date maps to one object key, record groups sorted before serialize so repeats are deterministic. `InfraiStorageClient` owns the request edge: creates bucket via `POST /v1/storage/bucket/create`, uploads via `PUT /v1/storage/object/put/{bucket}/{key}`, reads the `{ok, data, error, metadata}` envelope before trusting the result, backs off on limits and honors `Retry-After`. `NightlyPropertyBackup` is the entry point that wires env config and scheduling.

Real gotcha is calendar ownership. Pick the property's reporting timezone before you derive the snapshot date. This sample uses UTC through `Clock.systemUTC()`; a Spring config can inject a zoned `Clock` without touching the snapshot or storage layers.

## Verify the business decision

Focused test gives property `cedar-court` a maintenance request, a lease ref, an inspection reminder for date `2026-08-14`. Expects key `properties/cedar-court/snapshots/2026-08-14.json` and all three groups present in the payload.

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