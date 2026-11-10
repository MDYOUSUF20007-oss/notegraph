const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('notegraphDesktop', {
  getAppInfo: () => ipcRenderer.invoke('app:info'),
  toggleFullscreen: () => ipcRenderer.invoke('app:toggle-fullscreen'),
  loadVault: () => ipcRenderer.invoke('vault:load'),
  saveVault: (vaultData) => ipcRenderer.invoke('vault:save', vaultData),
  importVaultZip: () => ipcRenderer.invoke('vault:import-zip'),
  exportVaultZip: (androidNotes) => ipcRenderer.invoke('vault:export-zip', androidNotes),
  pickPdf: () => ipcRenderer.invoke('file:pick-pdf'),
  pickImage: () => ipcRenderer.invoke('file:pick-image'),
  savePng: (payload) => ipcRenderer.invoke('export:save-png', payload),
  saveMarkdown: (payload) => ipcRenderer.invoke('export:save-markdown', payload),
  savePdf: (payload) => ipcRenderer.invoke('export:save-pdf', payload)
});
