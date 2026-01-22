#!/bin/bash
set -e

STACK_NAME="ai-trading-engine"
BUCKET_NAME=$1
REGION=${2:-ap-southeast-1}

if [ -z "$BUCKET_NAME" ]; then
  echo "Usage: ./scripts/deploy.sh <s3-bucket-name> [region]"
  exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "Error: AWS credentials not configured. Run 'aws configure' first."
  exit 1
fi

# Check if bucket exists, create if not
if ! aws s3 ls "s3://$BUCKET_NAME" >/dev/null 2>&1; then
  echo "Creating S3 bucket: $BUCKET_NAME"
  aws s3 mb "s3://$BUCKET_NAME" --region "$REGION"
fi

echo "Building project..."
./gradlew clean shadowJar

echo "Packaging CloudFormation template..."
aws cloudformation package \
  --template-file template.yaml \
  --s3-bucket "$BUCKET_NAME" \
  --output-template-file packaged-template.yaml \
  --region "$REGION"

echo "Deploying stack '$STACK_NAME'..."
aws cloudformation deploy \
  --template-file packaged-template.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_IAM \
  --region "$REGION" \
  --no-fail-on-empty-changeset

echo "Deployment complete!"
echo "API Endpoint:"
aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text \
  --region "$REGION"
