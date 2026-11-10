const { app, BrowserWindow, dialog, ipcMain, protocol, net } = require('electron');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const JSZip = require('jszip');
const VaultBridge = require('./renderer/vault-bridge');
const { buildMultiPagePdfFromJpegs } = require('./pdf-writer');

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
      stream: true
    }
  }
]);

let mainWindow = null;

function getVaultFilePath() {
  return path.join(app.getPath('userData'), 'notegraph-desktop-vault.json');
}

function readVaultFromDisk() {
  const filePath = getVaultFilePath();
  if (!fs.existsSync(filePath)) {
    const starter = VaultBridge.createStarterVault();
    writeVaultToDisk(starter);
    return starter;
  }
  try {
    const raw = fs.readFileSync(filePath, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed || !Array.isArray(parsed.notes)) {
      return VaultBridge.createStarterVault();
    }
    return parsed;
  } catch (err) {
    console.error('[NoteGraph] Failed to read vault JSON:', err);
    return VaultBridge.createStarterVault();
  }
}

function writeVaultToDisk(vaultData) {
  const filePath = getVaultFilePath();
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tmpPath = `${filePath}.tmp`;
  fs.writeFileSync(tmpPath, JSON.stringify(vaultData, null, 2), 'utf8');
  fs.renameSync(tmpPath, filePath);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1380,
    height: 860,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: '#000000',
    autoHideMenuBar: true,
    title: 'NoteGraph Board',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false
    }
  });

  mainWindow.loadURL('app://bundle/index.html');
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  const rendererDir = path.join(__dirname, 'renderer');

  protocol.handle('app', (request) => {
    const reqUrl = new URL(request.url);
    let relPath = decodeURIComponent(reqUrl.pathname).replace(/^\/+/, '');
    if (!relPath || relPath === '/') relPath = 'index.html';
    const resolved = path.normalize(path.join(rendererDir, relPath));
    if (!resolved.startsWith(rendererDir)) {
      return new Response('Forbidden', { status: 403 });
    }
    return net.fetch(pathToFileURL(resolved).toString());
  });

  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    platform: process.platform,
    arch: process.arch,
    vaultPath: getVaultFilePath()
  }));

  ipcMain.handle('app:toggle-fullscreen', () => {
    if (!mainWindow) return false;
    const next = !mainWindow.isFullScreen();
    mainWindow.setFullScreen(next);
    return next;
  });

  ipcMain.handle('vault:load', () => {
    return readVaultFromDisk();
  });

  ipcMain.handle('vault:save', (_event, vaultData) => {
    writeVaultToDisk(vaultData);
    return { ok: true, savedAt: Date.now() };
  });

  ipcMain.handle('vault:import-zip', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Import NoteGraph Android Vault Backup (.zip)',
      filters: [{ name: 'NoteGraph Backup ZIP', extensions: ['zip'] }],
      properties: ['openFile']
    });
    if (res.canceled || !res.filePaths.length) return null;

    const zipBytes = fs.readFileSync(res.filePaths[0]);
    const zip = await JSZip.loadAsync(zipBytes);
    const notesEntry = zip.file('notes.json');
    if (!notesEntry) {
      throw new Error('Selected ZIP does not contain notes.json');
    }
    const notesText = await notesEntry.async('string');
    const androidNotes = JSON.parse(notesText);
    return { filePath: res.filePaths[0], androidNotes };
  });

  ipcMain.handle('vault:export-zip', async (_event, androidNotes) => {
    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Android-Compatible NoteGraph Vault (.zip)',
      defaultPath: 'NoteGraph_Vault_Backup.zip',
      filters: [{ name: 'NoteGraph Backup ZIP', extensions: ['zip'] }]
    });
    if (res.canceled || !res.filePath) return null;

    const zip = new JSZip();
    zip.file('notes.json', JSON.stringify(androidNotes, null, 2));
    const buffer = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
    fs.writeFileSync(res.filePath, buffer);
    return { filePath: res.filePath, count: androidNotes.length };
  });

  ipcMain.handle('file:pick-pdf', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Import PDF Slides / Worksheet for Board Annotation',
      filters: [{ name: 'PDF Document', extensions: ['pdf'] }],
      properties: ['openFile']
    });
    if (res.canceled || !res.filePaths.length) return null;
    const filePath = res.filePaths[0];
    const bytes = fs.readFileSync(filePath);
    return {
      fileName: path.basename(filePath),
      bytesBase64: bytes.toString('base64')
    };
  });

  ipcMain.handle('file:pick-image', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Insert Image onto Board',
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif'] }],
      properties: ['openFile']
    });
    if (res.canceled || !res.filePaths.length) return null;
    const filePath = res.filePaths[0];
    const ext = path.extname(filePath).slice(1).toLowerCase() || 'png';
    const mime = ext === 'jpg' ? 'jpeg' : ext;
    const bytes = fs.readFileSync(filePath);
    return {
      fileName: path.basename(filePath),
      dataUrl: `data:image/${mime};base64,${bytes.toString('base64')}`
    };
  });

  ipcMain.handle('export:save-png', async (_event, { defaultName, pngBase64 }) => {
    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Board Slide as Lossless PNG',
      defaultPath: `${defaultName || 'notegraph-board'}.png`,
      filters: [{ name: 'PNG Image', extensions: ['png'] }]
    });
    if (res.canceled || !res.filePath) return null;
    fs.writeFileSync(res.filePath, Buffer.from(pngBase64, 'base64'));
    return res.filePath;
  });

  ipcMain.handle('export:save-markdown', async (_event, { defaultName, markdownText }) => {
    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Note & Checklist as Markdown',
      defaultPath: `${defaultName || 'notegraph-note'}.md`,
      filters: [{ name: 'Markdown', extensions: ['md'] }]
    });
    if (res.canceled || !res.filePath) return null;
    fs.writeFileSync(res.filePath, markdownText, 'utf8');
    return res.filePath;
  });

  ipcMain.handle('export:save-pdf', async (_event, { defaultName, pages }) => {
    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Annotated Board Slides as PDF',
      defaultPath: `${defaultName || 'notegraph-board'}.pdf`,
      filters: [{ name: 'PDF Document', extensions: ['pdf'] }]
    });
    if (res.canceled || !res.filePath) return null;
    const pdfBuffer = buildMultiPagePdfFromJpegs(pages);
    fs.writeFileSync(res.filePath, pdfBuffer);
    return res.filePath;
  });

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
