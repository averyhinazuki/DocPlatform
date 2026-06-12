#!/usr/bin/env bash
# Exactly-once demonstration: replay a duplicate Kafka event for an already-COMPLETED
# report and prove nothing changes — no second MinIO object, no status flip, no quota drift.
# This is the at-least-once redelivery scenario the consumer's idempotency guards exist for.
set -euo pipefail

DOC_ID="${1:-}"
if [ -z "$DOC_ID" ]; then
  DOC_ID=$(docker exec docplatform-mongo mongosh docplatform --quiet --eval \
    'print(db.generated_documents.findOne({status:"COMPLETED", minioObjectKey:{$ne:null}})._id.toHexString())')
fi

DOC_JSON=$(docker exec docplatform-mongo mongosh docplatform --quiet --eval "
const d = db.generated_documents.findOne({_id: ObjectId('$DOC_ID')});
print(JSON.stringify({tenantId: Number(d.tenantId), fileFormat: d.fileFormat, status: d.status}));")
TENANT_ID=$(echo "$DOC_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['tenantId'])")
FORMAT=$(echo "$DOC_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['fileFormat'])")
# Never read on the COMPLETED skip path — the consumer exits on the status check first
TEMPLATE_ID="replay-not-used"

# MinIO stores each object as a dir containing xl.meta; its image has neither find nor grep,
# so list inside the container and count on the host
count_minio() { docker exec docplatform-minio ls -R /data/reports 2>/dev/null | grep -c xl.meta | tr -d ' '; }

BEFORE_MINIO=$(count_minio)
BEFORE_QUOTA=$(docker exec docplatform-redis redis-cli GET "tenant:$TENANT_ID:running" | tr -d '"')
echo "replaying event for doc=$DOC_ID tenant=$TENANT_ID (MinIO objects: $BEFORE_MINIO, quota counter: ${BEFORE_QUOTA:-0})"

EVENT="{\"documentId\":\"$DOC_ID\",\"tenantId\":$TENANT_ID,\"scheduleId\":null,\"reportType\":\"replay\",\"fileFormat\":\"$FORMAT\",\"templateId\":\"$TEMPLATE_ID\",\"params\":{},\"recipients\":[],\"triggeredBy\":\"replay-demo\",\"note\":null,\"contentOverride\":null}"

# __TypeId__ header tells Spring's JsonDeserializer the payload type
printf '__TypeId__:com.example.docplatform.kafka.event.ReportRequestedEvent\t%s\n' "$EVENT" | \
  docker exec -i docplatform-kafka kafka-console-producer \
    --bootstrap-server localhost:9092 --topic report.requested \
    --property parse.headers=true > /dev/null

sleep 5

AFTER_MINIO=$(count_minio)
AFTER_QUOTA=$(docker exec docplatform-redis redis-cli GET "tenant:$TENANT_ID:running" | tr -d '"')
AFTER_STATUS=$(docker exec docplatform-mongo mongosh docplatform --quiet --eval \
  "print(db.generated_documents.findOne({_id: ObjectId('$DOC_ID')}).status)")

echo "after replay: MinIO objects $BEFORE_MINIO -> $AFTER_MINIO, status=$AFTER_STATUS, quota counter=${AFTER_QUOTA:-0}"
if [ "$BEFORE_MINIO" = "$AFTER_MINIO" ] && [ "$AFTER_STATUS" = "COMPLETED" ] && [ "${AFTER_QUOTA:-0}" = "${BEFORE_QUOTA:-0}" ]; then
  echo "PASS: duplicate event was a no-op (no new object, status intact, no quota drift)"
else
  echo "FAIL: state changed on replay"
  exit 1
fi
