const fs = require('fs-extra');
const { compileApp } = require('./build_compiler');

async function main() {
    const configPathOrJson = process.argv[2];
    if (!configPathOrJson) {
        console.error(JSON.stringify({ error: 'No configuration provided' }));
        process.exit(1);
    }

    try {
        let config;
        if (await fs.pathExists(configPathOrJson)) {
            config = await fs.readJson(configPathOrJson);
        } else {
            config = JSON.parse(configPathOrJson);
        }

        const result = await compileApp(config, (progress) => {
            console.log(`[STAGE_JSON] ${JSON.stringify(progress)}`);
        });

        console.log(`[RESULT_JSON] ${JSON.stringify(result)}`);
        process.exit(0);
    } catch (err) {
        console.error(`[ERROR_JSON] ${JSON.stringify({ error: err.message })}`);
        process.exit(1);
    }
}

main();
