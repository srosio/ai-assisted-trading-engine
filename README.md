# AI-Assisted Crypto Trading Engine (Serverless)

A professional, rule-based crypto trading system that uses AI **only** for market context analysis. Built with discipline, risk control, and auditability at its core.

**Now Re-architected for AWS Serverless.**

## Quick Start (Local Build)

```bash
# 1. Clone
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Build Project
./gradlew build -x test

# 3. Deploy to AWS (Requires AWS CLI configured)
./scripts/deploy.sh <your-s3-bucket-name>
```

## Features

- ✅ **Serverless Architecture**: Runs on AWS Lambda with Snapdragon (Java 21) for zero-management scaling.
- ✅ **Rule-Based Trading**: Strict, non-negotiable trading rules for risk management.
- 🤖 **AI Context Analysis**: Claude AI provides market context (not trading decisions).
- 📊 **TradingView Integration**: Webhook-based signal processing via API Gateway.
- 🔒 **Risk Management**: Position sizing, stop-loss, R-multiple tracking.
- 📝 **NoSQL Journal**: High-performance trade journal using Amazon DynamoDB.
- 🔔 **Telegram Notifications**: Real-time alerts for valid trade setups.

## Architecture

**Technology Stack:**
- **Runtime**: Java 21 (AWS Lambda SnapStart)
- **Framework**: Spring Boot 3 + Spring Cloud Function
- **Database**: Amazon DynamoDB (On-Demand)
- **AI**: Spring AI with Claude (Anthropic)
- **Infrastructure**: AWS SAM / CloudFormation

**Key Changes from Legacy:**
- **No Embedded Server**: Tomcat replaced by AWS Lambda invocation.
- **No Connection Pools**: PostgreSQL replaced by DynamoDB HTTP API.
- **Lazy Initialization**: Optimized for fast cold starts (~500ms with SnapStart).

## Project Structure

```
├── src/main/java/com/trading/engine/
│   ├── config/              # Functional Beans & Lambda Config
│   ├── domain/              # DynamoDB Beans & Domain Models
│   ├── service/             # Business Logic (Rule Engine, Signal Processing)
│   ├── repository/          # DynamoDB Enhanced Client Repositories
│   └── StreamLambdaHandler.java  # AWS Lambda Entry Point
├── template.yaml            # AWS Infrastructure Definition (SAM)
├── scripts/                 # Deployment scripts
└── build.gradle             # Build configuration
```

## Trading Philosophy

This engine enforces **strict, non-negotiable rules**:

1. **Risk Management**: 0.5-1% risk per trade, 2R daily loss limit
2. **Session Filtering**: London/NY sessions (configurable)
3. **Setup Quality**: Only A/B quality setups (C blocked)
4. **Higher Timeframe Alignment**: Required for trade validation
5. **Volatility Control**: High volatility setups blocked
6. **AI for Context Only**: AI analyzes but doesn't decide

**The rules cannot be overridden.** AI provides context; rules make decisions.

## Deployment

The project includes a `deploy.sh` script that automates the AWS CloudFormation deployment.

**Prerequisites:**
1.  AWS CLI installed and configured.
2.  An S3 bucket to store deployment artifacts.
3.  DynamoDB Table `journal_entries` (created automatically by template).

```bash
./scripts/deploy.sh my-deployment-bucket
```
