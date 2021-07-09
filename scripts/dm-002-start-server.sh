#!/usr/bin/env bash
echo "Installing project"
mvn clean install -D skipTests
echo "Starting data-management service"
mvn -q exec:java -pl data-management
