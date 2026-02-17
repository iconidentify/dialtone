# Dialtone

## AOL 3.x Protocol Server

Dialtone is a modern, from-scratch implementation of the AOL 3.x
protocol stack.\
It speaks the classic P3 wire protocol, implements token routing, FDO
(Form Definition Objects), XFER, DOD (Data on Demand), and session
control --- allowing vintage AOL clients to connect to a fully open,
modern server implementation.

------------------------------------------------------------------------

# Table of Contents

-   [Project Goals](#project-goals)
-   [Protocol Stack Overview](#protocol-stack-overview)
-   [Implemented Subsystems](#implemented-subsystems)
-   [Architecture](#architecture)
-   [Wire-Level Compatibility](#wire-level-compatibility)
-   [Authentication & Session Model](#authentication--session-model)
-   [Web Management Layer](#web-management-layer)
-   [Build & Run](#build--run)
-   [Configuration](#configuration)
-   [Docker Deployment](#docker-deployment)
-   [Testing Strategy](#testing-strategy)
-   [Development Philosophy](#development-philosophy)
-   [License](#license)

------------------------------------------------------------------------

# Project Goals

Dialtone exists to:

-   Reconstruct the AOL 3.x server-side protocol stack
-   Achieve wire-level compatibility with classic clients
-   Preserve protocol behavior including sliding window flow control
-   Modernize infrastructure without altering legacy semantics
-   Open-source a previously opaque ecosystem

This project emphasizes correctness at the packet boundary level.

------------------------------------------------------------------------

# Protocol Stack Overview

Dialtone implements the following layered architecture:

    TCP Socket
        ↓
    P3 Framing Layer
        ↓
    Token Dispatcher
        ↓
    Atomizer / FDO Engine
        ↓
    Application Tools (XFER, DOD, IM, etc.)

### P3 (Packet Protocol 3)

-   Fixed header framing
-   CRC-16 validation
-   Sliding window (max 16 outstanding packets)
-   Sequence number wrap (0x10--0x7F range)
-   Implicit ACK via RXseq field
-   Explicit ACK / NAK / HEARTBEAT support
-   INIT handshake logic

The P3 implementation mirrors WAOL semantics, including:

-   Direction bit masking
-   Control packet non-consumption of sequence numbers
-   Burst flow control behavior

------------------------------------------------------------------------

# Implemented Subsystems

## FDO (Form Definition Objects)

-   Dynamic UI rendering
-   Atom stream parsing
-   Object graph construction
-   Nested stream management
-   Proper handling of 8 encoding styles

## XFER

-   Client → server upload
-   Server → client download
-   Thumbnail prelude handling
-   Escape-based encoding (DL_ESC / DL_XOR)
-   Optional RLE support
-   Burst throttling (8-packet transmission window)
-   Proper `fX` completion signaling

## DOD (Download on Demand)

-   Hints-first model
-   Big-endian width/height encoding
-   Multi-form focus prioritization
-   Transaction ID correlation
-   Progressive streaming semantics

## Messaging

-   Instant messaging (real-time)
-   Multi-user chat rooms
-   Session tracking
-   Presence updates

## Session Control

-   LO logout handling
-   TOKEN_DSTAR clean disconnect
-   TOKEN_XS force-off with message
-   Proper state transitions and teardown

------------------------------------------------------------------------

# Architecture

Dialtone runs two servers inside a single JVM:

## 1. AOL Protocol Server (Port 5191)

-   Netty-based TCP implementation
-   Binary P3 framing
-   Token parsing and dispatch
-   Atom compilation/decompilation
-   Tool subsystem routing

## 2. Web Management Server (Port 5200)

-   Javalin HTTP server
-   React SPA frontend
-   OAuth login support
-   User administration
-   API endpoints for automation

Shared SQLite database using HikariCP connection pooling.

------------------------------------------------------------------------

# Wire-Level Compatibility

Dialtone maintains compatibility at the following layers:

  Layer                Compatibility Target
  -------------------- -----------------------------
  P3 framing           Byte-for-byte behavior
  Sequence control     Identical wrap semantics
  Token ordering       Preserved
  Atom encoding        ADA32-compatible
  DOD hints            Endianness-accurate
  XFER block markers   `'b'`, `'d'`, `'e'` honored

The implementation does not approximate --- it mirrors.

------------------------------------------------------------------------

# Authentication & Session Model

-   JWT-based web sessions
-   OAuth login (X and Discord)
-   Email magic link support
-   Ephemeral guest sessions
-   Clean logout and forced disconnect handling

All AOL client authentication flows pass through P3 token exchange and
stateful session binding.

------------------------------------------------------------------------

# Web Management Layer

The management interface provides:

-   Account creation
-   OAuth linking
-   Session visibility
-   Moderation tools
-   Administrative messaging

Designed to coexist with legacy client protocol without modifying it.

------------------------------------------------------------------------

# Build & Run

## Requirements

-   Java 21+
-   Maven 3.x
-   GitHub Packages access (for `atomforge-fdo-java`)

## Setup

``` bash
git clone https://github.com/iconidentify/dialtone.git
cd dialtone

cp application.properties.example src/main/resources/application.properties
# Configure credentials

mvn clean package
java -jar target/dialtone-1.0-SNAPSHOT.jar
```

Default ports:

-   5191 -- AOL protocol server
-   5200 -- Web interface

------------------------------------------------------------------------

# Configuration

Located at:

    src/main/resources/application.properties

### Required

  Setting                 Purpose
  ----------------------- ------------------------
  jwt.secret              32+ character HMAC key
  x.oauth.client.id       OAuth client ID
  x.oauth.client.secret   OAuth client secret

### Optional

  Setting                Purpose
  ---------------------- ---------------------------
  grok.api.key           AI chatbot integration
  discord.oauth.\*       Discord OAuth
  email.resend.api.key   Magic link authentication

------------------------------------------------------------------------

# Docker Deployment

``` bash
docker-compose up -d
```

Production recommendations:

-   Inject secrets via environment variables
-   Use persistent volumes for SQLite
-   Do not bake credentials into images

------------------------------------------------------------------------

# Testing Strategy

``` bash
mvn test
mvn test -Dtest=P3FrameExtractorTest
mvn clean test -Pci
```

CI profile includes:

-   Increased property-based testing iterations
-   Coverage enforcement
-   Protocol boundary validation

------------------------------------------------------------------------

# Development Philosophy

Dialtone follows strict protocol discipline:

-   No speculative behavior
-   No protocol shortcuts
-   Explicit handling of all token cases
-   Strict CRC validation
-   Clear separation of transport and application layers

Reverse engineering is treated as archaeology, not improvisation.

------------------------------------------------------------------------

# License

MIT License.

------------------------------------------------------------------------

Dialtone is a preservation project, a research project, and a
production-grade server implementation.

It is intentionally nerdy.
