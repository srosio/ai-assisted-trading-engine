# GitHub Actions Quick Start

Quick reference for common CI/CD operations.

## First-Time Setup (5 minutes)

### 1. Create S3 Bucket
```bash
aws s3 mb s3://ai-trading-deployment-bucket --region us-east-1
```

### 2. Add GitHub Secrets

Go to: **Settings → Secrets and variables → Actions**

Required secrets:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_DEPLOYMENT_BUCKET`
- `WEBHOOK_API_KEY`
- `CLAUDE_API_KEY`
- `BINANCE_API_KEY`
- `BINANCE_API_SECRET`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`

### 3. Push to Trigger Deployment
```bash
git add .
git commit -m "Enable GitHub Actions"
git push origin develop
```

✅ **Done!** Deployment runs automatically.

## Daily Workflow

### Deploy Changes
```bash
# 1. Make changes
vim src/main/java/com/trading/engine/service/RuleEngineService.java

# 2. Commit and push to develop
git add .
git commit -m "Update trading rules"
git push origin develop

# 3. GitHub Actions automatically:
#    - Runs tests
#    - Builds JAR
#    - Deploys to AWS
#    - Updates Lambda
```

### Create Pull Request
```bash
# 1. Create feature branch
git checkout -b feature/new-strategy

# 2. Make changes and push
git push origin feature/new-strategy

# 3. Open PR on GitHub
# PR validation workflow runs automatically (build + test only)

# 4. After approval, merge to develop
# Deployment workflow runs automatically
```

### Manual Deployment
```bash
# GitHub Actions → Build and Deploy to AWS → Run workflow
# Select branch → Run workflow
```

## Monitoring

### View Workflow Status
1. Go to **Actions** tab
2. Click on latest workflow run
3. View logs for each job

### Check Deployment
```bash
# Get API endpoint
aws cloudformation describe-stacks \
  --stack-name ai-trading-engine \
  --query 'Stacks[0].Outputs'

# View Lambda logs
aws logs tail /aws/lambda/ai-trading-engine --follow
```

### Workflow Run Time
- **Build & Test**: ~3-5 minutes
- **Deploy to AWS**: ~2-3 minutes
- **Total**: ~5-8 minutes

## Troubleshooting

### Workflow Fails on Build
```bash
# Run locally to debug
./gradlew clean shadowJar

# Check test failures
./gradlew test --info
```

### Workflow Fails on Deploy
```bash
# Check AWS credentials
aws sts get-caller-identity

# Verify S3 bucket exists
aws s3 ls s3://ai-trading-deployment-bucket

# Check CloudFormation stack
aws cloudformation describe-stacks --stack-name ai-trading-engine
```

### Re-run Failed Workflow
1. Go to **Actions** tab
2. Click failed workflow
3. Click **Re-run all jobs**

## Branch Strategy

| Branch | Workflow | Behavior |
|--------|----------|----------|
| `develop` | deploy.yml | Auto-deploy to AWS |
| `main` | deploy.yml | Auto-deploy to AWS |
| Feature branches | None | No auto-deploy |
| Pull Requests | pr-validation.yml | Build & test only |

## Cost Tracking

- **GitHub Actions**: Free for public repos, 2000 min/month for private
- **Typical workflow**: ~6 minutes
- **Monthly capacity**: ~330 deployments (on free tier)

## Quick Commands

```bash
# Manual local deployment (bypass GitHub Actions)
./scripts/deploy.sh ai-trading-deployment-bucket

# Update Lambda env vars manually
aws lambda update-function-configuration \
  --function-name ai-trading-engine \
  --environment Variables="{CLAUDE_API_KEY=new_key}"

# View latest deployment
aws cloudformation describe-stack-events \
  --stack-name ai-trading-engine \
  --max-items 5

# Roll back to previous version
aws lambda update-function-code \
  --function-name ai-trading-engine \
  --s3-bucket ai-trading-deployment-bucket \
  --s3-key <previous-version-key>
```

## Tips

1. **Test locally first**: Run `./gradlew test` before pushing
2. **Use feature branches**: Don't push directly to develop/main
3. **Monitor costs**: Check AWS billing for Lambda invocations
4. **Review logs**: Always check CloudWatch logs after deployment
5. **Version tagging**: Tag releases for easy rollback

## Need Help?

- **Detailed setup**: `.github/SETUP.md`
- **Full documentation**: `CLAUDE.md`
- **Workflow files**: `.github/workflows/`
