#!/bin/bash
set -e

STACK_NAME="ai-trading-engine"
REGION="us-east-1"
BUCKET_NAME=$1

if [ -z "$BUCKET_NAME" ]; then
  echo "Usage: ./scripts/deploy.sh <s3-bucket-name>"
  echo "Please provide an S3 bucket name to store deployment artifacts."
  exit 1
fi

echo "Building project..."
./gradlew bootJar -x test

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
  --region "$REGION"

echo "Deployment complete!"
aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs' \
  --region "$REGION"
