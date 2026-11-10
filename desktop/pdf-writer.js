/**
 * Builds a standards-compliant multi-page PDF 1.4 embedding JPEG-encoded
 * slide renders at 2x retina clarity with zero external native dependencies.
 */
function buildMultiPagePdfFromJpegs(pages) {
  if (!Array.isArray(pages) || pages.length === 0) {
    throw new Error('No pages provided for PDF export');
  }

  const objects = [];
  const addObject = (bodyBuffer) => {
    objects.push(bodyBuffer);
    return objects.length; // 1-based PDF object ID
  };

  const catalogId = addObject(Buffer.alloc(0)); // placeholder 1
  const pagesId = addObject(Buffer.alloc(0));   // placeholder 2
  const pageIds = [];

  for (const p of pages) {
    const wPt = Math.max(200, Math.round(p.widthPt || 842));
    const hPt = Math.max(200, Math.round(p.heightPt || 595));
    const imgW = Math.max(1, Math.round(p.imgWidth || 1684));
    const imgH = Math.max(1, Math.round(p.imgHeight || 1190));
    const jpegBuf = Buffer.from(p.jpegBase64, 'base64');

    const imgHeader = Buffer.from(
      `<< /Type /XObject /Subtype /Image /Width ${imgW} /Height ${imgH} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBuf.length} >>\nstream\n`,
      'ascii'
    );
    const imgFooter = Buffer.from('\nendstream', 'ascii');
    const imgId = addObject(Buffer.concat([imgHeader, jpegBuf, imgFooter]));

    const contentStr = `q\n${wPt} 0 0 ${hPt} 0 0 cm\n/Im0 Do\nQ\n`;
    const contentId = addObject(
      Buffer.from(`<< /Length ${contentStr.length} >>\nstream\n${contentStr}endstream`, 'ascii')
    );

    const pageId = addObject(
      Buffer.from(
        `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 ${wPt} ${hPt}] /Resources << /XObject << /Im0 ${imgId} 0 R >> >> /Contents ${contentId} 0 R >>`,
        'ascii'
      )
    );
    pageIds.push(pageId);
  }

  objects[catalogId - 1] = Buffer.from(`<< /Type /Catalog /Pages ${pagesId} 0 R >>`, 'ascii');
  objects[pagesId - 1] = Buffer.from(
    `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(' ')}] /Count ${pageIds.length} >>`,
    'ascii'
  );

  const chunks = [Buffer.from('%PDF-1.4\n%\xFF\xFF\xFF\xFF\n', 'latin1')];
  const offsets = [0];
  let byteOffset = chunks[0].length;

  for (let i = 0; i < objects.length; i++) {
    offsets.push(byteOffset);
    const prefix = Buffer.from(`${i + 1} 0 obj\n`, 'ascii');
    const suffix = Buffer.from('\nendobj\n', 'ascii');
    chunks.push(prefix, objects[i], suffix);
    byteOffset += prefix.length + objects[i].length + suffix.length;
  }

  const xrefStart = byteOffset;
  let xrefStr = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (let i = 1; i <= objects.length; i++) {
    xrefStr += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  }
  xrefStr += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogId} 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;
  chunks.push(Buffer.from(xrefStr, 'ascii'));

  return Buffer.concat(chunks);
}

module.exports = { buildMultiPagePdfFromJpegs };
