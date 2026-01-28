# GitHub Actions CI/CD Documentation

This directory contains GitHub Actions workflows for automated build and deployment.

## 📁 Files Overview

```
.github/
├── workflows/
│   ├── deploy.yml          # Main deployment workflow
│   └── pr-validation.yml   # Pull request validation
├── SETUP.md                # Detailed setup instructions
├── QUICKSTART.md           # Quick reference guide
├── WORKFLOWS.md            # Architecture diagrams
└── README.md               # This file
```

## 🚀 Quick Links

- **First-time setup?** → Read [SETUP.md](SETUP.md)
- **Quick commands?** → Read [QUICKSTART.md](QUICKSTART.md)
- **How it works?** → Read [WORKFLOWS.md](WORKFLOWS.md)

## 🔧 What's Configured

### Workflows

1. **deploy.yml** - Automated deployment
   - Triggers: Push to `develop` or `main`
   - Actions: Build → Test → Deploy to AWS
   - Duration: ~5-8 minutes

2. **pr-validation.yml** - PR validation
   - Triggers: Pull requests to `develop` or `main`
   - Actions: Build → Test (no deployment)
   - Duration: ~3-5 minutes

### AWS Resources Deployed

- ✅ Lambda Function (Java 21, SnapStart enabled)
- ✅ DynamoDB Tables (journal_entries, ai_cache)
- ✅ API Gateway (HTTP API)
- ✅ CloudWatch Logs & Alarms
- ✅ IAM Roles

## 📋 Prerequisites

Before using GitHub Actions, you need:

1. ✅ AWS account with appropriate permissions
2. ✅ S3 bucket for CloudFormation artifacts
3. ✅ GitHub Secrets configured (9 required)

See [SETUP.md](SETUP.md) for detailed instructions.

## 🎯 Usage

### Deploy to AWS
```bash
# Push to develop or main
git push origin develop

# Or trigger manually via GitHub Actions UI
```

### Validate Pull Request
```bash
# Create and push feature branch
git checkout -b feature/my-feature
git push origin feature/my-feature

# Open PR on GitHub - validation runs automatically
```

## 📊 Workflow Status

Check workflow runs: **GitHub → Actions tab**

## 🆘 Need Help?

| Question | Document |
|----------|----------|
| How do I set up GitHub Actions? | [SETUP.md](SETUP.md) |
| What are the quick commands? | [QUICKSTART.md](QUICKSTART.md) |
| How does it work internally? | [WORKFLOWS.md](WORKFLOWS.md) |
| Something broke, what now? | [SETUP.md#troubleshooting](SETUP.md#troubleshooting) |

## 🔐 Security

- All credentials stored as GitHub Secrets (encrypted)
- AWS IAM follows least-privilege principle
- Secrets never appear in logs
- Deployment uses temporary credentials

## 💰 Cost

- **GitHub Actions**: Free for public repos, 2000 min/month for private
- **AWS**: Covered by free tier for moderate usage
- **Estimated**: ~$5-10/month for typical development workload

## 📝 License

Same as main project (Proprietary)
