# Photo Sync App

A personal photo syncing system that allows your Android device to upload photos directly to your Linux laptop running a Spring Boot server. Perfect for those who want to create their own cloud storage without relying on services like Google One.

## Features

- **Automatic Syncing:** A background `SyncWorker` checks for new photos every 15 minutes and uploads them automatically.
- **Deduplication:** Prevents resyncing of photos already uploaded by checking the database.
- **File Storage:** Photos are saved with unique filenames to prevent conflicts.
- **Metadata Management:** Stores original name, stored name, path, size, and MIME type in a database using JPA repositories.
- **Server-Side Upload Handling:** The server actively listens for incoming photo uploads.
- **Error Handling:** Returns structured responses (`ApiResponse`) for success or failure.

## Limitations

- Works only if your laptop and phone are connected to the **same Wi-Fi network**.
- Requests are **not yet secured** with JWT.
- Still a **work in progress**.

## Getting Started

### Requirements

- Java 17+
- Spring Boot
- Android Studio
- SQLite / Your preferred database (for storing metadata)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/MandarDhamale/photo-sync-app.git

   
