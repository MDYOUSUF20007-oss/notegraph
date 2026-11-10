(function () {
  'use strict';

  const VaultBridge = window.VaultBridge;
  const BoardCanvasEngine = window.BoardCanvasEngine;
  const desktop = window.notegraphDesktop;

  const GRID_ORDER = ['DOTS', 'RULED', 'GRAPH', 'NONE'];
  const GRID_LABELS = {
    DOTS: 'Dots',
    RULED: 'Cornell',
    GRAPH: 'XY Grid',
    NONE: 'Blank'
  };

  const THEME_LABELS = {
    DEEP_PURPLE_AMOLED: 'AMOLED Gradients',
    KINDLE_PARCHMENT: 'Kindle Gradients',
    MATTE_STUDIO: 'Matte / Mac Gradients',
    BLACKBOARD_GREEN: 'Blackboard Gradients'
  };

  const THEME_GRADIENT_PRESETS = {
    DEEP_PURPLE_AMOLED: [
      {
        id: 'obsidian_violet',
        name: 'Obsidian Violet',
        accentStart: '#A855F7',
        accentEnd: '#6366F1',
        onAccent: '#FFFFFF',
        bgTop: '#140726',
        bgMid: '#05010A',
        bgBottom: '#000000',
        surface: '#07040D',
        elevated: '#110920',
        cardBg: '#0E0819',
        cardTint: '#1B0F30',
        glass: 'rgba(10, 6, 18, 0.78)',
        borderSubtle: 'rgba(168, 85, 247, 0.20)',
        borderStrong: 'rgba(168, 85, 247, 0.50)',
        dotColor: '#2A2238',
        lineColor: '#191128'
      },
      {
        id: 'aurora_cyan',
        name: 'Aurora Cyan',
        accentStart: '#0EA5E9',
        accentEnd: '#6366F1',
        onAccent: '#FFFFFF',
        bgTop: '#051824',
        bgMid: '#02070E',
        bgBottom: '#000000',
        surface: '#040B12',
        elevated: '#091826',
        cardBg: '#07111A',
        cardTint: '#0D2032',
        glass: 'rgba(6, 16, 26, 0.80)',
        borderSubtle: 'rgba(14, 165, 233, 0.22)',
        borderStrong: 'rgba(14, 165, 233, 0.52)',
        dotColor: '#17354D',
        lineColor: '#0E2233'
      },
      {
        id: 'crimson_velvet',
        name: 'Crimson Velvet',
        accentStart: '#F43F5E',
        accentEnd: '#A855F7',
        onAccent: '#FFFFFF',
        bgTop: '#210712',
        bgMid: '#080206',
        bgBottom: '#000000',
        surface: '#0E0409',
        elevated: '#1B0A12',
        cardBg: '#14080E',
        cardTint: '#260D19',
        glass: 'rgba(18, 6, 12, 0.80)',
        borderSubtle: 'rgba(244, 63, 94, 0.22)',
        borderStrong: 'rgba(244, 63, 94, 0.52)',
        dotColor: '#3E182B',
        lineColor: '#260E1B'
      },
      {
        id: 'emerald_flux',
        name: 'Emerald Flux',
        accentStart: '#10B981',
        accentEnd: '#06B6D4',
        onAccent: '#03140F',
        bgTop: '#041C16',
        bgMid: '#010806',
        bgBottom: '#000000',
        surface: '#030E0B',
        elevated: '#071B15',
        cardBg: '#061310',
        cardTint: '#0B241E',
        glass: 'rgba(4, 18, 14, 0.80)',
        borderSubtle: 'rgba(16, 185, 129, 0.22)',
        borderStrong: 'rgba(16, 185, 129, 0.52)',
        dotColor: '#143A30',
        lineColor: '#0C241E'
      },
      {
        id: 'solar_ember',
        name: 'Solar Ember',
        accentStart: '#F59E0B',
        accentEnd: '#EC4899',
        onAccent: '#1C0C02',
        bgTop: '#1F0E04',
        bgMid: '#080304',
        bgBottom: '#000000',
        surface: '#0D0603',
        elevated: '#1A0D07',
        cardBg: '#130B06',
        cardTint: '#24140B',
        glass: 'rgba(18, 9, 5, 0.80)',
        borderSubtle: 'rgba(245, 158, 11, 0.22)',
        borderStrong: 'rgba(245, 158, 11, 0.52)',
        dotColor: '#3B2214',
        lineColor: '#24150C'
      },
      {
        id: 'stealth_mono',
        name: 'OLED Stealth',
        accentStart: '#CBD5E1',
        accentEnd: '#94A3B8',
        onAccent: '#0B0D11',
        bgTop: '#0B0D11',
        bgMid: '#020203',
        bgBottom: '#000000',
        surface: '#060709',
        elevated: '#12151C',
        cardBg: '#0D0E12',
        cardTint: '#161920',
        glass: 'rgba(10, 11, 15, 0.82)',
        borderSubtle: 'rgba(148, 163, 184, 0.20)',
        borderStrong: 'rgba(203, 213, 225, 0.45)',
        dotColor: '#252A34',
        lineColor: '#161A21'
      }
    ],
    KINDLE_PARCHMENT: [
      {
        id: 'warm_sepia',
        name: 'Warm Sepia',
        accentStart: '#B45309',
        accentEnd: '#D97706',
        onAccent: '#FFFFFF',
        bgTop: '#F7F4EC',
        bgMid: '#F3EEE2',
        bgBottom: '#EBE4D4',
        surface: '#EFEBE0',
        elevated: '#E6E0D2',
        cardBg: '#FCF9F2',
        cardTint: '#F3EADC',
        glass: 'rgba(247, 244, 236, 0.86)',
        borderSubtle: 'rgba(140, 124, 98, 0.24)',
        borderStrong: 'rgba(180, 83, 9, 0.55)',
        dotColor: '#C8BFA8',
        lineColor: '#DCD6CD'
      },
      {
        id: 'sage_folio',
        name: 'Sage Folio',
        accentStart: '#2D6A4F',
        accentEnd: '#528C5C',
        onAccent: '#FFFFFF',
        bgTop: '#F3F6EF',
        bgMid: '#EDF2E7',
        bgBottom: '#E3EAD9',
        surface: '#EAF0E3',
        elevated: '#DFE7D6',
        cardBg: '#FAFDF7',
        cardTint: '#E8EFE2',
        glass: 'rgba(243, 246, 239, 0.88)',
        borderSubtle: 'rgba(82, 112, 92, 0.24)',
        borderStrong: 'rgba(45, 106, 79, 0.55)',
        dotColor: '#B5C5B0',
        lineColor: '#CCD7C5'
      },
      {
        id: 'editorial_rose',
        name: 'Dusty Rose',
        accentStart: '#9D4452',
        accentEnd: '#BE6A73',
        onAccent: '#FFFFFF',
        bgTop: '#F8F2F1',
        bgMid: '#F2E9E7',
        bgBottom: '#E8DCDA',
        surface: '#EFE5E3',
        elevated: '#E5D8D6',
        cardBg: '#FDF9F8',
        cardTint: '#F1E3E2',
        glass: 'rgba(248, 242, 241, 0.88)',
        borderSubtle: 'rgba(157, 68, 82, 0.22)',
        borderStrong: 'rgba(157, 68, 82, 0.52)',
        dotColor: '#CBB8B6',
        lineColor: '#DCCAC8'
      },
      {
        id: 'oxford_ink',
        name: 'Oxford Ink',
        accentStart: '#2B4C7E',
        accentEnd: '#476A9E',
        onAccent: '#FFFFFF',
        bgTop: '#F2F5F9',
        bgMid: '#EBEFF6',
        bgBottom: '#E0E7F1',
        surface: '#E8EDF5',
        elevated: '#DCE4EE',
        cardBg: '#FAFBFD',
        cardTint: '#E4EBF4',
        glass: 'rgba(242, 245, 249, 0.88)',
        borderSubtle: 'rgba(43, 76, 126, 0.22)',
        borderStrong: 'rgba(43, 76, 126, 0.52)',
        dotColor: '#B8C4D4',
        lineColor: '#C9D3E0'
      },
      {
        id: 'classic_paper',
        name: 'Classic Ink',
        accentStart: '#44403C',
        accentEnd: '#78716C',
        onAccent: '#FFFFFF',
        bgTop: '#F4F1EA',
        bgMid: '#F4F1EA',
        bgBottom: '#EEE9DF',
        surface: '#ECE8DF',
        elevated: '#E2DDD3',
        cardBg: '#FAF7F1',
        cardTint: '#EDE8DE',
        glass: 'rgba(244, 241, 234, 0.90)',
        borderSubtle: 'rgba(120, 113, 108, 0.24)',
        borderStrong: 'rgba(68, 64, 60, 0.55)',
        dotColor: '#C4BCB0',
        lineColor: '#DCD6CD'
      }
    ],
    MATTE_STUDIO: [
      {
        id: 'studio_cyan',
        name: 'Studio Cyan',
        accentStart: '#38BDF8',
        accentEnd: '#6366F1',
        onAccent: '#0A1320',
        bgTop: '#1A2230',
        bgMid: '#1E2229',
        bgBottom: '#15181F',
        surface: '#161920',
        elevated: '#232833',
        cardBg: '#212631',
        cardTint: '#2B3446',
        glass: 'rgba(24, 28, 37, 0.82)',
        borderSubtle: 'rgba(148, 163, 184, 0.18)',
        borderStrong: 'rgba(56, 189, 248, 0.50)',
        dotColor: '#3B4252',
        lineColor: '#2B313D'
      },
      {
        id: 'nord_indigo',
        name: 'Nord Indigo',
        accentStart: '#818CF8',
        accentEnd: '#A78BFA',
        onAccent: '#0E1022',
        bgTop: '#1E2030',
        bgMid: '#1D202A',
        bgBottom: '#161820',
        surface: '#171923',
        elevated: '#242736',
        cardBg: '#222534',
        cardTint: '#2E3249',
        glass: 'rgba(26, 28, 40, 0.84)',
        borderSubtle: 'rgba(129, 140, 248, 0.20)',
        borderStrong: 'rgba(129, 140, 248, 0.52)',
        dotColor: '#3D4259',
        lineColor: '#2C3042'
      },
      {
        id: 'titanium_sage',
        name: 'Titanium Sage',
        accentStart: '#34D399',
        accentEnd: '#2DD4BF',
        onAccent: '#061B16',
        bgTop: '#182423',
        bgMid: '#1C2326',
        bgBottom: '#151A1C',
        surface: '#151B1C',
        elevated: '#21292B',
        cardBg: '#1E2627',
        cardTint: '#273936',
        glass: 'rgba(22, 30, 31, 0.84)',
        borderSubtle: 'rgba(52, 211, 153, 0.18)',
        borderStrong: 'rgba(52, 211, 153, 0.48)',
        dotColor: '#334342',
        lineColor: '#263332'
      },
      {
        id: 'terracotta_slate',
        name: 'Warm Copper',
        accentStart: '#FB923C',
        accentEnd: '#EC4899',
        onAccent: '#1D0D07',
        bgTop: '#261F22',
        bgMid: '#202127',
        bgBottom: '#17181D',
        surface: '#19191E',
        elevated: '#27262E',
        cardBg: '#24232A',
        cardTint: '#382C33',
        glass: 'rgba(30, 26, 30, 0.84)',
        borderSubtle: 'rgba(251, 146, 60, 0.20)',
        borderStrong: 'rgba(251, 146, 60, 0.50)',
        dotColor: '#423A44',
        lineColor: '#2F2931'
      },
      {
        id: 'anodized_silver',
        name: 'Anodized Gray',
        accentStart: '#CBD5E1',
        accentEnd: '#94A3B8',
        onAccent: '#101318',
        bgTop: '#22252C',
        bgMid: '#1E2229',
        bgBottom: '#171A20',
        surface: '#181B22',
        elevated: '#252932',
        cardBg: '#232730',
        cardTint: '#2F3440',
        glass: 'rgba(28, 31, 38, 0.86)',
        borderSubtle: 'rgba(203, 213, 225, 0.18)',
        borderStrong: 'rgba(203, 213, 225, 0.46)',
        dotColor: '#3B414F',
        lineColor: '#2C313C'
      }
    ],
    BLACKBOARD_GREEN: [
      {
        id: 'emerald_chalk',
        name: 'Emerald Chalk',
        accentStart: '#34D399',
        accentEnd: '#0EA5E9',
        onAccent: '#052E16',
        bgTop: '#0D2A1F',
        bgMid: '#091F17',
        bgBottom: '#06150F',
        surface: '#071812',
        elevated: '#102B20',
        cardBg: '#0E281E',
        cardTint: '#15392B',
        glass: 'rgba(8, 25, 19, 0.84)',
        borderSubtle: 'rgba(52, 211, 153, 0.18)',
        borderStrong: 'rgba(52, 211, 153, 0.50)',
        dotColor: '#1E4636',
        lineColor: '#153629'
      },
      {
        id: 'matcha_board',
        name: 'Matcha Slate',
        accentStart: '#A3E635',
        accentEnd: '#22C55E',
        onAccent: '#0F1F05',
        bgTop: '#132415',
        bgMid: '#0D1C0F',
        bgBottom: '#08120A',
        surface: '#09140B',
        elevated: '#142617',
        cardBg: '#122215',
        cardTint: '#1B3420',
        glass: 'rgba(11, 24, 13, 0.84)',
        borderSubtle: 'rgba(163, 230, 53, 0.18)',
        borderStrong: 'rgba(163, 230, 53, 0.48)',
        dotColor: '#274229',
        lineColor: '#1B301D'
      },
      {
        id: 'cobalt_slate',
        name: 'Lecture Cobalt',
        accentStart: '#38BDF8',
        accentEnd: '#34D399',
        onAccent: '#061A26',
        bgTop: '#0A2229',
        bgMid: '#071B21',
        bgBottom: '#051217',
        surface: '#06161B',
        elevated: '#0E2830',
        cardBg: '#0C232A',
        cardTint: '#13353F',
        glass: 'rgba(7, 23, 28, 0.84)',
        borderSubtle: 'rgba(56, 189, 248, 0.18)',
        borderStrong: 'rgba(56, 189, 248, 0.50)',
        dotColor: '#1B4450',
        lineColor: '#12323B'
      }
    ]
  };

  function svgMini(kind) {
    const map = {
      layers: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 12 12 17 22 12"/><polyline points="2 17 12 22 22 17"/></svg>',
      bell: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
      inbox: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg>',
      pin: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><path d="M12 17v5"/><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"/></svg>',
      note: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="14 3 14 9 20 9"/></svg>',
      checklist: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>',
      pdf: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>',
      image: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>',
      lock: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>',
      unlock: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg>',
      edit: '<svg class="ui-svg ui-svg-xs" viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>'
    };
    return map[kind] || '';
  }

  function hexToRgbaCss(hex, alpha) {
    if (!hex || typeof hex !== 'string') return `rgba(168,85,247,${alpha})`;
    let clean = hex.replace('#', '').trim();
    if (clean.length === 3) clean = clean.split('').map((c) => c + c).join('');
    if (clean.length !== 6) return `rgba(168,85,247,${alpha})`;
    const num = parseInt(clean, 16);
    if (Number.isNaN(num)) return `rgba(168,85,247,${alpha})`;
    return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`;
  }

  let vault = null;
  let activeFolderFilter = 'ALL'; // 'ALL' | 'UNFILED' | 'FOLLOWUPS' | folderId
  let activeView = 'board'; // 'board' | 'notes' | 'graph'
  let searchQuery = '';
  let saveTimer = null;
  let isZenMode = false;
  let isMacOS = false;
  let syncConfigCache = null;
  let lastSyncToastAt = 0;

  // Connect workflow state
  let pendingConnectSourceId = null;
  let pendingConnectTargetId = null;

  // PDF import transient state
  let pendingPdfDoc = null;
  let pendingPdfName = '';

  // Folder editor transient state
  let editingFolderId = null;
  let pickedFolderIcon = '📚';
  let pickedFolderColor = '#6366F1';

  // Classroom stopwatch / Pomodoro state
  let timerMode = 'stopwatch'; // 'stopwatch' | 'pomodoro'
  let timerRunning = false;
  let timerSeconds = 0;
  let timerInterval = null;

  // Lazy PDF.js cache
  let pdfjsModule = null;

  // DOM Cache
  const el = {
    appShell: document.getElementById('appShell'),
    sidebar: document.getElementById('sidebar'),
    toggleSidebarBtn: document.getElementById('toggleSidebarBtn'),
    activeTitleInput: document.getElementById('activeTitleInput'),
    pinNoteBtn: document.getElementById('pinNoteBtn'),
    followUpBtn: document.getElementById('followUpBtn'),
    followUpLabel: document.getElementById('followUpLabel'),
    followUpPopover: document.getElementById('followUpPopover'),
    followUpDateInput: document.getElementById('followUpDateInput'),
    clearFollowUpBtn: document.getElementById('clearFollowUpBtn'),
    classTimerGroup: document.getElementById('classTimerGroup'),
    classTimerBtn: document.getElementById('classTimerBtn'),
    classTimerDisplay: document.getElementById('classTimerDisplay'),
    classTimerModeBtn: document.getElementById('classTimerModeBtn'),
    classTimerResetBtn: document.getElementById('classTimerResetBtn'),
    zenModeBtn: document.getElementById('zenModeBtn'),
    exitZenFloatingBtn: document.getElementById('exitZenFloatingBtn'),
    gradientBtn: document.getElementById('gradientBtn'),
    gradientBtnOrb: document.getElementById('gradientBtnOrb'),
    gradientBtnLabel: document.getElementById('gradientBtnLabel'),
    gradientPopover: document.getElementById('gradientPopover'),
    gradientPopThemeTitle: document.getElementById('gradientPopThemeTitle'),
    gradientToggleBtn: document.getElementById('gradientToggleBtn'),
    gradientOrbsGrid: document.getElementById('gradientOrbsGrid'),
    gradientResetBtn: document.getElementById('gradientResetBtn'),
    openTutorialBtn: document.getElementById('openTutorialBtn'),
    openSyncModalBtn: document.getElementById('openSyncModalBtn'),
    importMenuBtn: document.getElementById('importMenuBtn'),
    importMenu: document.getElementById('importMenu'),
    exportMenuBtn: document.getElementById('exportMenuBtn'),
    exportMenu: document.getElementById('exportMenu'),
    sidebarSearchInput: document.getElementById('sidebarSearchInput'),
    cmdPaletteBtn: document.getElementById('cmdPaletteBtn'),
    newBoardBtn: document.getElementById('newBoardBtn'),
    todayJournalBtn: document.getElementById('todayJournalBtn'),
    newFolderBtn: document.getElementById('newFolderBtn'),
    folderList: document.getElementById('folderList'),
    notesListHeading: document.getElementById('notesListHeading'),
    notesCountBadge: document.getElementById('notesCountBadge'),
    notesList: document.getElementById('notesList'),
    saveStatusLabel: document.getElementById('saveStatusLabel'),
    boardView: document.getElementById('boardView'),
    notesView: document.getElementById('notesView'),
    graphView: document.getElementById('graphView'),
    boardStage: document.getElementById('boardStage'),
    gridCanvas: document.getElementById('gridCanvas'),
    inkStaticCanvas: document.getElementById('inkStaticCanvas'),
    cardsLayer: document.getElementById('cardsLayer'),
    inkLiveCanvas: document.getElementById('inkLiveCanvas'),
    shapeSnapBtn: document.getElementById('shapeSnapBtn'),
    customInkColor: document.getElementById('customInkColor'),
    strokeWidthSlider: document.getElementById('strokeWidthSlider'),
    gridCycleBtn: document.getElementById('gridCycleBtn'),
    lassoBar: document.getElementById('lassoBar'),
    lassoCountLabel: document.getElementById('lassoCountLabel'),
    lassoMoveModeBtn: document.getElementById('lassoMoveModeBtn'),
    connectHintPill: document.getElementById('connectHintPill'),
    slideTabsContainer: document.getElementById('slideTabsContainer'),
    addSlideBtn: document.getElementById('addSlideBtn'),
    zoomOutBtn: document.getElementById('zoomOutBtn'),
    zoomResetBtn: document.getElementById('zoomResetBtn'),
    zoomInBtn: document.getElementById('zoomInBtn'),
    zoomFitBtn: document.getElementById('zoomFitBtn'),
    notesFolderSelect: document.getElementById('notesFolderSelect'),
    noteViewTitleInput: document.getElementById('noteViewTitleInput'),
    noteViewBodyInput: document.getElementById('noteViewBodyInput'),
    outgoingLinksRow: document.getElementById('outgoingLinksRow'),
    backlinksRow: document.getElementById('backlinksRow'),
    noteViewChecklists: document.getElementById('noteViewChecklists'),
    graphFilterInput: document.getElementById('graphFilterInput'),
    graphResetLayoutBtn: document.getElementById('graphResetLayoutBtn'),
    graphGuideBtn: document.getElementById('graphGuideBtn'),
    graphCanvas: document.getElementById('graphCanvas'),
    tutorialModal: document.getElementById('tutorialModal'),
    tutorialBody: document.getElementById('tutorialBody'),
    tutOpenGraphBtn: document.getElementById('tutOpenGraphBtn'),
    cmdPaletteModal: document.getElementById('cmdPaletteModal'),
    cmdPaletteInput: document.getElementById('cmdPaletteInput'),
    cmdPaletteResults: document.getElementById('cmdPaletteResults'),
    folderModal: document.getElementById('folderModal'),
    folderModalTitle: document.getElementById('folderModalTitle'),
    folderNameInput: document.getElementById('folderNameInput'),
    deleteFolderBtn: document.getElementById('deleteFolderBtn'),
    saveFolderBtn: document.getElementById('saveFolderBtn'),
    pdfImportModal: document.getElementById('pdfImportModal'),
    pdfImportSummary: document.getElementById('pdfImportSummary'),
    pdfProgressWrap: document.getElementById('pdfProgressWrap'),
    pdfProgressFill: document.getElementById('pdfProgressFill'),
    pdfProgressText: document.getElementById('pdfProgressText'),
    linkPickerModal: document.getElementById('linkPickerModal'),
    linkPickerSearch: document.getElementById('linkPickerSearch'),
    linkPickerResults: document.getElementById('linkPickerResults'),
    connectLabelModal: document.getElementById('connectLabelModal'),
    connectLabelInput: document.getElementById('connectLabelInput'),
    syncModal: document.getElementById('syncModal'),
    syncDeviceSummary: document.getElementById('syncDeviceSummary'),
    syncLastStatusLabel: document.getElementById('syncLastStatusLabel'),
    syncAutoFolderToggle: document.getElementById('syncAutoFolderToggle'),
    syncCloudSuggestions: document.getElementById('syncCloudSuggestions'),
    syncFolderPathLabel: document.getElementById('syncFolderPathLabel'),
    syncChooseFolderBtn: document.getElementById('syncChooseFolderBtn'),
    syncFolderNowBtn: document.getElementById('syncFolderNowBtn'),
    syncLanHostToggle: document.getElementById('syncLanHostToggle'),
    syncLanHostInfo: document.getElementById('syncLanHostInfo'),
    syncLanHostAddrLabel: document.getElementById('syncLanHostAddrLabel'),
    syncLanPinBadge: document.getElementById('syncLanPinBadge'),
    syncLanRegenPinBtn: document.getElementById('syncLanRegenPinBtn'),
    syncLanPeerInput: document.getElementById('syncLanPeerInput'),
    syncLanPeerPinInput: document.getElementById('syncLanPeerPinInput'),
    syncLanConnectBtn: document.getElementById('syncLanConnectBtn'),
    syncZipExportBtn: document.getElementById('syncZipExportBtn'),
    syncZipImportBtn: document.getElementById('syncZipImportBtn'),
    toastContainer: document.getElementById('toastContainer')
  };

  let engine = null;

  function showToast(msg) {
    const t = document.createElement('div');
    t.className = 'toast';
    t.textContent = msg;
    el.toastContainer.appendChild(t);
    setTimeout(() => {
      t.remove();
    }, 3000);
  }

  function getActiveNote() {
    if (!vault || !Array.isArray(vault.notes)) return null;
    let found = vault.notes.find((n) => n.id === vault.activeNoteId && !n.isTrash);
    if (!found) {
      found = vault.notes.find((n) => !n.isTrash) || null;
      vault.activeNoteId = found ? found.id : null;
    }
    if (found && !found.board) {
      found.board = {
        gridStyle: 'DOTS',
        activePageIndex: 0,
        pages: [VaultBridge.createEmptyBoardPage('Slide 1')]
      };
    }
    return found;
  }

  function getActivePage(note = getActiveNote()) {
    if (!note || !note.board || !Array.isArray(note.board.pages) || note.board.pages.length === 0) {
      return null;
    }
    const idx = Math.max(0, Math.min(note.board.activePageIndex || 0, note.board.pages.length - 1));
    note.board.activePageIndex = idx;
    return note.board.pages[idx];
  }

  function scheduleSave(immediate = false) {
    el.saveStatusLabel.textContent = 'Saving...';
    if (saveTimer) clearTimeout(saveTimer);
    if (immediate) {
      persistNow();
    } else {
      saveTimer = setTimeout(persistNow, 380);
    }
  }

  async function persistNow() {
    if (!vault) return;
    try {
      await desktop.saveVault(vault);
      const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      el.saveStatusLabel.textContent = `Saved offline · ${timeStr}`;
    } catch (err) {
      console.error('Failed to save vault:', err);
      el.saveStatusLabel.textContent = 'Save error (retrying)';
    }
  }

  function ensureVaultGradientState() {
    if (!vault) return;
    if (typeof vault.gradientsEnabled !== 'boolean') vault.gradientsEnabled = true;
    if (!vault.themeGradients || typeof vault.themeGradients !== 'object') {
      vault.themeGradients = {};
    }
    for (const [themeKey, presets] of Object.entries(THEME_GRADIENT_PRESETS)) {
      const validIds = new Set(presets.map((p) => p.id));
      if (!validIds.has(vault.themeGradients[themeKey])) {
        vault.themeGradients[themeKey] = presets[0].id;
      }
    }
  }

  function getActiveGradientPreset(themeName = (vault && vault.activeTheme) || 'DEEP_PURPLE_AMOLED') {
    const list = THEME_GRADIENT_PRESETS[themeName] || THEME_GRADIENT_PRESETS.DEEP_PURPLE_AMOLED;
    const savedId = vault && vault.themeGradients ? vault.themeGradients[themeName] : null;
    return list.find((p) => p.id === savedId) || list[0];
  }

  function applyGradientTheme(themeName = (vault && vault.activeTheme) || 'DEEP_PURPLE_AMOLED') {
    ensureVaultGradientState();
    const resolved = themeName || 'DEEP_PURPLE_AMOLED';
    const preset = getActiveGradientPreset(resolved);
    const enabled = vault ? vault.gradientsEnabled !== false : true;
    const rootStyle = document.documentElement.style;

    document.documentElement.setAttribute('data-gradients', enabled ? 'on' : 'off');
    rootStyle.setProperty('--accent-start', preset.accentStart);
    rootStyle.setProperty('--accent-end', preset.accentEnd);
    rootStyle.setProperty('--accent', preset.accentStart);
    rootStyle.setProperty('--accent-contrast', preset.onAccent);
    rootStyle.setProperty('--accent-soft', hexToRgbaCss(preset.accentStart, 0.14));
    rootStyle.setProperty(
      '--accent-gradient',
      enabled ? `linear-gradient(135deg, ${preset.accentStart}, ${preset.accentEnd})` : preset.accentStart
    );
    rootStyle.setProperty('--bg-root', preset.bgBottom);
    rootStyle.setProperty('--bg-surface', preset.surface);
    rootStyle.setProperty('--bg-elevated', preset.elevated);
    rootStyle.setProperty('--bg-card', preset.cardBg);
    rootStyle.setProperty('--bg-card-tint', preset.cardTint);
    rootStyle.setProperty('--bg-glass', preset.glass);
    rootStyle.setProperty('--border-subtle', preset.borderSubtle);
    rootStyle.setProperty('--border-strong', preset.borderStrong);
    rootStyle.setProperty(
      '--bg-gradient',
      enabled
        ? `linear-gradient(180deg, ${preset.bgTop} 0%, ${preset.bgMid} 52%, ${preset.bgBottom} 100%)`
        : preset.bgBottom
    );

    if (el.gradientBtnOrb) {
      el.gradientBtnOrb.style.background = `linear-gradient(135deg, ${preset.accentStart}, ${preset.accentEnd})`;
    }
    if (el.gradientBtnLabel) {
      el.gradientBtnLabel.textContent = preset.name;
    }
    if (el.gradientPopThemeTitle) {
      el.gradientPopThemeTitle.textContent = THEME_LABELS[resolved] || 'Theme Gradients';
    }
    if (el.gradientToggleBtn) {
      el.gradientToggleBtn.classList.toggle('active', enabled);
      el.gradientToggleBtn.textContent = enabled ? 'Ambient On' : 'Flat Mode';
    }

    const note = getActiveNote();
    if (engine) {
      engine.setAppearance(
        resolved,
        (note && note.board && note.board.gridStyle) || 'DOTS',
        preset,
        enabled
      );
    }
    renderGradientPopover();
    renderGraphIfVisible();
  }

  function renderGradientPopover() {
    if (!el.gradientOrbsGrid || !vault) return;
    const resolved = vault.activeTheme || 'DEEP_PURPLE_AMOLED';
    const presets = THEME_GRADIENT_PRESETS[resolved] || THEME_GRADIENT_PRESETS.DEEP_PURPLE_AMOLED;
    const active = getActiveGradientPreset(resolved);

    el.gradientOrbsGrid.innerHTML = '';
    for (const preset of presets) {
      const isSelected = preset.id === active.id;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `gradient-orb-item${isSelected ? ' active' : ''}`;
      btn.title = preset.name;
      btn.innerHTML = `
        <span class="gradient-orb-swatch" style="background:linear-gradient(135deg, ${preset.accentStart}, ${preset.accentEnd})">${isSelected ? '✓' : ''}</span>
        <span class="gradient-orb-label">${escapeHtml(preset.name)}</span>
      `;
      btn.addEventListener('click', () => {
        ensureVaultGradientState();
        vault.themeGradients[resolved] = preset.id;
        applyGradientTheme(resolved);
        scheduleSave();
      });
      el.gradientOrbsGrid.appendChild(btn);
    }
  }

  function applyTheme(themeName) {
    const resolved = themeName || 'DEEP_PURPLE_AMOLED';
    vault.activeTheme = resolved;
    document.documentElement.setAttribute('data-theme', resolved);
    document.querySelectorAll('.theme-chip').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.themeChoice === resolved);
    });
    applyGradientTheme(resolved);
  }

  function formatDateChip(isoDate) {
    if (!isoDate) return 'Follow-Up';
    const today = new Date().toISOString().slice(0, 10);
    if (isoDate === today) return 'Today';
    const d = new Date(`${isoDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }

  function addDaysIso(offsetDays) {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    return d.toISOString().slice(0, 10);
  }

  function getChecklistStats(note) {
    let total = 0;
    let done = 0;
    const pages = (note.board && note.board.pages) || [];
    for (const p of pages) {
      for (const c of (p.cards || [])) {
        if (c.kind === 'checklist' && Array.isArray(c.items)) {
          total += c.items.length;
          done += c.items.filter((i) => i.isChecked).length;
        }
      }
    }
    return { total, done };
  }

  // ==================== SIDEBAR: FOLDERS & NOTES ====================

  function renderFolders() {
    const activeNotes = (vault.notes || []).filter((n) => !n.isTrash);
    const followUpCount = activeNotes.filter((n) => Boolean(n.followUpAt)).length;
    const unfiledCount = activeNotes.filter((n) => !n.folderId).length;

    el.folderList.innerHTML = '';

    const specialRows = [
      { id: 'ALL', icon: svgMini('layers'), rawIcon: true, name: 'All Boards & Notes', count: activeNotes.length, color: '#a855f7', fixed: true },
      { id: 'FOLLOWUPS', icon: svgMini('bell'), rawIcon: true, name: 'Follow-Ups Due', count: followUpCount, color: '#f59e0b', fixed: true },
      { id: 'UNFILED', icon: svgMini('inbox'), rawIcon: true, name: 'Unfiled', count: unfiledCount, color: '#64748b', fixed: true }
    ];

    for (const sp of specialRows) {
      const row = buildFolderRow(sp);
      el.folderList.appendChild(row);
    }

    for (const f of (vault.folders || [])) {
      const count = activeNotes.filter((n) => n.folderId === f.id).length;
      const row = buildFolderRow({
        id: f.id,
        icon: f.icon || '📁',
        name: f.name,
        count,
        color: f.colorHex || '#6366f1',
        fixed: false
      });
      el.folderList.appendChild(row);
    }

    // Update Notes View folder dropdown too
    if (el.notesFolderSelect) {
      el.notesFolderSelect.innerHTML = '<option value="">Unfiled</option>';
      for (const f of (vault.folders || [])) {
        const opt = document.createElement('option');
        opt.value = f.id;
        opt.textContent = `${f.icon || ''} ${f.name}`.trim();
        el.notesFolderSelect.appendChild(opt);
      }
      const active = getActiveNote();
      el.notesFolderSelect.value = (active && active.folderId) || '';
    }
  }

  function buildFolderRow(meta) {
    const row = document.createElement('div');
    row.className = `folder-row${activeFolderFilter === meta.id ? ' active' : ''}`;
    row.dataset.folderId = meta.id;

    row.innerHTML = `
      <span class="folder-dot" style="background:${meta.color}"></span>
      <span class="folder-icon-slot">${meta.rawIcon ? meta.icon : escapeHtml(meta.icon)}</span>
      <span class="folder-name">${escapeHtml(meta.name)}</span>
      <span class="folder-count">${meta.count}</span>
      ${meta.fixed ? '' : `<button class="folder-edit-btn" title="Customize Folder">${svgMini('edit')}</button>`}
    `;

    row.addEventListener('click', (e) => {
      if (e.target.closest('.folder-edit-btn')) {
        openFolderModal(meta.id);
        return;
      }
      activeFolderFilter = meta.id;
      renderFolders();
      renderNotesList();
    });

    // Drag & Drop support: drop notes onto any real folder or Unfiled
    if (meta.id !== 'ALL' && meta.id !== 'FOLLOWUPS') {
      row.addEventListener('dragover', (e) => {
        e.preventDefault();
        row.classList.add('drag-over');
      });
      row.addEventListener('dragleave', () => {
        row.classList.remove('drag-over');
      });
      row.addEventListener('drop', (e) => {
        e.preventDefault();
        row.classList.remove('drag-over');
        const draggedNoteId = e.dataTransfer.getData('text/note-id') || e.dataTransfer.getData('text/plain');
        if (!draggedNoteId) return;
        const target = vault.notes.find((n) => n.id === draggedNoteId);
        if (target) {
          target.folderId = meta.id === 'UNFILED' ? null : meta.id;
          target.updatedAt = Date.now();
          scheduleSave();
          renderFolders();
          renderNotesList();
          renderNoteView();
          showToast(`Moved "${target.title || 'Untitled'}" to ${meta.name}`);
        }
      });
    }

    return row;
  }

  function renderNotesList() {
    let notes = VaultBridge.searchNotes(vault, searchQuery);

    if (activeFolderFilter === 'FOLLOWUPS') {
      notes = notes.filter((n) => Boolean(n.followUpAt));
      el.notesListHeading.textContent = 'FOLLOW-UP REMINDERS';
    } else if (activeFolderFilter === 'UNFILED') {
      notes = notes.filter((n) => !n.folderId);
      el.notesListHeading.textContent = 'UNFILED';
    } else if (activeFolderFilter !== 'ALL') {
      notes = notes.filter((n) => n.folderId === activeFolderFilter);
      const folder = (vault.folders || []).find((f) => f.id === activeFolderFilter);
      el.notesListHeading.textContent = folder ? folder.name.toUpperCase() : 'FOLDER';
    } else {
      el.notesListHeading.textContent = searchQuery.trim() ? 'SEARCH RESULTS' : 'ALL BOARDS & NOTES';
    }

    notes.sort((a, b) => {
      if (Boolean(a.isPinned) !== Boolean(b.isPinned)) return a.isPinned ? -1 : 1;
      return (b.updatedAt || 0) - (a.updatedAt || 0);
    });

    el.notesCountBadge.textContent = String(notes.length);
    el.notesList.innerHTML = '';

    const active = getActiveNote();
    const today = new Date().toISOString().slice(0, 10);

    for (const note of notes) {
      const item = document.createElement('div');
      item.className = `note-list-item${active && active.id === note.id ? ' active' : ''}`;
      item.draggable = true;

      const folderObj = (vault.folders || []).find((f) => f.id === note.folderId);
      const chk = getChecklistStats(note);
      const slideCount = (note.board && note.board.pages && note.board.pages.length) || 1;
      const isDue = note.followUpAt && note.followUpAt <= today;

      const badges = [];
      if (folderObj) {
        badges.push(`<span class="badge-chip">${folderObj.icon || '📁'} ${escapeHtml(folderObj.name)}</span>`);
      }
      if (slideCount > 1) {
        badges.push(`<span class="badge-chip">${slideCount} slides</span>`);
      }
      if (chk.total > 0) {
        badges.push(`<span class="badge-chip">✓ ${chk.done}/${chk.total}</span>`);
      }
      if (note.followUpAt) {
        badges.push(`<span class="badge-chip${isDue ? ' due' : ''}">${svgMini('bell')} ${formatDateChip(note.followUpAt)}</span>`);
      }

      item.innerHTML = `
        <div class="note-item-top">
          ${note.isPinned ? `<span class="pin-glyph">${svgMini('pin')}</span>` : ''}
          <span class="note-item-title">${escapeHtml(note.title || 'Untitled Board')}</span>
          <button class="note-item-del" title="Move to Trash">✕</button>
        </div>
        <div class="note-item-meta">${badges.join('')}</div>
      `;

      item.addEventListener('dragstart', (e) => {
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/note-id', note.id);
        e.dataTransfer.setData('text/plain', note.id);
      });

      item.addEventListener('click', (e) => {
        if (e.target.closest('.note-item-del')) {
          deleteNoteRecord(note.id);
          return;
        }
        selectNote(note.id);
      });

      el.notesList.appendChild(item);
    }
  }

  function selectNote(noteId) {
    vault.activeNoteId = noteId;
    pendingConnectSourceId = null;
    updateConnectHint();
    scheduleSave();
    renderAllActiveViews();
  }

  function createNewBoard(customTitle = 'Untitled Class Board', folderOverride = undefined) {
    const folderId = folderOverride !== undefined
      ? folderOverride
      : (!['ALL', 'FOLLOWUPS', 'UNFILED'].includes(activeFolderFilter) ? activeFolderFilter : null);

    const newNote = VaultBridge.createNoteRecord({
      title: customTitle,
      folderId,
      board: {
        gridStyle: 'DOTS',
        activePageIndex: 0,
        pages: [VaultBridge.createEmptyBoardPage('Slide 1')]
      }
    });

    vault.notes.unshift(newNote);
    vault.activeNoteId = newNote.id;
    scheduleSave(true);
    renderFolders();
    renderNotesList();
    renderAllActiveViews();
    el.activeTitleInput.focus();
    el.activeTitleInput.select();
  }

  function openOrCreateTodayJournal() {
    const todayStr = new Date().toISOString().slice(0, 10);
    let existing = vault.notes.find((n) => !n.isTrash && n.title === todayStr);
    if (!existing) {
      existing = VaultBridge.createNoteRecord({
        title: todayStr,
        content: '#daily\n\nNotes & class highlights for today:',
        folderId: (vault.folders || []).some((f) => f.id === 'folder_reminder') ? 'folder_reminder' : null,
        followUpAt: todayStr
      });
      vault.notes.unshift(existing);
      scheduleSave(true);
      renderFolders();
    }
    selectNote(existing.id);
  }

  function deleteNoteRecord(noteId) {
    const activeNotes = vault.notes.filter((n) => !n.isTrash);
    const target = vault.notes.find((n) => n.id === noteId);
    if (!target) return;
    const now = Date.now();
    target.isTrash = true;
    target.trashTimestamp = now;
    target.updatedAt = now;
    if (typeof VaultBridge.recordTombstone === 'function') {
      VaultBridge.recordTombstone(vault, noteId, 'note', now);
    }

    if (activeNotes.length <= 1) {
      const fresh = VaultBridge.createNoteRecord({ title: 'New Board' });
      vault.notes.unshift(fresh);
      vault.activeNoteId = fresh.id;
    } else if (vault.activeNoteId === noteId) {
      const next = vault.notes.find((n) => !n.isTrash);
      vault.activeNoteId = next ? next.id : null;
    }

    scheduleSave(true);
    renderFolders();
    renderNotesList();
    renderAllActiveViews();
    showToast(`Deleted "${target.title || 'Board'}"`);
  }

  // ==================== HEADER & METADATA ====================

  function syncTopBar() {
    const note = getActiveNote();
    if (!note) return;
    el.activeTitleInput.value = note.title || '';
    el.pinNoteBtn.classList.toggle('active', Boolean(note.isPinned));
    el.followUpBtn.classList.toggle('active', Boolean(note.followUpAt));
    el.followUpLabel.textContent = formatDateChip(note.followUpAt);
    el.followUpDateInput.value = note.followUpAt || '';
  }

  // ==================== VIEW 1: BOARD STAGE & SPATIAL CARDS ====================

  function renderActiveBoard() {
    const note = getActiveNote();
    const page = getActivePage(note);
    if (!note || !page || !engine) return;

    const gridStyle = (note.board && note.board.gridStyle) || 'DOTS';
    el.gridCycleBtn.textContent = GRID_LABELS[gridStyle] || GRID_LABELS.DOTS;
    engine.setPage(page, vault.activeTheme, gridStyle, getActiveGradientPreset(), vault.gradientsEnabled !== false);
    updateZoomLabel();
    renderSlidesBar(note, page);
    renderSpatialCards(note, page);
  }

  function updateZoomLabel() {
    const page = getActivePage();
    if (!page || !page.viewport) return;
    el.zoomResetBtn.textContent = `${Math.round((page.viewport.zoom || 1) * 100)}%`;
  }

  function renderSlidesBar(note, activePage) {
    const pages = (note.board && note.board.pages) || [];
    el.slideTabsContainer.innerHTML = '';
    pages.forEach((p, idx) => {
      const tab = document.createElement('div');
      tab.className = `slide-tab${p.id === activePage.id ? ' active' : ''}`;
      tab.title = 'Click to switch slide · Double-click to rename';

      const span = document.createElement('span');
      span.textContent = p.name || `Slide ${idx + 1}`;
      tab.appendChild(span);

      if (pages.length > 1) {
        const del = document.createElement('button');
        del.className = 'slide-tab-del';
        del.textContent = '✕';
        del.title = 'Delete Slide';
        del.addEventListener('click', (e) => {
          e.stopPropagation();
          note.board.pages.splice(idx, 1);
          note.board.activePageIndex = Math.max(0, Math.min(idx, note.board.pages.length - 1));
          note.updatedAt = Date.now();
          scheduleSave();
          renderActiveBoard();
          renderNotesList();
        });
        tab.appendChild(del);
      }

      tab.addEventListener('click', () => {
        note.board.activePageIndex = idx;
        renderActiveBoard();
      });

      tab.addEventListener('dblclick', () => {
        const input = document.createElement('input');
        input.type = 'text';
        input.value = p.name || `Slide ${idx + 1}`;
        input.style.cssText = 'width:110px;background:var(--bg-root);border:1px solid var(--accent);border-radius:4px;padding:1px 4px;font-size:12px;';
        span.replaceWith(input);
        input.focus();
        input.select();
        const commit = () => {
          p.name = input.value.trim() || `Slide ${idx + 1}`;
          note.updatedAt = Date.now();
          scheduleSave();
          renderActiveBoard();
        };
        input.addEventListener('blur', commit);
        input.addEventListener('keydown', (ev) => {
          if (ev.key === 'Enter') input.blur();
        });
      });

      el.slideTabsContainer.appendChild(tab);
    });
  }

  function renderSpatialCards(note, page) {
    el.cardsLayer.innerHTML = '';
    for (const card of (page.cards || [])) {
      const cardEl = buildSpatialCardElement(note, page, card);
      el.cardsLayer.appendChild(cardEl);
    }
  }

  function buildSpatialCardElement(note, page, card) {
    const cardEl = document.createElement('div');
    cardEl.className = `spatial-card${pendingConnectSourceId === card.id ? ' connect-source' : ''}`;
    cardEl.style.left = `${card.x}px`;
    cardEl.style.top = `${card.y}px`;
    cardEl.style.width = `${card.w}px`;
    cardEl.style.height = `${card.h}px`;
    cardEl.style.borderColor = card.colorHex || 'var(--border-subtle)';
    cardEl.dataset.cardId = card.id;

    // Header
    const head = document.createElement('div');
    head.className = 'card-header';
    head.style.background = hexToRgbaCss(card.colorHex || '#A855F7', 0.11);

    const iconSpan = document.createElement('span');
    iconSpan.className = 'card-kind-icon';
    iconSpan.style.color = card.colorHex || 'var(--accent)';
    iconSpan.innerHTML = card.kind === 'checklist'
      ? svgMini('checklist')
      : card.kind === 'pdf'
        ? svgMini('pdf')
        : card.kind === 'image'
          ? svgMini('image')
          : svgMini('note');

    const titleIn = document.createElement('input');
    titleIn.className = 'card-title-input';
    titleIn.value = card.title || 'Card';
    titleIn.addEventListener('pointerdown', (e) => e.stopPropagation());
    titleIn.addEventListener('input', () => {
      card.title = titleIn.value;
      note.updatedAt = Date.now();
      scheduleSave();
    });

    const lockBtn = document.createElement('button');
    lockBtn.className = 'card-action-btn';
    lockBtn.innerHTML = card.locked ? svgMini('lock') : svgMini('unlock');
    lockBtn.title = card.locked ? 'Unlock card drag/resize' : 'Lock card in place while teaching';
    lockBtn.addEventListener('pointerdown', (e) => e.stopPropagation());
    lockBtn.addEventListener('click', () => {
      card.locked = !card.locked;
      lockBtn.innerHTML = card.locked ? svgMini('lock') : svgMini('unlock');
      scheduleSave();
    });

    const delBtn = document.createElement('button');
    delBtn.className = 'card-action-btn';
    delBtn.textContent = '✕';
    delBtn.title = 'Remove Card';
    delBtn.addEventListener('pointerdown', (e) => e.stopPropagation());
    delBtn.addEventListener('click', () => {
      page.cards = page.cards.filter((c) => c.id !== card.id);
      page.connectors = (page.connectors || []).filter((cn) => cn.fromCardId !== card.id && cn.toCardId !== card.id);
      note.updatedAt = Date.now();
      scheduleSave();
      cardEl.remove();
      engine.redrawStatic();
      renderNotesList();
      renderNoteViewChecklists();
    });

    head.append(iconSpan, titleIn, lockBtn, delBtn);
    cardEl.appendChild(head);

    // Drag header to reposition card smoothly on world coordinates
    head.addEventListener('pointerdown', (e) => {
      if (e.button !== 0 || card.locked) return;
      if (engine.tool === 'connect') return;
      e.preventDefault();
      e.stopPropagation();
      const startX = e.clientX;
      const startY = e.clientY;
      const origX = card.x;
      const origY = card.y;
      const zoom = (page.viewport && page.viewport.zoom) || 1;

      const onMove = (ev) => {
        card.x = Math.round(origX + (ev.clientX - startX) / zoom);
        card.y = Math.round(origY + (ev.clientY - startY) / zoom);
        cardEl.style.left = `${card.x}px`;
        cardEl.style.top = `${card.y}px`;
        engine.redrawStatic();
      };
      const onUp = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', onUp);
        note.updatedAt = Date.now();
        scheduleSave();
      };
      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
    });

    // Click handler when in Connect mode
    cardEl.addEventListener('click', (e) => {
      if (engine.tool !== 'connect') return;
      e.stopPropagation();
      if (!pendingConnectSourceId) {
        pendingConnectSourceId = card.id;
        updateConnectHint(card.title || 'Card');
        renderSpatialCards(note, page);
      } else if (pendingConnectSourceId === card.id) {
        pendingConnectSourceId = null;
        updateConnectHint();
        renderSpatialCards(note, page);
      } else {
        pendingConnectTargetId = card.id;
        openConnectLabelModal();
      }
    });

    // Body
    const body = document.createElement('div');
    body.className = `card-body${(card.kind === 'image' || card.kind === 'pdf') ? ' card-image-wrap' : ''}`;

    if (card.kind === 'image' || card.kind === 'pdf') {
      const img = document.createElement('img');
      img.src = card.imageDataUrl || '';
      img.draggable = false;
      body.appendChild(img);
    } else if (card.kind === 'checklist') {
      renderChecklistDomInto(body, note, card, () => {
        renderNotesList();
        renderNoteViewChecklists();
      });
    } else {
      const ta = document.createElement('textarea');
      ta.className = 'card-body-note';
      ta.placeholder = 'Write formulas, explanations, or links...';
      ta.value = card.content || '';
      ta.addEventListener('input', () => {
        card.content = ta.value;
        note.updatedAt = Date.now();
        scheduleSave();
      });
      body.appendChild(ta);
    }
    cardEl.appendChild(body);

    // Resize handle
    const resizer = document.createElement('div');
    resizer.className = 'card-resize-handle';
    resizer.addEventListener('pointerdown', (e) => {
      if (e.button !== 0 || card.locked) return;
      e.preventDefault();
      e.stopPropagation();
      const startX = e.clientX;
      const startY = e.clientY;
      const origW = card.w;
      const origH = card.h;
      const zoom = (page.viewport && page.viewport.zoom) || 1;

      const onMove = (ev) => {
        card.w = Math.max(200, Math.round(origW + (ev.clientX - startX) / zoom));
        card.h = Math.max(130, Math.round(origH + (ev.clientY - startY) / zoom));
        cardEl.style.width = `${card.w}px`;
        cardEl.style.height = `${card.h}px`;
        engine.redrawStatic();
      };
      const onUp = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', onUp);
        note.updatedAt = Date.now();
        scheduleSave();
      };
      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
    });
    cardEl.appendChild(resizer);

    return cardEl;
  }

  function renderChecklistDomInto(container, note, card, onAfterChange) {
    if (!Array.isArray(card.items)) card.items = [];
    container.innerHTML = '';

    const itemsWrap = document.createElement('div');
    itemsWrap.className = 'checklist-items';

    card.items.forEach((item, idx) => {
      const row = document.createElement('div');
      row.className = `checklist-row${item.isChecked ? ' checked' : ''}`;

      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.className = 'checklist-box';
      cb.checked = Boolean(item.isChecked);
      cb.addEventListener('change', () => {
        item.isChecked = cb.checked;
        row.classList.toggle('checked', item.isChecked);
        note.updatedAt = Date.now();
        scheduleSave();
        if (onAfterChange) onAfterChange();
      });

      const txt = document.createElement('input');
      txt.type = 'text';
      txt.className = 'checklist-text-input';
      txt.value = item.text || '';
      txt.placeholder = 'Task...';
      txt.addEventListener('input', () => {
        item.text = txt.value;
        note.updatedAt = Date.now();
        scheduleSave();
      });

      const del = document.createElement('button');
      del.className = 'checklist-del-btn';
      del.textContent = '✕';
      del.title = 'Delete Task';
      del.addEventListener('click', () => {
        card.items.splice(idx, 1);
        note.updatedAt = Date.now();
        scheduleSave();
        renderChecklistDomInto(container, note, card, onAfterChange);
        if (onAfterChange) onAfterChange();
      });

      row.append(cb, txt, del);
      itemsWrap.appendChild(row);
    });

    const addRow = document.createElement('div');
    addRow.className = 'checklist-add-row';
    const addInput = document.createElement('input');
    addInput.type = 'text';
    addInput.className = 'checklist-add-input';
    addInput.placeholder = '+ Add to-do item (Enter)...';

    const addBtn = document.createElement('button');
    addBtn.className = 'dock-btn';
    addBtn.textContent = '+';

    const commitNewTask = () => {
      const val = addInput.value.trim();
      if (!val) return;
      card.items.push({ id: VaultBridge.makeId('chk'), text: val, isChecked: false });
      note.updatedAt = Date.now();
      scheduleSave();
      renderChecklistDomInto(container, note, card, onAfterChange);
      if (onAfterChange) onAfterChange();
    };

    addInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        commitNewTask();
      }
    });
    addBtn.addEventListener('click', commitNewTask);

    addRow.append(addInput, addBtn);
    container.append(itemsWrap, addRow);
  }

  function addSpatialCardAtViewportCenter(cardProps) {
    const note = getActiveNote();
    const page = getActivePage(note);
    if (!note || !page || !engine) return;

    const w = cardProps.w || 340;
    const h = cardProps.h || 220;
    const centerWorld = engine.screenToWorld(engine.width * 0.5, engine.height * 0.5);
    const jitter = (page.cards.length % 5) * 18;

    const newCard = {
      id: VaultBridge.makeId('card'),
      kind: cardProps.kind || 'note',
      x: Math.round(centerWorld.x - w * 0.5 + jitter),
      y: Math.round(centerWorld.y - h * 0.5 + jitter),
      w,
      h,
      title: cardProps.title || (cardProps.kind === 'checklist' ? 'Checklist' : 'Sticky Note'),
      content: cardProps.content || '',
      items: cardProps.items || (cardProps.kind === 'checklist' ? [{ id: VaultBridge.makeId('chk'), text: 'First item', isChecked: false }] : undefined),
      imageDataUrl: cardProps.imageDataUrl,
      colorHex: cardProps.colorHex || '#A855F7',
      locked: false
    };

    page.cards.push(newCard);
    note.updatedAt = Date.now();
    scheduleSave();
    if (!cardProps.keepTool) {
      setActiveTool('select');
    }
    renderSpatialCards(note, page);
    renderNotesList();
    renderNoteViewChecklists();
    return newCard;
  }

  function setActiveTool(toolName) {
    if (!engine) return;
    if (toolName !== 'connect') {
      pendingConnectSourceId = null;
      updateConnectHint();
    } else {
      updateConnectHint();
    }
    engine.setTool(toolName);
    document.querySelectorAll('#toolButtonsGroup .tool-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.tool === toolName);
    });
  }

  function updateConnectHint(sourceTitle = null) {
    if (engine && engine.tool === 'connect') {
      el.connectHintPill.classList.remove('hidden');
      el.connectHintPill.textContent = sourceTitle
        ? `Linking from "${sourceTitle}" → Click target card (or Esc to cancel)`
        : 'Connect Mode: Click source card, then click destination card';
    } else {
      el.connectHintPill.classList.add('hidden');
    }
  }

  // ==================== VIEW 2: NOTES & CHECKLIST VIEW ====================

  function renderNoteView() {
    const note = getActiveNote();
    if (!note) return;
    el.noteViewTitleInput.value = note.title || '';
    el.noteViewBodyInput.value = note.content || '';
    el.notesFolderSelect.value = note.folderId || '';
    renderWikiLinkChips(note);
    renderNoteViewChecklists();
  }

  function renderWikiLinkChips(note) {
    el.outgoingLinksRow.innerHTML = '';
    el.backlinksRow.innerHTML = '';

    const combined = [note.content || ''];
    for (const p of ((note.board && note.board.pages) || [])) {
      for (const c of (p.cards || [])) combined.push(c.content || '');
    }
    const text = combined.join('\n');
    const matches = new Set();
    const re = /\[\[([^\]]+)\]\]/g;
    let m;
    while ((m = re.exec(text)) !== null) {
      matches.add(m[1].trim());
    }

    if (matches.size === 0) {
      el.outgoingLinksRow.innerHTML = '<span class="palette-row-sub">None yet — click [[ Link Note above</span>';
    } else {
      for (const titleRef of matches) {
        const btn = document.createElement('button');
        btn.className = 'link-chip-btn';
        btn.textContent = `[[${titleRef}]]`;
        btn.addEventListener('click', () => openOrCreateNoteByTitle(titleRef));
        el.outgoingLinksRow.appendChild(btn);
      }
    }

    const myTitle = (note.title || '').trim().toLowerCase();
    const graph = VaultBridge.buildGraphData(vault);
    const connectedIds = new Set();
    for (const edge of graph.edges) {
      if (edge.targetId === note.id) connectedIds.add(edge.sourceId);
      if (edge.sourceId === note.id) connectedIds.add(edge.targetId);
    }

    const backNotes = vault.notes.filter((n) => !n.isTrash && n.id !== note.id && (
      connectedIds.has(n.id) || (myTitle.length >= 3 && (n.content || '').toLowerCase().includes(myTitle))
    ));

    if (backNotes.length === 0) {
      el.backlinksRow.innerHTML = '<span class="palette-row-sub">No backlinks yet</span>';
    } else {
      for (const bn of backNotes) {
        const btn = document.createElement('button');
        btn.className = 'link-chip-btn';
        btn.textContent = `← ${bn.title || 'Untitled'}`;
        btn.addEventListener('click', () => selectNote(bn.id));
        el.backlinksRow.appendChild(btn);
      }
    }
  }

  function renderNoteViewChecklists() {
    const note = getActiveNote();
    el.noteViewChecklists.innerHTML = '';
    if (!note || !note.board) return;

    const checklists = [];
    for (const p of (note.board.pages || [])) {
      for (const c of (p.cards || [])) {
        if (c.kind === 'checklist') checklists.push({ page: p, card: c });
      }
    }

    if (checklists.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'palette-row-sub';
      empty.textContent = 'No checklists on this note yet. Click "+ New Checklist" to add tasks underneath your note.';
      el.noteViewChecklists.appendChild(empty);
      return;
    }

    for (const { page, card } of checklists) {
      const box = document.createElement('div');
      box.className = 'inline-checklist-card';

      const head = document.createElement('div');
      head.className = 'inline-checklist-head';
      const titleInput = document.createElement('input');
      titleInput.value = card.title || 'To-Do Checklist';
      titleInput.addEventListener('input', () => {
        card.title = titleInput.value;
        note.updatedAt = Date.now();
        scheduleSave();
      });

      const delBtn = document.createElement('button');
      delBtn.className = 'card-action-btn';
      delBtn.textContent = '✕';
      delBtn.title = 'Delete Checklist';
      delBtn.addEventListener('click', () => {
        page.cards = page.cards.filter((x) => x.id !== card.id);
        note.updatedAt = Date.now();
        scheduleSave();
        renderNoteViewChecklists();
        renderActiveBoard();
        renderNotesList();
      });

      head.append(titleInput, delBtn);
      const body = document.createElement('div');
      renderChecklistDomInto(body, note, card, () => {
        renderNotesList();
        renderActiveBoard();
      });

      box.append(head, body);
      el.noteViewChecklists.appendChild(box);
    }
  }

  function openOrCreateNoteByTitle(titleRef) {
    const clean = titleRef.trim();
    const existing = vault.notes.find((n) => !n.isTrash && (n.title || '').trim().toLowerCase() === clean.toLowerCase());
    if (existing) {
      selectNote(existing.id);
      return;
    }
    createNewBoard(clean);
  }

  // ==================== VIEW 3: 2D KNOWLEDGE GRAPH ====================

  const graphState = {
    nodes: [],
    edges: [],
    panX: 0,
    panY: 0,
    zoom: 1,
    dragNode: null,
    isPanning: false,
    panStart: { x: 0, y: 0 },
    hoverId: null,
    rafId: null,
    settleFrames: 0,
    ticks: 0
  };

  function rebuildGraphSimulation() {
    const data = VaultBridge.buildGraphData(vault);
    const rect = el.graphCanvas.getBoundingClientRect();
    const cx = Math.max(300, rect.width) * 0.5;
    const cy = Math.max(240, rect.height) * 0.5;

    const oldMap = new Map(graphState.nodes.map((n) => [n.id, n]));
    graphState.nodes = data.nodes.map((n, idx) => {
      const prev = oldMap.get(n.id);
      const angle = (idx / Math.max(1, data.nodes.length)) * Math.PI * 2;
      const rad = 120 + (idx % 3) * 55;
      return {
        ...n,
        x: prev ? prev.x : cx + Math.cos(angle) * rad,
        y: prev ? prev.y : cy + Math.sin(angle) * rad,
        vx: 0,
        vy: 0
      };
    });
    graphState.edges = data.edges;
  }

  function renderGraphIfVisible() {
    if (activeView !== 'graph') return;
    rebuildGraphSimulation();
    startGraphLoop();
  }

  function startGraphLoop() {
    if (graphState.rafId) cancelAnimationFrame(graphState.rafId);
    graphState.settleFrames = 0;
    graphState.ticks = 0;

    const step = () => {
      if (activeView !== 'graph') {
        graphState.rafId = null;
        return;
      }
      const motion = stepGraphPhysics();
      graphState.ticks += 1;
      drawGraphCanvas();

      const interacting = Boolean(graphState.dragNode || graphState.isPanning);
      if (!interacting && (motion < 0.12 || (graphState.ticks > 300 && motion < 0.35))) {
        graphState.settleFrames += 1;
      } else {
        graphState.settleFrames = 0;
      }
      if (graphState.settleFrames >= 12) {
        graphState.rafId = null;
        return;
      }
      graphState.rafId = requestAnimationFrame(step);
    };
    graphState.rafId = requestAnimationFrame(step);
  }

  function stepGraphPhysics() {
    const nodes = graphState.nodes;
    if (!nodes.length) return 0;
    const rect = el.graphCanvas.getBoundingClientRect();
    const cx = (rect.width || 800) * 0.5;
    const cy = (rect.height || 600) * 0.5;
    const nodeById = new Map(nodes.map((n) => [n.id, n]));

    // Repulsion
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        let dx = b.x - a.x;
        let dy = b.y - a.y;
        let distSq = dx * dx + dy * dy + 1;
        if (distSq > 90000) continue;
        const f = 1400 / distSq;
        const d = Math.sqrt(distSq);
        dx /= d;
        dy /= d;
        a.vx -= dx * f;
        a.vy -= dy * f;
        b.vx += dx * f;
        b.vy += dy * f;
      }
    }

    // Spring edges
    for (const e of graphState.edges) {
      const a = nodeById.get(e.sourceId);
      const b = nodeById.get(e.targetId);
      if (!a || !b) continue;
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      const f = (dist - 150) * 0.015;
      a.vx += (dx / dist) * f;
      a.vy += (dy / dist) * f;
      b.vx -= (dx / dist) * f;
      b.vy -= (dy / dist) * f;
    }

    // Gravity toward center + damping
    let totalMotion = 0;
    for (const n of nodes) {
      if (graphState.dragNode && graphState.dragNode.id === n.id) continue;
      n.vx += (cx - n.x) * 0.0028;
      n.vy += (cy - n.y) * 0.0028;
      n.vx *= 0.85;
      n.vy *= 0.85;
      n.x += n.vx;
      n.y += n.vy;
      totalMotion += Math.abs(n.vx) + Math.abs(n.vy);
    }
    return totalMotion;
  }

  function drawGraphCanvas() {
    const cv = el.graphCanvas;
    const rect = cv.getBoundingClientRect();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(200, Math.floor(rect.width));
    const h = Math.max(200, Math.floor(rect.height));
    if (cv.width !== Math.floor(w * dpr) || cv.height !== Math.floor(h * dpr)) {
      cv.width = Math.floor(w * dpr);
      cv.height = Math.floor(h * dpr);
    }

    const ctx = cv.getContext('2d');
    const pal = engine ? engine.getPaletteColors() : { bg: '#000', accent: '#a855f7', accentEnd: '#6366f1', text: '#fff', line: '#222' };
    ctx.save();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (vault && vault.gradientsEnabled !== false && pal.bgTop && pal.bgBottom) {
      const bgGrad = ctx.createLinearGradient(0, 0, 0, h);
      bgGrad.addColorStop(0, pal.bgTop);
      if (pal.bgMid) bgGrad.addColorStop(0.52, pal.bgMid);
      bgGrad.addColorStop(1, pal.bgBottom);
      ctx.fillStyle = bgGrad;
    } else {
      ctx.fillStyle = pal.bg;
    }
    ctx.fillRect(0, 0, w, h);

    ctx.translate(graphState.panX, graphState.panY);
    ctx.scale(graphState.zoom, graphState.zoom);

    const nodeMap = new Map(graphState.nodes.map((n) => [n.id, n]));
    const filterTxt = (el.graphFilterInput.value || '').trim().toLowerCase();

    // Edges: batch large graphs in a single draw call to eliminate gradient churn
    ctx.lineWidth = 1.6;
    const usePerEdgeGradient = vault && vault.gradientsEnabled !== false && graphState.edges.length <= 80;
    if (usePerEdgeGradient) {
      for (const e of graphState.edges) {
        const a = nodeMap.get(e.sourceId);
        const b = nodeMap.get(e.targetId);
        if (!a || !b) continue;
        const eg = ctx.createLinearGradient(a.x, a.y, b.x, b.y);
        eg.addColorStop(0, hexToRgbaCss(pal.accent, 0.42));
        eg.addColorStop(1, hexToRgbaCss(pal.accentEnd || pal.accent, 0.42));
        ctx.strokeStyle = eg;
        ctx.beginPath();
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
        ctx.stroke();
      }
    } else {
      ctx.strokeStyle = hexToRgbaCss(pal.accent, 0.36);
      ctx.beginPath();
      for (const e of graphState.edges) {
        const a = nodeMap.get(e.sourceId);
        const b = nodeMap.get(e.targetId);
        if (!a || !b) continue;
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
      }
      ctx.stroke();
    }

    // Nodes
    const active = getActiveNote();
    const folderColorMap = new Map((vault.folders || []).map((f) => [f.id, f.colorHex]));

    for (const n of graphState.nodes) {
      const isMatch = filterTxt && (n.title || '').toLowerCase().includes(filterTxt);
      const isActive = active && active.id === n.id;
      const isHover = graphState.hoverId === n.id;
      const r = 9 + Math.min(10, n.degree * 2.5);
      const color = folderColorMap.get(n.folderId) || pal.accent;

      ctx.save();
      if (isActive || isMatch || isHover) {
        ctx.shadowColor = color;
        ctx.shadowBlur = 16;
      }
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = isActive || isHover ? '#ffffff' : 'rgba(255,255,255,0.28)';
      ctx.lineWidth = isActive ? 2.5 : 1.2;
      ctx.stroke();
      ctx.restore();

      ctx.fillStyle = pal.text;
      ctx.font = `${isActive || isMatch ? '700' : '500'} 12px system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.fillText((n.title || 'Untitled').slice(0, 28), n.x, n.y + r + 15);
    }

    ctx.restore();
  }

  function initGraphInteractions() {
    const cv = el.graphCanvas;
    const toWorld = (ev) => {
      const r = cv.getBoundingClientRect();
      return {
        x: (ev.clientX - r.left - graphState.panX) / graphState.zoom,
        y: (ev.clientY - r.top - graphState.panY) / graphState.zoom
      };
    };

    const hitNode = (wx, wy) => {
      for (const n of graphState.nodes) {
        const r = 14 + Math.min(10, n.degree * 2.5);
        if (Math.hypot(n.x - wx, n.y - wy) <= r) return n;
      }
      return null;
    };

    let downPos = null;
    cv.addEventListener('pointerdown', (e) => {
      const w = toWorld(e);
      downPos = { x: e.clientX, y: e.clientY };
      const hit = hitNode(w.x, w.y);
      if (hit) {
        graphState.dragNode = hit;
      } else {
        graphState.isPanning = true;
        graphState.panStart = { x: e.clientX - graphState.panX, y: e.clientY - graphState.panY };
      }
      if (!graphState.rafId) startGraphLoop();
    });

    window.addEventListener('pointermove', (e) => {
      if (activeView !== 'graph') return;
      const w = toWorld(e);
      if (graphState.dragNode) {
        graphState.dragNode.x = w.x;
        graphState.dragNode.y = w.y;
        if (!graphState.rafId) startGraphLoop();
      } else if (graphState.isPanning) {
        graphState.panX = e.clientX - graphState.panStart.x;
        graphState.panY = e.clientY - graphState.panStart.y;
        if (!graphState.rafId) drawGraphCanvas();
      } else {
        const hit = hitNode(w.x, w.y);
        const nextHover = hit ? hit.id : null;
        if (nextHover !== graphState.hoverId) {
          graphState.hoverId = nextHover;
          if (!graphState.rafId) drawGraphCanvas();
        }
        cv.style.cursor = hit ? 'pointer' : 'grab';
      }
    });

    window.addEventListener('pointerup', (e) => {
      if (graphState.dragNode && downPos && Math.hypot(e.clientX - downPos.x, e.clientY - downPos.y) < 5) {
        const targetId = graphState.dragNode.id;
        graphState.dragNode = null;
        selectNote(targetId);
        switchWorkspaceView('board');
        return;
      }
      const wasDraggingNode = Boolean(graphState.dragNode);
      graphState.dragNode = null;
      graphState.isPanning = false;
      if (wasDraggingNode && !graphState.rafId) startGraphLoop();
    });

    cv.addEventListener('wheel', (e) => {
      e.preventDefault();
      const factor = e.deltaY < 0 ? 1.1 : 0.91;
      graphState.zoom = Math.max(0.25, Math.min(3.0, graphState.zoom * factor));
      if (!graphState.rafId) drawGraphCanvas();
    }, { passive: false });
  }

  // ==================== PDF OFFLINE ENGINE ====================

  async function ensurePdfJs() {
    if (!pdfjsModule) {
      pdfjsModule = await import('./vendor/pdf.min.mjs');
      pdfjsModule.GlobalWorkerOptions.workerSrc = new URL('./vendor/pdf.worker.min.mjs', import.meta.url).toString();
    }
    return pdfjsModule;
  }

  async function handlePdfPick() {
    closeDropdowns();
    try {
      const picked = await desktop.pickPdf();
      if (!picked) return;

      if (pendingPdfDoc && typeof pendingPdfDoc.destroy === 'function') {
        await pendingPdfDoc.destroy().catch(() => {});
        pendingPdfDoc = null;
      }

      const binStr = atob(picked.bytesBase64);
      const uint8 = new Uint8Array(binStr.length);
      for (let i = 0; i < binStr.length; i++) uint8[i] = binStr.charCodeAt(i);

      const pdfjs = await ensurePdfJs();
      pendingPdfDoc = await pdfjs.getDocument({ data: uint8 }).promise;
      pendingPdfName = picked.fileName.replace(/\.pdf$/i, '');

      const maxPages = Math.min(pendingPdfDoc.numPages, 35);
      el.pdfImportSummary.textContent = pendingPdfDoc.numPages > 35
        ? `"${picked.fileName}" has ${pendingPdfDoc.numPages} pages (first 35 pages will be imported for crisp instant performance). Choose how to place them:`
        : `"${picked.fileName}" (${maxPages} page${maxPages > 1 ? 's' : ''}) ready! Choose how to import onto your board:`;

      el.pdfProgressWrap.classList.add('hidden');
      el.pdfImportModal.classList.remove('hidden');
    } catch (err) {
      console.error('PDF load failed:', err);
      showToast(`Failed to open PDF: ${err.message || err}`);
    }
  }

  async function runPdfRenderImport(mode) {
    if (!pendingPdfDoc) return;
    const note = getActiveNote();
    if (!note) return;

    const total = Math.min(pendingPdfDoc.numPages, 35);
    el.pdfProgressWrap.classList.remove('hidden');

    const renderedPages = [];
    const scratchCanvas = document.createElement('canvas');
    const scratchCtx = scratchCanvas.getContext('2d');
    const activeDoc = pendingPdfDoc;
    pendingPdfDoc = null;

    try {
      for (let i = 1; i <= total; i++) {
        el.pdfProgressFill.style.width = `${Math.round((i / total) * 100)}%`;
        el.pdfProgressText.textContent = `Rendering page ${i} of ${total} for offline ink markup...`;
        const page = await activeDoc.getPage(i);
        try {
          const viewport = page.getViewport({ scale: 1.65 });
          scratchCanvas.width = Math.round(viewport.width);
          scratchCanvas.height = Math.round(viewport.height);
          await page.render({ canvasContext: scratchCtx, viewport }).promise;
          renderedPages.push({
            pageNum: i,
            w: Math.round(viewport.width * 0.65),
            h: Math.round(viewport.height * 0.65),
            dataUrl: scratchCanvas.toDataURL('image/jpeg', 0.88)
          });
        } finally {
          if (typeof page.cleanup === 'function') page.cleanup();
        }
      }
    } finally {
      scratchCanvas.width = 0;
      scratchCanvas.height = 0;
      if (typeof activeDoc.destroy === 'function') {
        await activeDoc.destroy().catch(() => {});
      }
    }

    if (mode === 'slides') {
      const startEmpty = note.board.pages.length === 1 &&
        note.board.pages[0].strokes.length === 0 &&
        note.board.pages[0].cards.length === 0;
      const startIndex = startEmpty ? 0 : note.board.pages.length;
      if (startEmpty) note.board.pages = [];

      for (const rp of renderedPages) {
        const boardPage = VaultBridge.createEmptyBoardPage(`${pendingPdfName} p.${rp.pageNum}`);
        boardPage.cards.push({
          id: VaultBridge.makeId('pdf'),
          kind: 'pdf',
          x: 110,
          y: 70,
          w: rp.w,
          h: rp.h,
          title: `${pendingPdfName} — Page ${rp.pageNum}`,
          imageDataUrl: rp.dataUrl,
          colorHex: '#38BDF8',
          locked: true
        });
        note.board.pages.push(boardPage);
      }
      note.board.activePageIndex = startIndex;
    } else {
      const page = getActivePage(note);
      const vp = engine.screenToWorld(120, 90);
      renderedPages.forEach((rp, idx) => {
        const col = idx % 3;
        const row = Math.floor(idx / 3);
        page.cards.push({
          id: VaultBridge.makeId('pdf'),
          kind: 'pdf',
          x: Math.round(vp.x + col * (rp.w + 40)),
          y: Math.round(vp.y + row * (rp.h + 40)),
          w: rp.w,
          h: rp.h,
          title: `${pendingPdfName} — Page ${rp.pageNum}`,
          imageDataUrl: rp.dataUrl,
          colorHex: '#38BDF8',
          locked: true
        });
      });
    }

    el.pdfImportModal.classList.add('hidden');
    note.updatedAt = Date.now();
    scheduleSave(true);
    switchWorkspaceView('board');
    renderActiveBoard();
    engine.fitToContent();
    setActiveTool('pen');
    showToast(`Imported ${total} PDF page${total > 1 ? 's' : ''} — Pen ready to annotate!`);
  }

  // ==================== EXPORT HANDLERS ====================

  async function exportCurrentSlidePng() {
    closeDropdowns();
    const note = getActiveNote();
    const page = getActivePage(note);
    if (!note || !page || !engine) return;
    const pngBase64 = await engine.renderPageToOffscreenCanvas(page, 1920, 1200, 'image/png', 1.0);
    const saved = await desktop.savePng({
      defaultName: `${note.title || 'board'}-${page.name || 'slide'}`.replace(/[^a-z0-9-_]+/gi, '_'),
      pngBase64
    });
    if (saved) showToast('Exported sharp PNG slide!');
  }

  async function exportAllSlidesPdf() {
    closeDropdowns();
    const note = getActiveNote();
    if (!note || !note.board || !engine) return;
    showToast('Rendering slides into multi-page PDF...');
    const pagesPayload = [];
    for (const p of note.board.pages) {
      const jpegBase64 = await engine.renderPageToOffscreenCanvas(p, 1684, 1190, 'image/jpeg', 0.91);
      pagesPayload.push({
        widthPt: 842,
        heightPt: 595,
        imgWidth: 1684,
        imgHeight: 1190,
        jpegBase64
      });
    }
    const saved = await desktop.savePdf({
      defaultName: (note.title || 'notegraph-board').replace(/[^a-z0-9-_]+/gi, '_'),
      pages: pagesPayload
    });
    if (saved) showToast(`Exported ${pagesPayload.length}-page PDF!`);
  }

  async function exportMarkdown() {
    closeDropdowns();
    const note = getActiveNote();
    if (!note) return;
    const md = VaultBridge.noteToMarkdown(note);
    const saved = await desktop.saveMarkdown({
      defaultName: (note.title || 'note').replace(/[^a-z0-9-_]+/gi, '_'),
      markdownText: md
    });
    if (saved) showToast('Exported Markdown file!');
  }

  function hydrateSyncedImageCards(targetVault, assetImages) {
    if (!targetVault || !Array.isArray(targetVault.notes) || !assetImages) return;
    for (const note of targetVault.notes) {
      if (!Array.isArray(note.extraBlocks) || !note.board || !Array.isArray(note.board.pages)) continue;
      for (const blk of note.extraBlocks) {
        if (!blk || blk.type !== 'image') continue;
        const rawRef = String(blk.uriString || blk.filePath || '');
        const baseName = rawRef.split(/[\\/]/).pop();
        const dataUrl = baseName ? assetImages[baseName] : null;
        if (!dataUrl) continue;
        for (const p of note.board.pages) {
          const card = (p.cards || []).find((c) => c.id === blk.id);
          if (card) {
            card.kind = 'image';
            card.imageDataUrl = dataUrl;
          }
        }
      }
    }
  }

  function applyIncomingSyncedVault(incomingVault, summary = null, showRemoteToast = false) {
    if (!incomingVault || !Array.isArray(incomingVault.notes)) return;
    const prevActive = getActiveNote();
    const focused = document.activeElement;
    const isEditingTitle = focused === el.activeTitleInput || focused === el.noteViewTitleInput;
    const isEditingBody = focused === el.noteViewBodyInput;

    vault = incomingVault;
    ensureVaultGradientState();
    renderFolders();
    renderNotesList();

    const nextActive = getActiveNote();
    if (isEditingTitle && prevActive && nextActive && prevActive.id === nextActive.id && focused.value === (nextActive.title || '')) {
      if (focused !== el.activeTitleInput && el.activeTitleInput) el.activeTitleInput.value = nextActive.title || '';
      if (focused !== el.noteViewTitleInput && el.noteViewTitleInput) el.noteViewTitleInput.value = nextActive.title || '';
    } else if (!isEditingBody || !prevActive || !nextActive || prevActive.id !== nextActive.id || el.noteViewBodyInput.value !== (nextActive.content || '')) {
      syncTopBar();
      renderNoteView();
    } else {
      syncTopBar();
      renderWikiLinkChips(nextActive);
      renderNoteViewChecklists();
    }

    if (activeView === 'board') {
      renderActiveBoard();
    } else if (activeView === 'graph') {
      renderGraphIfVisible();
    }

    if (el.syncLastStatusLabel && summary) {
      el.syncLastStatusLabel.textContent = `Synced with ${summary.remoteDevice || 'Peer'} (${summary.notesMerged || 0} merged, ${summary.newNotesAdded || 0} added, ${summary.linksRebuilt || 0} links)`;
    }

    if (showRemoteToast && summary && (summary.notesMerged > 0 || summary.newNotesAdded > 0 || summary.deletionsApplied > 0)) {
      const now = Date.now();
      if (now - lastSyncToastAt > 1200) {
        lastSyncToastAt = now;
        showToast(`Synced ${summary.notesMerged + summary.newNotesAdded} nodes from ${summary.remoteDevice || 'remote device'}`);
      }
    }
  }

  function refreshSyncModal(cfg) {
    if (!cfg) return;
    syncConfigCache = cfg;
    const osBadge = cfg.platform === 'macos' ? 'macOS' : cfg.platform === 'windows' ? 'Windows' : (cfg.platform || 'Desktop').toUpperCase();
    const lanRunning = Boolean(cfg.lan && cfg.lan.isRunning);
    if (el.syncDeviceSummary) {
      el.syncDeviceSummary.textContent = `${cfg.deviceName || 'Desktop'} (${osBadge}) • ${lanRunning ? 'Wi-Fi Host On' : 'Offline-First'}`;
    }
    if (el.syncLastStatusLabel) {
      el.syncLastStatusLabel.textContent = cfg.lastSyncMessage || 'Ready to sync every node across Windows, macOS & Android';
    }
    if (el.syncAutoFolderToggle) {
      el.syncAutoFolderToggle.classList.toggle('active', Boolean(cfg.autoSyncFolder));
      el.syncAutoFolderToggle.textContent = cfg.autoSyncFolder ? 'Auto-Sync On' : 'Auto-Sync Off';
    }
    if (el.syncFolderPathLabel) {
      el.syncFolderPathLabel.textContent = cfg.sharedFolderPath || 'No shared folder selected';
    }
    if (el.syncFolderNowBtn) {
      el.syncFolderNowBtn.disabled = !cfg.sharedFolderPath;
    }
    if (el.syncCloudSuggestions) {
      el.syncCloudSuggestions.innerHTML = '';
      const suggestions = Array.isArray(cfg.suggestedFolders) ? cfg.suggestedFolders : [];
      if (suggestions.length === 0) {
        el.syncCloudSuggestions.innerHTML = '<span class="palette-row-sub">Tip: Select iCloud Drive, OneDrive, Dropbox, Google Drive, or Syncthing folder below</span>';
      } else {
        for (const s of suggestions) {
          const chip = document.createElement('button');
          chip.type = 'button';
          chip.className = `sync-suggest-chip${cfg.sharedFolderPath === s.path ? ' active' : ''}`;
          chip.textContent = `${s.label}${cfg.sharedFolderPath === s.path ? ' ✓' : ''}`;
          chip.title = s.path;
          chip.addEventListener('click', async () => {
            if (!desktop || typeof desktop.setSyncFolderConfig !== 'function') return;
            try {
              await persistNow();
              const res = await desktop.setSyncFolderConfig({ folderPath: s.path, autoSync: cfg.autoSyncFolder });
              if (res && res.vault) applyIncomingSyncedVault(res.vault, res.summary, false);
              if (res && res.config) refreshSyncModal(res.config);
              showToast(`Connected shared folder: ${s.label}`);
            } catch (err) {
              showToast(`Folder sync failed: ${err.message || err}`);
            }
          });
          el.syncCloudSuggestions.appendChild(chip);
        }
      }
    }
    if (el.syncLanHostToggle && el.syncLanHostInfo) {
      el.syncLanHostToggle.classList.toggle('active', lanRunning);
      el.syncLanHostToggle.textContent = lanRunning ? 'Stop Host Server' : 'Start Host Server';
      el.syncLanHostInfo.classList.toggle('hidden', !lanRunning);
      if (lanRunning && cfg.lan) {
        const addrs = Array.isArray(cfg.lan.addresses) ? cfg.lan.addresses : [];
        const endpointStr = addrs.length
          ? addrs.map((ip) => `${ip}:${cfg.lan.port}`).join('  ·  ')
          : `0.0.0.0:${cfg.lan.port}`;
        if (el.syncLanHostAddrLabel) el.syncLanHostAddrLabel.textContent = `Wi-Fi IP: ${endpointStr}`;
        if (el.syncLanPinBadge) el.syncLanPinBadge.textContent = `PIN: ${cfg.lan.pin || '----'}`;
      }
    }
    if (el.syncLanPeerInput && !el.syncLanPeerInput.value && cfg.lastPeer) {
      el.syncLanPeerInput.value = cfg.lastPeer;
    }
  }

  async function openSyncModal() {
    closeDropdowns();
    if (!el.syncModal) return;
    el.syncModal.classList.remove('hidden');
    if (desktop && typeof desktop.getSyncConfig === 'function') {
      try {
        const cfg = await desktop.getSyncConfig();
        refreshSyncModal(cfg);
      } catch (err) {
        console.warn('Could not load sync config:', err);
      }
    }
  }

  async function exportAndroidZip() {
    closeDropdowns();
    const meta = syncConfigCache || {
      deviceName: 'Desktop',
      platform: isMacOS ? 'macos' : 'windows'
    };
    const pkgV2 = typeof VaultBridge.buildSyncPackage === 'function'
      ? VaultBridge.buildSyncPackage(vault, meta)
      : null;
    const androidNotes = VaultBridge.exportToAndroidNotesArray(vault);
    const res = await desktop.exportVaultZip(pkgV2 ? { pkgV2, androidNotes } : androidNotes);
    if (res) showToast(`Exported ${res.count} synced nodes to NoteGraph .zip!`);
  }

  async function importAndroidZip() {
    closeDropdowns();
    try {
      const res = await desktop.importVaultZip();
      if (!res) return;
      if (res.syncPackage && typeof VaultBridge.mergeSyncPackage === 'function') {
        const { vault: mergedVault, summary } = VaultBridge.mergeSyncPackage(vault, res.syncPackage);
        hydrateSyncedImageCards(mergedVault, res.assetImages);
        applyIncomingSyncedVault(mergedVault, summary, false);
        await persistNow();
        showToast(
          `Synced archive: ${summary.notesMerged} merged, ${summary.newNotesAdded} added, ${summary.linksRebuilt} links`
        );
      } else {
        vault = VaultBridge.importAndroidNotesArray(res.androidNotes, vault);
        scheduleSave(true);
        renderFolders();
        renderNotesList();
        renderAllActiveViews();
        showToast(`Imported ${(res.androidNotes || []).length} notes from Android backup!`);
      }
    } catch (err) {
      showToast(`Import failed: ${err.message || err}`);
    }
  }

  // ==================== MODALS: FOLDER, COMMAND PALETTE, LINK PICKER ====================

  function openFolderModal(folderId = null) {
    editingFolderId = folderId;
    const existing = folderId ? (vault.folders || []).find((f) => f.id === folderId) : null;

    el.folderModalTitle.textContent = existing ? 'Customize Folder' : 'New Folder';
    el.folderNameInput.value = existing ? existing.name : '';
    pickedFolderIcon = existing ? (existing.icon || '📚') : '📚';
    pickedFolderColor = existing ? (existing.colorHex || '#6366F1') : '#6366F1';
    el.deleteFolderBtn.classList.toggle('hidden', !existing);

    syncFolderModalChoices();
    el.folderModal.classList.remove('hidden');
    el.folderNameInput.focus();
  }

  function syncFolderModalChoices() {
    document.querySelectorAll('#folderIconPresets .icon-choice').forEach((b) => {
      b.classList.toggle('active', b.dataset.icon === pickedFolderIcon);
    });
    document.querySelectorAll('#folderColorPresets .color-choice').forEach((b) => {
      b.classList.toggle('active', b.dataset.color === pickedFolderColor);
    });
  }

  function openCommandPalette() {
    el.cmdPaletteInput.value = '';
    renderCommandPaletteResults('');
    el.cmdPaletteModal.classList.remove('hidden');
    el.cmdPaletteInput.focus();
  }

  function renderCommandPaletteResults(q) {
    el.cmdPaletteResults.innerHTML = '';
    const notes = VaultBridge.searchNotes(vault, q).slice(0, 12);

    const modHint = isMacOS ? '⌘' : 'Ctrl+';
    const commands = [
      { label: 'Create New Class Board', hint: `${modHint}N`, run: () => createNewBoard() },
      { label: 'Open Today Daily Board', hint: 'Journal', run: () => openOrCreateTodayJournal() },
      { label: 'Open Cross-OS Node Sync Hub', hint: 'Win·Mac·Android', run: () => openSyncModal() },
      { label: 'Import PDF Slides onto Board', hint: 'PDF Markup', run: () => handlePdfPick() },
      { label: 'Toggle Classroom Zen Mode', hint: 'F11', run: () => toggleZenMode() },
      { label: 'Open App & Graph Field Guide (Tutorial)', hint: 'Guide', run: () => openTutorialModal('all') },
      { label: 'Export All Slides to Multi-Page PDF', hint: 'PDF', run: () => exportAllSlidesPdf() }
    ].filter((c) => !q.trim() || c.label.toLowerCase().includes(q.trim().toLowerCase()));

    for (const n of notes) {
      const btn = document.createElement('button');
      btn.className = 'palette-row';
      const folder = (vault.folders || []).find((f) => f.id === n.folderId);
      btn.innerHTML = `
        <span><strong>${escapeHtml(n.title || 'Untitled')}</strong></span>
        <span class="palette-row-sub">${folder ? `${folder.icon} ${escapeHtml(folder.name)}` : 'Board / Note'}</span>
      `;
      btn.addEventListener('click', () => {
        el.cmdPaletteModal.classList.add('hidden');
        selectNote(n.id);
      });
      el.cmdPaletteResults.appendChild(btn);
    }

    for (const cmd of commands) {
      const btn = document.createElement('button');
      btn.className = 'palette-row';
      btn.innerHTML = `
        <span>${escapeHtml(cmd.label)}</span>
        <span class="palette-row-sub">${cmd.hint}</span>
      `;
      btn.addEventListener('click', () => {
        el.cmdPaletteModal.classList.add('hidden');
        cmd.run();
      });
      el.cmdPaletteResults.appendChild(btn);
    }
  }

  function openLinkPicker() {
    el.linkPickerSearch.value = '';
    renderLinkPickerResults('');
    el.linkPickerModal.classList.remove('hidden');
    el.linkPickerSearch.focus();
  }

  function renderLinkPickerResults(q) {
    el.linkPickerResults.innerHTML = '';
    const active = getActiveNote();
    const matches = VaultBridge.searchNotes(vault, q).filter((n) => !active || n.id !== active.id);
    for (const n of matches.slice(0, 15)) {
      const btn = document.createElement('button');
      btn.className = 'palette-row';
      btn.innerHTML = `
        <span>[[${escapeHtml(n.title || 'Untitled')}]]</span>
        <span class="palette-row-sub">Insert Link</span>
      `;
      btn.addEventListener('click', () => {
        insertTextAtCursor(el.noteViewBodyInput, `[[${n.title || 'Untitled'}]] `);
        if (active) {
          active.content = el.noteViewBodyInput.value;
          active.updatedAt = Date.now();
          scheduleSave();
          renderWikiLinkChips(active);
        }
        el.linkPickerModal.classList.add('hidden');
      });
      el.linkPickerResults.appendChild(btn);
    }
  }

  function openConnectLabelModal() {
    el.connectLabelInput.value = '';
    el.connectLabelModal.classList.remove('hidden');
    el.connectLabelInput.focus();
  }

  function commitCardConnector() {
    const note = getActiveNote();
    const page = getActivePage(note);
    if (note && page && pendingConnectSourceId && pendingConnectTargetId) {
      if (!Array.isArray(page.connectors)) page.connectors = [];
      page.connectors.push({
        id: VaultBridge.makeId('conn'),
        fromCardId: pendingConnectSourceId,
        toCardId: pendingConnectTargetId,
        label: el.connectLabelInput.value.trim(),
        colorHex: engine.strokeColor || '#A855F7'
      });
      note.updatedAt = Date.now();
      scheduleSave();
      engine.redrawStatic();
    }
    pendingConnectSourceId = null;
    pendingConnectTargetId = null;
    updateConnectHint();
    el.connectLabelModal.classList.add('hidden');
    renderActiveBoard();
  }

  function setTutorialTab(tabKey = 'all') {
    const resolved = tabKey || 'all';
    document.querySelectorAll('#tutorialTabs .tutorial-tab').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tutTab === resolved);
    });
    document.querySelectorAll('#tutorialBody .tutorial-section').forEach((sec) => {
      const show = resolved === 'all' || sec.dataset.tutSection === resolved;
      sec.classList.toggle('hidden', !show);
    });
  }

  function openTutorialModal(initialTab = 'all') {
    closeDropdowns();
    setTutorialTab(initialTab);
    if (el.tutorialModal) {
      el.tutorialModal.classList.remove('hidden');
      if (el.tutorialBody) el.tutorialBody.scrollTop = 0;
    }
  }

  // ==================== CLASSROOM TIMER & ZEN MODE ====================

  function formatTimer(sec) {
    const abs = Math.max(0, sec);
    const m = Math.floor(abs / 60);
    const s = abs % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  function toggleTimer() {
    timerRunning = !timerRunning;
    el.classTimerGroup.classList.toggle('running', timerRunning);
    if (timerRunning && !timerInterval) {
      timerInterval = setInterval(() => {
        if (!timerRunning) return;
        if (timerMode === 'pomodoro') {
          timerSeconds = Math.max(0, timerSeconds - 1);
          if (timerSeconds === 0) {
            timerRunning = false;
            el.classTimerGroup.classList.remove('running');
            showToast('25-minute Focus Session Complete!');
          }
        } else {
          timerSeconds += 1;
        }
        el.classTimerDisplay.textContent = formatTimer(timerSeconds);
      }, 1000);
    }
  }

  async function toggleZenMode(forceVal = undefined) {
    isZenMode = forceVal !== undefined ? forceVal : !isZenMode;
    el.appShell.classList.toggle('zen-mode', isZenMode);
    el.exitZenFloatingBtn.classList.toggle('hidden', !isZenMode);
    try {
      await desktop.toggleFullscreen();
    } catch (e) {
      // Ignore if test runner overrides
    }
    setTimeout(() => {
      if (engine) engine.resize();
    }, 80);
  }

  // ==================== UTILITIES ====================

  function switchWorkspaceView(viewName) {
    activeView = viewName;
    document.querySelectorAll('.view-tab').forEach((t) => {
      t.classList.toggle('active', t.dataset.viewTarget === viewName);
    });
    el.boardView.classList.toggle('active', viewName === 'board');
    el.notesView.classList.toggle('active', viewName === 'notes');
    el.graphView.classList.toggle('active', viewName === 'graph');

    if (viewName === 'board' && engine) {
      setTimeout(() => engine.resize(), 20);
      renderActiveBoard();
    } else if (viewName === 'notes') {
      renderNoteView();
    } else if (viewName === 'graph') {
      renderGraphIfVisible();
    }
  }

  function renderAllActiveViews() {
    syncTopBar();
    renderActiveBoard();
    renderNoteView();
    renderGraphIfVisible();
  }

  function closeDropdowns() {
    el.importMenu.classList.add('hidden');
    el.exportMenu.classList.add('hidden');
    el.followUpPopover.classList.add('hidden');
    if (el.gradientPopover) el.gradientPopover.classList.add('hidden');
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function insertTextAtCursor(ta, snippet) {
    const start = ta.selectionStart || 0;
    const end = ta.selectionEnd || 0;
    const val = ta.value || '';
    ta.value = val.slice(0, start) + snippet + val.slice(end);
    ta.selectionStart = ta.selectionEnd = start + snippet.length;
    ta.focus();
  }

  // ==================== EVENT BINDINGS & INIT ====================

  function bindAllEvents() {
    // Close dropdowns on outside click
    document.addEventListener('click', (e) => {
      if (!e.target.closest('.dropdown-wrap') && !e.target.closest('.followup-wrap')) {
        closeDropdowns();
      }
    });

    el.toggleSidebarBtn.addEventListener('click', () => {
      el.sidebar.classList.toggle('collapsed');
      setTimeout(() => engine && engine.resize(), 40);
    });

    el.activeTitleInput.addEventListener('input', () => {
      const note = getActiveNote();
      if (!note) return;
      note.title = el.activeTitleInput.value;
      note.updatedAt = Date.now();
      el.noteViewTitleInput.value = note.title;
      scheduleSave();
      renderNotesList();
    });

    el.pinNoteBtn.addEventListener('click', () => {
      const note = getActiveNote();
      if (!note) return;
      note.isPinned = !note.isPinned;
      note.updatedAt = Date.now();
      syncTopBar();
      scheduleSave();
      renderNotesList();
    });

    // Follow-Up CRM Reminder popover
    el.followUpBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const wasHidden = el.followUpPopover.classList.contains('hidden');
      closeDropdowns();
      if (wasHidden) el.followUpPopover.classList.remove('hidden');
    });

    el.followUpPopover.querySelectorAll('[data-followup-offset]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const note = getActiveNote();
        if (!note) return;
        const offset = Number(btn.dataset.followupOffset);
        note.followUpAt = addDaysIso(offset);
        note.updatedAt = Date.now();
        closeDropdowns();
        syncTopBar();
        scheduleSave();
        renderFolders();
        renderNotesList();
        showToast(`Follow-up set for ${formatDateChip(note.followUpAt)}`);
      });
    });

    el.followUpDateInput.addEventListener('change', () => {
      const note = getActiveNote();
      if (!note) return;
      note.followUpAt = el.followUpDateInput.value || null;
      note.updatedAt = Date.now();
      closeDropdowns();
      syncTopBar();
      scheduleSave();
      renderFolders();
      renderNotesList();
    });

    el.clearFollowUpBtn.addEventListener('click', () => {
      const note = getActiveNote();
      if (!note) return;
      note.followUpAt = null;
      note.updatedAt = Date.now();
      closeDropdowns();
      syncTopBar();
      scheduleSave();
      renderFolders();
      renderNotesList();
    });

    // Classroom timer
    el.classTimerBtn.addEventListener('click', toggleTimer);
    el.classTimerModeBtn.addEventListener('click', () => {
      timerRunning = false;
      el.classTimerGroup.classList.remove('running');
      timerMode = timerMode === 'stopwatch' ? 'pomodoro' : 'stopwatch';
      timerSeconds = timerMode === 'pomodoro' ? 25 * 60 : 0;
      el.classTimerDisplay.textContent = formatTimer(timerSeconds);
      showToast(timerMode === 'pomodoro' ? '25-min Focus Pomodoro Ready' : 'Class Stopwatch Ready');
    });
    el.classTimerResetBtn.addEventListener('click', () => {
      timerRunning = false;
      el.classTimerGroup.classList.remove('running');
      timerSeconds = timerMode === 'pomodoro' ? 25 * 60 : 0;
      el.classTimerDisplay.textContent = formatTimer(timerSeconds);
    });

    // Theme switcher & Choosable Per-Theme Gradient Studio
    document.querySelectorAll('.theme-chip').forEach((btn) => {
      btn.addEventListener('click', () => {
        applyTheme(btn.dataset.themeChoice);
        scheduleSave();
      });
    });

    if (el.gradientBtn) {
      el.gradientBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const willOpen = el.gradientPopover.classList.contains('hidden');
        closeDropdowns();
        if (willOpen) {
          renderGradientPopover();
          el.gradientPopover.classList.remove('hidden');
        }
      });
    }

    if (el.gradientToggleBtn) {
      el.gradientToggleBtn.addEventListener('click', () => {
        ensureVaultGradientState();
        vault.gradientsEnabled = vault.gradientsEnabled === false;
        applyGradientTheme(vault.activeTheme || 'DEEP_PURPLE_AMOLED');
        scheduleSave();
      });
    }

    if (el.gradientResetBtn) {
      el.gradientResetBtn.addEventListener('click', () => {
        ensureVaultGradientState();
        const themeKey = vault.activeTheme || 'DEEP_PURPLE_AMOLED';
        const presets = THEME_GRADIENT_PRESETS[themeKey] || THEME_GRADIENT_PRESETS.DEEP_PURPLE_AMOLED;
        vault.gradientsEnabled = true;
        vault.themeGradients[themeKey] = presets[0].id;
        applyGradientTheme(themeKey);
        scheduleSave();
        showToast(`Reset ${presets[0].name} gradient for current theme`);
      });
    }

    // Guide, Sync Hub & Zen mode
    if (el.openTutorialBtn) {
      el.openTutorialBtn.addEventListener('click', () => openTutorialModal('all'));
    }
    if (el.openSyncModalBtn) {
      el.openSyncModalBtn.addEventListener('click', openSyncModal);
    }
    if (el.syncAutoFolderToggle) {
      el.syncAutoFolderToggle.addEventListener('click', async () => {
        if (!desktop || typeof desktop.setSyncFolderConfig !== 'function') return;
        try {
          const nextAuto = !Boolean(syncConfigCache && syncConfigCache.autoSyncFolder);
          await persistNow();
          const res = await desktop.setSyncFolderConfig({ autoSync: nextAuto });
          if (res && res.vault) applyIncomingSyncedVault(res.vault, res.summary, false);
          if (res && res.config) refreshSyncModal(res.config);
        } catch (err) {
          showToast(`Sync toggle failed: ${err.message || err}`);
        }
      });
    }
    if (el.syncChooseFolderBtn) {
      el.syncChooseFolderBtn.addEventListener('click', async () => {
        if (!desktop || typeof desktop.chooseSyncFolder !== 'function') return;
        try {
          await persistNow();
          const res = await desktop.chooseSyncFolder();
          if (!res) return;
          if (res.vault) applyIncomingSyncedVault(res.vault, res.summary, false);
          if (res.config) refreshSyncModal(res.config);
          showToast('Shared sync folder connected & merged!');
        } catch (err) {
          showToast(`Could not connect folder: ${err.message || err}`);
        }
      });
    }
    if (el.syncFolderNowBtn) {
      el.syncFolderNowBtn.addEventListener('click', async () => {
        if (!desktop || typeof desktop.syncFolderNow !== 'function') return;
        try {
          const res = await desktop.syncFolderNow(vault);
          if (res && res.vault) applyIncomingSyncedVault(res.vault, res.summary, false);
          if (res && res.config) refreshSyncModal(res.config);
          if (res && res.summary) {
            showToast(`Folder synced: ${res.summary.notesMerged} merged, ${res.summary.newNotesAdded} added`);
          }
        } catch (err) {
          showToast(`Folder sync failed: ${err.message || err}`);
        }
      });
    }
    if (el.syncLanHostToggle) {
      el.syncLanHostToggle.addEventListener('click', async () => {
        if (!desktop) return;
        try {
          const running = Boolean(syncConfigCache && syncConfigCache.lan && syncConfigCache.lan.isRunning);
          const cfg = running
            ? await desktop.stopLanServer()
            : await desktop.startLanServer();
          refreshSyncModal(cfg);
          showToast(cfg.lan && cfg.lan.isRunning ? 'Wi-Fi LAN Sync Server started!' : 'Wi-Fi LAN Server stopped');
        } catch (err) {
          showToast(`Wi-Fi server error: ${err.message || err}`);
        }
      });
    }
    if (el.syncLanRegenPinBtn) {
      el.syncLanRegenPinBtn.addEventListener('click', async () => {
        if (!desktop || typeof desktop.regenLanPin !== 'function') return;
        const cfg = await desktop.regenLanPin();
        refreshSyncModal(cfg);
        showToast('Generated new Wi-Fi sync PIN');
      });
    }
    if (el.syncLanConnectBtn) {
      el.syncLanConnectBtn.addEventListener('click', async () => {
        if (!desktop || typeof desktop.syncLanPeer !== 'function') return;
        const hostPort = (el.syncLanPeerInput && el.syncLanPeerInput.value || '').trim();
        const pin = (el.syncLanPeerPinInput && el.syncLanPeerPinInput.value || '').trim();
        if (!hostPort) {
          showToast('Enter peer IP:Port first (e.g. 192.168.1.24:8765)');
          return;
        }
        try {
          el.syncLanConnectBtn.disabled = true;
          el.syncLanConnectBtn.textContent = 'Syncing…';
          const res = await desktop.syncLanPeer({ hostPort, pin, rendererVault: vault });
          if (res && res.vault) applyIncomingSyncedVault(res.vault, res.summary, false);
          if (res && res.config) refreshSyncModal(res.config);
          if (res && res.summary) {
            showToast(`Wi-Fi synced with ${res.summary.remoteDevice}: ${res.summary.notesMerged} merged, ${res.summary.newNotesAdded} added`);
          }
        } catch (err) {
          showToast(`Wi-Fi sync failed: ${err.message || err}`);
        } finally {
          el.syncLanConnectBtn.disabled = false;
          el.syncLanConnectBtn.textContent = 'Sync Wi-Fi';
        }
      });
    }
    if (el.syncZipExportBtn) {
      el.syncZipExportBtn.addEventListener('click', exportAndroidZip);
    }
    if (el.syncZipImportBtn) {
      el.syncZipImportBtn.addEventListener('click', importAndroidZip);
    }
    el.zenModeBtn.addEventListener('click', () => toggleZenMode());
    el.exitZenFloatingBtn.addEventListener('click', () => toggleZenMode(false));

    // Import / Export menus
    el.importMenuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const willOpen = el.importMenu.classList.contains('hidden');
      closeDropdowns();
      if (willOpen) el.importMenu.classList.remove('hidden');
    });
    el.exportMenuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const willOpen = el.exportMenu.classList.contains('hidden');
      closeDropdowns();
      if (willOpen) el.exportMenu.classList.remove('hidden');
    });

    document.getElementById('actionImportPdf').addEventListener('click', handlePdfPick);
    document.getElementById('quickPdfBtn').addEventListener('click', handlePdfPick);
    document.getElementById('actionImportImage').addEventListener('click', async () => {
      closeDropdowns();
      const picked = await desktop.pickImage();
      if (!picked) return;
      const img = new Image();
      img.onload = () => {
        const w = Math.min(640, Math.max(240, img.naturalWidth));
        const h = Math.round((img.naturalHeight / Math.max(1, img.naturalWidth)) * w);
        addSpatialCardAtViewportCenter({
          kind: 'image',
          title: picked.fileName,
          w,
          h: Math.min(640, h + 38),
          imageDataUrl: picked.dataUrl,
          colorHex: '#38BDF8'
        });
      };
      img.src = picked.dataUrl;
    });
    document.getElementById('actionImportZip').addEventListener('click', importAndroidZip);
    document.getElementById('actionExportPng').addEventListener('click', exportCurrentSlidePng);
    document.getElementById('actionExportPdf').addEventListener('click', exportAllSlidesPdf);
    document.getElementById('actionExportMd').addEventListener('click', exportMarkdown);
    document.getElementById('actionExportZip').addEventListener('click', exportAndroidZip);

    // Sidebar controls
    el.sidebarSearchInput.addEventListener('input', () => {
      searchQuery = el.sidebarSearchInput.value;
      renderNotesList();
    });
    el.cmdPaletteBtn.addEventListener('click', openCommandPalette);
    el.newBoardBtn.addEventListener('click', () => createNewBoard());
    el.todayJournalBtn.addEventListener('click', openOrCreateTodayJournal);
    el.newFolderBtn.addEventListener('click', () => openFolderModal(null));

    document.querySelectorAll('.view-tab').forEach((tab) => {
      tab.addEventListener('click', () => switchWorkspaceView(tab.dataset.viewTarget));
    });

    // Floating Board Dock
    document.getElementById('addNoteCardBtn').addEventListener('click', () => {
      addSpatialCardAtViewportCenter({ kind: 'note', title: 'Concept Note', colorHex: '#A855F7' });
    });
    document.getElementById('addChecklistCardBtn').addEventListener('click', () => {
      addSpatialCardAtViewportCenter({ kind: 'checklist', title: 'Action Checklist', colorHex: '#10B981' });
    });

    document.querySelectorAll('#toolButtonsGroup .tool-btn').forEach((btn) => {
      btn.addEventListener('click', () => setActiveTool(btn.dataset.tool));
    });

    el.shapeSnapBtn.addEventListener('click', () => {
      engine.shapeSnap = !engine.shapeSnap;
      el.shapeSnapBtn.classList.toggle('active', engine.shapeSnap);
    });

    document.querySelectorAll('#inkSwatches .swatch-btn').forEach((sw) => {
      sw.addEventListener('click', () => {
        engine.strokeColor = sw.dataset.color;
        el.customInkColor.value = sw.dataset.color;
        document.querySelectorAll('#inkSwatches .swatch-btn').forEach((x) => x.classList.remove('active'));
        sw.classList.add('active');
      });
    });

    el.customInkColor.addEventListener('input', () => {
      engine.strokeColor = el.customInkColor.value;
      document.querySelectorAll('#inkSwatches .swatch-btn').forEach((x) => x.classList.remove('active'));
    });

    el.strokeWidthSlider.addEventListener('input', () => {
      engine.strokeWidth = Number(el.strokeWidthSlider.value);
    });

    el.gridCycleBtn.addEventListener('click', () => {
      const note = getActiveNote();
      if (!note) return;
      const cur = (note.board && note.board.gridStyle) || 'DOTS';
      const next = GRID_ORDER[(GRID_ORDER.indexOf(cur) + 1) % GRID_ORDER.length];
      note.board.gridStyle = next;
      el.gridCycleBtn.textContent = GRID_LABELS[next];
      engine.setAppearance(vault.activeTheme, next, getActiveGradientPreset(), vault.gradientsEnabled !== false);
      scheduleSave();
    });

    document.getElementById('undoBtn').addEventListener('click', () => engine.undo());
    document.getElementById('redoBtn').addEventListener('click', () => engine.redo());

    // Lasso actions
    if (el.lassoMoveModeBtn) {
      el.lassoMoveModeBtn.addEventListener('click', () => engine.toggleLassoMoveMode());
    }
    document.getElementById('nudgeLeftBtn').addEventListener('click', () => engine.nudgeSelectedStrokes(-20, 0));
    document.getElementById('nudgeRightBtn').addEventListener('click', () => engine.nudgeSelectedStrokes(20, 0));
    document.getElementById('nudgeUpBtn').addEventListener('click', () => engine.nudgeSelectedStrokes(0, -20));
    document.getElementById('nudgeDownBtn').addEventListener('click', () => engine.nudgeSelectedStrokes(0, 20));
    document.getElementById('deleteLassoBtn').addEventListener('click', () => engine.deleteSelectedStrokes());
    document.getElementById('clearSlideInkBtn').addEventListener('click', () => engine.clearPageInk());

    // Slides & zoom
    el.addSlideBtn.addEventListener('click', () => {
      const note = getActiveNote();
      if (!note) return;
      const nextNum = note.board.pages.length + 1;
      note.board.pages.push(VaultBridge.createEmptyBoardPage(`Slide ${nextNum}`));
      note.board.activePageIndex = note.board.pages.length - 1;
      note.updatedAt = Date.now();
      scheduleSave();
      renderActiveBoard();
      renderNotesList();
    });

    el.zoomOutBtn.addEventListener('click', () => engine.zoomBy(0.85));
    el.zoomInBtn.addEventListener('click', () => engine.zoomBy(1.18));
    el.zoomResetBtn.addEventListener('click', () => engine.resetViewport());
    el.zoomFitBtn.addEventListener('click', () => engine.fitToContent());

    // Notes & Checklists View handlers
    el.noteViewTitleInput.addEventListener('input', () => {
      const note = getActiveNote();
      if (!note) return;
      note.title = el.noteViewTitleInput.value;
      el.activeTitleInput.value = note.title;
      note.updatedAt = Date.now();
      scheduleSave();
      renderNotesList();
    });

    el.noteViewBodyInput.addEventListener('input', () => {
      const note = getActiveNote();
      if (!note) return;
      note.content = el.noteViewBodyInput.value;
      note.updatedAt = Date.now();
      scheduleSave();
      renderWikiLinkChips(note);
    });

    el.notesFolderSelect.addEventListener('change', () => {
      const note = getActiveNote();
      if (!note) return;
      note.folderId = el.notesFolderSelect.value || null;
      note.updatedAt = Date.now();
      scheduleSave();
      renderFolders();
      renderNotesList();
    });

    document.getElementById('insertWikiLinkBtn').addEventListener('click', openLinkPicker);
    document.getElementById('insertTagBtn').addEventListener('click', () => {
      insertTextAtCursor(el.noteViewBodyInput, '#study ');
      const note = getActiveNote();
      if (note) {
        note.content = el.noteViewBodyInput.value;
        scheduleSave();
      }
    });

    const addChecklistFromNoteView = () => {
      const card = addSpatialCardAtViewportCenter({
        kind: 'checklist',
        title: 'To-Do Checklist',
        colorHex: '#10B981'
      });
      if (card) renderNoteViewChecklists();
    };
    document.getElementById('addNoteChecklistBtn').addEventListener('click', addChecklistFromNoteView);
    document.getElementById('addChecklistUnderNoteBtn').addEventListener('click', addChecklistFromNoteView);

    // Graph handlers
    el.graphFilterInput.addEventListener('input', () => drawGraphCanvas());
    el.graphResetLayoutBtn.addEventListener('click', () => {
      graphState.nodes = [];
      graphState.panX = 0;
      graphState.panY = 0;
      graphState.zoom = 1;
      rebuildGraphSimulation();
    });
    if (el.graphGuideBtn) {
      el.graphGuideBtn.addEventListener('click', () => openTutorialModal('graph'));
    }

    // Tutorial modal tabs & jump action
    document.querySelectorAll('#tutorialTabs .tutorial-tab').forEach((tab) => {
      tab.addEventListener('click', () => setTutorialTab(tab.dataset.tutTab));
    });
    if (el.tutOpenGraphBtn) {
      el.tutOpenGraphBtn.addEventListener('click', () => {
        if (el.tutorialModal) el.tutorialModal.classList.add('hidden');
        switchWorkspaceView('graph');
      });
    }

    // Modals close & submit
    document.querySelectorAll('[data-close-modal]').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.getElementById(btn.dataset.closeModal).classList.add('hidden');
      });
    });

    document.querySelectorAll('.modal-backdrop').forEach((bd) => {
      bd.addEventListener('click', (e) => {
        if (e.target === bd) bd.classList.add('hidden');
      });
    });

    el.cmdPaletteInput.addEventListener('input', () => renderCommandPaletteResults(el.cmdPaletteInput.value));
    el.cmdPaletteInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const first = el.cmdPaletteResults.querySelector('.palette-row');
        if (first) first.click();
      }
    });

    el.linkPickerSearch.addEventListener('input', () => renderLinkPickerResults(el.linkPickerSearch.value));

    document.querySelectorAll('#folderIconPresets .icon-choice').forEach((btn) => {
      btn.addEventListener('click', () => {
        pickedFolderIcon = btn.dataset.icon;
        syncFolderModalChoices();
      });
    });

    document.querySelectorAll('#folderColorPresets .color-choice').forEach((btn) => {
      btn.addEventListener('click', () => {
        pickedFolderColor = btn.dataset.color;
        syncFolderModalChoices();
      });
    });

    el.saveFolderBtn.addEventListener('click', () => {
      const name = el.folderNameInput.value.trim();
      if (!name) return;
      const now = Date.now();
      if (editingFolderId) {
        const existing = vault.folders.find((f) => f.id === editingFolderId);
        if (existing) {
          existing.name = name;
          existing.icon = pickedFolderIcon;
          existing.colorHex = pickedFolderColor;
          existing.updatedAt = now;
        }
      } else {
        vault.folders.push({
          id: VaultBridge.makeId('folder'),
          name,
          icon: pickedFolderIcon,
          colorHex: pickedFolderColor,
          createdAt: now,
          updatedAt: now
        });
      }
      el.folderModal.classList.add('hidden');
      scheduleSave(true);
      renderFolders();
      renderNotesList();
    });

    el.deleteFolderBtn.addEventListener('click', () => {
      if (!editingFolderId) return;
      const now = Date.now();
      vault.folders = vault.folders.filter((f) => f.id !== editingFolderId);
      if (typeof VaultBridge.recordTombstone === 'function') {
        VaultBridge.recordTombstone(vault, editingFolderId, 'folder', now);
      }
      for (const n of vault.notes) {
        if (n.folderId === editingFolderId) {
          n.folderId = null;
          n.updatedAt = now;
        }
      }
      if (activeFolderFilter === editingFolderId) activeFolderFilter = 'ALL';
      el.folderModal.classList.add('hidden');
      scheduleSave(true);
      renderFolders();
      renderNotesList();
    });

    document.getElementById('pdfImportAsSlidesBtn').addEventListener('click', () => runPdfRenderImport('slides'));
    document.getElementById('pdfImportOntoCanvasBtn').addEventListener('click', () => runPdfRenderImport('canvas'));

    document.getElementById('confirmConnectBtn').addEventListener('click', commitCardConnector);
    el.connectLabelInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') commitCardConnector();
    });

    // Global Keyboard Shortcuts + Ctrl+V Screenshot Paste
    window.addEventListener('paste', (e) => {
      const items = (e.clipboardData && e.clipboardData.items) || [];
      for (const item of items) {
        if (item.type && item.type.startsWith('image/')) {
          e.preventDefault();
          const blob = item.getAsFile();
          if (!blob) return;
          const reader = new FileReader();
          reader.onload = () => {
            const dataUrl = reader.result;
            const img = new Image();
            img.onload = () => {
              const w = Math.min(680, Math.max(240, img.naturalWidth));
              const h = Math.round((img.naturalHeight / Math.max(1, img.naturalWidth)) * w);
              switchWorkspaceView('board');
              addSpatialCardAtViewportCenter({
                kind: 'image',
                title: 'Pasted Question / Screenshot',
                w,
                h: Math.min(680, h + 38),
                imageDataUrl: dataUrl,
                colorHex: '#A855F7',
                keepTool: true
              });
              showToast('Pasted image onto board!');
            };
            img.src = dataUrl;
          };
          reader.readAsDataURL(blob);
          break;
        }
      }
    });

    window.addEventListener('keydown', (e) => {
      const typing = engine && engine._isTypingTarget(document.activeElement);

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        openCommandPalette();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        createNewBoard();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault();
        el.sidebar.classList.toggle('collapsed');
        setTimeout(() => engine && engine.resize(), 40);
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === '1') { e.preventDefault(); switchWorkspaceView('board'); return; }
      if ((e.ctrlKey || e.metaKey) && e.key === '2') { e.preventDefault(); switchWorkspaceView('notes'); return; }
      if ((e.ctrlKey || e.metaKey) && e.key === '3') { e.preventDefault(); switchWorkspaceView('graph'); return; }

      if (e.key === 'F11') {
        e.preventDefault();
        toggleZenMode();
        return;
      }

      if (e.key === 'Escape') {
        document.querySelectorAll('.modal-backdrop').forEach((m) => m.classList.add('hidden'));
        closeDropdowns();
        pendingConnectSourceId = null;
        updateConnectHint();
        renderActiveBoard();
        return;
      }

      if (typing || activeView !== 'board') return;

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
        e.preventDefault();
        e.shiftKey ? engine.redo() : engine.undo();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
        e.preventDefault();
        engine.redo();
        return;
      }

      if (e.key === 'Delete' || e.key === 'Backspace') {
        if (engine.selectedStrokeIds.size > 0) {
          e.preventDefault();
          engine.deleteSelectedStrokes();
        }
        return;
      }

      if (e.key === 'PageDown' || e.key === 'PageUp') {
        const note = getActiveNote();
        if (!note || note.board.pages.length <= 1) return;
        e.preventDefault();
        const dir = e.key === 'PageDown' ? 1 : -1;
        note.board.activePageIndex = (note.board.activePageIndex + dir + note.board.pages.length) % note.board.pages.length;
        renderActiveBoard();
        return;
      }

      const keyMap = {
        v: 'select',
        h: 'pan',
        p: 'pen',
        m: 'highlighter',
        a: 'arrow',
        l: 'laser',
        e: 'eraser',
        s: 'lasso',
        c: 'connect'
      };
      const k = e.key.toLowerCase();
      if (keyMap[k]) {
        setActiveTool(keyMap[k]);
      } else if (k === 'n') {
        addSpatialCardAtViewportCenter({ kind: 'note', title: 'Sticky Note' });
      } else if (k === 't') {
        addSpatialCardAtViewportCenter({ kind: 'checklist', title: 'Checklist', colorHex: '#10B981' });
      }
    });
  }

  async function init() {
    try {
      if (desktop && typeof desktop.getAppInfo === 'function') {
        const info = await desktop.getAppInfo();
        isMacOS = Boolean(info && info.platform === 'darwin');
      }
    } catch (_) {
      isMacOS = /Mac/i.test(navigator.platform || '');
    }

    const modHint = isMacOS ? '⌘' : 'Ctrl+';
    if (el.cmdPaletteBtn) {
      el.cmdPaletteBtn.textContent = isMacOS ? '⌘K' : 'Ctrl+K';
      el.cmdPaletteBtn.title = `Quick Switcher & Commands (${modHint}K)`;
    }
    if (el.toggleSidebarBtn) {
      el.toggleSidebarBtn.title = `Toggle Sidebar (${modHint}B)`;
    }

    vault = await desktop.loadVault();
    if (!vault || !Array.isArray(vault.notes) || vault.notes.length === 0) {
      vault = VaultBridge.createStarterVault();
    }
    if (!Array.isArray(vault.folders) || vault.folders.length === 0) {
      vault.folders = VaultBridge.STARTER_FOLDERS.map((f, idx) => ({
        ...f,
        createdAt: 1 + idx,
        updatedAt: 1 + idx
      }));
    }
    ensureVaultGradientState();

    engine = new BoardCanvasEngine({
      stageEl: el.boardStage,
      gridCanvas: el.gridCanvas,
      staticCanvas: el.inkStaticCanvas,
      liveCanvas: el.inkLiveCanvas,
      cardsLayer: el.cardsLayer,
      onStrokeCommitted: () => {
        const note = getActiveNote();
        if (note) note.updatedAt = Date.now();
        scheduleSave();
      },
      onStrokesModified: () => {
        const note = getActiveNote();
        if (note) note.updatedAt = Date.now();
        scheduleSave();
      },
      onViewportChanged: () => {
        updateZoomLabel();
        scheduleSave();
      },
      onLassoMoveModeChanged: (active) => {
        if (el.lassoMoveModeBtn) {
          el.lassoMoveModeBtn.classList.toggle('active', Boolean(active));
        }
      },
      onSelectionChanged: (count) => {
        el.lassoBar.classList.toggle('hidden', count === 0);
        el.lassoCountLabel.textContent = `${count} stroke${count === 1 ? '' : 's'} · Drag box or use Drag Move`;
      }
    });

    bindAllEvents();
    initGraphInteractions();
    applyTheme(vault.activeTheme || 'DEEP_PURPLE_AMOLED');
    renderFolders();
    renderNotesList();
    renderAllActiveViews();
    setActiveTool('pen');

    if (desktop && typeof desktop.onVaultUpdated === 'function') {
      desktop.onVaultUpdated((payload) => {
        if (!payload || !payload.vault) return;
        applyIncomingSyncedVault(payload.vault, payload.summary, true);
      });
    }
    if (desktop && typeof desktop.getSyncConfig === 'function') {
      desktop.getSyncConfig()
        .then((cfg) => { syncConfigCache = cfg; })
        .catch(() => {});
    }
  }

  init();
})();
