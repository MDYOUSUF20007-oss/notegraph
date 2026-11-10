/**
 * Headless Electron runtime smoke verification script.
 * Launches NoteGraph Board offscreen (hidden), loads app://bundle/index.html,
 * verifies preload bridge, rendered boards/cards/checklists, theme switching,
 * view switching, and dynamic offline import of pdf.min.mjs.
 */
const { app, BrowserWindow, protocol, net, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { pathToFileURL } = require('url');
const VaultBridge = require('../renderer/vault-bridge');

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

const tempUserData = fs.mkdtempSync(path.join(os.tmpdir(), 'notegraph-smoke-'));
app.setPath('userData', tempUserData);
app.disableHardwareAcceleration();

setTimeout(() => {
  console.error('[smoke-test] Timed out after 15s');
  app.exit(1);
}, 15000);

app.whenReady().then(async () => {
  try {
  const rendererDir = path.join(__dirname, '..', 'renderer');

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

  let savedVaultCount = 0;
  ipcMain.handle('app:info', () => ({ version: '2.0.0-smoke', platform: process.platform }));
  ipcMain.handle('app:toggle-fullscreen', () => false);
  ipcMain.handle('vault:load', () => VaultBridge.createStarterVault());
  ipcMain.handle('vault:save', () => {
    savedVaultCount += 1;
    return { ok: true };
  });
  ipcMain.handle('sync:get-config', () => ({
    deviceId: 'smoke-dev',
    deviceName: 'Smoke Test Machine',
    platform: process.platform === 'darwin' ? 'macos' : 'windows',
    sharedFolderPath: null,
    autoSyncFolder: true,
    lastSyncAt: 0,
    lastSyncMessage: 'Ready in smoke test',
    suggestedFolders: [],
    lan: { isRunning: false, port: 8765, addresses: ['127.0.0.1'], pin: '1234' }
  }));

  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false
    }
  });

  const consoleErrors = [];
  win.webContents.on('console-message', (_e, level, message) => {
    if (level >= 3) consoleErrors.push(message);
  });
  win.webContents.on('did-fail-load', (_e, code, desc) => {
    console.error('did-fail-load:', code, desc);
    app.exit(1);
  });

  await win.loadURL('app://bundle/index.html');
  // Wait briefly for module initialization + async loadVault() render
  await new Promise((r) => setTimeout(r, 900));

  const report = await win.webContents.executeJavaScript(`(async () => {
    const titleVal = document.getElementById('activeTitleInput').value;
    const foldersCount = document.querySelectorAll('#folderList .folder-row').length;
    const cardsCount = document.querySelectorAll('#cardsLayer .spatial-card').length;
    const slidesCount = document.querySelectorAll('#slideTabsContainer .slide-tab').length;
    const checklistsCount = document.querySelectorAll('.checklist-box').length;

    // Test interactive theme switch, checklist check, and view switching
    document.querySelector('[data-theme-choice="KINDLE_PARCHMENT"]').click();
    const themeAfterClick = document.documentElement.getAttribute('data-theme');
    const firstCheckbox = document.querySelector('#cardsLayer .checklist-box');
    const prevChecked = firstCheckbox.checked;
    firstCheckbox.click();
    const afterChecked = firstCheckbox.checked;
    document.querySelector('[data-view-target="notes"]').click();
    const noteViewActive = document.getElementById('notesView').classList.contains('active');
    document.querySelector('[data-view-target="graph"]').click();
    const graphViewActive = document.getElementById('graphView').classList.contains('active');

    // Test Laser tool switch and Sync Hub modal
    document.querySelector('[data-view-target="board"]').click();
    document.querySelector('[data-tool="laser"]').click();
    const laserActive = document.querySelector('[data-tool="laser"]').classList.contains('active');
    document.getElementById('openSyncModalBtn').click();
    await new Promise((r) => setTimeout(r, 80));
    const syncModalOpened = !document.getElementById('syncModal').classList.contains('hidden');
    const syncDeviceText = document.getElementById('syncDeviceSummary').textContent;
    document.querySelector('[data-close-modal="syncModal"]').click();

    // Test PDF.js offline module import inside app:// bundle protocol
    const pdfjs = await import('./vendor/pdf.min.mjs');
    return {
      titleVal,
      foldersCount,
      cardsCount,
      slidesCount,
      checklistsCount,
      themeAfterClick,
      checklistToggled: prevChecked !== afterChecked,
      noteViewActive,
      graphViewActive,
      laserActive,
      syncModalOpened,
      syncDeviceText,
      hasPdfJsGetDoc: typeof pdfjs.getDocument === 'function'
    };
  })()`);

  // Wait briefly for debounced vault:save to flush
  await new Promise((r) => setTimeout(r, 500));

  console.log('[smoke-test] DOM + Runtime Report:', JSON.stringify(report, null, 2));
  console.log('[smoke-test] Console errors:', consoleErrors.length, consoleErrors);
  console.log('[smoke-test] Vault saves triggered:', savedVaultCount);

  try { fs.rmSync(tempUserData, { recursive: true, force: true }); } catch (_) {}

  const ok =
    consoleErrors.length === 0 &&
    report.titleVal.includes('Physics') &&
    report.foldersCount >= 8 &&
    report.cardsCount >= 2 &&
    report.slidesCount >= 2 &&
    report.checklistsCount >= 4 &&
    report.themeAfterClick === 'KINDLE_PARCHMENT' &&
    report.checklistToggled &&
    report.noteViewActive &&
    report.graphViewActive &&
    report.laserActive &&
    report.syncModalOpened &&
    report.syncDeviceText.includes('Smoke Test Machine') &&
    report.hasPdfJsGetDoc &&
    savedVaultCount >= 1;

  if (!ok) {
    console.error('[smoke-test] Assertion failure!');
    app.exit(1);
    return;
  }

  console.log('[smoke-test] PASSED ALL CHECKS!');
  app.exit(0);
  } catch (err) {
    console.error('[smoke-test] ERROR:', err);
    try { fs.rmSync(tempUserData, { recursive: true, force: true }); } catch (_) {}
    app.exit(1);
  }
});
