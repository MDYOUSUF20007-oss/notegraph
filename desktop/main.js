const { app, BrowserWindow, Menu, dialog, ipcMain, protocol, net } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');
const JSZip = require('jszip');
const VaultBridge = require('./renderer/vault-bridge');
const { buildMultiPagePdfFromJpegs } = require('./pdf-writer');

// Prevent duplicate instances from racing on the local SQLite/JSON vault file
const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
}

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

const SYNC_FILENAME_V2 = 'notegraph-sync-v2.json';
const ZIP_FILENAME_V2 = 'vault-sync-v2.json';
const ZIP_FILENAME_LEGACY = 'notes.json';
const DEFAULT_LAN_PORT = 8765;

let mainWindow = null;
let latestVaultSnapshot = null;
let vaultDirtyForDisk = false;
let writeQueuePromise = Promise.resolve();

let lanServer = null;
let lanServerPort = DEFAULT_LAN_PORT;
let folderWatcher = null;
let folderWatchDebounce = null;
let lastOwnFolderWriteAt = 0;

function getPlatformTag() {
  if (process.platform === 'darwin') return 'macos';
  if (process.platform === 'win32') return 'windows';
  return process.platform;
}

function getDeviceMeta(cfg = readSyncConfig()) {
  return {
    deviceId: cfg.deviceId,
    deviceName: os.hostname() || `NoteGraph ${getPlatformTag()}`,
    platform: getPlatformTag()
  };
}

function getVaultFilePath() {
  return path.join(app.getPath('userData'), 'notegraph-desktop-vault.json');
}

function getSyncConfigPath() {
  return path.join(app.getPath('userData'), 'notegraph-sync-config.json');
}

function generatePin() {
  return String(1000 + crypto.randomInt(9000));
}

function readSyncConfig() {
  const cfgPath = getSyncConfigPath();
  let cfg = null;
  try {
    if (fs.existsSync(cfgPath)) {
      cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
    }
  } catch (_) {
    cfg = null;
  }
  let changed = false;
  if (!cfg || typeof cfg !== 'object') {
    cfg = {};
    changed = true;
  }
  if (!cfg.deviceId) {
    cfg.deviceId = crypto.randomUUID ? crypto.randomUUID() : `dev_${Date.now().toString(36)}`;
    changed = true;
  }
  if (!cfg.lanPin) {
    cfg.lanPin = generatePin();
    changed = true;
  }
  if (typeof cfg.autoSyncFolder !== 'boolean') {
    cfg.autoSyncFolder = true;
    changed = true;
  }
  if (changed) {
    writeSyncConfig(cfg);
  }
  return cfg;
}

function writeSyncConfig(cfg) {
  const cfgPath = getSyncConfigPath();
  try {
    fs.mkdirSync(path.dirname(cfgPath), { recursive: true });
    const tmp = `${cfgPath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(cfg, null, 2), 'utf8');
    fs.renameSync(tmp, cfgPath);
  } catch (err) {
    console.error('[NoteGraph] Failed to write sync config:', err);
  }
}

function readVaultFromDisk() {
  if (latestVaultSnapshot && Array.isArray(latestVaultSnapshot.notes)) {
    return latestVaultSnapshot;
  }
  const filePath = getVaultFilePath();
  if (!fs.existsSync(filePath)) {
    const starter = VaultBridge.createStarterVault();
    writeVaultToDiskSync(starter);
    return starter;
  }
  try {
    const raw = fs.readFileSync(filePath, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed || !Array.isArray(parsed.notes)) {
      const starter = VaultBridge.createStarterVault();
      latestVaultSnapshot = starter;
      return starter;
    }
    latestVaultSnapshot = parsed;
    return parsed;
  } catch (err) {
    console.error('[NoteGraph] Failed to read vault JSON:', err);
    const starter = VaultBridge.createStarterVault();
    latestVaultSnapshot = starter;
    return starter;
  }
}

function writeVaultToDiskSync(vaultData) {
  latestVaultSnapshot = vaultData;
  vaultDirtyForDisk = false;
  const filePath = getVaultFilePath();
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tmpPath = `${filePath}.tmp`;
  fs.writeFileSync(tmpPath, JSON.stringify(vaultData), 'utf8');
  fs.renameSync(tmpPath, filePath);
}

function scheduleAsyncVaultWrite(vaultData) {
  latestVaultSnapshot = vaultData;
  vaultDirtyForDisk = true;
  const filePath = getVaultFilePath();
  writeQueuePromise = writeQueuePromise.then(async () => {
    const snapshot = latestVaultSnapshot;
    if (!snapshot) return;
    await fs.promises.mkdir(path.dirname(filePath), { recursive: true });
    const tmpPath = `${filePath}.tmp`;
    await fs.promises.writeFile(tmpPath, JSON.stringify(snapshot), 'utf8');
    await fs.promises.rename(tmpPath, filePath);
    if (latestVaultSnapshot === snapshot) {
      vaultDirtyForDisk = false;
    }
  }).catch((err) => {
    console.error('[NoteGraph] Async vault save failed:', err);
  });
  return writeQueuePromise;
}

function getLocalIpv4Addresses() {
  const preferred = [];
  const fallback = [];
  const interfaces = os.networkInterfaces();
  for (const [name, addrs] of Object.entries(interfaces)) {
    if (!Array.isArray(addrs)) continue;
    for (const info of addrs) {
      if (!info || info.internal || info.family !== 'IPv4') continue;
      const lower = name.toLowerCase();
      if (lower.includes('wi-fi') || lower.includes('wifi') || lower.includes('wlan') || lower.includes('en0')) {
        preferred.push(info.address);
      } else {
        fallback.push(info.address);
      }
    }
  }
  return Array.from(new Set([...preferred, ...fallback]));
}

function getSuggestedCloudFolders() {
  const home = os.homedir();
  const candidates = [];

  if (process.platform === 'darwin') {
    const icloud = path.join(home, 'Library', 'Mobile Documents', 'com~apple~CloudDocs');
    if (fs.existsSync(icloud)) {
      candidates.push({ label: 'iCloud Drive (macOS)', path: path.join(icloud, 'NoteGraphSync') });
    }
    const cloudStorage = path.join(home, 'Library', 'CloudStorage');
    if (fs.existsSync(cloudStorage)) {
      try {
        for (const entry of fs.readdirSync(cloudStorage)) {
          const full = path.join(cloudStorage, entry);
          if (fs.statSync(full).isDirectory()) {
            candidates.push({ label: entry, path: path.join(full, 'NoteGraphSync') });
          }
        }
      } catch (_) {}
    }
  }

  if (process.env.OneDrive && fs.existsSync(process.env.OneDrive)) {
    candidates.push({ label: 'OneDrive', path: path.join(process.env.OneDrive, 'NoteGraphSync') });
  }
  const homeOneDrive = path.join(home, 'OneDrive');
  if (fs.existsSync(homeOneDrive) && !candidates.some((c) => c.path.startsWith(homeOneDrive))) {
    candidates.push({ label: 'OneDrive', path: path.join(homeOneDrive, 'NoteGraphSync') });
  }
  const dropbox = path.join(home, 'Dropbox');
  if (fs.existsSync(dropbox)) {
    candidates.push({ label: 'Dropbox', path: path.join(dropbox, 'NoteGraphSync') });
  }
  const gdrive = path.join(home, 'Google Drive');
  if (fs.existsSync(gdrive)) {
    candidates.push({ label: 'Google Drive', path: path.join(gdrive, 'NoteGraphSync') });
  }
  return candidates;
}

function pushMergedVaultToRenderer(mergedVault, summary) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('sync:vault-updated', { vault: mergedVault, summary });
  }
}

function readFolderSyncPayload(folderPath) {
  for (const name of [SYNC_FILENAME_V2, ZIP_FILENAME_V2, ZIP_FILENAME_LEGACY]) {
    const candidate = path.join(folderPath, name);
    if (fs.existsSync(candidate)) {
      const raw = fs.readFileSync(candidate, 'utf8');
      if (raw && raw.trim()) return VaultBridge.parseSyncPackage(raw);
    }
  }
  return null;
}

function syncFolderBidirectional(folderPath, overrideLocalVault = null) {
  if (!folderPath) throw new Error('No shared sync folder configured');
  fs.mkdirSync(folderPath, { recursive: true });
  const cfg = readSyncConfig();
  const baseVault = overrideLocalVault || readVaultFromDisk();

  let workingVault = baseVault;
  let summary = {
    foldersSynced: (baseVault.folders || []).length,
    notesMerged: 0,
    newNotesAdded: 0,
    linksRebuilt: 0,
    deletionsApplied: 0,
    remoteDevice: 'Shared Folder'
  };

  const remotePkg = readFolderSyncPayload(folderPath);
  if (remotePkg) {
    const merged = VaultBridge.mergeSyncPackage(workingVault, remotePkg);
    workingVault = merged.vault;
    summary = merged.summary;
  }

  writeVaultToDiskSync(workingVault);

  const outPkg = VaultBridge.buildSyncPackage(workingVault, getDeviceMeta(cfg));
  const targetFile = path.join(folderPath, SYNC_FILENAME_V2);
  const tmpFile = `${targetFile}.tmp`;
  lastOwnFolderWriteAt = Date.now();
  fs.writeFileSync(tmpFile, JSON.stringify(outPkg, null, 2), 'utf8');
  fs.renameSync(tmpFile, targetFile);

  cfg.lastSyncAt = Date.now();
  cfg.lastSyncMessage = `Folder synced (${summary.notesMerged} merged, ${summary.newNotesAdded} added)`;
  writeSyncConfig(cfg);
  pushMergedVaultToRenderer(workingVault, summary);

  return { vault: workingVault, summary, config: buildSyncConfigPayload(cfg) };
}

function restartFolderWatcher(folderPath) {
  if (folderWatcher) {
    try { folderWatcher.close(); } catch (_) {}
    folderWatcher = null;
  }
  if (!folderPath) return;
  try {
    fs.mkdirSync(folderPath, { recursive: true });
    folderWatcher = fs.watch(folderPath, (_eventType, filename) => {
      if (!filename) return;
      const lower = String(filename).toLowerCase();
      if (![SYNC_FILENAME_V2, ZIP_FILENAME_V2, ZIP_FILENAME_LEGACY].includes(lower)) {
        return;
      }
      if (Date.now() - lastOwnFolderWriteAt < 1800) {
        return;
      }
      if (folderWatchDebounce) clearTimeout(folderWatchDebounce);
      folderWatchDebounce = setTimeout(() => {
        try {
          syncFolderBidirectional(folderPath);
        } catch (err) {
          console.error('[NoteGraph] Auto folder sync failed:', err);
        }
      }, 900);
    });
  } catch (err) {
    console.warn('[NoteGraph] Folder watcher unavailable:', err.message);
  }
}

function buildLanStatusPayload() {
  const cfg = readSyncConfig();
  return {
    isRunning: Boolean(lanServer),
    port: lanServerPort,
    pin: cfg.lanPin,
    addresses: getLocalIpv4Addresses(),
    platform: getPlatformTag(),
    deviceName: os.hostname()
  };
}

function buildSyncConfigPayload(cfg = readSyncConfig()) {
  return {
    deviceId: cfg.deviceId,
    deviceName: os.hostname() || `NoteGraph ${getPlatformTag()}`,
    platform: getPlatformTag(),
    sharedFolderPath: cfg.sharedFolderPath || '',
    autoSyncFolder: Boolean(cfg.autoSyncFolder),
    lastPeer: cfg.lastPeer || '',
    lastSyncAt: cfg.lastSyncAt || 0,
    lastSyncMessage: cfg.lastSyncMessage || 'Not synced yet',
    suggestedFolders: getSuggestedCloudFolders(),
    lan: buildLanStatusPayload()
  };
}

function handleIncomingLanHttp(req, res) {
  const urlPath = String(req.url || '/').split('?')[0];
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-notegraph-pin');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method === 'GET' && urlPath === '/api/ping') {
    const cfg = readSyncConfig();
    const meta = getDeviceMeta(cfg);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ ok: true, app: 'NoteGraph', ...meta, port: lanServerPort }));
    return;
  }

  if (req.method === 'POST' && urlPath === '/api/sync') {
    const cfg = readSyncConfig();
    const suppliedPin = String(req.headers['x-notegraph-pin'] || '').trim();
    if (cfg.lanPin && suppliedPin !== cfg.lanPin) {
      res.writeHead(401, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: 'Invalid sync PIN' }));
      return;
    }

    const chunks = [];
    let totalBytes = 0;
    const maxBytes = 64 * 1024 * 1024;
    let aborted = false;

    req.on('data', (chunk) => {
      totalBytes += chunk.length;
      if (totalBytes > maxBytes) {
        aborted = true;
        res.writeHead(413, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ error: 'Sync payload exceeds 64MB limit' }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      if (aborted) return;
      try {
        const bodyStr = Buffer.concat(chunks).toString('utf8');
        const remotePkg = VaultBridge.parseSyncPackage(bodyStr);
        const currentVault = readVaultFromDisk();
        const { vault: mergedVault, summary } = VaultBridge.mergeSyncPackage(currentVault, remotePkg);
        writeVaultToDiskSync(mergedVault);

        const freshCfg = readSyncConfig();
        freshCfg.lastSyncAt = Date.now();
        freshCfg.lastSyncMessage = `Wi-Fi synced with ${summary.remoteDevice} (${summary.notesMerged} merged, ${summary.newNotesAdded} added)`;
        writeSyncConfig(freshCfg);

        const responsePkg = VaultBridge.buildSyncPackage(mergedVault, getDeviceMeta(freshCfg));
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(responsePkg));

        pushMergedVaultToRenderer(mergedVault, summary);
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ error: err.message || 'Invalid sync payload' }));
      }
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify({ error: 'Not found' }));
}

function startLanServer(preferredPort = DEFAULT_LAN_PORT) {
  if (lanServer) return Promise.resolve(buildLanStatusPayload());
  const ports = [preferredPort, preferredPort + 1, preferredPort + 2, 0];

  return new Promise((resolve, reject) => {
    const tryBind = (idx) => {
      if (idx >= ports.length) {
        reject(new Error('Could not bind Wi-Fi sync port'));
        return;
      }
      const candidate = ports[idx];
      const srv = http.createServer(handleIncomingLanHttp);
      srv.once('error', () => {
        try { srv.close(); } catch (_) {}
        tryBind(idx + 1);
      });
      srv.listen(candidate, '0.0.0.0', () => {
        const addr = srv.address();
        lanServer = srv;
        lanServerPort = typeof addr === 'object' && addr ? addr.port : candidate;
        resolve(buildLanStatusPayload());
      });
    };
    tryBind(0);
  });
}

function stopLanServer() {
  if (lanServer) {
    try { lanServer.close(); } catch (_) {}
    lanServer = null;
  }
  return buildLanStatusPayload();
}

function parsePeerHostPort(rawInput) {
  let clean = String(rawInput || '').trim().replace(/^https?:\/\//i, '').split('/')[0];
  if (clean.startsWith('[')) {
    const end = clean.indexOf(']');
    if (end > 0) {
      const host = clean.slice(1, end);
      const port = parseInt(clean.slice(end + 1).replace(/^:/, ''), 10) || DEFAULT_LAN_PORT;
      return { host, port };
    }
  }
  const colonIdx = clean.lastIndexOf(':');
  if (colonIdx > 0) {
    return {
      host: clean.slice(0, colonIdx).trim(),
      port: parseInt(clean.slice(colonIdx + 1).trim(), 10) || DEFAULT_LAN_PORT
    };
  }
  return { host: clean, port: DEFAULT_LAN_PORT };
}

function postJsonHttp(host, port, apiPath, pin, payloadObj) {
  const bodyBuf = Buffer.from(JSON.stringify(payloadObj), 'utf8');
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host,
        port,
        path: apiPath,
        method: 'POST',
        timeout: 20000,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Content-Length': bodyBuf.length,
          'x-notegraph-pin': String(pin || '').trim()
        }
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          if (res.statusCode === 401 || res.statusCode === 403) {
            reject(new Error(`Invalid sync PIN for ${host}:${port}`));
            return;
          }
          if (res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`Peer returned HTTP ${res.statusCode}: ${text.slice(0, 120)}`));
            return;
          }
          try {
            resolve(JSON.parse(text));
          } catch (e) {
            reject(new Error('Invalid JSON response from peer'));
          }
        });
      }
    );
    req.on('timeout', () => {
      req.destroy(new Error(`Connection timed out (${host}:${port})`));
    });
    req.on('error', (err) => reject(err));
    req.write(bodyBuf);
    req.end();
  });
}

function installNativeMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    { role: 'fileMenu' },
    { role: 'editMenu' },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    },
    { role: 'windowMenu' }
  ];
  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1380,
    height: 860,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: '#000000',
    autoHideMenuBar: process.platform !== 'darwin',
    titleBarStyle: process.platform === 'darwin' ? 'default' : undefined,
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

if (gotSingleInstanceLock) {
app.on('second-instance', () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  }
});

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

  installNativeMenu();

  // Start folder watcher if configured
  const bootCfg = readSyncConfig();
  if (bootCfg.sharedFolderPath && bootCfg.autoSyncFolder) {
    restartFolderWatcher(bootCfg.sharedFolderPath);
  }

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
    const loaded = readVaultFromDisk();
    const cfg = readSyncConfig();
    if (cfg.sharedFolderPath && cfg.autoSyncFolder) {
      try {
        const res = syncFolderBidirectional(cfg.sharedFolderPath, loaded);
        return res.vault;
      } catch (err) {
        console.warn('[NoteGraph] Startup folder sync skipped:', err.message);
      }
    }
    return loaded;
  });

  ipcMain.handle('vault:save', async (_event, vaultData) => {
    await scheduleAsyncVaultWrite(vaultData);
    return { ok: true, savedAt: Date.now() };
  });

  ipcMain.handle('vault:import-zip', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Import / Merge NoteGraph Sync Archive (.zip)',
      filters: [{ name: 'NoteGraph Backup ZIP', extensions: ['zip'] }],
      properties: ['openFile']
    });
    if (res.canceled || !res.filePaths.length) return null;

    const zipBytes = await fs.promises.readFile(res.filePaths[0]);
    const zip = await JSZip.loadAsync(zipBytes);
    const v2Entry = zip.file(ZIP_FILENAME_V2) || zip.file(SYNC_FILENAME_V2);
    const legacyEntry = zip.file(ZIP_FILENAME_LEGACY);
    if (!v2Entry && !legacyEntry) {
      throw new Error('Selected ZIP does not contain vault-sync-v2.json or notes.json');
    }
    const rawText = await (v2Entry || legacyEntry).async('string');
    const syncPackage = VaultBridge.parseSyncPackage(rawText);

    // Import embedded image previews if present so image cards render cleanly on desktop
    const assetImages = {};
    for (const [entryPath, entry] of Object.entries(zip.files)) {
      if (!entry.dir && entryPath.startsWith('assets/images/')) {
        const base = path.basename(entryPath);
        const buf = await entry.async('nodebuffer');
        if (buf.length <= 4 * 1024 * 1024) {
          const ext = path.extname(base).slice(1).toLowerCase() || 'png';
          const mime = ext === 'jpg' ? 'jpeg' : ext;
          assetImages[base] = `data:image/${mime};base64,${buf.toString('base64')}`;
        }
      }
    }

    return { filePath: res.filePaths[0], syncPackage, androidNotes: syncPackage.notes, assetImages };
  });

  ipcMain.handle('vault:export-zip', async (_event, payload) => {
    const isArrayPayload = Array.isArray(payload);
    const androidNotes = isArrayPayload ? payload : (payload && payload.androidNotes) || [];
    const pkgV2 = !isArrayPayload && payload && payload.pkgV2
      ? payload.pkgV2
      : VaultBridge.buildSyncPackage(readVaultFromDisk(), getDeviceMeta());

    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Cross-OS NoteGraph Sync Archive (.zip)',
      defaultPath: 'NoteGraph_Vault_Sync_v2.zip',
      filters: [{ name: 'NoteGraph Sync ZIP', extensions: ['zip'] }]
    });
    if (res.canceled || !res.filePath) return null;

    const zip = new JSZip();
    zip.file(ZIP_FILENAME_V2, JSON.stringify(pkgV2, null, 2));
    zip.file(ZIP_FILENAME_LEGACY, JSON.stringify(androidNotes, null, 2));
    const buffer = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
    await fs.promises.writeFile(res.filePath, buffer);
    return { filePath: res.filePath, count: pkgV2.notes.length };
  });

  // ─── Cross-OS Sync Config, Cloud Folder, & Direct Wi-Fi LAN IPC ─────

  ipcMain.handle('sync:get-config', () => buildSyncConfigPayload());

  ipcMain.handle('sync:choose-folder', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Choose Shared Cloud Sync Folder (iCloud / OneDrive / Dropbox / Drive)',
      properties: ['openDirectory', 'createDirectory']
    });
    if (res.canceled || !res.filePaths.length) return null;
    const cfg = readSyncConfig();
    cfg.sharedFolderPath = res.filePaths[0];
    writeSyncConfig(cfg);
    if (cfg.autoSyncFolder) restartFolderWatcher(cfg.sharedFolderPath);
    return syncFolderBidirectional(cfg.sharedFolderPath);
  });

  ipcMain.handle('sync:set-folder', (_event, { folderPath, autoSync }) => {
    const cfg = readSyncConfig();
    if (typeof folderPath === 'string') cfg.sharedFolderPath = folderPath;
    if (typeof autoSync === 'boolean') cfg.autoSyncFolder = autoSync;
    writeSyncConfig(cfg);
    if (cfg.sharedFolderPath && cfg.autoSyncFolder) {
      restartFolderWatcher(cfg.sharedFolderPath);
    } else if (folderWatcher) {
      try { folderWatcher.close(); } catch (_) {}
      folderWatcher = null;
    }
    if (cfg.sharedFolderPath) {
      return syncFolderBidirectional(cfg.sharedFolderPath);
    }
    return { config: buildSyncConfigPayload(cfg) };
  });

  ipcMain.handle('sync:folder-now', (_event, rendererVault = null) => {
    const cfg = readSyncConfig();
    if (!cfg.sharedFolderPath) throw new Error('Choose a shared sync folder first');
    return syncFolderBidirectional(cfg.sharedFolderPath, rendererVault);
  });

  ipcMain.handle('sync:lan-start', async () => {
    await startLanServer(DEFAULT_LAN_PORT);
    return buildSyncConfigPayload();
  });

  ipcMain.handle('sync:lan-stop', () => {
    stopLanServer();
    return buildSyncConfigPayload();
  });

  ipcMain.handle('sync:lan-regen-pin', () => {
    const cfg = readSyncConfig();
    cfg.lanPin = generatePin();
    writeSyncConfig(cfg);
    return buildSyncConfigPayload(cfg);
  });

  ipcMain.handle('sync:lan-sync-peer', async (_event, { hostPort, pin, rendererVault }) => {
    const { host, port } = parsePeerHostPort(hostPort);
    if (!host) throw new Error('Enter peer IP:Port (e.g. 192.168.1.25:8765)');

    const cfg = readSyncConfig();
    cfg.lastPeer = `${host}:${port}`;
    writeSyncConfig(cfg);

    const localVault = rendererVault || readVaultFromDisk();
    writeVaultToDiskSync(localVault);

    const outboundPkg = VaultBridge.buildSyncPackage(localVault, getDeviceMeta(cfg));
    const remoteResp = await postJsonHttp(host, port, '/api/sync', pin, outboundPkg);
    const remotePkg = VaultBridge.parseSyncPackage(remoteResp);
    const { vault: mergedVault, summary } = VaultBridge.mergeSyncPackage(localVault, remotePkg);
    writeVaultToDiskSync(mergedVault);

    const updatedCfg = readSyncConfig();
    updatedCfg.lastSyncAt = Date.now();
    updatedCfg.lastSyncMessage = `Wi-Fi synced with ${summary.remoteDevice} (${summary.notesMerged} merged, ${summary.newNotesAdded} added)`;
    writeSyncConfig(updatedCfg);
    pushMergedVaultToRenderer(mergedVault, summary);

    return { vault: mergedVault, summary, config: buildSyncConfigPayload(updatedCfg) };
  });

  // ─── File Pickers & Slide Exporters ───────────────────────────────

  ipcMain.handle('file:pick-pdf', async () => {
    const res = await dialog.showOpenDialog(mainWindow, {
      title: 'Import PDF Slides / Worksheet for Board Annotation',
      filters: [{ name: 'PDF Document', extensions: ['pdf'] }],
      properties: ['openFile']
    });
    if (res.canceled || !res.filePaths.length) return null;
    const filePath = res.filePaths[0];
    const bytes = await fs.promises.readFile(filePath);
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
    const bytes = await fs.promises.readFile(filePath);
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
    await fs.promises.writeFile(res.filePath, Buffer.from(pngBase64, 'base64'));
    return res.filePath;
  });

  ipcMain.handle('export:save-markdown', async (_event, { defaultName, markdownText }) => {
    const res = await dialog.showSaveDialog(mainWindow, {
      title: 'Export Note & Checklist as Markdown',
      defaultPath: `${defaultName || 'notegraph-note'}.md`,
      filters: [{ name: 'Markdown', extensions: ['md'] }]
    });
    if (res.canceled || !res.filePath) return null;
    await fs.promises.writeFile(res.filePath, markdownText, 'utf8');
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
    await fs.promises.writeFile(res.filePath, pdfBuffer);
    return res.filePath;
  });

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('before-quit', () => {
  if (vaultDirtyForDisk && latestVaultSnapshot) {
    try {
      writeVaultToDiskSync(latestVaultSnapshot);
    } catch (err) {
      console.error('[NoteGraph] Final vault flush failed:', err);
    }
  }
  stopLanServer();
  if (folderWatcher) {
    try { folderWatcher.close(); } catch (_) {}
    folderWatcher = null;
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
}
