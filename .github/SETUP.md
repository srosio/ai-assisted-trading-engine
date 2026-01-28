# GitHub Actions CI/CD Setup

This document explains how to configure GitHub Actions for automatic deployment to AWS.

## Overview

Two workflows are configured:

1. **`deploy.yml`**: Builds, tests, and deploys to AWS on push to `develop` or `main` branches
2. **`pr-validation.yml`**: Validates pull requests by building and testing (no deployment)

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **S3 Bucket** for CloudFormation deployment artifacts
3. **GitHub Repository** with Actions enabled

## Required GitHub Secrets

Navigate to your repository: **Settings → Secrets and variables → Actions → New repository secret**

Add the following secrets:

### AWS Credentials

| Secret Name | Description | Example |
|------------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS IAM access key with deployment permissions | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |
| `AWS_DEPLOYMENT_BUCKET` | S3 bucket name for CloudFormation artifacts | `ai-trading-deployment-bucket` |

### Application Secrets

| Secret Name | Description | Required |
|------------|-------------|----------|
| `WEBHOOK_API_KEY` | API key for TradingView webhook authentication | Yes |
| `CLAUDE_API_KEY` | Anthropic Claude API key | Yes |
| `BINANCE_API_KEY` | Binance Futures API key | Yes |
| `BINANCE_API_SECRET` | Binance Futures API secret | Yes |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token for notifications | Yes |
| `TELEGRAM_CHAT_ID` | Telegram chat ID for notifications | Yes |

## AWS IAM Permissions

The AWS user/role needs these permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateStack",
        "cloudformation:UpdateStack",
        "cloudformation:DescribeStacks",
        "cloudformation:DescribeStackEvents",
        "cloudformation:GetTemplateSummary"
      ],
      "Resource": "arn:aws:cloudformation:*:*:stack/ai-trading-engine/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "lambda:CreateFunction",
        "lambda:UpdateFunctionCode",
        "lambda:UpdateFunctionConfiguration",
        "lambda:GetFunction",
        "lambda:PublishVersion",
        "lambda:CreateAlias",
        "lambda:UpdateAlias"
      ],
      "Resource": "arn:aws:lambda:*:*:function:ai-trading-engine"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole",
        "iam:GetRole",
        "iam:AttachRolePolicy",
        "iam:PassRole"
      ],
      "Resource": "arn:aws:iam::*:role/ai-trading-engine-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:CreateTable",
        "dynamodb:DescribeTable",
        "dynamodb:UpdateTable"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/journal_entries",
        "arn:aws:dynamodb:*:*:table/ai_cache"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "apigateway:*"
      ],
      "Resource": "arn:aws:apigateway:*::/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::YOUR-DEPLOYMENT-BUCKET/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:DescribeLogGroups",
        "logs:PutRetentionPolicy"
      ],
      "Resource": "arn:aws:logs:*:*:log-group:/aws/lambda/ai-trading-engine*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "cloudwatch:PutMetricAlarm",
        "cloudwatch:DescribeAlarms"
      ],
      "Resource": "*"
    }
  ]
}
```

## Setup Steps

### 1. Create S3 Deployment Bucket

```bash
# Create bucket (replace with your bucket name)
aws s3 mb s3://ai-trading-deployment-bucket --region us-east-1

# Enable versioning (recommended)
aws s3api put-bucket-versioning \
  --bucket ai-trading-deployment-bucket \
  --versioning-configuration Status=Enabled
```

### 2. Create IAM User for GitHub Actions

```bash
# Create IAM user
aws iam create-user --user-name github-actions-trading-engine

# Attach policy (save the JSON policy above as policy.json)
aws iam put-user-policy \
  --user-name github-actions-trading-engine \
  --policy-name DeploymentPolicy \
  --policy-document file://policy.json

# Create access key
aws iam create-access-key --user-name github-actions-trading-engine
```

Save the `AccessKeyId` and `SecretAccessKey` from the output.

### 3. Configure GitHub Secrets

1. Go to your repository on GitHub
2. Navigate to **Settings → Secrets and variables → Actions**
3. Click **New repository secret**
4. Add all secrets listed in the table above

### 4. Test the Workflow

#### Option A: Push to develop/main

```bash
git add .
git commit -m "Set up GitHub Actions CI/CD"
git push origin develop
```

#### Option B: Manual trigger

1. Go to **Actions** tab in GitHub
2. Select **Build and Deploy to AWS** workflow
3. Click **Run workflow**
4. Choose branch and click **Run workflow**

## Workflow Behavior

### On Push to `develop` or `main`

1. **Build and Test** job runs:
   - Checks out code
   - Sets up Java 21 (Corretto)
   - Runs tests
   - Builds Shadow JAR
   - Uploads JAR as artifact

2. **Deploy to AWS** job runs (after successful build):
   - Downloads JAR artifact
   - Configures AWS credentials
   - Packages CloudFormation template
   - Deploys stack to AWS
   - Updates Lambda environment variables
   - Outputs API Gateway endpoint

### On Pull Request

1. Runs build and test only (no deployment)
2. Validates that JAR builds successfully
3. Reports results in PR checks

## Monitoring Deployments

### View Workflow Runs

- Go to **Actions** tab in your GitHub repository
- Click on a workflow run to see detailed logs

### View Deployment Summary

After successful deployment, check the workflow summary for:
- Stack name
- AWS region
- API Gateway endpoint
- Deployed commit SHA

### AWS Logs

```bash
# View Lambda logs
aws logs tail /aws/lambda/ai-trading-engine --follow

# Check stack status
aws cloudformation describe-stacks --stack-name ai-trading-engine
```

## Customization

### Deploy to Different Environments

To deploy to staging/production environments, modify `deploy.yml`:

```yaml
env:
  STACK_NAME: ${{ github.ref_name == 'main' && 'ai-trading-engine-prod' || 'ai-trading-engine-dev' }}
```

### Deploy Only on Tags

Change the trigger in `deploy.yml`:

```yaml
on:
  push:
    tags:
      - 'v*'  # Only deploy on version tags like v1.0.0
```

### Add Slack/Discord Notifications

Add a notification step at the end of `deploy.yml`:

```yaml
- name: Notify Slack
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
    payload: |
      {
        "text": "Deployed to AWS: ${{ steps.get-endpoint.outputs.endpoint }}"
      }
```

## Troubleshooting

### Build Fails

- **Issue**: Gradle build fails
- **Solution**: Check test logs, ensure Java 21 compatibility

### Deployment Fails

- **Issue**: CloudFormation stack creation fails
- **Solution**: Check AWS credentials, IAM permissions, and CloudFormation logs

### Environment Variables Not Updated

- **Issue**: Lambda environment variables not set
- **Solution**: The workflow includes `|| echo "continuing..."` to prevent failure. Update manually:

```bash
aws lambda update-function-configuration \
  --function-name ai-trading-engine \
  --environment Variables="{...}"
```

### S3 Bucket Access Denied

- **Issue**: Cannot upload to S3 bucket
- **Solution**: Verify bucket name in secrets and IAM permissions

## Security Best Practices

1. **Rotate Secrets Regularly**: Update AWS and API keys periodically
2. **Principle of Least Privilege**: Only grant necessary IAM permissions
3. **Enable MFA**: Use MFA for AWS accounts with deployment access
4. **Audit Logs**: Monitor CloudTrail for deployment activities
5. **Branch Protection**: Require PR reviews before merging to `main`
6. **Separate Environments**: Use different AWS accounts for staging/production

## Cost Optimization

GitHub Actions provides:
- **2,000 minutes/month** free for private repos
- **Unlimited minutes** for public repos

This workflow uses approximately:
- **5-8 minutes** per deployment
- **~40-60 deployments/month** within free tier

## Support

- **GitHub Actions Docs**: https://docs.github.com/actions
- **AWS CloudFormation Docs**: https://docs.aws.amazon.com/cloudformation/
- **Repository Issues**: Report issues in this repository
