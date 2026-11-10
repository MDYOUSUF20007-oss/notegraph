const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('notegraphDesktop', {
  getAppInfo: () => ipcRenderer.invoke('app:info'),
  toggleFullscreen: () => ipcRenderer.invoke('app:toggle-fullscreen'),
  loadVault: () => ipcRenderer.invoke('vault:load'),
  saveVault: (vaultData) => ipcRenderer.invoke('vault:save', vaultData),
  importVaultZip: () => ipcRenderer.invoke('vault:import-zip'),
  exportVaultZip: (payload) => ipcRenderer.invoke('vault:export-zip', payload),
  pickPdf: () => ipcRenderer.invoke('file:pick-pdf'),
  pickImage: () => ipcRenderer.invoke('file:pick-image'),
  savePng: (payload) => ipcRenderer.invoke('export:save-png', payload),
  saveMarkdown: (payload) => ipcRenderer.invoke('export:save-markdown', payload),
  savePdf: (payload) => ipcRenderer.invoke('export:save-pdf', payload),
  // Cross-OS Sync Hub (Cloud Folder + Direct Wi-Fi LAN)
  getSyncConfig: () => ipcRenderer.invoke('sync:get-config'),
  chooseSyncFolder: () => ipcRenderer.invoke('sync:choose-folder'),
  setSyncFolderConfig: (payload) => ipcRenderer.invoke('sync:set-folder', payload),
  syncFolderNow: (vaultData) => ipcRenderer.invoke('sync:folder-now', vaultData),
  startLanServer: () => ipcRenderer.invoke('sync:lan-start'),
  stopLanServer: () => ipcRenderer.invoke('sync:lan-stop'),
  regenLanPin: () => ipcRenderer.invoke('sync:lan-regen-pin'),
  syncLanPeer: (payload) => ipcRenderer.invoke('sync:lan-sync-peer', payload),
  onVaultUpdated: (callback) => {
    if (typeof callback !== 'function') return () => {};
    const listener = (_event, payload) => callback(payload);
    ipcRenderer.on('sync:vault-updated', listener);
    return () => ipcRenderer.removeListener('sync:vault-updated', listener);
  }
});
