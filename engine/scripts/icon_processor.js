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
 * Generates all Android mipmap icons from a source image or base64 string
 * @param {string} sourceImagePath - Path to source image or base64 string
 * @param {string} resDirPath - Path to android project res directory
 * @param {string} themeColorHex - Primary color for default/fallback icon
 */
async function processIcons(sourceImagePath, resDirPath, themeColorHex = '#2563EB') {
    let sourceImage;

    try {
        if (sourceImagePath && typeof sourceImagePath === 'string') {
            if (sourceImagePath.startsWith('data:image') || sourceImagePath.length > 500) {
                const base64Data = sourceImagePath.replace(/^data:image\/\w+;base64,/, '');
                const buffer = Buffer.from(base64Data, 'base64');
                sourceImage = await Jimp.read(buffer);
            } else if (sourceImagePath.startsWith('http://') || sourceImagePath.startsWith('https://')) {
                console.log(`[Icon Processor] Downloading custom icon from URL: ${sourceImagePath}`);
                const res = await fetch(sourceImagePath);
                const buffer = Buffer.from(await res.arrayBuffer());
                sourceImage = await Jimp.read(buffer);
            } else if (fs.existsSync(sourceImagePath)) {
                sourceImage = await Jimp.read(sourceImagePath);
            } else {
                sourceImage = new Jimp(512, 512, themeColorHex);
            }
        } else {
            sourceImage = new Jimp(512, 512, themeColorHex);
        }
    } catch (e) {
        console.warn('[Icon Processor] Failed to parse custom icon, falling back to brand color icon:', e.message);
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
