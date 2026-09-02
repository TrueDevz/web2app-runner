const Jimp = require('jimp');
const path = require('path');
const fs = require('fs-extra');

const SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
};

/**
 * Generates all Android mipmap icons from a source image
 * @param {string} sourceImagePath - Path to source image or data URL
 * @param {string} resDirPath - Path to android project res directory
 * @param {string} themeColorHex - Primary color for default/fallback icon
 */
async function processIcons(sourceImagePath, resDirPath, themeColorHex = '#2563EB') {
    let sourceImage;

    if (sourceImagePath && fs.existsSync(sourceImagePath)) {
        sourceImage = await Jimp.read(sourceImagePath);
    } else {
        // Generate a clean branded icon
        sourceImage = new Jimp(512, 512, themeColorHex);
    }

    for (const [folder, size] of Object.entries(SIZES)) {
        const targetDir = path.join(resDirPath, folder);
        await fs.ensureDir(targetDir);

        // Square launcher icon
        const squareIcon = sourceImage.clone().resize(size, size);
        await squareIcon.writeAsync(path.join(targetDir, 'ic_launcher.png'));

        // Round launcher icon
        const roundIcon = sourceImage.clone().resize(size, size).circle();
        await roundIcon.writeAsync(path.join(targetDir, 'ic_launcher_round.png'));
    }

    console.log(`[Icon Processor] Generated all Android icon densities in ${resDirPath}`);
}

module.exports = { processIcons, SIZES };

if (require.main === module) {
    const resDir = path.join(__dirname, '../templates/android-base/app/src/main/res');
    processIcons(null, resDir, '#2563EB').then(() => {
        console.log('Default icons generated successfully.');
    }).catch(err => {
        console.error('Error generating icons:', err);
    });
}
