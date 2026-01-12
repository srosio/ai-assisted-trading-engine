# Render.com Deployment Guide

## Prerequisites

1. A [Render.com](https://render.com) account
2. Your repository pushed to GitHub/GitLab/Bitbucket

## Deployment Steps

### 1. Connect Your Repository

1. Go to [Render Dashboard](https://dashboard.render.com/)
2. Click **"New"** → **"Blueprint"**
3. Connect your Git repository
4. Render will automatically detect `render.yaml`

### 2. Set Environment Variables

After creating the blueprint, go to each service and set these environment variables:

**Required Variables:**
- `TELEGRAM_BOT_TOKEN` - Your Telegram bot token
- `TELEGRAM_CHAT_ID` - Your Telegram chat ID

**Optional Variables (for live trading):**
- `CLAUDE_API_KEY` - Your Anthropic Claude API key (optional, falls back to static analysis)
- `BINANCE_API_KEY` - Your Binance API key
- `BINANCE_API_SECRET` - Your Binance API secret
- `WEBHOOK_API_KEY` - Webhook authentication key (optional in dev mode)
- `ACCOUNT_BALANCE` - Your trading account balance (default: 10000)

### 3. Deploy

Click **"Apply"** and Render will:
- Create PostgreSQL database
- Create Redis instance
- Build and deploy your Spring Boot application
- Set up automatic HTTPS

## Services Created

### 1. **ai-trading-engine** (Web Service)
- **Type:** Web Service
- **Plan:** Free tier
- **Port:** 8080
- **Health Check:** `/api/webhook/health`
- **Auto-deploy:** On git push

### 2. **trading-engine-postgres** (Database)
- **Type:** PostgreSQL 14
- **Plan:** Free tier (1GB storage)
- **Auto-backup:** Enabled
- **Connection:** Automatically linked to app

### 3. **trading-engine-redis** (Cache)
- **Type:** Redis 7
- **Plan:** Free tier
- **Connection:** Automatically linked to app

## Webhook URL

After deployment, your webhook URL will be:
```
https://ai-trading-engine.onrender.com/api/webhook/tradingview?apiKey=YOUR_WEBHOOK_KEY
```

Configure this in TradingView alert settings.

## Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CLAUDE_API_KEY` | No | mock-key | Anthropic API key (falls back to static analysis) |
| `BINANCE_API_KEY` | No | mock-key | Binance futures API key |
| `BINANCE_API_SECRET` | No | mock-secret | Binance futures API secret |
| `TELEGRAM_BOT_TOKEN` | Yes | - | Telegram bot token for notifications |
| `TELEGRAM_CHAT_ID` | Yes | - | Telegram chat ID for notifications |
| `WEBHOOK_API_KEY` | No | - | Webhook authentication (optional in dev) |
| `ACCOUNT_BALANCE` | No | 10000 | Trading account balance |
| `DB_PASSWORD` | Auto | - | Auto-generated PostgreSQL password |

## Free Tier Limits

Render.com free tier includes:
- ✅ 750 hours/month runtime (enough for 1 service 24/7)
- ✅ PostgreSQL with 1GB storage
- ✅ Redis instance
- ✅ Automatic HTTPS
- ⚠️ Services sleep after 15 minutes of inactivity (cold start ~30s)
- ⚠️ 100GB bandwidth/month

## Monitoring

### Health Check
```bash
curl https://ai-trading-engine.onrender.com/api/webhook/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "AI-Assisted Trading Engine"
}
```

### Logs
View logs in Render Dashboard:
1. Go to your service
2. Click **"Logs"** tab
3. Real-time streaming logs

### Telegram Notifications
- ✅ Valid signals sent to Telegram
- ❌ Invalid signals sent to Telegram
- 🚨 Processing errors sent to Telegram

## Troubleshooting

### Service Won't Start
1. Check logs for errors
2. Verify database connection string
3. Ensure all required env vars are set

### Webhook Returns 401 Unauthorized
- Set `WEBHOOK_API_KEY` in Render dashboard
- Update TradingView webhook URL with `?apiKey=YOUR_KEY`
- Or leave unset for dev mode (no auth required)

### AI Analysis Not Working
- Normal behavior if `CLAUDE_API_KEY` not set
- System automatically falls back to static analysis
- Check logs for "Using static analysis" messages

### Database Connection Failed
- Database takes ~30 seconds to start on first deploy
- Check `SPRING_DATASOURCE_URL` is properly linked
- Verify PostgreSQL service is running

## Updating Your Application

### Automatic Deployment
Push to your git repository:
```bash
git push origin main
```
Render automatically rebuilds and redeploys.

### Manual Deployment
1. Go to Render Dashboard
2. Select your service
3. Click **"Manual Deploy"** → **"Deploy latest commit"**

## Cost Optimization

### Stay on Free Tier
- Use only 1 web service (750 hours/month)
- Keep database under 1GB
- Monitor bandwidth usage

### Prevent Cold Starts
Use a free uptime monitor (e.g., UptimeRobot) to ping your health endpoint every 10 minutes:
```
https://ai-trading-engine.onrender.com/api/webhook/health
```

## Production Checklist

Before going live:
- [ ] Set all required environment variables
- [ ] Configure real Binance API keys
- [ ] Set strong `WEBHOOK_API_KEY`
- [ ] Test webhook with TradingView
- [ ] Verify Telegram notifications
- [ ] Monitor first few signals
- [ ] Set up uptime monitoring
- [ ] Configure backup strategy

## Support

- **Render Documentation:** https://render.com/docs
- **Application Logs:** Render Dashboard → Your Service → Logs
- **Telegram Errors:** Check your Telegram chat for error notifications
