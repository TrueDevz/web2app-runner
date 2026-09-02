# 🚀 WebToApp Dedicated Cloud Android Runner

[![Build Android App](https://github.com/TrueDevz/web2app-runner/actions/workflows/build-android.yml/badge.svg)](https://github.com/TrueDevz/web2app-runner/actions/workflows/build-android.yml)

This is the dedicated, high-performance **Cloud Android Compilation Worker** for WebToApp SaaS.

## ⚡ Highlights
- **100% Free & Unlimited Builds**: As a public GitHub repository, GitHub Actions provides **unlimited free runner minutes**.
- **Modern Android Stack**: Ubuntu Latest with OpenJDK 21, Android SDK Tools 34.0.0, and Gradle.
- **Automated Cloudflare R2 Upload**: Compiled Signed `.apk` and Google Play `.aab` bundles are uploaded directly to Cloudflare R2 CDN with zero bandwidth fees.
- **Webhook Callback**: Automatically notifies your main WebToApp SaaS backend (`/api/webhooks/build-complete`) upon build completion.

## 🔑 Required GitHub Secrets
Configure these under **Settings ➡️ Secrets and variables ➡️ Actions**:
- `R2_ACCOUNT_ID`
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_BUCKET_NAME`
- `R2_PUBLIC_DOMAIN`
