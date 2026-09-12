#!/bin/bash
export JAVA_HOME="$HOME/.jdks/ms-21.0.12.1"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Building the Spring Boot application..."
mvn clean package -DskipTests

echo "Packaging as a standalone application using jpackage..."
# Remove any old builds
rm -rf PhotoSyncServer

# Create a standalone app image (includes the Java runtime)
# This creates a folder containing the executable and all dependencies
jpackage \
  --type app-image \
  --name PhotoSyncServer \
  --input target \
  --main-jar photosync-0.0.1-SNAPSHOT.jar \
  --dest .

echo "Packaging complete!"
echo "You can now run your standalone application:"
echo "./PhotoSyncServer/bin/PhotoSyncServer"
