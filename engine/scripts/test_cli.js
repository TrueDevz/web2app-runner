const { compileApp } = require('./build_compiler');

async function main() {
    console.log('🚀 Starting Test App Build...');
    
    const config = {
        buildId: `test_${Date.now()}`,
        appName: 'Test Web App',
        packageId: 'com.pixvibe.testapp',
        websiteUrl: 'https://websitetoapp.app',
        themeColor: '#4F46E5',
        pullToRefresh: true,
        offlineMode: true,
        biometricAuth: false,
        enableDownloads: true,
        enableAdMob: false,
        exitConfirmation: true
    };

    try {
        const result = await compileApp(config, (progress) => {
            console.log(`[PROGRESS] ${progress.percent}% - ${progress.stage}: ${progress.message}`);
        });

        console.log('\n========================================');
        console.log('🎉 BUILD SUCCESSFUL!');
        console.log(`APK Output: ${result.apkPath}`);
        console.log(`Keystore Output: ${result.keystorePath}`);
        console.log('========================================\n');
    } catch (err) {
        console.error('\n❌ BUILD FAILED:');
        console.error(err);
    }
}

main();
