#!/usr/bin/env bash

python3 -m grpc_tools.protoc \
-I proto \
--python_out="$(dirname "$0")/../../../../training-code/text-classification/" \
--grpc_python_out="$(dirname "$0")/../../../../training-code/text-classification/" \
--proto_path=../proto ../proto/*.proto
