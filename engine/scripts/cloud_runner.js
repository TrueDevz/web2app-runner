const fs = require('fs-extra');
const path = require('path');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const { compileApp } = require('./build_compiler');

async function uploadToR2(filePath, r2Key, r2Config) {
    if (!fs.existsSync(filePath)) {
        console.warn(`[R2] File does not exist at ${filePath}, skipping upload.`);
        return null;
    }

    try {
        const client = new S3Client({
            region: 'auto',
            endpoint: `https://${r2Config.accountId}.r2.cloudflarestorage.com`,
            credentials: {
                accessKeyId: r2Config.accessKeyId,
                secretAccessKey: r2Config.secretAccessKey,
            },
        });

        const fileBuffer = await fs.readFile(filePath);
        let contentType = 'application/octet-stream';
        if (filePath.endsWith('.apk')) contentType = 'application/vnd.android.package-archive';
        else if (filePath.endsWith('.aab')) contentType = 'application/octet-stream';
        else if (filePath.endsWith('.png')) contentType = 'image/png';

        console.log(`[R2] Uploading ${path.basename(filePath)} (${fileBuffer.length} bytes) to ${r2Key}...`);
        await client.send(
            new PutObjectCommand({
                Bucket: r2Config.bucketName,
                Key: r2Key,
                Body: fileBuffer,
                ContentType: contentType,
            })
        );

        const cdnDomain = r2Config.publicDomain ? r2Config.publicDomain.replace(/\/$/, '') : '';
        const downloadUrl = cdnDomain ? `${cdnDomain}/${r2Key}` : `https://${r2Config.bucketName}.${r2Config.accountId}.r2.cloudflarestorage.com/${r2Key}`;
        console.log(`[R2] Successfully uploaded! URL: ${downloadUrl}`);
        return downloadUrl;
    } catch (err) {
        console.error(`[R2] Upload failed for ${filePath}:`, err.message);
        return null;
    }
}

async function main() {
    console.log('🚀 WebToApp Cloud Android Runner Initialized (GitHub Actions)...');

    // Read config from file or environment
    let config = null;
    const configPath = process.env.BUILD_CONFIG_PATH || path.join(__dirname, '..', 'build_config.json');

    if (fs.existsSync(configPath)) {
        config = fs.readJsonSync(configPath);
    } else if (process.env.BUILD_CONFIG_JSON) {
        config = JSON.parse(process.env.BUILD_CONFIG_JSON);
    } else {
        console.error('❌ Error: No build config provided (BUILD_CONFIG_PATH or BUILD_CONFIG_JSON missing)');
        process.exit(1);
    }

    console.log(`\n📦 Compiling App: ${config.appName} (${config.packageId})`);
    console.log(`🌐 Target URL: ${config.websiteUrl}\n`);

    try {
        const result = await compileApp(config, (progress) => {
            console.log(`[PROGRESS ${progress.percent}%] [${progress.stage}] ${progress.message}`);
        });

        console.log('\n========================================');
        console.log('🎉 LOCAL COMPILATION SUCCESSFUL!');
        console.log(`APK: ${result.apkPath}`);
        console.log(`AAB: ${result.aabPath}`);
        console.log('========================================\n');

        // Cloudflare R2 Uploads if credentials provided
        const r2Config = {
            accountId: process.env.R2_ACCOUNT_ID || config.r2AccountId,
            accessKeyId: process.env.R2_ACCESS_KEY_ID || config.r2AccessKeyId,
            secretAccessKey: process.env.R2_SECRET_ACCESS_KEY || config.r2SecretAccessKey,
            bucketName: process.env.R2_BUCKET_NAME || config.r2BucketName || 'webtoapp-builds',
            publicDomain: process.env.R2_PUBLIC_DOMAIN || config.r2PublicDomain || '',
        };

        let r2ApkUrl = null;
        let r2AabUrl = null;
        let r2KeystoreUrl = null;

        if (r2Config.accountId && r2Config.accessKeyId && r2Config.secretAccessKey) {
            console.log('☁️ Uploading build artifacts to Cloudflare R2...');
            if (result.apkPath) {
                r2ApkUrl = await uploadToR2(result.apkPath, `builds/${config.buildId}/app-release.apk`, r2Config);
            }
            if (result.aabPath) {
                r2AabUrl = await uploadToR2(result.aabPath, `builds/${config.buildId}/app-release.aab`, r2Config);
            }
            if (result.keystorePath) {
                r2KeystoreUrl = await uploadToR2(result.keystorePath, `builds/${config.buildId}/release.keystore`, r2Config);
            }
        }

        // Webhook notification back to SaaS site
        const callbackUrl = process.env.CALLBACK_URL || config.callbackUrl;
        if (callbackUrl) {
            console.log(`📡 Sending completion webhook to ${callbackUrl}...`);
            try {
                const fetch = (...args) => import('node-fetch').then(({ default: f }) => f(...args));
                await fetch(callbackUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        buildId: config.buildId,
                        isComplete: true,
                        isError: false,
                        apkUrl: r2ApkUrl,
                        aabUrl: r2AabUrl,
                        keystoreUrl: r2KeystoreUrl,
                        completedAt: new Date().toISOString(),
                    }),
                });
                console.log('✅ Webhook callback delivered successfully!');
            } catch (cbErr) {
                console.warn('⚠️ Webhook callback failed:', cbErr.message);
            }
        }

        console.log('\n🌟 CLOUD RUNNER FINISHED SUCCESSFULLY!\n');
        process.exit(0);
    } catch (err) {
        console.error('\n❌ BUILD FAILED:');
        console.error(err);

        const callbackUrl = process.env.CALLBACK_URL || config?.callbackUrl;
        if (callbackUrl && config?.buildId) {
            try {
                const fetch = (...args) => import('node-fetch').then(({ default: f }) => f(...args));
                await fetch(callbackUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        buildId: config.buildId,
                        isComplete: true,
                        isError: true,
                        errorMessage: err.message,
                    }),
                });
            } catch (e) {}
        }

        process.exit(1);
    }
}

main();
