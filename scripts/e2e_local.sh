#!/bin/zsh
set -euo pipefail

base=http://localhost:8080
ts=$(date +%s)

create_workflow() {
  local code="$1"
  local name="$2"
  curl -s -X POST "$base/api/workflows" \
    -H 'Content-Type: application/json' \
    -d "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"test\"}" | jq -r '.id'
}

publish_version() {
  local id="$1"
  local file="$2"
  curl -s -X POST "$base/api/workflows/$id/versions" \
    -H 'Content-Type: application/json' \
    --data-binary @"$file" | jq -r '.id'
}

start_instance() {
  local id="$1"
  local payload="$2"
  curl -s -X POST "$base/api/workflows/$id/instances" \
    -H 'Content-Type: application/json' \
    -d "$payload" | jq -r '.instanceId'
}

condition_wf=$(create_workflow "condition_$ts" "Condition Test")
publish_version "$condition_wf" docs/condition-workflow.json >/dev/null
condition_instance=$(start_instance "$condition_wf" '{"inputPayload":{"approved":true}}')
condition_status=$(curl -s "$base/api/instances/$condition_instance" | jq -r '.status')
condition_nodes=$(curl -s "$base/api/instances/$condition_instance/node-executions" | jq -r '.[].nodeId' | paste -sd ',' -)

wait_wf=$(create_workflow "wait_$ts" "Wait Test")
publish_version "$wait_wf" docs/wait-for-feedback-workflow.json >/dev/null
wait_instance=$(start_instance "$wait_wf" '{"inputPayload":{"ticketId":"WT-1"}}')
wait_status_before=$(curl -s "$base/api/instances/$wait_instance" | jq -r '.status')
curl -s -X POST "$base/api/instances/$wait_instance/feedback" \
  -H 'Content-Type: application/json' \
  -d '{"feedbackType":"MANUAL_APPROVAL","feedbackPayload":{"approved":true},"createdBy":"tester"}' >/dev/null
sleep 1
wait_status_after=$(curl -s "$base/api/instances/$wait_instance" | jq -r '.status')
feedback_count=$(curl -s "$base/api/instances/$wait_instance/feedback-records" | jq 'length')

rest_wf=$(create_workflow "rest_$ts" "REST Session Test")
publish_version "$rest_wf" docs/rest-login-session-workflow.json >/dev/null
rest_instance=$(start_instance "$rest_wf" '{"inputPayload":{"requestId":"REST-1"}}')
rest_status=$(curl -s "$base/api/instances/$rest_instance" | jq -r '.status')
rest_auth=$(curl -s "$base/api/instances/$rest_instance/node-executions" | jq -r '.[1].outputJson')

stream_wf=$(create_workflow "stream_$ts" "REST Stream Test")
publish_version "$stream_wf" docs/rest-stream-workflow.json >/dev/null
stream_instance=$(start_instance "$stream_wf" '{}')
stream_status=$(curl -s "$base/api/instances/$stream_instance" | jq -r '.status')
stream_output=$(curl -s "$base/api/instances/$stream_instance/node-executions" | jq -r '.[1].outputJson')

digital_wf=$(create_workflow "digital_$ts" "Digital Test")
publish_version "$digital_wf" docs/digital-employee-workflow.json >/dev/null
digital_instance=$(start_instance "$digital_wf" '{}')
digital_status=$(curl -s "$base/api/instances/$digital_instance" | jq -r '.status')
digital_output=$(curl -s "$base/api/instances/$digital_instance/node-executions" | jq -r '.[1].outputJson')

db_wf=$(create_workflow "db_$ts" "DB Test")
publish_version "$db_wf" docs/database-workflow.json >/dev/null
db_instance=$(start_instance "$db_wf" '{}')
db_status=$(curl -s "$base/api/instances/$db_instance" | jq -r '.status')
db_output=$(curl -s "$base/api/instances/$db_instance/node-executions" | jq -r '.[1].outputJson')

workflow_count=$(curl -s "$base/api/workflows" | jq 'length')
instance_count=$(curl -s "$base/api/instances" | jq 'length')

jq -n \
  --arg condition_status "$condition_status" \
  --arg condition_nodes "$condition_nodes" \
  --arg wait_before "$wait_status_before" \
  --arg wait_after "$wait_status_after" \
  --argjson feedback_count "$feedback_count" \
  --arg rest_status "$rest_status" \
  --arg rest_auth "$rest_auth" \
  --arg stream_status "$stream_status" \
  --arg stream_output "$stream_output" \
  --arg digital_status "$digital_status" \
  --arg digital_output "$digital_output" \
  --arg db_status "$db_status" \
  --arg db_output "$db_output" \
  --argjson workflow_count "$workflow_count" \
  --argjson instance_count "$instance_count" \
  '{
    condition_status: $condition_status,
    condition_nodes: $condition_nodes,
    wait_before: $wait_before,
    wait_after: $wait_after,
    feedback_count: $feedback_count,
    rest_status: $rest_status,
    rest_auth: $rest_auth,
    stream_status: $stream_status,
    stream_output: $stream_output,
    digital_status: $digital_status,
    digital_output: $digital_output,
    db_status: $db_status,
    db_output: $db_output,
    workflow_count: $workflow_count,
    instance_count: $instance_count
  }'
