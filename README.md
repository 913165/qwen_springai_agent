# UPI Failure Agentic RAG (Spring AI)

Spring AI demo for **UPI transaction failure classification and incident creation**.

A new runtime transaction is classified using **RAG knowledge** (generic UPI failure codes), then a second agent decides whether to create a **mock incident**.

```text
New Transaction
      ↓
RAG Retrieval (generic UPI failure knowledge)
      ↓
Agent 1 — Classification
      ↓
Agent 2 — Incident (optional createIncident tool)
      ↓
Final Response
```

## Stack

| Component | Version / detail |
|-----------|------------------|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Chat model | `qwen/qwen3-8b` (LM Studio) |
| Vector store | pgvector (`COSINE_DISTANCE`, 4096 dims) |
| App port | `8090` |

## Prerequisites

1. **Java 21** and Maven Wrapper (`mvnw` / `mvnw.cmd`)
2. **PostgreSQL with pgvector**:

```bash
docker run -it --rm --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  pgvector/pgvector
```

3. **LM Studio** at `http://localhost:1234/v1` with:
   - Chat model: `qwen/qwen3-8b`
   - Embedding model producing **4096**-dimension vectors

## Configuration

`src/main/resources/application.yml`:

- Datasource: `jdbc:postgresql://localhost:5432/postgres` (`postgres` / `postgres`)
- OpenAI-compatible base URL: `http://localhost:1234/v1`
- API key: `LM_API_TOKEN` or default `lm-studio`
- pgvector: `dimensions: 4096`, `index-type: NONE`, `distance-type: COSINE_DISTANCE`

## What happens on startup

`SpringAiHelloApplication` loads **generic UPI failure knowledge** from `classpath:/input.txt` into pgvector (batches of 3).

Important:

- RAG stores **failure-code knowledge** (e.g. `U30`, `INSUFFICIENT_FUNDS`)
- Runtime transactions (e.g. `TXN987654`) are **not** stored in RAG

## Run

```bash
./mvnw spring-boot:run
```

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

## Sample curl requests

### UPI failure workflow — bank timeout (incident expected)

```bash
curl -X POST "http://localhost:8090/agent/upi-failure" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"TXN987654\",\"bank\":\"SBI\",\"psp\":\"Google Pay\",\"transactionType\":\"P2P\",\"amount\":2500,\"status\":\"FAILED\",\"failureCode\":\"U30\",\"errorMessage\":\"Transaction timed out while waiting for response from remitter bank\"}"
```

Linux / macOS / Git Bash:

```bash
curl -X POST "http://localhost:8090/agent/upi-failure" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN987654",
    "bank": "SBI",
    "psp": "Google Pay",
    "transactionType": "P2P",
    "amount": 2500,
    "status": "FAILED",
    "failureCode": "U30",
    "errorMessage": "Transaction timed out while waiting for response from remitter bank"
  }'
```

### UPI failure workflow — customer error (no incident)

```bash
curl -X POST "http://localhost:8090/agent/upi-failure" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN987655",
    "bank": "HDFC Bank",
    "psp": "PhonePe",
    "transactionType": "P2M",
    "amount": 850,
    "status": "FAILED",
    "failureCode": "INSUFFICIENT_FUNDS",
    "errorMessage": "Insufficient account balance"
  }'
```

### Search UPI failure knowledge

```bash
curl "http://localhost:8090/search?query=U30%20transaction%20timeout%20remitter%20bank"
```

### Chat (optional)

```bash
curl "http://localhost:8090/chat?message=Explain%20UPI%20failure%20code%20U30"
```

## API

### 1) UPI failure workflow (main demo)

```http
POST http://localhost:8090/agent/upi-failure
Content-Type: application/json
```

**Example — bank timeout (incident expected):**

```json
{
  "transactionId": "TXN987654",
  "bank": "SBI",
  "psp": "Google Pay",
  "transactionType": "P2P",
  "amount": 2500,
  "status": "FAILED",
  "failureCode": "U30",
  "errorMessage": "Transaction timed out while waiting for response from remitter bank"
}
```

**Example — customer error (no incident):**

```json
{
  "transactionId": "TXN987655",
  "bank": "HDFC Bank",
  "psp": "PhonePe",
  "transactionType": "P2M",
  "amount": 850,
  "status": "FAILED",
  "failureCode": "INSUFFICIENT_FUNDS",
  "errorMessage": "Insufficient account balance"
}
```

**Example response shape:**

```json
{
  "transaction": { "transactionId": "TXN987654", "failureCode": "U30" },
  "classification": {
    "transactionId": "TXN987654",
    "failureType": "TRANSIENT_BANK_TIMEOUT",
    "failureCode": "U30",
    "severity": "HIGH",
    "rootCause": "Remitter bank did not respond within configured timeout",
    "incidentRequired": true,
    "incidentCategory": "BANK_SERVICE_DEGRADATION"
  },
  "incident": {
    "incidentCreated": true,
    "incidentId": "INC-2026-0001",
    "status": "OPEN",
    "category": "BANK_SERVICE_DEGRADATION",
    "severity": "HIGH"
  },
  "retrievedKnowledge": [
    "{\"failure_code\": \"U30\", \"failure_type\": \"TRANSIENT_BANK_TIMEOUT\", ...}"
  ]
}
```

### 2) Semantic search over UPI failure knowledge

Useful to inspect what RAG retrieves for a failure code/message:

```http
GET http://localhost:8090/search?query=U30%20transaction%20timeout%20remitter%20bank
```

Example response:

```json
[
  {
    "id": "...",
    "content": "{\"failure_code\": \"U30\", \"failure_type\": \"TRANSIENT_BANK_TIMEOUT\", \"severity\": \"HIGH\", ...}",
    "score": 0.85,
    "metadata": {}
  }
]
```

### 3) Simple chat (optional)

```http
GET http://localhost:8090/chat?message=What%20is%20UPI%20error%20U30
```

## Project layout

```text
src/main/java/com/example/hello/
  SpringAiHelloApplication.java          # boots app + loads UPI knowledge into pgvector
  controller/
    ChatController.java                  # GET /chat
    SearchController.java                # GET /search (UPI knowledge, topK=3)
    UpiAgentController.java              # POST /agent/upi-failure
  model/
    TransactionFailureRequest.java       # runtime transaction input
    ClassificationResult.java            # Agent 1 structured output
    IncidentResult.java                  # Agent 2 structured output
    WorkflowResponse.java                # final API response
  service/
    UpiFailureWorkflow.java              # sequential RAG → Agent1 → Agent2
  tool/
    CreateIncidentTool.java              # mock createIncident tool
src/main/resources/
  application.yml                        # datasource + Spring AI / pgvector config
  input.txt                              # generic UPI failure knowledge (one JSON doc per line)
```

## Sequential agent flow

1. Receive a **new** UPI transaction/failure at runtime
2. Retrieve relevant **generic** documents from RAG (`failureCode` / `errorMessage`)
3. **Agent 1** classifies the failure (structured output)
4. Agent 1 output becomes **Agent 2** input
5. If `incidentRequired=true`, Agent 2 calls mock `createIncident`
6. If `incidentRequired=false`, Agent 2 skips the tool and explains why
7. Return the final combined response

## Notes

- Embedding dimension in pgvector **must** match the local embedding model.
- Do not use runtime `transactionId` values as RAG knowledge keys.
# qwen_springai_agent
