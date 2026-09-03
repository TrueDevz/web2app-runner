const fs = require('fs-extra');
const path = require('path');
const { spawn } = require('child_process');
const { processIcons } = require('./icon_processor');

const isWin = process.platform === 'win32';
const SDK_PATH = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || (isWin ? 'C:\\Users\\PixVibe\\AppData\\Local\\Android\\Sdk' : '/usr/local/lib/android/sdk');
const JAVA_HOME = process.env.JAVA_HOME || (isWin ? 'C:\\Program Files\\Java\\jdk-21.0.12' : undefined);
const KEYTOOL_PATH = isWin ? (JAVA_HOME ? path.join(JAVA_HOME, 'bin', 'keytool.exe') : 'keytool') : 'keytool';
const GRADLE_BIN = isWin ? (process.env.GRADLE_BIN || 'C:\\gradle-9.5.0\\bin\\gradle.bat') : 'gradle';

/**
 * Executes a shell command with promise and progress logging
 */
function runCommand(command, args, options = {}, onLog) {
    return new Promise((resolve, reject) => {
        const fullCmd = `${command} ${args.join(' ')}`;
        if (onLog) onLog(`[RUN] ${fullCmd}`);

        const pathEnvName = isWin ? 'Path' : 'PATH';
        const pathSeparator = isWin ? ';' : ':';
        const currentPath = process.env.PATH || process.env.Path || '';
        const binPath = JAVA_HOME ? path.join(JAVA_HOME, 'bin') : '';
        const combinedPath = binPath ? `${binPath}${pathSeparator}${currentPath}` : currentPath;

        const procEnv = {
            ...process.env,
            [pathEnvName]: combinedPath,
        };
        if (JAVA_HOME) procEnv.JAVA_HOME = JAVA_HOME;
        if (SDK_PATH) procEnv.ANDROID_HOME = SDK_PATH;

        const proc = spawn(command, args, {
            ...options,
            shell: true,
            env: procEnv,
        });

        let stdout = '';
        let stderr = '';

        proc.stdout.on('data', (data) => {
            const str = data.toString();
            stdout += str;
            if (onLog) onLog(str.trim());
        });

        proc.stderr.on('data', (data) => {
            const str = data.toString();
            stderr += str;
            if (onLog) onLog(`[STDERR] ${str.trim()}`);
        });

        proc.on('close', (code) => {
            if (code === 0) {
                resolve({ stdout, stderr });
            } else {
                reject(new Error(`Command failed with code ${code}\nStderr: ${stderr}\nStdout: ${stdout}`));
            }
        });

        proc.on('error', (err) => {
            reject(err);
        });
    });
}

/**
 * Main Build Compiler
 */
async function compileApp(config, onProgress) {
    const buildId = config.buildId || `build_${Date.now()}`;
    const notify = (stage, percent, message) => {
        if (onProgress) {
            onProgress({ stage, percent, message, buildId });
        }
        console.log(`[${percent}%] [${stage}] ${message}`);
    };

    const rootDir = path.resolve(__dirname, '..');
    const templateDir = path.join(rootDir, 'templates', 'android-base');
    const workspaceDir = path.join(rootDir, 'workspaces', buildId);
    const outputDir = path.join(rootDir, 'output', buildId);

    try {
        notify('INITIALIZING', 10, 'Creating isolated build workspace...');
        await fs.ensureDir(workspaceDir);
        await fs.ensureDir(outputDir);

        // Copy template to isolated workspace
        await fs.copy(templateDir, workspaceDir);

        // Step 2: Ingest Assets & Icons
        notify('ASSETS', 25, 'Processing and generating application launcher icons...');
        const resDir = path.join(workspaceDir, 'app', 'src', 'main', 'res');
        await processIcons(config.iconBase64 || config.iconPath, resDir, config.themeColor || '#2563EB');

        // Step 3: Inject Configuration
        notify('CONFIGURING', 40, 'Injecting app configuration and custom styling...');

        // 3.1 app_config.json
        const assetsDir = path.join(workspaceDir, 'app', 'src', 'main', 'assets');
        await fs.ensureDir(assetsDir);
        const appConfigPath = path.join(assetsDir, 'app_config.json');
        const runtimeConfig = {
            appName: config.appName || 'My Web App',
            websiteUrl: config.websiteUrl || 'https://example.com',
            pullToRefresh: config.pullToRefresh !== false,
            offlineMode: config.offlineMode !== false,
            biometricAuth: !!config.biometricAuth,
            keepScreenOn: !!config.keepScreenOn,
            enableDownloads: config.enableDownloads !== false,
            enableAdMob: !!config.enableAdMob,
            adMobBannerId: config.adMobBannerId || '',
            adMobInterstitialId: config.adMobInterstitialId || '',
            customUserAgent: config.customUserAgent || '',
            exitConfirmation: config.exitConfirmation !== false,
            preventScreenshots: !!config.preventScreenshots,
            fullScreenMode: !!config.fullScreenMode,
            customCss: config.customCss || '',
            customJs: config.customJs || '',
            enableFloatingButton: !!config.enableFloatingButton,
            floatingButtonUrl: config.floatingButtonUrl || '',
            floatingButtonIcon: config.floatingButtonIcon || 'MessageCircle',
            enableBottomNav: !!config.enableBottomNav,
            bottomNavItems: config.bottomNavItems || [],
            enableSplashScreen: config.enableSplashScreen !== false,
            splashDurationMs: config.splashDurationMs || 2000,
            enableOnboarding: !!config.enableOnboarding,
            onboardingSlides: config.onboardingSlides || [],
            enableNotifications: config.enableNotifications !== false,
            oneSignalAppId: config.oneSignalAppId || '',
            enableTopBar: !!config.enableTopBar,
            topBarTitle: config.topBarTitle || '',
            topBarActions: config.topBarActions || [],
            enableDrawer: !!config.enableDrawer,
            drawerItems: config.drawerItems || [],
            enablePinLock: !!config.enablePinLock,
            pinCode: config.pinCode || '',
            enableOfflineBundle: !!config.enableOfflineBundle,
            versionCode: parseInt(config.versionCode, 10) || 1,
            versionName: String(config.versionName || '1.0.0').trim(),
            themeColor: config.themeColor || '#2563EB',
            isFreeUser: !!config.isFreeUser,
        };
        await fs.writeJson(appConfigPath, runtimeConfig, { spaces: 2 });

        // If offline HTML is provided, write it into assets/offline_web/index.html
        if (config.enableOfflineBundle && config.offlineHtml) {
            const offlineDir = path.join(assetsDir, 'offline_web');
            await fs.ensureDir(offlineDir);
            await fs.writeFile(path.join(offlineDir, 'index.html'), config.offlineHtml, 'utf8');
        }

        // 3.2 strings.xml
        const stringsPath = path.join(resDir, 'values', 'strings.xml');
        const admobAppId = config.adMobAppId || 'ca-app-pub-3940256099942544~3347511713';
        const stringsXml = `<resources>
    <string name="app_name">${escapeXml(config.appName || 'My Web App')}</string>
    <string name="admob_app_id">${admobAppId}</string>
    <string name="offline_title">No Internet Connection</string>
    <string name="offline_desc">Please check your network settings and try again.</string>
    <string name="retry">Retry</string>
    <string name="exit_confirm">Press back again to exit</string>
</resources>`;
        await fs.writeFile(stringsPath, stringsXml, 'utf8');

        // 3.3 colors.xml
        const colorsPath = path.join(resDir, 'values', 'colors.xml');
        const themeColor = config.themeColor || '#2563EB';
        const colorsXml = `<resources>
    <color name="primary">${themeColor}</color>
    <color name="primary_dark">${darkenHex(themeColor, 20)}</color>
    <color name="accent">${themeColor}</color>
    <color name="status_bar_color">${themeColor}</color>
    <color name="nav_bar_color">#FFFFFF</color>
    <color name="splash_bg">#FFFFFF</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>`;
        await fs.writeFile(colorsPath, colorsXml, 'utf8');

        // 3.4 Update build.gradle.kts with package ID, versionCode, and versionName
        const packageId = sanitizePackageId(config.packageId || 'com.webtoapp.myapp');
        const versionCode = parseInt(config.versionCode, 10) || 1;
        const versionName = String(config.versionName || '1.0.0').trim();
        const appBuildGradle = path.join(workspaceDir, 'app', 'build.gradle.kts');
        let gradleContent = await fs.readFile(appBuildGradle, 'utf8');
        gradleContent = gradleContent
            .replace(/applicationId = ".*"/g, `applicationId = "${packageId}"`)
            .replace(/versionCode = \d+/g, `versionCode = ${versionCode}`)
            .replace(/versionName = ".*"/g, `versionName = "${versionName}"`);
        await fs.writeFile(appBuildGradle, gradleContent, 'utf8');

        // 3.5 Deep Linking in AndroidManifest.xml
        try {
            const manifestPath = path.join(workspaceDir, 'app', 'src', 'main', 'AndroidManifest.xml');
            let manifestContent = await fs.readFile(manifestPath, 'utf8');
            let parsedHost = 'example.com';
            try {
                parsedHost = new URL(config.websiteUrl || 'https://example.com').hostname;
            } catch (e) {}
            manifestContent = manifestContent.replace(
                '<data android:scheme="https" />',
                `<data android:scheme="https" android:host="${parsedHost}" />\n                <data android:scheme="http" android:host="${parsedHost}" />`
            );
            await fs.writeFile(manifestPath, manifestContent, 'utf8');
        } catch (e) {
            console.error('Error configuring deep link host:', e);
        }

        // 3.6 local.properties
        const localPropsPath = path.join(workspaceDir, 'local.properties');
        const formattedSdkPath = isWin ? SDK_PATH.replace(/\\/g, '/') : SDK_PATH;
        await fs.writeFile(localPropsPath, `sdk.dir=${formattedSdkPath}\n`, 'utf8');

        // Step 4: Generate Release Keystore
        notify('SIGNING', 55, 'Generating cryptographic release keystore...');
        const keystorePath = path.join(workspaceDir, 'release.jks');
        const storePass = 'webtoapp123';
        const keyAlias = 'releaseKey';
        const keyPass = 'webtoapp123';

        const keytoolArgs = [
            '-genkeypair',
            '-v',
            '-keystore', keystorePath,
            '-alias', keyAlias,
            '-keyalg', 'RSA',
            '-keysize', '2048',
            '-validity', '10000',
            '-storepass', storePass,
            '-keypass', keyPass,
            '-dname', 'CN=WebToApp,OU=AppBuilder,O=WebToApp,L=City,ST=State,C=US'
        ];

        try {
            await runCommand(KEYTOOL_PATH, keytoolArgs);
        } catch (kErr) {
            console.warn('[Keystore] Notice:', kErr.message);
        }

        // Step 5: Gradle Compilation
        notify('COMPILING', 70, 'Running Gradle release compiler (Generating signed APK & Play Store .AAB bundle)...');
        
        const gradleArgs = [
            'assembleRelease',
            'bundleRelease',
            `-PKEYSTORE_PATH=${keystorePath.replace(/\\/g, '/')}`,
            `-PKEYSTORE_PASSWORD=${storePass}`,
            `-PKEY_ALIAS=${keyAlias}`,
            `-PKEY_PASSWORD=${keyPass}`,
            '--no-daemon'
        ];

        await runCommand(GRADLE_BIN, gradleArgs, { cwd: workspaceDir }, (msg) => {
            notify('COMPILING', 75, `Gradle: ${msg.slice(0, 100)}`);
        });

        // Step 6: Collect Output Files
        notify('PACKAGING', 90, 'Packaging APK, AAB and Keystore files...');
        const releaseApkDir = path.join(workspaceDir, 'app', 'build', 'outputs', 'apk', 'release');
        const generatedApk = path.join(releaseApkDir, 'app-release.apk');
        const releaseAabDir = path.join(workspaceDir, 'app', 'build', 'outputs', 'bundle', 'release');
        const generatedAab = path.join(releaseAabDir, 'app-release.aab');

        const targetApk = path.join(outputDir, `${slugify(config.appName || 'app')}-release.apk`);
        const targetAab = path.join(outputDir, `${slugify(config.appName || 'app')}-release.aab`);
        const targetKeystore = path.join(outputDir, `${slugify(config.appName || 'app')}-release.jks`);

        if (await fs.pathExists(generatedApk)) {
            await fs.copy(generatedApk, targetApk);
        }
        if (await fs.pathExists(generatedAab)) {
            await fs.copy(generatedAab, targetAab);
        }
        if (await fs.pathExists(keystorePath)) {
            await fs.copy(keystorePath, targetKeystore);
        }

        notify('FINISHED', 100, 'App build completed successfully!');

        return {
            success: true,
            buildId,
            appName: config.appName,
            packageId,
            apkPath: (await fs.pathExists(targetApk)) ? targetApk : (await fs.pathExists(generatedApk) ? generatedApk : null),
            aabPath: (await fs.pathExists(targetAab)) ? targetAab : (await fs.pathExists(generatedAab) ? generatedAab : null),
            keystorePath: (await fs.pathExists(targetKeystore)) ? targetKeystore : keystorePath,
            apkFileName: path.basename(targetApk),
            aabFileName: path.basename(targetAab),
            keystoreFileName: path.basename(targetKeystore),
            timestamp: new Date().toISOString()
        };

    } catch (error) {
        notify('ERROR', 0, `Build failed: ${error.message}`);
        throw error;
    }
}

function escapeXml(unsafe) {
    return String(unsafe || '').replace(/[<>&'"]/g, (c) => {
        switch (c) {
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '&': return '&amp;';
            case '\'': return '&apos;';
            case '"': return '&quot;';
        }
    });
}

function sanitizePackageId(id) {
    return id.replace(/[^a-zA-Z0-9._]/g, '').toLowerCase();
}

function darkenHex(hex, percent) {
    const num = parseInt(hex.replace('#', ''), 16);
    const amt = Math.round(2.55 * percent);
    const R = Math.max(0, (num >> 16) - amt);
    const G = Math.max(0, ((num >> 8) & 0x00FF) - amt);
    const B = Math.max(0, (num & 0x0000FF) - amt);
    return `#${((1 << 24) + (R << 16) + (G << 8) + B).toString(16).slice(1)}`;
}

function slugify(text) {
    return text.toString().toLowerCase().trim().replace(/[\s\W-]+/g, '-');
}

module.exports = { compileApp };
