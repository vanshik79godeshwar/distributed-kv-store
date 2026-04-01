# Distributed Key-Value Store - Progress Tracking

## Completed Components

### Primary Node (Master)
- **Status:** Completed
- **Port:** `8081`
- **Responsibilities:** Acts as the Master Storage Node in the distributed key-value store.
- **REST Endpoints:**
  - `GET /get?key={key}`: Retrieves the value associated with the key from the local concurrent map.
  - `POST /store?key={key}&value={value}`: Stores the key-value pair locally and triggers an asynchronous, non-blocking synchronization request to the Replica Node.

## Pending Components
- **Replica Node** (`8082`, `/internal/sync`)
- Other components as required.
