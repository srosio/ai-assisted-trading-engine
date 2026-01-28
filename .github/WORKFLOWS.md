# GitHub Actions Workflow Architecture

## Workflow Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      GitHub Repository                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                            │
        │                                            │
   Push to develop/main                    Create Pull Request
        │                                            │
        ▼                                            ▼
┌──────────────────┐                        ┌──────────────────┐
│   deploy.yml     │                        │ pr-validation.yml│
│                  │                        │                  │
│ 1. Build & Test  │                        │ 1. Build & Test  │
│ 2. Deploy to AWS │                        │ 2. Validate JAR  │
└──────────────────┘                        └──────────────────┘
        │                                            │
        │                                            │
        ▼                                            ▼
┌──────────────────┐                        ┌──────────────────┐
│   AWS Lambda     │                        │   PR Checks      │
│                  │                        │                  │
│ - Function       │                        │ ✅ Build Success │
│ - API Gateway    │                        │ ✅ Tests Pass    │
│ - DynamoDB       │                        │                  │
└──────────────────┘                        └──────────────────┘
```

## Deploy Workflow (deploy.yml)

### Trigger Events
- Push to `develop` branch
- Push to `main` branch
- Manual workflow dispatch

### Job 1: Build and Test

```
Checkout Code
    ↓
Setup Java 21 (Corretto)
    ↓
Cache Gradle Dependencies
    ↓
Run Tests (./gradlew test)
    ↓
Build Shadow JAR (./gradlew shadowJar)
    ↓
Upload JAR Artifact
```

**Duration**: ~3-5 minutes

### Job 2: Deploy to AWS (runs after Job 1 succeeds)

```
Download JAR Artifact
    ↓
Configure AWS Credentials
    ↓
Package CloudFormation Template
    ↓
Deploy Stack to AWS
    ↓
Update Lambda Environment Variables
    ↓
Wait for Function Update
    ↓
Get API Gateway Endpoint
    ↓
Display Deployment Summary
```

**Duration**: ~2-3 minutes

### AWS Resources Created/Updated

1. **Lambda Function**: `ai-trading-engine`
   - Runtime: Java 21
   - Memory: 2048 MB
   - Timeout: 30 seconds
   - SnapStart: Enabled

2. **DynamoDB Tables**:
   - `journal_entries` (trade journal)
   - `ai_cache` (AI response caching)

3. **API Gateway**: HTTP API with routes:
   - POST `/api/webhook/tradingview`
   - GET `/api/journal`
   - GET `/api/journal/statistics`

4. **CloudWatch**:
   - Log group: `/aws/lambda/ai-trading-engine`
   - Alarms for usage limits

5. **IAM Role**: Lambda execution role with DynamoDB permissions

## PR Validation Workflow (pr-validation.yml)

### Trigger Events
- Pull request to `develop` branch
- Pull request to `main` branch

### Job: Validate

```
Checkout Code
    ↓
Setup Java 21 (Corretto)
    ↓
Cache Gradle Dependencies
    ↓
Run Tests
    ↓
Build Shadow JAR
    ↓
Verify JAR Exists
    ↓
Display Summary
```

**Duration**: ~3-5 minutes

### Output
- ✅ Tests passed
- ✅ Build successful
- 📦 JAR size displayed
- No deployment (validation only)

## Environment Variables Flow

```
GitHub Secrets
    │
    ├── AWS_ACCESS_KEY_ID ──────────┐
    ├── AWS_SECRET_ACCESS_KEY ──────┤
    ├── AWS_DEPLOYMENT_BUCKET ──────┤──→ CloudFormation Package
    └── Application Secrets ─────────┤
                                     │
                                     ▼
                            AWS Lambda Function
                                     │
                                     ├── WEBHOOK_API_KEY
                                     ├── CLAUDE_API_KEY
                                     ├── BINANCE_API_KEY
                                     ├── BINANCE_API_SECRET
                                     ├── TELEGRAM_BOT_TOKEN
                                     └── TELEGRAM_CHAT_ID
```

## Deployment Timeline

```
0:00  Push to develop
      ↓
0:10  Workflow triggered
      ↓
0:30  Java setup complete
      ↓
1:00  Dependencies cached
      ↓
2:30  Tests complete (✅)
      ↓
3:30  JAR built (✅)
      ↓
4:00  JAR uploaded
      ↓
4:30  AWS credentials configured
      ↓
5:00  Template packaged to S3
      ↓
6:00  CloudFormation stack updated
      ↓
7:00  Lambda function updated
      ↓
7:30  Environment variables set
      ↓
8:00  Deployment complete (✅)
      ↓
      API endpoint displayed
```

## Security Model

```
┌─────────────────────────────────────┐
│      GitHub Repository              │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   GitHub Secrets (Encrypted)  │  │
│  │   - AWS Credentials           │  │
│  │   - API Keys                  │  │
│  └──────────────────────────────┘  │
│               │                     │
└───────────────┼─────────────────────┘
                │ (Injected at runtime)
                ▼
┌─────────────────────────────────────┐
│    GitHub Actions Runner            │
│    (Ephemeral Environment)          │
│                                     │
│    - Credentials only in memory     │
│    - Destroyed after workflow       │
│    - No persistent storage          │
└─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────┐
│           AWS Account               │
│                                     │
│    IAM User: github-actions-*       │
│    - Limited permissions            │
│    - CloudFormation access          │
│    - Lambda access                  │
│    - DynamoDB access                │
└─────────────────────────────────────┘
```

## Artifact Storage

```
GitHub Actions Artifact
    │
    ├── Name: trading-engine-jar
    ├── Path: build/libs/ai-assisted-trading-engine-1.0.0.jar
    ├── Retention: 7 days
    └── Size: ~60-80 MB
         │
         └──→ Downloaded by Deploy job
              │
              └──→ Packaged to S3
                   │
                   └──→ Deployed to Lambda
```

## Failure Handling

```
Job Fails
    │
    ├── Build Fails ──→ No deployment
    │                   (Safe)
    │
    ├── Tests Fail ───→ No deployment
    │                   (Safe)
    │
    ├── Deploy Fails ─→ Existing Lambda unaffected
    │                   (Rollback automatic)
    │
    └── Env Vars Fail → Deployment continues
                        (Manual update needed)
```

## Monitoring Points

1. **GitHub Actions UI**
   - Workflow status
   - Job logs
   - Artifact download

2. **AWS CloudFormation**
   - Stack events
   - Change sets
   - Rollback status

3. **AWS Lambda**
   - Function configuration
   - Version/aliases
   - Invocation metrics

4. **CloudWatch Logs**
   - Application logs
   - Error tracking
   - Performance metrics

## Optimization Features

1. **Gradle Caching**
   - Dependencies cached between runs
   - ~2 minutes saved per build

2. **Artifact Upload/Download**
   - JAR built once, used by deploy job
   - Avoids rebuilding

3. **Parallel Jobs**
   - Build and test can run independently
   - Deploy waits for build completion

4. **Conditional Steps**
   - Environment variable update errors don't fail deployment
   - Graceful degradation

## Branch Strategy Recommendations

### Option 1: GitFlow (Current Setup)
```
develop ──→ Auto-deploy to DEV
    │
    └──→ main ──→ Auto-deploy to PROD
```

### Option 2: Trunk-Based
```
main ──→ Auto-deploy to PROD
    │
    └──→ Feature branches ──→ PR validation only
```

### Option 3: Tag-Based
```
Tags (v1.0.0) ──→ Auto-deploy to PROD
    │
    └──→ All branches ──→ PR validation only
```

**Current**: Option 1 (GitFlow) is configured
