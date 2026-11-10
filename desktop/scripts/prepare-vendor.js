const fs = require('fs');
const path = require('path');

const vendorDir = path.join(__dirname, '..', 'renderer', 'vendor');
fs.mkdirSync(vendorDir, { recursive: true });

const pdfDistBuild = path.join(__dirname, '..', 'node_modules', 'pdfjs-dist', 'build');
const filesToCopy = ['pdf.min.mjs', 'pdf.worker.min.mjs'];

for (const fileName of filesToCopy) {
  const src = path.join(pdfDistBuild, fileName);
  const dst = path.join(vendorDir, fileName);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dst);
    console.log(`[prepare-vendor] Copied ${fileName} -> renderer/vendor/${fileName}`);
  } else {
    console.warn(`[prepare-vendor] Warning: ${src} not found`);
  }
}
