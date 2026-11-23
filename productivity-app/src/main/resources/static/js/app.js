(() => {
  const sidebar = document.querySelector('#appSidebar');
  const toggles = document.querySelectorAll('[data-sidebar-toggle]');
  const setOpen = (open) => {
    document.body.classList.toggle('sidebar-open', open);
    toggles.forEach((toggle) => {
      if (toggle.hasAttribute('aria-expanded')) toggle.setAttribute('aria-expanded', String(open));
    });
  };
  toggles.forEach((toggle) => toggle.addEventListener('click', () => setOpen(!document.body.classList.contains('sidebar-open'))));
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') setOpen(false); });
  sidebar?.querySelectorAll('a').forEach((link) => link.addEventListener('click', () => setOpen(false)));

  const csrfMeta = document.querySelector('meta[name="_csrf"]');
  const csrfToken = csrfMeta?.content;

  const getCsrfToken = () => {
    const meta = document.querySelector('meta[name="_csrf"]')?.content;
    if (meta && meta !== '') return meta;
    const cookieMatch = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return cookieMatch ? decodeURIComponent(cookieMatch[1]) : (csrfToken || '');
  };

  const ensureCsrf = (form) => {
    if (!csrfToken) return;
    const method = (form.getAttribute('method') || '').toUpperCase();
    if (method !== 'POST') return;
    if (!form.querySelector('input[name="_csrf"]')) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = '_csrf';
      input.value = csrfToken;
      form.appendChild(input);
    }
    if (form.enctype && form.enctype.includes('multipart') && form.action && !form.action.includes('_csrf=')) {
      const separator = form.action.includes('?') ? '&' : '?';
      form.action += separator + '_csrf=' + encodeURIComponent(csrfToken);
    }
  };

  document.querySelectorAll('form').forEach(ensureCsrf);

  const uploadNotice = document.getElementById('uploadNotice');
  const uploadNoticeTitle = document.getElementById('uploadNoticeTitle');
  const uploadNoticeProgress = document.getElementById('uploadNoticeProgress');
  const pendingUploads = new Set();

  uploadNotice?.addEventListener('cancel', (event) => event.preventDefault());

  const hasUploadFiles = (formData) => Array.from(formData.values())
    .some(entry => entry instanceof File && entry.name);

  function updateUploadNotice() {
    if (!uploadNotice) return;
    const upload = pendingUploads.values().next().value;
    if (!upload) {
      if (uploadNotice.open) uploadNotice.close();
      return;
    }
    uploadNoticeTitle.textContent = upload.title;
    if (upload.percent == null) {
      uploadNoticeProgress.removeAttribute('value');
    } else {
      uploadNoticeProgress.value = upload.percent;
    }
    if (!uploadNotice.open) uploadNotice.showModal();
  }

  function startUploadNotice(title) {
    const upload = { title, percent: null };
    pendingUploads.add(upload);
    updateUploadNotice();
    return upload;
  }

  function finishUploadNotice(upload) {
    pendingUploads.delete(upload);
    updateUploadNotice();
  }

  function showFormUpload(form) {
    ensureCsrf(form);
    if (form.enctype !== 'multipart/form-data' || !hasUploadFiles(new FormData(form))) return;
    const restoring = form.querySelector('input[type="file"][name="backup"]');
    startUploadNotice(restoring ? 'Uploading and restoring backup…' : 'Uploading and saving files…');
  }

  window.sendFocusUpload = (url, formData) => {
    const headers = { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' };
    if (!hasUploadFiles(formData)) return fetch(url, { method: 'POST', headers, body: formData });

    const upload = startUploadNotice('Uploading files…');
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', url);
      Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable) return;
        upload.percent = Math.floor(100 * event.loaded / event.total);
        updateUploadNotice();
      };
      xhr.upload.onload = () => {
        upload.title = 'Saving files…';
        upload.percent = null;
        updateUploadNotice();
      };
      xhr.onload = () => resolve({
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        json: async () => JSON.parse(xhr.responseText)
      });
      xhr.onerror = () => reject(new Error('The upload connection was interrupted.'));
      xhr.onabort = () => reject(new Error('The upload was cancelled.'));
      xhr.send(formData);
    }).finally(() => finishUploadNotice(upload));
  };

  window.addEventListener('pageshow', (event) => {
    if (!event.persisted) return;
    pendingUploads.clear();
    updateUploadNotice();
  });

  /* ==========================================================
   * Filter State Memory (Keeps all applied filters in memory)
   * ========================================================== */
  const FilterMemory = {
    get(key) {
      try {
        const sessionVal = sessionStorage.getItem('focus_filter_' + key);
        if (sessionVal !== null) return sessionVal;
        return localStorage.getItem('focus_filter_' + key);
      } catch (e) {
        return null;
      }
    },
    getJSON(key) {
      const raw = this.get(key);
      if (!raw) return null;
      try {
        return JSON.parse(raw);
      } catch (e) {
        return null;
      }
    },
    set(key, val) {
      try {
        if (val === null || val === undefined || val === '') {
          this.remove(key);
          return;
        }
        const s = typeof val === 'string' ? val : JSON.stringify(val);
        sessionStorage.setItem('focus_filter_' + key, s);
        localStorage.setItem('focus_filter_' + key, s);
      } catch (e) {}
    },
    remove(key) {
      try {
        sessionStorage.removeItem('focus_filter_' + key);
        localStorage.removeItem('focus_filter_' + key);
      } catch (e) {}
    }
  };

  window.FilterMemory = FilterMemory;

  const initTasksFilterMemory = () => {
    const isTasksPage = window.location.pathname === '/tasks';
    const params = new URLSearchParams(window.location.search);

    if (isTasksPage) {
      if (params.has('clear')) {
        FilterMemory.remove('tasks');
        const cleanParams = new URLSearchParams(window.location.search);
        cleanParams.delete('clear');
        const searchStr = cleanParams.toString();
        window.history.replaceState(null, '', '/tasks' + (searchStr ? '?' + searchStr : ''));
      } else {
        const filterKeys = ['query', 'category', 'status', 'priority', 'sort'];
        const activeParams = new URLSearchParams();
        let hasCustomFilter = false;

        filterKeys.forEach((k) => {
          const val = params.get(k);
          if (val !== null && val.trim() !== '') {
            activeParams.set(k, val.trim());
            if (k === 'sort' && val.trim() !== 'due') hasCustomFilter = true;
            if (k !== 'sort') hasCustomFilter = true;
          }
        });

        if (hasCustomFilter) {
          FilterMemory.set('tasks', activeParams.toString());
        } else if (params.toString() !== '' && !hasCustomFilter) {
          FilterMemory.remove('tasks');
        } else if (!window.location.search) {
          const saved = FilterMemory.get('tasks');
          if (saved && saved.trim() !== '') {
            window.location.replace('/tasks?' + saved);
            return;
          }
        }
      }
    }
  };

  const initBoardFilterMemory = () => {
    const isBoardPage = window.location.pathname === '/board';
    const params = new URLSearchParams(window.location.search);

    if (isBoardPage) {
      if (params.has('clear')) {
        FilterMemory.remove('board_category');
      } else if (params.has('category')) {
        const cat = params.get('category');
        if (cat && cat.trim() !== '') {
          FilterMemory.set('board_category', cat.trim());
        } else {
          FilterMemory.remove('board_category');
        }
      } else {
        const savedCat = FilterMemory.get('board_category');
        if (savedCat && savedCat.trim() !== '') {
          params.set('category', savedCat.trim());
          window.location.replace('/board?' + params.toString());
          return;
        }
      }
    }
  };

  const initHistoryFilterMemory = () => {
    const isHistoryPage = window.location.pathname === '/history';
    const params = new URLSearchParams(window.location.search);

    if (isHistoryPage) {
      if (params.has('clear')) {
        FilterMemory.remove('history');
        const view = params.get('view');
        window.history.replaceState(null, '', '/history' + (view ? '?view=' + view : ''));
      } else {
        const filterKeys = ['query', 'category', 'from', 'to', 'action', 'view'];
        const activeParams = new URLSearchParams();
        let hasFilter = false;

        filterKeys.forEach((k) => {
          const val = params.get(k);
          if (val !== null && val.trim() !== '') {
            activeParams.set(k, val.trim());
            if (k !== 'view') hasFilter = true;
          }
        });

        if (hasFilter) {
          FilterMemory.set('history', activeParams.toString());
        } else if (params.toString() !== '' && !hasFilter && !params.has('view')) {
          FilterMemory.remove('history');
        } else if (!window.location.search) {
          const saved = FilterMemory.get('history');
          if (saved && saved.trim() !== '') {
            window.location.replace('/history?' + saved);
            return;
          }
        }
      }
    }
  };

  const initCategoriesFilterMemory = () => {
    const isCategoriesPage = window.location.pathname === '/categories';
    const params = new URLSearchParams(window.location.search);

    if (isCategoriesPage) {
      if (params.has('clear')) {
        FilterMemory.remove('categories');
      } else if (params.has('filter')) {
        const f = params.get('filter');
        if (f === 'active' || f === 'archived') {
          FilterMemory.set('categories', f);
        } else {
          FilterMemory.remove('categories');
        }
      } else if (!params.has('edit')) {
        const saved = FilterMemory.get('categories');
        if (saved === 'active' || saved === 'archived') {
          window.location.replace('/categories?filter=' + encodeURIComponent(saved));
          return;
        }
      }
    }
  };

  const initAnalyticsFilterMemory = () => {
    const isAnalyticsPage = window.location.pathname === '/analytics';
    const params = new URLSearchParams(window.location.search);

    if (isAnalyticsPage) {
      if (params.has('clear')) {
        FilterMemory.remove('analytics');
      } else if (params.has('period')) {
        const p = params.get('period');
        if (p && p !== 'week') {
          const saveObj = { period: p };
          if (p === 'custom') {
            saveObj.from = params.get('from') || '';
            saveObj.to = params.get('to') || '';
          }
          FilterMemory.set('analytics', saveObj);
        } else {
          FilterMemory.remove('analytics');
        }
      } else {
        const saved = FilterMemory.getJSON('analytics');
        if (saved && saved.period && saved.period !== 'week') {
          let target = '/analytics?period=' + encodeURIComponent(saved.period);
          if (saved.period === 'custom' && saved.from && saved.to) {
            target += '&from=' + encodeURIComponent(saved.from) + '&to=' + encodeURIComponent(saved.to);
          }
          window.location.replace(target);
          return;
        }
      }
    }
  };

  const updateNavigationLinksWithFilterMemory = () => {
    // Tasks
    const savedTasks = FilterMemory.get('tasks');
    if (savedTasks && savedTasks.trim() !== '') {
      document.querySelectorAll('a[href="/tasks"], a[href^="/tasks?"]').forEach((link) => {
        if (link.closest('.sidebar-nav') || link.closest('.tasks-view') || link.classList.contains('nav-link')) {
          link.href = '/tasks?' + savedTasks;
        }
      });
    }

    // Board
    const savedBoardCat = FilterMemory.get('board_category');
    if (savedBoardCat && savedBoardCat.trim() !== '') {
      document.querySelectorAll('a[href="/board"]').forEach((link) => {
        if (link.closest('.sidebar-nav') || link.closest('.tasks-view')) {
          link.href = '/board?category=' + encodeURIComponent(savedBoardCat);
        }
      });
    }

    // History
    const savedHistory = FilterMemory.get('history');
    if (savedHistory && savedHistory.trim() !== '') {
      document.querySelectorAll('a[href="/history"]').forEach((link) => {
        if (link.closest('.sidebar-nav')) {
          link.href = '/history?' + savedHistory;
        }
      });
    }

    // Categories
    const savedCatFilter = FilterMemory.get('categories');
    if (savedCatFilter && savedCatFilter !== 'all') {
      document.querySelectorAll('a[href="/categories"]').forEach((link) => {
        if (link.closest('.sidebar-nav')) {
          link.href = '/categories?filter=' + encodeURIComponent(savedCatFilter);
        }
      });
    }

    // Analytics
    const savedAnalytics = FilterMemory.getJSON('analytics');
    if (savedAnalytics && savedAnalytics.period && savedAnalytics.period !== 'week') {
      let target = '/analytics?period=' + encodeURIComponent(savedAnalytics.period);
      if (savedAnalytics.period === 'custom' && savedAnalytics.from && savedAnalytics.to) {
        target += '&from=' + encodeURIComponent(savedAnalytics.from) + '&to=' + encodeURIComponent(savedAnalytics.to);
      }
      document.querySelectorAll('a[href="/analytics"]').forEach((link) => {
        if (link.closest('.sidebar-nav')) {
          link.href = target;
        }
      });
    }
  };

  document.querySelectorAll('[data-clear-filter]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const key = btn.dataset.clearFilter;
      if (key) {
        FilterMemory.remove(key);
      }
    });
  });

  initTasksFilterMemory();
  initBoardFilterMemory();
  initHistoryFilterMemory();
  initCategoriesFilterMemory();
  initAnalyticsFilterMemory();
  updateNavigationLinksWithFilterMemory();

  const scrollDashboardCalendar = () => {
    const monthCalendar = document.getElementById('dashboardCalendarWeeks');
    if (!monthCalendar) return;
    const openingWeek = monthCalendar.querySelector('[data-opening-week]');
    if (openingWeek) monthCalendar.scrollTop = openingWeek.offsetTop;
  };
  scrollDashboardCalendar();

  // Dashboard Calendar Navigation without full page refresh
  document.addEventListener('click', async (event) => {
    const link = event.target.closest('.week-strip-card a.week-day-number, .week-strip-card a.week-nav-btn, .week-strip-header a[title="Go to today"]');
    if (!link) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

    const href = link.getAttribute('href');
    if (!href || (!href.startsWith('/dashboard') && !href.startsWith('/?') && href !== '/')) return;

    event.preventDefault();
    try {
      const response = await fetch(href, {
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      });
      if (!response.ok) {
        window.location.href = href;
        return;
      }
      const html = await response.text();
      const doc = new DOMParser().parseFromString(html, 'text/html');

      const newWeekCard = doc.querySelector('.week-strip-card');
      const newMetrics = doc.querySelector('.dashboard-metrics-grid');
      const newTodaysWork = doc.querySelector('.todays-work-card');

      const currentWeekCard = document.querySelector('.week-strip-card');
      const currentMetrics = document.querySelector('.dashboard-metrics-grid');
      const currentTodaysWork = document.querySelector('.todays-work-card');

      if (newWeekCard && currentWeekCard) currentWeekCard.outerHTML = newWeekCard.outerHTML;
      if (newMetrics && currentMetrics) currentMetrics.outerHTML = newMetrics.outerHTML;
      if (newTodaysWork && currentTodaysWork) currentTodaysWork.outerHTML = newTodaysWork.outerHTML;

      window.history.pushState({ path: href }, '', href);
      scrollDashboardCalendar();
    } catch (err) {
      window.location.href = href;
    }
  });

  window.addEventListener('popstate', () => {
    window.location.reload();
  });

  const taskEditor = document.querySelector('.task-editor');
  if (taskEditor) {
    const description = taskEditor.querySelector('#description');
    const counter = taskEditor.querySelector('#task-description-count');
    const plannedDate = taskEditor.querySelector('#plannedDate');
    const deadline = taskEditor.querySelector('#deadline');
    description.addEventListener('input', () => {
      counter.textContent = `${description.value.length} / ${description.maxLength}`;
    });
    plannedDate.addEventListener('change', () => {
      deadline.min = plannedDate.value;
    });
  }


  const formatFileSize = (bytes) => {
    if (bytes < 1024) return `${bytes} B`;
    const units = ['KB', 'MB', 'GB', 'TB'];
    let unit = 0;
    let size = bytes / 1024;
    while (size >= 1024 && unit < units.length - 1) {
      size /= 1024;
      unit += 1;
    }
    return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(size)} ${units[unit]}`;
  };

  const selectedPreviewTemplate = document.getElementById('selectedFilePreviewButton');
  const selectedPreviewUrls = new WeakMap();
  const selectedPreviewTypes = new Set([
    'application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/avif', 'image/bmp',
    'audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/mp4', 'video/mp4', 'video/webm', 'video/ogg'
  ]);
  const selectedTextTypes = new Set([
    'text/plain', 'text/csv', 'text/markdown', 'text/tab-separated-values', 'application/json'
  ]);

  function configureSelectedPreview(button, file) {
    const type = file && (selectedTextTypes.has(file.type) ? 'text/plain' : file.type);
    const canPreview = type === 'text/plain' || selectedPreviewTypes.has(type);
    button.hidden = !canPreview;
    button.onclick = null;
    if (!canPreview) return;
    button.title = 'Preview file';
    button.setAttribute('aria-label', `Preview ${file.name} in a new tab`);
    button.onclick = () => {
      let url = selectedPreviewUrls.get(file);
      if (!url) {
        const preview = type === file.type ? file : file.slice(0, file.size, type);
        url = URL.createObjectURL(preview);
        selectedPreviewUrls.set(file, url);
      }
      window.open(url, '_blank', 'noopener,noreferrer');
    };
  }

  function addSelectedPreview(file, before) {
    const button = selectedPreviewTemplate.content.firstElementChild.cloneNode(true);
    configureSelectedPreview(button, file);
    if (!button.hidden) before.before(button);
  }

  const getFileBadge = (file) => {
    if (!file) return 'FILE';
    const name = file.name || file.originalName || '';
    const type = (file.type || file.contentType || '').toLowerCase();
    const extension = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
    if (extension === 'pdf' || type === 'application/pdf') return 'PDF';
    if (type.startsWith('image/') || /^(png|jpe?g|webp|gif|svg|avif|heic|heif|bmp|tiff?|ico)$/.test(extension)) return 'IMG';
    if (/^(zip|rar|7z|tar|gz|bz2|xz|tgz|dmg|iso)$/.test(extension)) return 'ZIP';
    if (/^(docx?|odt|rtf)$/.test(extension)) return 'DOC';
    if (extension === 'csv') return 'CSV';
    if (/^(xlsx?|ods)$/.test(extension)) return 'XLS';
    if (/^(pptx?|odp)$/.test(extension)) return 'PPT';
    if (type.startsWith('text/') || /^(txt|md|json|xml|log|sql|java|py|js|ts|css|html)$/.test(extension)) return 'TXT';
    return 'FILE';
  };

  document.querySelectorAll('.timeline-file-chip').forEach((chip) => {
    const type = getFileBadge({ name: chip.dataset.filename, type: chip.dataset.contentType });
    chip.dataset.fileType = type;
    const icon = chip.querySelector('.timeline-file-badge use');
    const symbol = { XLS: 'grid', CSV: 'grid', IMG: 'image', ZIP: 'inbox' }[type] || 'file-text';
    icon.setAttribute('href', icon.getAttribute('href').replace(/#.*$/, '#' + symbol));
  });

  document.querySelectorAll('.task-editor-file-type').forEach((badge) => {
    const filename = badge.dataset.filename || '';
    const contentType = badge.dataset.contentType || '';
    const type = getFileBadge({ name: filename, type: contentType });
    badge.dataset.fileType = type;
    const icon = badge.querySelector('.supporting-file-icon');
    if (icon) {
      icon.dataset.fileType = type;
      const text = icon.querySelector('.supporting-file-type');
      if (text) text.textContent = type;
    }
  });

  if (typeof DataTransfer === 'function') {
    document.querySelectorAll('[data-supporting-files]').forEach((picker) => {
      const input = picker.querySelector('input');
      const upload = input.id === 'attachmentFile'
        ? document.getElementById('taskEditorDropzone') || picker.querySelector('.supporting-files-upload')
        : picker.querySelector('.supporting-files-upload');
      if (!upload) return;
      const list = picker.querySelector('ul');
      const template = picker.querySelector('template');
      let selected = Array.from(input.files);

      const setFiles = (files) => {
        const transfer = new DataTransfer();
        files.forEach((file) => transfer.items.add(file));
        input.files = transfer.files;
        selected = Array.from(input.files);
      };

      const render = () => {
        list.replaceChildren();
        list.hidden = selected.length === 0;
        selected.forEach((file, index) => {
          const row = template.content.cloneNode(true);
          const badge = getFileBadge(file);
          const icon = row.querySelector('.supporting-file-icon');
          if (icon) icon.dataset.fileType = badge;
          const text = row.querySelector('.supporting-file-type');
          if (text) text.textContent = badge;
          row.querySelector('.supporting-file-name').textContent = file.name;
          row.querySelector('.supporting-file-size').textContent = formatFileSize(file.size) + (input.id === 'attachmentFile' ? ' · Added just now' : '');
          const remove = row.querySelector('.supporting-file-remove');
          addSelectedPreview(file, remove);
          remove.setAttribute('aria-label', `Remove ${file.name}`);
          remove.addEventListener('click', () => {
            setFiles(selected.filter((_, fileIndex) => fileIndex !== index));
            render();
            const next = list.querySelectorAll('.supporting-file-remove')[Math.min(index, selected.length - 1)];
            (next || upload).focus();
          });
          list.appendChild(row);
        });
      };

      upload.addEventListener('click', () => input.click());
      if (upload.id === 'taskEditorDropzone' || upload.hasAttribute('data-file-drop') || upload.classList.contains('file-dropzone')) {
        upload.addEventListener('dragover', (event) => {
          if (!event.dataTransfer.types.includes('Files')) return;
          event.preventDefault();
          event.dataTransfer.dropEffect = 'copy';
          upload.classList.add('is-dragover');
        });
        upload.addEventListener('dragleave', () => upload.classList.remove('is-dragover'));
        upload.addEventListener('drop', (event) => {
          event.preventDefault();
          upload.classList.remove('is-dragover');
          if (!event.dataTransfer.files.length) return;
          setFiles([...selected, ...event.dataTransfer.files]);
          render();
        });
      }

      input.addEventListener('change', () => {
        setFiles([...selected, ...input.files]);
        render();
      });
      input.form?.addEventListener('reset', () => {
        queueMicrotask(() => {
          selected = Array.from(input.files);
          render();
        });
      });

      input.hidden = true;
      upload.hidden = false;
      render();
    });
  }

  // Direct Task Attachments with File Uploading Pop-up and Cancel Option
  const taskAttachmentDropzone = document.getElementById('taskAttachmentDropzone');
  const taskAttachmentInput = document.getElementById('taskAttachmentInput');
  const taskAttachmentForm = document.getElementById('taskAttachmentUploadForm');
  const fileUploadModalEl = document.getElementById('fileUploadModal');

  if (taskAttachmentDropzone && taskAttachmentInput && taskAttachmentForm && fileUploadModalEl && typeof bootstrap !== 'undefined') {
    const uploadModal = bootstrap.Modal.getOrCreateInstance(fileUploadModalEl);
    const modalTitle = document.getElementById('fileUploadModalTitle');
    const countEl = document.getElementById('fileUploadCount');
    const totalSizeEl = document.getElementById('fileUploadTotalSize');
    const listEl = document.getElementById('fileUploadList');
    const progressSection = document.getElementById('fileUploadProgressSection');
    const progressBar = document.getElementById('fileUploadProgressBar');
    const percentEl = document.getElementById('fileUploadPercent');
    const statusTextEl = document.getElementById('fileUploadStatusText');
    const errorAlert = document.getElementById('fileUploadErrorAlert');
    const errorMessage = document.getElementById('fileUploadErrorMessage');
    const submitBtn = document.getElementById('fileUploadSubmitBtn');
    const cancelBtn = document.getElementById('fileUploadCancelBtn');
    const closeBtn = document.getElementById('fileUploadModalCloseBtn');

    let activeXhr = null;

    const resetModalState = ({ clearFiles = true } = {}) => {
      if (activeXhr) {
        try { activeXhr.abort(); } catch (e) {}
        activeXhr = null;
      }
      if (clearFiles) taskAttachmentInput.value = '';
      if (progressSection) progressSection.style.display = 'none';
      if (progressBar) {
        progressBar.style.width = '0%';
        progressBar.className = 'progress-bar progress-bar-striped progress-bar-animated bg-primary';
      }
      progressBar?.parentElement.setAttribute('aria-valuenow', '0');
      if (closeBtn) closeBtn.disabled = false;
      if (percentEl) percentEl.textContent = '0%';
      if (errorAlert) errorAlert.style.display = 'none';
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.style.display = '';
      }
      if (cancelBtn) {
        cancelBtn.disabled = false;
        cancelBtn.style.display = '';
        cancelBtn.innerHTML = '<svg class="btn-icon me-1" style="width:0.9rem;height:0.9rem;"><use href="/images/icons.svg#x"/></svg> Cancel upload';
      }
    };

    const showUploadModal = (files) => {
      if (!files || !files.length) return;
      resetModalState({ clearFiles: false });

      let totalBytes = 0;
      listEl.innerHTML = '';
      Array.from(files).forEach((file) => {
        totalBytes += file.size;
        const li = document.createElement('li');
        li.className = 'list-group-item d-flex justify-content-between align-items-center py-2 px-3 small';
        li.innerHTML = `
          <div class="d-flex align-items-center text-truncate me-2">
            <svg class="btn-icon me-2 text-primary" style="width:1rem;height:1rem;flex-shrink:0;"><use href="/images/icons.svg#file-text"/></svg>
            <span class="text-truncate fw-medium text-body"></span>
          </div>
          <span class="badge bg-light text-secondary border font-monospace ms-auto" style="flex-shrink:0;">${formatFileSize(file.size)}</span>
        `;
        li.querySelector('.text-truncate.fw-medium').textContent = file.name;
        addSelectedPreview(file, li.lastElementChild);
        listEl.appendChild(li);
      });

      countEl.textContent = `${files.length} file${files.length > 1 ? 's' : ''} selected`;
      totalSizeEl.textContent = formatFileSize(totalBytes);
      modalTitle.textContent = `Upload ${files.length} File${files.length > 1 ? 's' : ''}`;
      uploadModal.show();
    };

    const cancelAndClose = () => {
      const wasUploading = activeXhr !== null;
      resetModalState();
      uploadModal.hide();
      if (wasUploading && window.showToast) {
        window.showToast('File upload cancelled.', 'info');
      }
    };

    cancelBtn?.addEventListener('click', cancelAndClose);
    closeBtn?.addEventListener('click', cancelAndClose);

    submitBtn?.addEventListener('click', () => {
      const files = taskAttachmentInput.files;
      if (!files || !files.length) return;

      submitBtn.disabled = true;
      if (errorAlert) errorAlert.style.display = 'none';
      if (progressSection) progressSection.style.display = '';
      if (statusTextEl) statusTextEl.textContent = 'Uploading files…';
      if (cancelBtn) {
        cancelBtn.innerHTML = '<svg class="btn-icon me-1" style="width:0.9rem;height:0.9rem;"><use href="/images/icons.svg#x"/></svg> Cancel upload';
      }

      const formData = new FormData(taskAttachmentForm);
      const xhr = new XMLHttpRequest();
      activeXhr = xhr;

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const percent = Math.min(100, Math.floor((e.loaded / e.total) * 100));
          if (progressBar) progressBar.style.width = `${percent}%`;
          progressBar?.parentElement.setAttribute('aria-valuenow', String(percent));
          if (percentEl) percentEl.textContent = `${percent}%`;
        }
      };

      xhr.upload.onload = () => {
        if (progressBar) progressBar.style.width = '100%';
        progressBar?.parentElement.setAttribute('aria-valuenow', '100');
        if (percentEl) percentEl.textContent = '100%';
        if (statusTextEl) statusTextEl.textContent = 'Saving files…';
        if (cancelBtn) cancelBtn.disabled = true;
        if (closeBtn) closeBtn.disabled = true;
      };

      const showUploadError = (message) => {
        activeXhr = null;
        submitBtn.disabled = false;
        if (cancelBtn) cancelBtn.disabled = false;
        if (closeBtn) closeBtn.disabled = false;
        if (progressSection) progressSection.style.display = 'none';
        if (progressBar) progressBar.style.width = '0%';
        progressBar?.parentElement.setAttribute('aria-valuenow', '0');
        if (percentEl) percentEl.textContent = '0%';
        if (errorAlert) errorAlert.style.display = '';
        if (errorMessage) errorMessage.textContent = message;
      };

      xhr.onload = () => {
        activeXhr = null;
        if (xhr.status >= 200 && xhr.status < 300 && xhr.response?.success) {
          if (progressBar) {
            progressBar.style.width = '100%';
            progressBar.className = 'progress-bar bg-success';
          }
          if (percentEl) percentEl.textContent = '100%';
          if (statusTextEl) statusTextEl.textContent = 'Upload completed!';
          if (cancelBtn) cancelBtn.style.display = 'none';
          if (submitBtn) submitBtn.style.display = 'none';
          if (window.showToast) window.showToast('Files uploaded successfully.', 'success');
          setTimeout(() => {
            uploadModal.hide();
            window.location.reload();
          }, 600);
        } else {
          showUploadError(xhr.response?.error || `Upload failed (HTTP ${xhr.status}). Please try again.`);
        }
      };

      xhr.onerror = () => showUploadError('The connection was interrupted. Check the task before retrying.');

      xhr.onabort = () => {
        activeXhr = null;
      };

      const csrf = taskAttachmentForm.querySelector('input[name="_csrf"]')?.value;
      xhr.open('POST', taskAttachmentForm.action);
      xhr.responseType = 'json';
      if (csrf) xhr.setRequestHeader('X-CSRF-TOKEN', csrf);
      xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
      xhr.send(formData);
    });

    taskAttachmentInput.classList.add('d-none');
    taskAttachmentDropzone.hidden = false;
    taskAttachmentDropzone.addEventListener('click', () => taskAttachmentInput.click());
    taskAttachmentDropzone.addEventListener('dragover', (event) => {
      if (!event.dataTransfer.types.includes('Files')) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
      taskAttachmentDropzone.classList.add('is-dragover');
    });
    taskAttachmentDropzone.addEventListener('dragleave', () => {
      taskAttachmentDropzone.classList.remove('is-dragover');
    });
    taskAttachmentDropzone.addEventListener('drop', (event) => {
      event.preventDefault();
      taskAttachmentDropzone.classList.remove('is-dragover');
      if (!event.dataTransfer.files.length) return;
      if (typeof DataTransfer === 'function') {
        const transfer = new DataTransfer();
        Array.from(event.dataTransfer.files).forEach((file) => transfer.items.add(file));
        taskAttachmentInput.files = transfer.files;
      }
      showUploadModal(taskAttachmentInput.files);
    });
    taskAttachmentInput.addEventListener('change', () => {
      if (taskAttachmentInput.files && taskAttachmentInput.files.length) {
        showUploadModal(taskAttachmentInput.files);
      }
    });
  }


  // Global Toast & Undo Notification System
  let activeUndoHandler = null;

  window.showToast = function(message, type = 'success', options = {}) {
    let stack = document.querySelector('.flash-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.className = 'flash-stack';
      stack.setAttribute('aria-live', 'polite');
      document.body.appendChild(stack);
    }

    const {
      actionText = null,
      onAction = null,
      duration = actionText ? 6000 : 3500,
      dismissible = true
    } = options;

    const toast = document.createElement('div');
    toast.className = `undo-toast undo-toast-${type}`;
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
    toast.style.setProperty('--undo-duration', `${duration}ms`);

    let iconSvg = '';
    if (type === 'error') {
      iconSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;
    } else {
      iconSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>`;
    }

    let actionButtonHtml = '';
    if (actionText && typeof onAction === 'function') {
      actionButtonHtml = `
        <button type="button" class="btn-undo-action" aria-label="${actionText} action">
          <svg class="undo-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M3 7v6h6"/>
            <path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/>
          </svg>
          <span>${actionText}</span>
        </button>
      `;
    }

    let closeButtonHtml = '';
    if (dismissible) {
      closeButtonHtml = `
        <button type="button" class="undo-toast-close" aria-label="Dismiss notification">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      `;
    }

    const progressBarHtml = duration > 0 ? `<div class="undo-toast-progress-bar" aria-hidden="true"></div>` : '';

    toast.innerHTML = `
      <div class="undo-toast-body">
        <div class="undo-toast-icon-wrap" aria-hidden="true">${iconSvg}</div>
        <div class="undo-toast-message">
          <span class="undo-toast-text">${message}</span>
        </div>
      </div>
      <div class="undo-toast-actions">
        ${actionButtonHtml}
        ${closeButtonHtml}
      </div>
      ${progressBarHtml}
    `;

    stack.appendChild(toast);

    let dismissTimer = null;
    let remainingTime = duration;
    let startTime = Date.now();

    const startTimer = () => {
      if (duration <= 0) return;
      startTime = Date.now();
      dismissTimer = setTimeout(dismiss, remainingTime);
    };

    const pauseTimer = () => {
      if (dismissTimer) {
        clearTimeout(dismissTimer);
        dismissTimer = null;
        remainingTime -= (Date.now() - startTime);
      }
    };

    const resumeTimer = () => {
      if (remainingTime > 0) {
        startTimer();
      } else {
        dismiss();
      }
    };

    const dismiss = () => {
      if (activeUndoHandler === executeAction) {
        activeUndoHandler = null;
      }
      toast.classList.add('is-leaving');
      setTimeout(() => {
        toast.remove();
      }, 150);
    };

    const executeAction = async () => {
      const btn = toast.querySelector('.btn-undo-action');
      if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span>Undoing…</span>`;
      }
      pauseTimer();
      try {
        await onAction(toast);
      } catch (err) {
        console.error('Undo action failed:', err);
      } finally {
        dismiss();
      }
    };

    if (actionText && typeof onAction === 'function') {
      activeUndoHandler = executeAction;
      const undoBtn = toast.querySelector('.btn-undo-action');
      undoBtn?.addEventListener('click', (e) => {
        e.preventDefault();
        executeAction();
      });
    }

    toast.querySelector('.undo-toast-close')?.addEventListener('click', (e) => {
      e.preventDefault();
      dismiss();
    });

    toast.addEventListener('mouseenter', pauseTimer);
    toast.addEventListener('mouseleave', resumeTimer);

    startTimer();

    return {
      dismiss,
      toast
    };
  };

  window.showUndoToast = function(message, onUndo, options = {}) {
    return window.showToast(message, options.type || 'success', {
      ...options,
      actionText: options.actionText || 'Undo',
      onAction: onUndo
    });
  };

  // Keyboard shortcut: Cmd+Z / Ctrl+Z triggers active undo action
  document.addEventListener('keydown', (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'z' && !event.shiftKey) {
      if (activeUndoHandler) {
        const tag = document.activeElement?.tagName;
        if (tag !== 'INPUT' && tag !== 'TEXTAREA') {
          event.preventDefault();
          activeUndoHandler();
        }
      }
    }
  });

  // Automatically convert any server-rendered flash messages into sleek popups
  document.querySelectorAll('.flash-stack .alert').forEach((alert) => {
    const text = alert.querySelector('span')?.textContent?.trim() || alert.textContent.trim();
    const isError = alert.classList.contains('alert-danger');
    const deletedTaskId = alert.dataset.deletedTaskId;
    const deletedTaskName = alert.dataset.deletedTaskName;
    alert.remove();

    if (text) {
      if (deletedTaskId) {
        const taskLabel = deletedTaskName ? `Task "${deletedTaskName}"` : 'Task';
        window.showToast(`${taskLabel} deleted`, 'success', {
          actionText: 'Undo',
          duration: 7000,
          onAction: async () => {
            try {
              const token = getCsrfToken();
              const res = await fetch(`/tasks/${deletedTaskId}/undo-delete`, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded',
                  'Accept': 'application/json',
                  'X-Requested-With': 'XMLHttpRequest',
                  'X-CSRF-TOKEN': token || ''
                },
                body: token ? `_csrf=${encodeURIComponent(token)}` : ''
              });

              let data = null;
              try {
                data = await res.json();
              } catch (_) {}

              if (res.ok && (data?.success !== false)) {
                window.showToast(data?.message || `${taskLabel} restored successfully`, 'success');
                setTimeout(() => window.location.reload(), 600);
              } else {
                window.showToast(data?.message || 'Could not restore task', 'error');
              }
            } catch (err) {
              console.error('Undo delete task failed:', err);
              window.showToast('Could not restore task', 'error');
            }
          }
        });
      } else {
        window.showToast(text, isError ? 'error' : 'success', {
          duration: 4500,
          dismissible: true
        });
      }
    }
  });

  // Enterprise Accessible Confirmation Dialog (Delete & Modify)
  window.focusConfirm = function({ title, message, proceedLabel = 'Proceed', isDestructive = true }) {
    return new Promise((resolve) => {
      const modalEl = document.getElementById('focusConfirmModal');
      if (!modalEl || typeof bootstrap === 'undefined') {
        resolve(window.confirm(message || 'Are you sure?'));
        return;
      }

      const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
      const titleEl = document.getElementById('focusConfirmModalTitle');
      const msgEl = document.getElementById('focusConfirmModalMessage');
      const proceedBtn = document.getElementById('focusConfirmProceedBtn');
      const cancelBtn = document.getElementById('focusConfirmCancelBtn');
      const iconBadge = document.getElementById('focusConfirmIconBadge');

      if (titleEl) titleEl.textContent = title || (isDestructive ? 'Delete Confirmation' : 'Confirm Modification');
      if (msgEl) msgEl.textContent = message || 'Are you sure you want to proceed?';
      if (proceedBtn) {
        proceedBtn.textContent = proceedLabel;
        proceedBtn.className = isDestructive ? 'btn btn-danger btn-sm px-3' : 'btn btn-primary btn-sm px-3';
      }

      if (iconBadge) {
        if (isDestructive) {
          iconBadge.className = 'confirm-icon-badge mx-auto mb-3';
          iconBadge.innerHTML = `
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          `;
        } else {
          iconBadge.className = 'confirm-icon-badge confirm-icon-modify mx-auto mb-3';
          iconBadge.innerHTML = `
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          `;
        }
      }

      let confirmed = false;

      const handleProceed = () => {
        confirmed = true;
        modal.hide();
        resolve(true);
      };

      const handleHidden = () => {
        proceedBtn?.removeEventListener('click', handleProceed);
        modalEl.removeEventListener('hidden.bs.modal', handleHidden);
        if (!confirmed) resolve(false);
      };

      proceedBtn?.addEventListener('click', handleProceed, { once: true });
      modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });

      modal.show();
      cancelBtn?.focus();
    });
  };

  document.querySelectorAll('[data-confirm]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      if (form.dataset.confirmed === 'true') return;
      event.preventDefault();
      const message = form.dataset.confirm;
      const lower = message.toLowerCase();
      const isDel = lower.includes('delete') || lower.includes('permanently') || lower.includes('remove');
      const isMod = lower.includes('modify') || lower.includes('save') || lower.includes('changes') || lower.includes('update') || lower.includes('edit');

      let title = isDel ? 'Confirm Delete' : (isMod ? 'Confirm Modification' : 'Confirm Action');
      let proceedLabel = isDel ? 'Delete' : (isMod ? 'Save changes' : 'Proceed');
      let isDestructive = isDel || lower.includes('replace') || lower.includes('restore');

      const confirmed = await window.focusConfirm({
        title,
        message,
        proceedLabel,
        isDestructive
      });
      if (confirmed) {
        form.dataset.confirmed = 'true';
        const action = form.getAttribute('action') || '';
        const row = form.closest('tr');
        if (action.match(/\/tasks\/\d+\/delete$/) && row) {
          const taskId = action.match(/\/tasks\/(\d+)\/delete$/)[1];
          const taskName = row.querySelector('.tasks-name a, .task-title-link')?.textContent?.trim() || 'Task';
          const csrf = form.querySelector('input[name="_csrf"]')?.value || csrfToken;

          try {
            const formData = new URLSearchParams();
            if (csrf) formData.append('_csrf', csrf);

            const res = await fetch(action, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
                'X-CSRF-TOKEN': csrf || ''
              },
              body: formData.toString()
            });

            if (!res.ok) throw new Error('Delete failed');

            row.style.transition = 'opacity 180ms ease, transform 180ms ease';
            row.style.opacity = '0';
            row.style.transform = 'scaleY(0.8)';
            setTimeout(() => { row.style.display = 'none'; }, 180);

            window.showToast(`Task "${taskName}" deleted`, 'success', {
              actionText: 'Undo',
              duration: 7000,
              onAction: async () => {
                try {
                  const token = csrf || getCsrfToken();
                  const revertRes = await fetch(`/tasks/${taskId}/undo-delete`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/x-www-form-urlencoded',
                      'Accept': 'application/json',
                      'X-Requested-With': 'XMLHttpRequest',
                      'X-CSRF-TOKEN': token || ''
                    },
                    body: token ? `_csrf=${encodeURIComponent(token)}` : ''
                  });

                  let data = null;
                  try {
                    data = await revertRes.json();
                  } catch (_) {}

                  if (revertRes.ok && (data?.success !== false)) {
                    window.showToast(data?.message || `Task "${taskName}" restored`, 'success');
                    setTimeout(() => window.location.reload(), 600);
                  } else {
                    window.showToast(data?.message || 'Could not restore task', 'error');
                  }
                } catch (err) {
                  console.error('Undo delete failed:', err);
                  window.showToast('Could not restore task', 'error');
                }
              }
            });
            return;
          } catch (err) {
            console.error('Delete task failed:', err);
          }
        }
        if (form.requestSubmit) {
          form.requestSubmit();
        } else {
          showFormUpload(form);
          form.submit();
        }
      }
    });
  });

  // Discard modifications confirmation for edit forms
  const taskForm = document.querySelector('form[action*="/tasks/save"]');
  if (taskForm) {
    let isDirty = false;
    taskForm.querySelectorAll('input:not([type="hidden"]), textarea, select').forEach((input) => {
      input.addEventListener('change', () => { isDirty = true; });
      input.addEventListener('input', () => { isDirty = true; });
    });

    taskForm.querySelectorAll('a[href*="/tasks"]').forEach((cancelBtn) => {
      cancelBtn.addEventListener('click', async (e) => {
        if (isDirty) {
          e.preventDefault();
          const discard = await window.focusConfirm({
            title: 'Discard Modifications',
            message: 'You have unsaved modifications. Are you sure you want to discard them?',
            proceedLabel: 'Discard',
            isDestructive: true
          });
          if (discard) {
            window.location.href = cancelBtn.href;
          }
        }
      });
    });
  }

  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (event.defaultPrevented) return;
    if (form.classList.contains('is-submitting')) {
      event.preventDefault();
      return;
    }
    showFormUpload(form);
    form.classList.add('is-submitting');
    form.setAttribute('aria-busy', 'true');
    form.querySelectorAll('button[type="submit"]').forEach((button) => {
      button.disabled = true;
      if (!button.dataset.originalText) button.dataset.originalText = button.textContent.trim();
      button.textContent = 'Working…';
    });
  });

  const deadline = document.querySelector('#deadline');
  const planned = document.querySelector('#plannedDate');
  planned?.addEventListener('change', () => { if (deadline) deadline.min = planned.value; });

  // Theme management
  const THEME_KEY = 'focus-theme';

  const applyTheme = (theme) => {
    const effectiveTheme = (theme === 'auto' || !theme)
      ? (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : theme;

    document.documentElement.setAttribute('data-theme', effectiveTheme);
    document.documentElement.setAttribute('data-bs-theme', effectiveTheme);

    const stored = localStorage.getItem(THEME_KEY) || 'auto';
    document.querySelectorAll('[data-theme-set]').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.themeSet === stored);
    });
  };

  const setTheme = (theme) => {
    if (theme === 'auto') {
      localStorage.removeItem(THEME_KEY);
    } else {
      localStorage.setItem(THEME_KEY, theme);
    }
    applyTheme(theme);
  };

  document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'light';
      const next = current === 'dark' ? 'light' : 'dark';
      setTheme(next);
    });
  });

  document.querySelectorAll('[data-theme-set]').forEach((btn) => {
    btn.addEventListener('click', () => {
      setTheme(btn.dataset.themeSet);
    });
  });

  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (!localStorage.getItem(THEME_KEY)) {
        applyTheme('auto');
      }
    });
  }

  if (document.querySelector('[data-workspace-reset="true"]')) {
    setTheme('auto');
  } else {
    applyTheme(localStorage.getItem(THEME_KEY) || 'auto');
  }

  // ⌘ K Search shortcut
  const searchInput = document.querySelector('#topbarSearchInput');
  document.addEventListener('keydown', (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      searchInput?.focus();
      searchInput?.select();
    }
    if (event.key === 'Escape' && document.activeElement === searchInput) {
      searchInput?.blur();
    }
  });

  // Dashboard Today's Work Tabs
  const applyTaskFilter = (filter) => {
    const taskRows = document.querySelectorAll('.todays-task-list .task-row');
    const emptyState = document.querySelector('#dashboardEmptyState');
    let visibleCount = 0;
    taskRows.forEach((row) => {
      const bucket = row.dataset.bucket;
      const status = row.dataset.status;
      let show = false;

      if (filter === 'all') {
        show = bucket === 'today';
      } else if (filter === 'inbox') {
        show = bucket === 'today' && status === 'YET_TO_START';
      } else if (filter === 'now') {
        show = bucket === 'today' && status === 'IN_PROGRESS';
      } else if (filter === 'tomorrow') {
        show = bucket === 'tomorrow';
      } else if (filter === 'nextweek') {
        show = bucket === 'nextweek';
      }

      row.style.display = show ? 'grid' : 'none';
      if (show) visibleCount++;
    });

    if (emptyState) {
      emptyState.style.display = visibleCount === 0 ? 'flex' : 'none';
    }
  };

  document.addEventListener('click', (e) => {
    const tab = e.target.closest('.work-filter-tabs .tab-pill[data-filter]');
    if (!tab) return;
    const workTabs = document.querySelectorAll('.work-filter-tabs .tab-pill[data-filter]');
    workTabs.forEach((t) => {
      t.classList.remove('active');
      t.setAttribute('aria-selected', 'false');
    });
    tab.classList.add('active');
    tab.setAttribute('aria-selected', 'true');
    applyTaskFilter(tab.dataset.filter);
    if (tab.dataset.filter && tab.dataset.filter !== 'all') {
      FilterMemory.set('dashboard_work', tab.dataset.filter);
    } else {
      FilterMemory.remove('dashboard_work');
    }
  });

  const savedWorkFilter = FilterMemory.get('dashboard_work');
  if (savedWorkFilter && savedWorkFilter !== 'all') {
    const matchingTab = document.querySelector(`.work-filter-tabs .tab-pill[data-filter="${savedWorkFilter}"]`);
    if (matchingTab) {
      const workTabs = document.querySelectorAll('.work-filter-tabs .tab-pill[data-filter]');
      workTabs.forEach((t) => {
        t.classList.remove('active');
        t.setAttribute('aria-selected', 'false');
      });
      matchingTab.classList.add('active');
      matchingTab.setAttribute('aria-selected', 'true');
      applyTaskFilter(savedWorkFilter);
    }
  }

  function parseReferenceUrl(value) {
    const candidate = value.trim();
    if (!/^https?:\/\//i.test(candidate) || /[\s\\]/u.test(candidate)) return null;
    try {
      const url = new URL(candidate);
      if (url.hostname) return url;
    } catch {
      // The input can contain a partial URL while the user is editing it.
    }
    return null;
  }

  function updateDraftUrl(group) {
    const url = parseReferenceUrl(group.querySelector('input').value);
    const open = group.querySelector('a');
    open.hidden = !url;
    if (url) open.href = url.href;
    else open.removeAttribute('href');
  }

  document.querySelectorAll('[data-draft-url]').forEach(updateDraftUrl);
  document.addEventListener('input', (event) => {
    const group = event.target.closest('[data-draft-url]');
    if (group) updateDraftUrl(group);
  });
  document.addEventListener('reset', (event) => {
    queueMicrotask(() => event.target.querySelectorAll('[data-draft-url]').forEach(updateDraftUrl));
  });

  // Dynamic Links Helper for Modals and Forms
  const setupDynamicLinks = (containerId, addBtnId, hideEmpty = false) => {
    const container = document.getElementById(containerId);
    const addBtn = document.getElementById(addBtnId);
    if (!container || !addBtn) return;

    const updateRemoveButtons = () => {
      const rows = container.querySelectorAll('.link-row');
      rows.forEach((row) => {
        const removeBtn = row.querySelector('.remove-link-btn');
        if (removeBtn) {
          removeBtn.style.display = rows.length > 1 ? 'inline-block' : 'none';
        }
      });
    };

    if (hideEmpty) {
      container.hidden = !Array.from(container.querySelectorAll('input')).some((input) => input.value);
    }

    addBtn.addEventListener('click', (e) => {
      e.preventDefault();
      const firstRow = container.querySelector('.link-row');
      if (!firstRow) return;
      if (hideEmpty && container.hidden) {
        container.hidden = false;
        firstRow.querySelector('input')?.focus();
        return;
      }
      const clone = firstRow.cloneNode(true);
      clone.querySelectorAll('input').forEach((input) => (input.value = ''));
      clone.querySelectorAll('[data-draft-url]').forEach(updateDraftUrl);
      container.appendChild(clone);
      updateRemoveButtons();
      clone.querySelector('input')?.focus();
    });

    container.addEventListener('click', (e) => {
      if (e.target.closest('.remove-link-btn')) {
        e.preventDefault();
        const row = e.target.closest('.link-row');
        const rows = container.querySelectorAll('.link-row');
        if (rows.length > 1) {
          row.remove();
          updateRemoveButtons();
        }
      }
    });

    updateRemoveButtons();
  };

  setupDynamicLinks('modalLinksContainer', 'modalAddLinkBtn');
  setupDynamicLinks('progressModalLinksContainer', 'progressModalAddLinkBtn', true);
  setupDynamicLinks('detailProgressLinksContainer', 'detailProgressAddLinkBtn', true);
  setupDynamicLinks('taskEditorLinkRows', 'taskEditorAddLink', true);

  const taskLinkRows = document.getElementById('taskEditorLinkRows');
  const taskLinkCards = document.getElementById('taskEditorLinkCards');
  if (taskLinkRows && taskLinkCards) {
    const divider = document.getElementById('taskEditorLinkDivider');
    const template = document.getElementById('taskEditorDraftLinkCard');

    const renderLinkCards = () => {
      taskLinkCards.querySelectorAll('[data-draft-link]').forEach((card) => card.remove());
      taskLinkRows.querySelectorAll('.link-row').forEach((row) => {
        const urlInput = row.querySelector('[name="linkUrls"]');
        const labelInput = row.querySelector('[name="linkLabels"]');
        const url = parseReferenceUrl(urlInput.value);
        if (!url) return;

        const label = labelInput.value.trim() || url.hostname;
        const card = template.content.cloneNode(true);
        card.querySelector('strong').textContent = label;
        card.querySelector('small').textContent = urlInput.value.trim();
        const open = card.querySelector('a');
        open.href = url.href;
        open.setAttribute('aria-label', `Open ${label} in a new tab`);
        const remove = card.querySelector('button');
        remove.setAttribute('aria-label', `Remove ${label}`);
        remove.addEventListener('click', () => {
          if (taskLinkRows.querySelectorAll('.link-row').length > 1) {
            row.querySelector('.remove-link-btn').click();
          } else {
            urlInput.value = '';
            labelInput.value = '';
            updateDraftUrl(urlInput.closest('[data-draft-url]'));
            renderLinkCards();
          }
          const focusTarget = taskLinkCards.querySelector('[data-draft-link] button') ||
            document.querySelector('[data-material-tab="links"]');
          focusTarget?.focus();
        });
        taskLinkCards.appendChild(card);
      });
      taskLinkCards.hidden = taskLinkCards.children.length === 0;
      divider.hidden = taskLinkCards.hidden;
    };

    taskLinkRows.addEventListener('input', renderLinkCards);
    taskLinkRows.addEventListener('click', (event) => {
      if (event.target.closest('.remove-link-btn')) renderLinkCards();
    });
    renderLinkCards();
  }


  const materialSwitch = document.querySelector('.task-editor-material-switch');
  if (materialSwitch) {
    const filePanel = document.getElementById('task-editor-file-panel');
    const linkPanel = document.getElementById('task-editor-link-panel');
    const buttons = materialSwitch.querySelectorAll('[data-material-tab]');

    const showPanel = (tab) => {
      filePanel.hidden = tab !== 'files';
      linkPanel.hidden = tab !== 'links';
      buttons.forEach((button) => {
        button.setAttribute('aria-pressed', String(button.dataset.materialTab === tab));
      });
    };

    buttons.forEach((button) => button.addEventListener('click', () => showPanel(button.dataset.materialTab)));
    linkPanel.addEventListener('invalid', () => showPanel('links'), true);
    materialSwitch.hidden = false;
    showPanel(document.getElementById('taskEditorLinkRows').hidden ? 'files' : 'links');
  }


  document.querySelectorAll('[data-count-for]').forEach((count) => {
    const field = document.getElementById(count.dataset.countFor);
    const refresh = () => { count.textContent = `${field.value.length} / ${field.maxLength}`; };
    field.addEventListener('input', refresh);
    field.closest('.modal').addEventListener('show.bs.modal', refresh);
    refresh();
  });

  // Global Task Progress Update Modal Helper
  window.promptProgressUpdate = function({ taskId, taskName, initialDescription, initialReminder, initialRecordedAt, milestone = false, reopenAfter, nextIteration, moveStatus, editUrl, deleteUrl, progressId, existingMaterials, redirectUrl, onComplete, onCancel }) {
    const modalEl = document.getElementById('progressUpdateModal');
    if (!modalEl || typeof bootstrap === 'undefined') {
      if (onComplete) onComplete(new FormData());
      return;
    }

    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    const form = document.getElementById('progressUpdateForm');
    const taskNameEl = document.getElementById('progressModalTaskName');
    const taskIdInput = document.getElementById('progressModalTaskId');
    const redirectUrlInput = document.getElementById('progressModalRedirectUrl');
    const descInput = document.getElementById('progressModalDescription');

    form.reset();
    descInput.value = initialDescription || '';
    descInput.required = Boolean(editUrl && (!milestone || progressId));
    document.getElementById('progressModalRequiredDetails').hidden = !descInput.required;
    document.getElementById('progressModalOptionalDetails').hidden = descInput.required;
    document.getElementById('progressModalReminder').value = initialReminder || '';

    const recordedAtInput = document.getElementById('progressModalRecordedAt');
    const setNowBtn = document.getElementById('progressModalSetNowBtn');
    if (recordedAtInput) {
      recordedAtInput.disabled = false;
      document.getElementById('progressModalDateHelp').textContent = milestone
        ? 'Correct when this milestone happened.'
        : 'Use the date and time the work happened.';
      if (initialRecordedAt) {
        recordedAtInput.value = initialRecordedAt;
      } else {
        const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        recordedAtInput.value = now;
      }
      if (setNowBtn) {
        setNowBtn.onclick = (e) => {
          e.preventDefault();
          const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
          recordedAtInput.value = now;
        };
      }
    }

    const deleteBtn = document.getElementById('progressModalDeleteBtn');
    if (deleteBtn) {
      let resolvedDeleteUrl = deleteUrl;
      if (!resolvedDeleteUrl && editUrl) {
        const match = editUrl.match(/\/tasks\/(\d+)\/progress\/(\d+)\/edit/);
        if (match) {
          resolvedDeleteUrl = `/tasks/${match[1]}/progress/${match[2]}/delete`;
        }
      }
      if (resolvedDeleteUrl) {
        deleteBtn.hidden = false;
        deleteBtn.querySelector('[data-progress-delete-label]').textContent = milestone ? 'Remove note' : 'Delete update';
        deleteBtn.onclick = (e) => {
          e.preventDefault();
          const message = milestone
            ? 'Remove the note, attachments, and links? The task milestone stays on the timeline.'
            : 'Permanently delete this progress update, its attachments, and links?';
          if (confirm(message)) {
            modal.hide();
            const deleteForm = document.createElement('form');
            deleteForm.method = 'POST';
            deleteForm.action = resolvedDeleteUrl;
            const csrf = form.querySelector('input[name="_csrf"]')?.value;
            if (csrf) {
              const csrfInput = document.createElement('input');
              csrfInput.type = 'hidden';
              csrfInput.name = '_csrf';
              csrfInput.value = csrf;
              deleteForm.appendChild(csrfInput);
            }
            document.body.appendChild(deleteForm);
            deleteForm.submit();
          }
        };
      } else {
        deleteBtn.hidden = true;
      }
    }
    const existingFiles = document.getElementById('progressModalExistingFiles');
    const existingLinks = document.getElementById('progressModalExistingLinks');
    const fileItems = existingFiles.querySelector('[data-existing-items]');
    const linkItems = existingLinks.querySelector('[data-existing-items]');
    fileItems.replaceChildren();
    linkItems.replaceChildren();

    if (editUrl && existingMaterials) {
      const fileTemplate = document.getElementById('progressModalFileRow');
      const linkTemplate = document.getElementById('progressModalLinkRow');

      const bindRemoval = (row, input, name, controls) => {
        const button = row.querySelector('[data-remove-toggle]');
        button.setAttribute('aria-label', name);
        button.addEventListener('click', () => {
          input.checked = !input.checked;
          button.setAttribute('aria-pressed', String(input.checked));
          controls.forEach((control) => { control.disabled = input.checked; });
        });
      };

      existingMaterials.querySelectorAll('[data-progress-file-id]').forEach((file) => {
        const id = file.dataset.progressFileId;
        const name = file.dataset.filename;
        const row = fileTemplate.content.cloneNode(true);
        const badge = getFileBadge({ name, type: file.dataset.contentType });
        row.querySelector('.supporting-file-icon').dataset.fileType = badge;
        row.querySelector('.supporting-file-type').textContent = badge;
        const title = row.querySelector('strong');
        const size = row.querySelector('small');
        title.textContent = name;
        title.title = name;
        size.textContent = formatFileSize(Number(file.dataset.sizeBytes));
        const download = row.querySelector('[data-download]');
        download.href = file.href;
        download.setAttribute('aria-label', `Download ${name}`);
        const preview = row.querySelector('[data-preview]');
        if (file.dataset.previewable === 'true') {
          const url = new URL(file.href);
          url.searchParams.set('preview', 'true');
          preview.href = url.href;
          preview.hidden = false;
          preview.setAttribute('aria-label', `Preview ${name}`);
        }
        const replacement = row.querySelector('[data-replacement]');
        const replace = row.querySelector('[data-replace]');
        const replacementPreview = row.querySelector('[data-replacement-preview]');
        replacement.name = `replaceFile.${id}`;
        replace.setAttribute('aria-label', `Replace ${name}`);
        replace.addEventListener('click', () => replacement.click());
        replacement.addEventListener('change', () => {
          const selected = replacement.files[0];
          preview.hidden = Boolean(selected) || file.dataset.previewable !== 'true';
          configureSelectedPreview(replacementPreview, selected);
          title.textContent = selected?.name || name;
          title.title = title.textContent;
          size.textContent = selected
            ? `${formatFileSize(selected.size)} · Replacement on save`
            : formatFileSize(Number(file.dataset.sizeBytes));
        });
        const remove = row.querySelector('[data-remove]');
        remove.name = `removeFile.${id}`;
        bindRemoval(row, remove, `Remove ${name} on save`, [replacement, replace, replacementPreview]);
        fileItems.appendChild(row);
      });

      existingMaterials.querySelectorAll('[data-progress-link-id]').forEach((link) => {
        const id = link.dataset.progressLinkId;
        const row = linkTemplate.content.cloneNode(true);
        const url = row.querySelector('[data-existing-url]');
        const label = row.querySelector('[data-existing-label]');
        const edit = row.querySelector('[data-edit]');
        const fields = row.querySelector('[data-link-fields]');
        const open = row.querySelector('[data-open]');
        const remove = row.querySelector('[data-remove]');
        url.name = `existingLinkUrl.${id}`;
        url.value = link.getAttribute('href');
        label.name = `existingLinkLabel.${id}`;
        label.value = link.querySelector('.timeline-material-label').textContent;
        open.href = link.href;
        remove.name = `removeLink.${id}`;
        const renderLink = () => {
          edit.querySelector('strong').textContent = label.value || url.value || 'Reference link';
          edit.querySelector('small').textContent = url.value;
          edit.setAttribute('aria-label', `Edit link ${label.value || url.value}`);
          const parsed = parseReferenceUrl(url.value);
          open.setAttribute('aria-label', `Open ${label.value.trim() || parsed?.hostname || 'link'} in a new tab`);
          open.hidden = !parsed;
          if (parsed) open.href = parsed.href;
          else open.removeAttribute('href');
        };
        const showFields = (show) => {
          fields.hidden = !show;
          edit.setAttribute('aria-expanded', String(show));
        };
        edit.addEventListener('click', () => {
          showFields(fields.hidden);
          if (!fields.hidden) url.focus();
        });
        url.addEventListener('input', renderLink);
        label.addEventListener('input', renderLink);
        url.addEventListener('invalid', () => showFields(true));
        bindRemoval(row, remove, `Remove link ${label.value} on save`, [url, label, edit]);
        row.querySelector('[data-remove-toggle]').addEventListener('click', () => {
          if (remove.checked) showFields(false);
        });
        renderLink();
        linkItems.appendChild(row);
      });
    }
    existingFiles.hidden = fileItems.children.length === 0;
    existingLinks.hidden = linkItems.children.length === 0;
    const status = moveStatus || '';
    const progressActions = {
      '': {
        title: 'Log progress update', icon: 'message-square', tone: 'info', context: 'Add details about the latest progress for',
        details: 'Progress details', placeholder: 'Describe the progress made or the next steps.',
        submit: 'Save progress update'
      },
      YET_TO_START: {
        title: 'Move to Yet to start', icon: 'clock', tone: 'primary', context: 'Moving to Yet to start',
        details: 'Reason for moving', placeholder: 'Describe why the task is moving to Yet to start.',
        submit: 'Move to Yet to start'
      },
      IN_PROGRESS: {
        title: 'Move to In progress', icon: 'hourglass', tone: 'warning', context: 'Moving to In progress',
        details: 'Work in progress', placeholder: 'Describe the work underway.',
        submit: 'Move to In progress'
      },
      COMPLETED: {
        title: 'Complete task', icon: 'check', tone: 'success', context: 'Completing',
        details: 'Completion notes', placeholder: 'Describe what was completed.',
        submit: 'Complete task'
      },
      BLOCKED: {
        title: 'Move to Blocked/Closed', icon: 'slash', tone: 'danger', context: 'Moving to Blocked/Closed',
        details: 'Reason for blocking / closing', placeholder: 'Describe why this task is blocked or closed...',
        submit: 'Move to Blocked/Closed'
      }
    };
    const logAction = progressActions[''];
    const action = editUrl
      ? { ...logAction, title: milestone ? 'Edit milestone' : 'Edit progress update', context: 'Editing timeline for', submit: 'Save changes' }
      : progressActions[status] || logAction;
    modalEl.dataset.progressTone = action.tone;
    document.getElementById('progressModalStatus').value = status;
    document.getElementById('progressModalTitle').textContent = action.title;
    document.getElementById('progressModalContext').textContent = action.context;
    document.getElementById('progressModalDetailsLabel').textContent = action.details;
    document.getElementById('progressModalSubmitLabel').textContent = action.submit;
    descInput.placeholder = action.placeholder;
    const icon = document.getElementById('progressModalIcon');
    const iconHref = icon.getAttribute('href').split('#')[0] + '#' + action.icon;
    icon.setAttribute('href', iconHref);

    const linksContainer = document.getElementById('progressModalLinksContainer');
    if (linksContainer) {
      linksContainer.hidden = true;
      const rows = linksContainer.querySelectorAll('.link-row');
      for (let i = 1; i < rows.length; i++) {
        rows[i].remove();
      }
      const firstRow = linksContainer.querySelector('.link-row');
      if (firstRow) {
        firstRow.querySelectorAll('input').forEach((input) => (input.value = ''));
        firstRow.querySelectorAll('[data-draft-url]').forEach(updateDraftUrl);
        const removeBtn = firstRow.querySelector('.remove-link-btn');
        if (removeBtn) removeBtn.style.display = 'none';
      }
    }
    if (taskNameEl) taskNameEl.textContent = taskName || `Task #${taskId}`;
    if (taskIdInput) taskIdInput.value = taskId;
    if (redirectUrlInput) redirectUrlInput.value = redirectUrl || (window.location.pathname + window.location.search);
    form.action = editUrl || `/tasks/${taskId}/progress`;

    let isSubmitted = false;

    const handleSubmit = async (e) => {
      e.preventDefault();
      const newDate = recordedAtInput?.value;
      if (reopenAfter && newDate && newDate !== initialRecordedAt && newDate > reopenAfter) {
        const message = `This date is after completion. Reopen as iteration ${nextIteration}? The previous timeline and its files and links stay intact. Note text and new files or links will be saved to the new iteration.`;
        if (!window.confirm(message)) return;
        document.getElementById('progressModalConfirmedCompletion').value = reopenAfter;
      }
      form.removeEventListener('submit', handleSubmit);
      isSubmitted = true;
      modal.hide();

      if (editUrl && recordedAtInput?.value === initialRecordedAt) recordedAtInput.disabled = true;
      const formData = new FormData(form);
      if (onComplete) {
        await onComplete(formData);
      } else {
        showFormUpload(form);
        form.submit();
      }
    };

    const handleHidden = () => {
      form.removeEventListener('submit', handleSubmit);
      modalEl.removeEventListener('hidden.bs.modal', handleHidden);
      if (!isSubmitted && onCancel) {
        onCancel();
      }
    };

    form.addEventListener('submit', handleSubmit);
    modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });

    if (editUrl) {
      modalEl.addEventListener('shown.bs.modal', () => descInput.focus(), { once: true });
    }
    modal.show();
  };

  // Global Task Completion Modal Helper
  window.promptTaskCompletion = function({ taskId, taskName, initialNote, redirectUrl, onComplete, onCancel }) {
    const modalEl = document.getElementById('taskCompletionModal');
    if (!modalEl || typeof bootstrap === 'undefined') {
      if (onComplete) onComplete(new FormData());
      return;
    }

    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    const form = document.getElementById('taskCompletionForm');
    const taskNameEl = document.getElementById('modalTaskName');
    const taskIdInput = document.getElementById('modalTaskId');
    const redirectUrlInput = document.getElementById('modalRedirectUrl');
    const noteInput = document.getElementById('modalCompletionNote');

    form.reset();
    if (noteInput) noteInput.value = initialNote || '';
    const modalLinksContainer = document.getElementById('modalLinksContainer');
    if (modalLinksContainer) {
      const rows = modalLinksContainer.querySelectorAll('.link-row');
      for (let i = 1; i < rows.length; i++) {
        rows[i].remove();
      }
      const firstRow = modalLinksContainer.querySelector('.link-row');
      if (firstRow) {
        firstRow.querySelectorAll('input').forEach((input) => (input.value = ''));
        firstRow.querySelectorAll('[data-draft-url]').forEach(updateDraftUrl);
        const removeBtn = firstRow.querySelector('.remove-link-btn');
        if (removeBtn) removeBtn.style.display = 'none';
      }
    }
    if (taskNameEl) taskNameEl.textContent = taskName || `Task #${taskId}`;
    if (taskIdInput) taskIdInput.value = taskId;
    if (redirectUrlInput) redirectUrlInput.value = redirectUrl || (window.location.pathname + window.location.search);
    form.action = `/tasks/${taskId}/status`;

    let isCompleted = false;

    const handleSubmit = async (e) => {
      e.preventDefault();
      isCompleted = true;
      modal.hide();

      const formData = new FormData(form);
      formData.set('status', 'COMPLETED');
      if (onComplete) {
        await onComplete(formData);
      } else {
        showFormUpload(form);
        form.submit();
      }
    };

    const handleHidden = () => {
      form.removeEventListener('submit', handleSubmit);
      modalEl.removeEventListener('hidden.bs.modal', handleHidden);
      if (!isCompleted && onCancel) {
        onCancel();
      }
    };

    form.addEventListener('submit', handleSubmit, { once: true });
    modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });

    modal.show();
  };

  // Dashboard Task Checkbox Toggle
  document.addEventListener('click', async (event) => {
    const btn = event.target.closest('.todays-task-list .task-check-btn');
    if (!btn) return;
    event.preventDefault();
    event.stopPropagation();
    const taskId = btn.dataset.taskId;
    const currentStatus = btn.dataset.status;
    const isCompleted = currentStatus === 'COMPLETED';

      if (!isCompleted) {
        const row = btn.closest('.task-row');
        const taskName = row?.querySelector('.task-row-title')?.textContent?.trim() || 'Task';
        window.promptTaskCompletion({
          taskId,
          taskName,
          onComplete: async (formData) => {
            btn.classList.add('checked');
            row?.classList.add('is-completed');
            btn.dataset.status = 'COMPLETED';
            if (row) row.dataset.status = 'COMPLETED';
            const pillCol = row?.querySelector('.task-pill-col');
            if (pillCol) pillCol.innerHTML = '<span class="status-pill status-COMPLETED">Completed</span>';

            try {
              const resp = await window.sendFocusUpload(`/tasks/${taskId}/status`, formData);
              if (!resp.ok) {
                btn.classList.remove('checked');
                row?.classList.remove('is-completed');
                btn.dataset.status = currentStatus;
                if (row) row.dataset.status = currentStatus;
                if (pillCol) pillCol.innerHTML = '<span class="status-pill status-IN_PROGRESS">In progress</span>';
                if (window.showToast) window.showToast('Could not complete task', 'error');
              } else {
                if (window.showToast) {
                  window.showToast(`Task "${taskName}" marked as completed`, 'success', {
                    actionText: 'Undo',
                    onAction: async () => {
                      const revertForm = new URLSearchParams();
                      revertForm.append('status', currentStatus || 'IN_PROGRESS');
                      if (csrfToken) revertForm.append('_csrf', csrfToken);

                      const revertResp = await fetch(`/board/tasks/${taskId}/move`, {
                        method: 'POST',
                        headers: {
                          'Content-Type': 'application/x-www-form-urlencoded',
                          'X-CSRF-TOKEN': csrfToken || ''
                        },
                        body: revertForm.toString()
                      });

                      if (revertResp.ok) {
                        btn.classList.remove('checked');
                        row?.classList.remove('is-completed');
                        btn.dataset.status = currentStatus || 'IN_PROGRESS';
                        if (row) row.dataset.status = currentStatus || 'IN_PROGRESS';
                        if (pillCol) {
                          const isYetToStart = (currentStatus === 'YET_TO_START');
                          pillCol.innerHTML = `<span class="status-pill status-${isYetToStart ? 'YET_TO_START' : 'IN_PROGRESS'}">${isYetToStart ? 'Yet to start' : 'In progress'}</span>`;
                        }
                        window.showToast(`Task "${taskName}" reopened`, 'success');
                      } else {
                        window.showToast('Could not undo task completion', 'error');
                      }
                    }
                  });
                }
              }
            } catch (e) {
              btn.classList.remove('checked');
              row?.classList.remove('is-completed');
              btn.dataset.status = currentStatus;
              if (row) row.dataset.status = currentStatus;
              if (pillCol) pillCol.innerHTML = '<span class="status-pill status-IN_PROGRESS">In progress</span>';
              if (window.showToast) window.showToast('Could not complete task', 'error');
            }
          }
        });
        return;
      }

      // Reopening task (COMPLETED -> IN_PROGRESS)
      const row = btn.closest('.task-row');
      btn.classList.remove('checked');
      row?.classList.remove('is-completed');
      btn.dataset.status = 'IN_PROGRESS';
      if (row) row.dataset.status = 'IN_PROGRESS';
      const pillCol = row?.querySelector('.task-pill-col');
      if (pillCol) pillCol.innerHTML = '<span class="status-pill status-IN_PROGRESS">In progress</span>';

      try {
        const formData = new URLSearchParams();
        formData.append('status', 'IN_PROGRESS');
        if (csrfToken) formData.append('_csrf', csrfToken);

        const resp = await fetch(`/board/tasks/${taskId}/move`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-CSRF-TOKEN': csrfToken || ''
          },
          body: formData.toString()
        });

        if (!resp.ok) {
          btn.classList.add('checked');
          row?.classList.add('is-completed');
          btn.dataset.status = currentStatus;
          if (row) row.dataset.status = currentStatus;
          if (pillCol) pillCol.innerHTML = '<span class="status-pill status-COMPLETED">Completed</span>';
          if (window.showToast) window.showToast('Could not reopen task', 'error');
        } else {
          const taskName = row?.querySelector('.task-row-title')?.textContent?.trim() || 'Task';
          if (window.showToast) {
            window.showToast(`Task "${taskName}" reopened`, 'success', {
              actionText: 'Undo',
              onAction: async () => {
                const revertForm = new URLSearchParams();
                revertForm.append('status', 'COMPLETED');
                if (csrfToken) revertForm.append('_csrf', csrfToken);

                const revertResp = await fetch(`/board/tasks/${taskId}/move`, {
                  method: 'POST',
                  headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-CSRF-TOKEN': csrfToken || ''
                  },
                  body: revertForm.toString()
                });

                if (revertResp.ok) {
                  btn.classList.add('checked');
                  row?.classList.add('is-completed');
                  btn.dataset.status = 'COMPLETED';
                  if (row) row.dataset.status = 'COMPLETED';
                  if (pillCol) pillCol.innerHTML = '<span class="status-pill status-COMPLETED">Completed</span>';
                  window.showToast(`Task "${taskName}" marked as completed`, 'success');
                } else {
                  window.showToast('Could not undo task reopen', 'error');
                }
              }
            });
          }
        }
      } catch (e) {
        btn.classList.add('checked');
        row?.classList.add('is-completed');
        btn.dataset.status = currentStatus;
        if (row) row.dataset.status = currentStatus;
        if (pillCol) pillCol.innerHTML = '<span class="status-pill status-COMPLETED">Completed</span>';
        if (window.showToast) window.showToast('Could not reopen task', 'error');
      }
    });

  // ==========================================================
  // Global Topbar Active Timer Manager
  // ==========================================================
  const topbarTimer = document.getElementById('topbarActiveTimer');
  if (topbarTimer) {
    const clockEl = document.getElementById('topbarTimerClock');
    const taskLink = document.getElementById('topbarTimerTaskName');
    const pauseForm = document.getElementById('topbarPauseForm');
    const stopForm = document.getElementById('topbarStopForm');

    const singleContainer = document.getElementById('topbarSingleTimer');
    const multiContainer = document.getElementById('topbarMultiTimer');
    const multiCountEl = document.getElementById('topbarActiveCount');
    const multiListEl = document.getElementById('topbarMultiTimerList');

    let hasActive = topbarTimer.dataset.hasActive === 'true';
    let activeCount = parseInt(topbarTimer.dataset.count || '0', 10);
    let taskId = topbarTimer.dataset.taskId;
    let timerStarted = topbarTimer.dataset.timerStarted ? Number(topbarTimer.dataset.timerStarted) : null;
    let timerAccumulated = parseInt(topbarTimer.dataset.timerAccumulated || '0', 10);

    const pad = (n) => String(n).padStart(2, '0');
    const formatClock = (secs) => {
      const s = Math.max(0, Math.floor(secs));
      const hours = Math.floor(s / 3600);
      const minutes = Math.floor((s % 3600) / 60);
      const seconds = s % 60;
      return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    };

    const updateTopbarClock = () => {
      if (!hasActive || !timerStarted) return;
      const elapsed = timerAccumulated + Math.max(0, Math.floor((Date.now() - timerStarted) / 1000));
      if (clockEl) clockEl.textContent = formatClock(elapsed);
    };

    setInterval(updateTopbarClock, 1000);
    updateTopbarClock();

    window.syncGlobalTimer = async () => {
      try {
        const res = await fetch('/tasks/timer/active', {
          headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        });
        if (!res.ok) return;
        const data = await res.json();
        if (data.hasActive) {
          hasActive = true;
          activeCount = data.count || 1;
          topbarTimer.style.display = '';

          if (activeCount > 1 && multiContainer) {
            if (singleContainer) singleContainer.style.display = 'none';
            multiContainer.style.display = '';
            if (multiCountEl) multiCountEl.textContent = `${activeCount} active timers`;
            if (multiListEl && data.activeTasks) {
              const csrf = getCsrfToken();
              multiListEl.innerHTML = data.activeTasks.map((t) => `
                <li class="p-2 border-bottom last-border-0" data-multi-task-id="${t.taskId}">
                  <div class="d-flex justify-content-between align-items-center gap-2">
                    <a href="/tasks/${t.taskId}" class="fw-semibold text-decoration-none small text-truncate" style="max-width: 140px;">${t.taskName}</a>
                    <div class="d-flex align-items-center gap-1">
                      <form action="/tasks/${t.taskId}/timer/pause" method="post" class="d-inline">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <button type="submit" class="icon-button topbar-timer-btn" title="Pause">
                          <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
                        </button>
                      </form>
                      <form action="/tasks/${t.taskId}/timer/stop" method="post" class="d-inline">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <button type="submit" class="icon-button topbar-timer-btn topbar-timer-btn-stop" title="Stop">
                          <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><rect x="5" y="5" width="14" height="14" rx="2"/></svg>
                        </button>
                      </form>
                    </div>
                  </div>
                </li>
              `).join('');
            }
          } else {
            if (multiContainer) multiContainer.style.display = 'none';
            if (singleContainer) singleContainer.style.display = '';
            taskId = data.taskId;
            timerStarted = data.timerStartedAtMillis ?? Date.now();
            timerAccumulated = data.timerAccumulatedSeconds || 0;
            if (taskLink) {
              taskLink.textContent = data.taskName;
              taskLink.href = `/tasks/${data.taskId}`;
            }
            if (pauseForm) pauseForm.action = `/tasks/${data.taskId}/timer/pause`;
            if (stopForm) stopForm.action = `/tasks/${data.taskId}/timer/stop`;
            updateTopbarClock();
          }
        } else {
          hasActive = false;
          topbarTimer.style.display = 'none';
        }
      } catch (err) {
        console.error('Error syncing global timer:', err);
      }
    };

    const handleTopbarAction = async (form, actionLabel) => {
      const formData = new FormData(form);
      const csrf = form.querySelector('input[name="_csrf"]')?.value;
      const res = await fetch(form.action, {
        method: 'POST',
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json',
          'X-CSRF-TOKEN': csrf || ''
        },
        body: formData
      });
      if (!res.ok) throw new Error(`${actionLabel} failed`);
      return await res.json();
    };

    topbarTimer.addEventListener('submit', async (e) => {
      const form = e.target.closest('form');
      if (!form) return;
      e.preventDefault();
      const isPause = form.action.includes('/pause');
      const actionLabel = isPause ? 'Pause' : 'Stop';
      try {
        const data = await handleTopbarAction(form, actionLabel);
        if (window.showToast) {
          window.showToast(isPause ? `Timer paused for "${data.taskName}"` : `Timer stopped for "${data.taskName}". Total time: ${data.formattedTotalTime}`, 'success');
        }
        await window.syncGlobalTimer();
        if (window.location.pathname.includes('/tasks/') || document.querySelector('.board-active-timer')) {
          setTimeout(() => window.location.reload(), 300);
        }
      } catch (err) {
        console.error('Topbar timer error:', err);
        form.submit();
      }
    });
  }
})();
