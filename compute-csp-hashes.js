const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const tdir = path.join(__dirname, 'src/main/resources/templates');
const handlers = new Set();

function walk(dir) {
    for (const f of fs.readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, f.name);
        if (f.isDirectory()) {
            walk(full);
        } else if (f.name.endsWith('.html')) {
            const content = fs.readFileSync(full, 'utf8');
            const re = /\bon(?:click|change|submit|keyup|keydown|input|focus|blur|reset|mouseover)="([^"]+)"/g;
            let m;
            while ((m = re.exec(content)) !== null) {
                handlers.add(m[1]);
            }
        }
    }
}

walk(tdir);

console.log('// Unique handler values: ' + handlers.size);
const hashes = [...handlers].sort().map(h => {
    const digest = crypto.createHash('sha256').update(h, 'utf8').digest('base64');
    return "'sha256-" + digest + "'";
});

// Print as a single CSP-compatible string
console.log(hashes.join(' '));
