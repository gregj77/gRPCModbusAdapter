#!/bin/bash

# Configuration
PROTOC_VERSION="28.2"
GO_PROTO_VER="latest"
GO_GRPC_VER="latest"
OUTPUT_DIR="grpc2modbus-sdk-go"

echo "🚀 Starting gRPC Go environment setup..."

# Local installation path (inside bash_utils)
INSTALL_DIR="$(pwd)/protoc_install"
PROTOC_BIN="$INSTALL_DIR/bin/protoc.exe"

# 1. Check/Install protoc
if [ ! -f "$PROTOC_BIN" ]; then
    echo "📦 Protoc not found. Downloading version $PROTOC_VERSION for Windows..."
    # Using the win64 specific package for Git Bash on Windows
    URL="https://github.com/protocolbuffers/protobuf/releases/download/v$PROTOC_VERSION/protoc-$PROTOC_VERSION-win64.zip"

    curl -L "$URL" -o protoc.zip
    unzip -o protoc.zip -d "$INSTALL_DIR"
    rm protoc.zip
fi

echo "✅ Using protoc: $PROTOC_BIN"

# 2. Install/Update Go plugins
echo "🛠️ Installing Go gRPC plugins..."
go install google.golang.org/protobuf/cmd/protoc-gen-go@$GO_PROTO_VER
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@$GO_GRPC_VER

# Add GOBIN to PATH for this session so protoc can find the plugins
export PATH="$PATH:$(go env GOPATH)/bin"

# 3. Prepare output directory in project root
cd ..
echo "📁 Preparing directory: $(pwd)/$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# 4. Generate code
echo "🏗️ Generating .pb.go files..."

# Processing Interface Module
if [ -d "gRPCModbusAdapter.Interface/src/main/proto" ]; then
    echo "🔹 Processing Interface module..."
    "$PROTOC_BIN" --proto_path=gRPCModbusAdapter.Interface/src/main/proto \
           --go_out="$OUTPUT_DIR" --go_opt=paths=source_relative \
           --go-grpc_out="$OUTPUT_DIR" --go-grpc_opt=paths=source_relative \
           gRPCModbusAdapter.Interface/src/main/proto/*.proto
else
    echo "⚠️ Interface proto directory not found, skipping..."
fi
