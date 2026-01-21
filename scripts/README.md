# Scripts Directory

This directory contains deployment scripts for the AI Trading Engine.

## AWS Lambda Deployment

### `deploy.sh` - Deploy to AWS Lambda

Deploys the application to AWS Lambda using CloudFormation.

**Prerequisites:**
1. AWS CLI installed and configured
2. An S3 bucket to store deployment artifacts
3. Proper AWS credentials with CloudFormation and Lambda permissions

**Usage:**

```bash
# Build and deploy to AWS
./scripts/deploy.sh <your-s3-bucket-name>

# Example
./scripts/deploy.sh ai-trading-deployment-bucket
```

**What it does:**
1. Builds the Spring Boot JAR using Gradle (`./gradlew bootJar -x test`)
2. Packages the CloudFormation template with the JAR artifact
3. Uploads to the specified S3 bucket
4. Deploys the CloudFormation stack (`ai-trading-engine`)
5. Creates/updates Lambda function with SnapStart enabled
6. Creates/updates DynamoDB table and API Gateway

**After deployment:**
- The API Gateway endpoint will be displayed in the CloudFormation outputs
- Set environment variables via AWS Console or CLI:

```bash
aws lambda update-function-configuration \
  --function-name TradingEngineFunction \
  --environment Variables="{
    WEBHOOK_API_KEY=your_key,
    CLAUDE_API_KEY=your_claude_key,
    BINANCE_API_KEY=your_binance_key,
    BINANCE_API_SECRET=your_binance_secret,
    TELEGRAM_BOT_TOKEN=your_telegram_token,
    TELEGRAM_CHAT_ID=your_chat_id
  }"
```

## Monitoring

View Lambda logs:
```bash
aws logs tail /aws/lambda/TradingEngineFunction --follow
```

Check stack status:
```bash
aws cloudformation describe-stacks \
  --stack-name ai-trading-engine \
  --query 'Stacks[0].{Status:StackStatus,Outputs:Outputs}'
```

## Updating the Application

To deploy updates:
```bash
./scripts/deploy.sh <your-s3-bucket-name>
```

The script will update the existing stack with the new code.
