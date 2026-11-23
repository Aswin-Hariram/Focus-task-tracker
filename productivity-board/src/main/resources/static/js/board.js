(() => {
  const board = document.querySelector('.kanban');
  if (!board) return;

  const formatTimerDigits = (secs) => {
    const s = Math.max(0, Math.floor(secs));
    const hours = Math.floor(s / 3600);
    const minutes = Math.floor((s % 3600) / 60);
    const seconds = s % 60;
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  };

  const formatCompactTimer = (secs) => {
    const s = Math.max(0, Math.floor(secs));
    if (s <= 0) return '0m';
    const hours = Math.floor(s / 3600);
    const minutes = Math.floor((s % 3600) / 60);
    if (hours > 0) return `${hours}h ${minutes}m`;
    if (minutes > 0) return `${minutes}m`;
    return `${s}s`;
  };

  let nextDayRefresh = 0;

  async function refreshTimerDay(now) {
    if (now < nextDayRefresh) return;
    nextDayRefresh = now + 10000;
    try {
      const response = await fetch('/tasks/timer/active?includeDayTotals=true', { cache: 'no-store' });
      if (!response.ok) return;
      const data = await response.json();
      if (!data.hasActive) return;
      const active = new Map(data.activeTasks.map(task => [String(task.taskId), task]));
      document.querySelectorAll('.board-active-timer-body').forEach(item => {
        const task = active.get(item.dataset.activeTaskId);
        item.dataset.timerDayStart = data.timerDayStart;
        item.dataset.timerNextDayStart = data.timerNextDayStart;
        item.dataset.todayBaseSeconds = task?.todayBaseSeconds ?? 0;
      });
    } catch {
      // Continue counting from midnight while waiting for the server.
    }
  }

  // Live ticking for all active running timers on the board (cards + active timer header section)
  const updateBoardTimers = () => {
    const now = Date.now();

    // 1. Update all task cards that have a running timer
    document.querySelectorAll('.task-card[data-timer-status="RUNNING"]').forEach((card) => {
      const startedStr = card.dataset.timerStarted;
      if (!startedStr) return;
      const startedTime = Number(startedStr);
      const accumulated = parseInt(card.dataset.timerAccumulated || '0', 10);
      const elapsed = accumulated + Math.max(0, Math.floor((now - startedTime) / 1000));

      const digitsEl = card.querySelector('.board-card-timer-digits');
      if (digitsEl) digitsEl.textContent = formatTimerDigits(elapsed);

      const clockText = card.querySelector('.task-meta-time span');
      if (clockText) clockText.textContent = formatCompactTimer(parseInt(card.dataset.totalSeconds || '0', 10) + elapsed);
    });

    // 2. Update all active timer bodies in the Active Timers section
    document.querySelectorAll('.board-active-timer-body').forEach((item) => {
      const startedStr = item.dataset.timerStarted;
      if (!startedStr) return;
      const startedTime = Number(startedStr);
      const accumulated = parseInt(item.dataset.timerAccumulated || '0', 10);
      const nextDay = Number(item.dataset.timerNextDayStart);
      const crossedMidnight = now >= nextDay;
      const dayStart = crossedMidnight ? nextDay : Number(item.dataset.timerDayStart);
      const baseToday = crossedMidnight ? 0 : Number(item.dataset.todayBaseSeconds);
      if (crossedMidnight) refreshTimerDay(now);
      const diff = Math.max(0, Math.floor((now - startedTime) / 1000));

      const sessionEl = item.querySelector('[data-board-session]');
      if (sessionEl) sessionEl.textContent = formatTimerDigits(accumulated + diff);

      const todayEl = item.querySelector('[data-board-today]');
      const beforeToday = Math.max(0, Math.floor((dayStart - startedTime) / 1000));
      if (todayEl) todayEl.textContent = formatTimerDigits(baseToday + diff - beforeToday);
    });
  };

  setInterval(updateBoardTimers, 1000);
  updateBoardTimers();

  const dateForm = document.querySelector('.date-strip-picker');
  if (dateForm) {
    const dateInput = dateForm.querySelector('input[type="date"]');
    const dateButton = dateForm.querySelector('button[type="button"]');

    dateInput.addEventListener('change', () => {
      if (dateInput.value && dateInput.value !== dateInput.defaultValue) dateForm.requestSubmit();
    });

    if (typeof dateInput.showPicker === 'function') {
      dateInput.tabIndex = -1;
      dateForm.classList.add('date-strip-picker-enhanced');
      dateButton.hidden = false;
      dateButton.addEventListener('click', () => {
        try {
          dateInput.showPicker();
        } catch {
          // Use the native input directly if opening from a button is blocked.
          dateForm.classList.remove('date-strip-picker-enhanced');
          dateButton.hidden = true;
          dateInput.removeAttribute('tabindex');
          dateInput.focus();
        }
      });
    }
  }

  const clearActiveTimer = (card, status) => {
    if (!card || status !== 'COMPLETED') return;
    card.dataset.timerStatus = 'STOPPED';
    const startForm = card.querySelector('.card-timer-start-form');
    if (startForm) {
      startForm.hidden = false;
      const timerButton = startForm.querySelector('button');
      timerButton.disabled = false;
      timerButton.title = 'Start timer';
      timerButton.querySelector('.card-timer-label').textContent = 'Timer';
    }
    card.dataset.timerRunning = 'false';
    card.classList.remove('task-card-timer-active');
    const badgeRunning = card.querySelector('.board-timer-badge-running');
    if (badgeRunning) badgeRunning.style.display = 'none';
    const badgePaused = card.querySelector('.board-timer-badge-paused');
    if (badgePaused) badgePaused.style.display = 'none';
    card.querySelector('.task-meta-time')?.classList.remove('timer-active-badge');
    const activeBody = document.querySelector(`.board-active-timer-body[data-active-task-id="${card.dataset.taskId}"]`);
    if (activeBody) activeBody.remove();
    window.syncGlobalTimer?.();
  };

  const columns = board.querySelectorAll('.kanban-column');
  const boardColumn = (status) => status === 'BLOCKED' ? 'COMPLETED' : status;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

  let draggedCard = null;
  let sourceColumn = null;
  let sourceIndex = -1;
  let sourceStatus = null;
  let placeholder = null;

  const showToast = (message, type = 'success', options = {}) => {
    if (typeof window.showToast === 'function') {
      return window.showToast(message, type, options);
    }
    let stack = document.querySelector('.flash-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.className = 'flash-stack';
      stack.setAttribute('aria-live', 'polite');
      document.body.appendChild(stack);
    }
    const alert = document.createElement('div');
    alert.className = `alert alert-${type === 'error' ? 'danger' : 'success'} alert-dismissible fade show`;
    alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
    alert.innerHTML = `<span>${message}</span><button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Dismiss"></button>`;
    stack.appendChild(alert);
    setTimeout(() => {
      alert.classList.remove('show');
      setTimeout(() => alert.remove(), 250);
    }, 3500);
  };

  const createPlaceholder = (card) => {
    const el = document.createElement('div');
    el.className = 'kanban-drop-placeholder';
    const cardHeight = card ? card.getBoundingClientRect().height : 120;
    el.style.height = `${Math.max(cardHeight, 80)}px`;
    el.setAttribute('aria-hidden', 'true');
    el.innerHTML = `
      <div class="drop-placeholder-inner">
        <svg class="drop-placeholder-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        <span>Drop here</span>
      </div>
    `;
    return el;
  };

  const getDragAfterElement = (container, y) => {
    const draggableElements = [...container.querySelectorAll('.task-card:not(.is-dragging)')];
    return draggableElements.reduce((closest, child) => {
      const box = child.getBoundingClientRect();
      const offset = y - box.top - box.height / 2;
      if (offset < 0 && offset > closest.offset) {
        return { offset: offset, element: child };
      } else {
        return closest;
      }
    }, { offset: Number.NEGATIVE_INFINITY }).element;
  };

  const updateBoardCounts = () => {
    columns.forEach((col) => {
      const list = col.querySelector('.kanban-list');
      const badge = col.querySelector('.col-count-badge, .count-badge');
      const cards = list ? list.querySelectorAll('.task-card') : [];
      if (badge) badge.textContent = String(cards.length);

      let emptyEl = list ? list.querySelector('.empty-column') : null;
      if (cards.length === 0) {
        if (!emptyEl && list) {
          emptyEl = document.createElement('div');
          emptyEl.className = 'empty-column';
          emptyEl.textContent = 'No tasks in this status.';
          list.appendChild(emptyEl);
        } else if (emptyEl) {
          emptyEl.style.display = '';
        }
      } else if (emptyEl) {
        emptyEl.style.display = 'none';
      }
    });

    updateBoardHeaderStats();
  };

  const updateBoardHeaderStats = () => {
    let total = 0;
    let completed = 0;
    columns.forEach((col) => {
      const status = col.dataset.status;
      const count = col.querySelectorAll('.task-card').length;
      total += count;
      if (status === 'COMPLETED') {
        completed += col.querySelectorAll('.task-card[data-task-status="COMPLETED"]').length;
      }
    });

    const totalValEl = document.querySelector('.board-stat-item:nth-child(1) .board-stat-value');
    if (totalValEl) totalValEl.textContent = String(total);

    const compValEl = document.querySelector('.board-stat-item:nth-child(2) .board-stat-value');
    if (compValEl) compValEl.textContent = String(completed);

    const pct = total === 0 ? 0 : Math.round((completed * 100) / total);
    const donutValEl = document.querySelector('.donut-center-value');
    if (donutValEl) donutValEl.textContent = `${pct}%`;

    const donutSegment = document.querySelector('.donut-segment');
    if (donutSegment) {
      const circum = 138.23;
      donutSegment.style.strokeDashoffset = `${circum - (circum * pct) / 100}`;
    }
  };

  const updateCardStatusDisplay = (card, newStatus) => {
    const timerGroup = card.querySelector('.card-timer-btn-group');
    if (timerGroup) timerGroup.hidden = newStatus !== 'IN_PROGRESS';
    const statusTextEl = card.querySelector('.task-card-status-text');
    const progressPctEl = card.querySelector('.task-card-progress-pct');
    const progressBarEl = card.querySelector('.task-progress-bar');

    progressBarEl?.classList.remove('bg-danger');

    if (newStatus === 'COMPLETED') {
      if (statusTextEl) statusTextEl.textContent = 'Completed';
      if (progressPctEl) progressPctEl.textContent = '100% completed';
      if (progressBarEl) {
        progressBarEl.style.width = '100%';
        progressBarEl.classList.remove('bg-progress-blue', 'bg-primary');
        progressBarEl.classList.add('bg-progress-green');
      }
    } else if (newStatus === 'IN_PROGRESS') {
      if (statusTextEl) statusTextEl.textContent = 'In progress';
      if (progressPctEl) progressPctEl.textContent = '50% completed';
      if (progressBarEl) {
        progressBarEl.style.width = '50%';
        progressBarEl.classList.remove('bg-progress-green', 'bg-success');
        progressBarEl.classList.add('bg-progress-blue');
      }
    } else if (newStatus === 'BLOCKED') {
      if (statusTextEl) statusTextEl.textContent = 'Blocked/Closed';
      if (progressPctEl) progressPctEl.textContent = '0% completed';
      if (progressBarEl) {
        progressBarEl.style.width = '0%';
        progressBarEl.classList.remove('bg-progress-green', 'bg-progress-blue', 'bg-success', 'bg-primary');
        progressBarEl.classList.add('bg-danger');
      }
    } else {
      if (statusTextEl) statusTextEl.textContent = 'Yet to start';
      if (progressPctEl) progressPctEl.textContent = '0% completed';
      if (progressBarEl) {
        progressBarEl.style.width = '0%';
        progressBarEl.classList.remove('bg-progress-green', 'bg-progress-blue', 'bg-success', 'bg-primary');
      }
    }
  };

  let dragEndTime = 0;

  const cleanupDrag = () => {
    dragEndTime = Date.now();
    if (draggedCard) {
      draggedCard.classList.remove('is-dragging');
      draggedCard.setAttribute('aria-grabbed', 'false');
      draggedCard = null;
    }
    if (placeholder && placeholder.parentNode) {
      placeholder.parentNode.removeChild(placeholder);
    }
    placeholder = null;

    columns.forEach((col) => {
      col.classList.remove('is-dragover');
      const list = col.querySelector('.kanban-list');
      if (list) {
        const cards = list.querySelectorAll('.task-card');
        const emptyEl = list.querySelector('.empty-column');
        if (emptyEl) {
          emptyEl.style.display = cards.length === 0 ? '' : 'none';
        }
      }
    });
  };

  // Card dragstart via delegation on board container
  board.addEventListener('dragstart', (event) => {
    // Prevent dragging when clicking directly on interactive elements (links, forms, buttons)
    if (event.target.closest('a, button, input, select, textarea, form')) {
      return;
    }

    const card = event.target.closest('.task-card');
    if (!card) return;

    draggedCard = card;
    sourceColumn = card.closest('.kanban-column');
    sourceStatus = card.dataset.taskStatus;
    const currentCards = Array.from(sourceColumn.querySelectorAll('.task-card'));
    sourceIndex = currentCards.indexOf(card);

    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', card.dataset.taskId);

    // Create the shadow placeholder with matching dimensions
    placeholder = createPlaceholder(card);

    // Apply dragging styling asynchronously so the browser captures the crisp original image
    requestAnimationFrame(() => {
      card.classList.add('is-dragging');
      card.setAttribute('aria-grabbed', 'true');
    });
  });

  board.addEventListener('dragend', cleanupDrag);

  // Column dragover and drop handlers
  columns.forEach((column) => {
    const list = column.querySelector('.kanban-list');
    if (!list) return;

    column.addEventListener('dragover', (event) => {
      if (!draggedCard) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = 'move';

      columns.forEach((col) => {
        if (col === column) {
          col.classList.add('is-dragover');
        } else {
          col.classList.remove('is-dragover');
        }
      });

      if (!placeholder) {
        placeholder = createPlaceholder(draggedCard);
      }

      // Hide empty notice while dragging over
      const emptyEl = list.querySelector('.empty-column');
      if (emptyEl) emptyEl.style.display = 'none';

      const afterElement = getDragAfterElement(list, event.clientY);
      if (afterElement == null) {
        if (placeholder.parentNode !== list || placeholder.nextSibling !== null) {
          list.appendChild(placeholder);
        }
      } else {
        if (placeholder.nextSibling !== afterElement) {
          list.insertBefore(placeholder, afterElement);
        }
      }
    });

    column.addEventListener('dragleave', (event) => {
      if (!column.contains(event.relatedTarget)) {
        column.classList.remove('is-dragover');
      }
    });

    column.addEventListener('drop', async (event) => {
      if (!draggedCard) return;
      event.preventDefault();
      column.classList.remove('is-dragover');

      const cardRef = draggedCard;
      const moveUrl = cardRef.dataset.moveUrl || `/board/tasks/${cardRef.dataset.taskId}/move`;
      const prevStatus = sourceStatus;
      const origCol = sourceColumn;
      const sameColumn = column === origCol;
      const targetStatus = sameColumn ? prevStatus : column.dataset.status;
      const origIdx = sourceIndex;

      // Extract sibling anchor ids for position persistence
      let beforeTaskId = null;
      let afterTaskId = null;

      if (placeholder && placeholder.parentNode === list) {
        const next = placeholder.nextElementSibling;
        const prev = placeholder.previousElementSibling;
        if (next && next.classList.contains('task-card') && next !== cardRef) {
          beforeTaskId = next.dataset.taskId;
        } else if (prev && prev.classList.contains('task-card') && prev !== cardRef) {
          afterTaskId = prev.dataset.taskId;
        }
        // Insert card exactly where the shadow placeholder is positioned
        list.insertBefore(cardRef, placeholder);
        placeholder.remove();
        placeholder = null;
      } else {
        list.appendChild(cardRef);
      }

      cleanupDrag();

      // Check if task stayed in the exact same spot
      const currentIdx = Array.from(list.querySelectorAll('.task-card')).indexOf(cardRef);
      if (sameColumn && currentIdx === origIdx) {
        return;
      }

      // Drag from one section to another is considered as progress update
      if (!sameColumn) {
        let defaultDesc = `Moved to ${targetStatus}`;
        if (targetStatus === 'IN_PROGRESS') {
          defaultDesc = 'Moved to In Progress';
        } else if (targetStatus === 'YET_TO_START') {
          defaultDesc = 'Yet to apply';
        } else if (targetStatus === 'COMPLETED') {
          defaultDesc = 'Completed';
        } else if (targetStatus === 'BLOCKED') {
          defaultDesc = 'Blocked/Closed';
        }

        const taskTitleEl = cardRef.querySelector('.task-card-title');
        const taskName = taskTitleEl ? taskTitleEl.textContent.trim() : (`Task #${cardRef.dataset.taskId}`);

        const applyMove = async (formData) => {
          if (!formData.get('description') || formData.get('description').trim() === '') {
            formData.set('description', defaultDesc);
          }
          formData.set('status', targetStatus);
          if (board.dataset.date) formData.set('date', board.dataset.date);
          if (beforeTaskId) formData.set('before', beforeTaskId);
          else if (afterTaskId) formData.set('after', afterTaskId);
          if (csrfToken && !formData.has('_csrf')) formData.set('_csrf', csrfToken);

          cardRef.dataset.taskStatus = targetStatus;
          updateCardStatusDisplay(cardRef, targetStatus);
          updateBoardCounts();

          try {
            const res = await window.sendFocusUpload(`/tasks/${cardRef.dataset.taskId}/progress`, formData);
            if (!res.ok) throw new Error(`HTTP error ${res.status}`);
            const data = await res.json();
            clearActiveTimer(cardRef, data.status);
            const prevStatusValue = prevStatus;
            const targetStatusValue = targetStatus;
            const progressDesc = formData.get('description') || defaultDesc;
            showToast(`Progress update: "${progressDesc}"`, 'success', {
              actionText: prevStatusValue !== targetStatusValue ? 'Undo' : null,
              onAction: prevStatusValue !== targetStatusValue ? async () => {
                cancelMove();
                const revertBody = new URLSearchParams();
                revertBody.append('status', prevStatusValue);
                if (board.dataset.date) revertBody.append('date', board.dataset.date);
                if (csrfToken) revertBody.append('_csrf', csrfToken);

                await fetch(moveUrl, {
                  method: 'POST',
                  headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Accept': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken || ''
                  },
                  body: revertBody.toString()
                });
                showToast('Move undone', 'success');
              } : null
            });

            const countEl = cardRef.querySelector('.progress-count-val');
            if (countEl) {
              const current = parseInt(countEl.textContent, 10) || 0;
              countEl.textContent = String(data.updateCount || (current + 1));
            }
          } catch (err) {
            console.error('Drag progress update failed:', err);
            cancelMove();
            showToast('Could not move task: ' + err.message, 'error');
          }
        };

        const cancelMove = () => {
          if (origCol) {
            const origList = origCol.querySelector('.kanban-list');
            if (origList) {
              const siblings = Array.from(origList.querySelectorAll('.task-card')).filter((c) => c !== cardRef);
              if (origIdx >= siblings.length) origList.appendChild(cardRef);
              else origList.insertBefore(cardRef, siblings[origIdx]);
            }
          }
          cardRef.dataset.taskStatus = prevStatus;
          updateCardStatusDisplay(cardRef, prevStatus);
          cleanupDrag();
          updateBoardCounts();
        };

        if (window.promptProgressUpdate) {
          window.promptProgressUpdate({
            taskId: cardRef.dataset.taskId,
            taskName: taskName,
            initialDescription: defaultDesc,
            moveStatus: targetStatus,
            redirectUrl: window.location.pathname + window.location.search,
            onComplete: applyMove,
            onCancel: cancelMove
          });
          return;
        }

        const fallbackData = new FormData();
        fallbackData.append('description', defaultDesc);
        applyMove(fallbackData);
        return;
      }

      // Immediate in-place visual and numerical updates
      cardRef.dataset.taskStatus = targetStatus;
      updateCardStatusDisplay(cardRef, targetStatus);
      updateBoardCounts();

      // Dispatch persistence request
      const body = new URLSearchParams();
      body.append('status', targetStatus);
      if (board.dataset.date) {
        body.append('date', board.dataset.date);
      }
      if (beforeTaskId) {
        body.append('before', beforeTaskId);
      } else if (afterTaskId) {
        body.append('after', afterTaskId);
      }
      if (csrfToken) {
        body.append('_csrf', csrfToken);
      }

      try {
        const headers = {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Accept': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        };
        if (csrfToken) {
          headers['X-CSRF-TOKEN'] = csrfToken;
        }

        const res = await fetch(moveUrl, {
          method: 'POST',
          headers,
          body: body.toString()
        });

        if (!res.ok) {
          throw new Error(`HTTP error ${res.status}`);
        }
        const data = await res.json();
        const prevColRef = origCol;
        const prevStatusValue = prevStatus;
        const prevIndexValue = origIdx;

        showToast(`Task moved to ${data.statusLabel || targetStatus}`, 'success', {
          actionText: 'Undo',
          onAction: async () => {
            if (prevColRef) {
              const origList = prevColRef.querySelector('.kanban-list');
              if (origList) {
                const siblings = Array.from(origList.querySelectorAll('.task-card')).filter((c) => c !== cardRef);
                if (prevIndexValue >= siblings.length) {
                  origList.appendChild(cardRef);
                } else {
                  origList.insertBefore(cardRef, siblings[prevIndexValue]);
                }
              }
            }
            cardRef.dataset.taskStatus = prevStatusValue;
            updateCardStatusDisplay(cardRef, prevStatusValue);
            updateBoardCounts();

            const revertBody = new URLSearchParams();
            revertBody.append('status', prevStatusValue);
            if (board.dataset.date) {
              revertBody.append('date', board.dataset.date);
            }
            if (csrfToken) {
              revertBody.append('_csrf', csrfToken);
            }

            const revertHeaders = {
              'Content-Type': 'application/x-www-form-urlencoded',
              'Accept': 'application/json',
              'X-Requested-With': 'XMLHttpRequest'
            };
            if (csrfToken) {
              revertHeaders['X-CSRF-TOKEN'] = csrfToken;
            }

            const revertRes = await fetch(moveUrl, {
              method: 'POST',
              headers: revertHeaders,
              body: revertBody.toString()
            });

            if (!revertRes.ok) {
              throw new Error(`HTTP error ${revertRes.status}`);
            }
            showToast('Move undone', 'success');
          }
        });
      } catch (err) {
        console.error('Task move failed:', err);
        showToast('Could not update task status. Reverting change…', 'error');

        // Roll back card to previous column and index
        if (origCol) {
          const origList = origCol.querySelector('.kanban-list');
          if (origList) {
            const siblings = Array.from(origList.querySelectorAll('.task-card')).filter((c) => c !== cardRef);
            if (origIdx >= siblings.length) {
              origList.appendChild(cardRef);
            } else {
              origList.insertBefore(cardRef, siblings[origIdx]);
            }
          }
        }
        cardRef.dataset.taskStatus = prevStatus;
        updateCardStatusDisplay(cardRef, prevStatus);
        updateBoardCounts();
      }
    });
  });

  // Handle click on Progress button / badge / progress bar to show progress update modal
  board.addEventListener('click', (event) => {
    const btn = event.target.closest('.open-progress-modal-btn');
    if (!btn) return;
    event.preventDefault();
    const menuToggle = btn.closest('.dropdown')?.querySelector('[data-bs-toggle="dropdown"]');
    if (menuToggle) bootstrap.Dropdown.getInstance(menuToggle)?.hide();
    const card = btn.closest('.task-card');
    const taskId = btn.dataset.taskId || card?.dataset.taskId;
    const taskName = btn.dataset.taskName || card?.querySelector('.task-card-title')?.textContent?.trim() || 'Task';

    if (window.promptProgressUpdate) {
      window.promptProgressUpdate({
        taskId,
        taskName,
        moveStatus: btn.dataset.progressStatus,
        redirectUrl: window.location.pathname + window.location.search,
        onComplete: async (formData) => {
          try {
            const status = formData.get('status');
            if (board.dataset.date && status && status !== card?.dataset.taskStatus) {
              formData.set('date', board.dataset.date);
            }
            const res = await window.sendFocusUpload(`/tasks/${taskId}/progress`, formData);
            if (!res.ok) throw new Error(`HTTP error ${res.status}`);
            const data = await res.json();
            const origStatus = card?.dataset.taskStatus;
            const hasMoved = card && origStatus && origStatus !== data.status;

            if (hasMoved) {
              const targetList = board.querySelector(`.kanban-column[data-status="${boardColumn(data.status)}"] .kanban-list`);
              card.dataset.taskStatus = data.status;
              updateCardStatusDisplay(card, data.status);
              if (targetList) targetList.appendChild(card);
              updateBoardCounts();

              showToast(`Task moved to ${data.statusLabel || data.status}`, 'success', {
                actionText: 'Undo',
                onAction: async () => {
                  const origCol = board.querySelector(`.kanban-column[data-status="${boardColumn(origStatus)}"]`);
                  const origList = origCol?.querySelector('.kanban-list');
                  if (origList && card) origList.appendChild(card);
                  if (card) {
                    card.dataset.taskStatus = origStatus;
                    updateCardStatusDisplay(card, origStatus);
                  }
                  updateBoardCounts();

                  const revertBody = new URLSearchParams();
                  revertBody.append('status', origStatus);
                  if (board.dataset.date) revertBody.append('date', board.dataset.date);
                  if (csrfToken) revertBody.append('_csrf', csrfToken);

                  await fetch(`/board/tasks/${taskId}/move`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/x-www-form-urlencoded',
                      'Accept': 'application/json',
                      'X-Requested-With': 'XMLHttpRequest',
                      'X-CSRF-TOKEN': csrfToken || ''
                    },
                    body: revertBody.toString()
                  });
                  showToast('Move undone', 'success');
                }
              });
            } else {
            clearActiveTimer(card, data.status);
            showToast('Progress update saved');
            }

            // Increment progress count badge
            const countEl = card?.querySelector('.progress-count-val');
            if (countEl) {
              const current = parseInt(countEl.textContent, 10) || 0;
              countEl.textContent = String(data.updateCount || (current + 1));
            }
          } catch (err) {
            console.error('Progress update failed:', err);
            showToast('Failed to save progress update: ' + err.message, 'error');
          }
        }
      });
    }
  });

  // Handle click on card to navigate to task details page
  board.addEventListener('click', (event) => {
    // Ignore click if a drag action recently finished
    if (Date.now() - dragEndTime < 250) {
      return;
    }

    // Ignore clicks on buttons, forms, dropdowns, drag handles, or progress modal triggers
    if (event.target.closest('button, input, select, textarea, form, .open-progress-modal-btn, .dropdown, .dropdown-menu, .drag-handle')) {
      return;
    }

    const card = event.target.closest('.task-card');
    if (!card) return;

    // If click was on an anchor tag (e.g. title link or dots button), let default link navigation handle it
    const link = event.target.closest('a');
    if (link && link.href) {
      return;
    }

    const taskUrl = card.dataset.taskUrl || (card.dataset.taskId ? `/tasks/${card.dataset.taskId}` : null);
    if (taskUrl) {
      window.location.href = taskUrl;
    }
  });

  // Intercept "✓ Done" button clicks on cards to show the completion modal
  board.addEventListener('submit', (event) => {
    const form = event.target;
    const statusInput = form.querySelector('input[name="status"]');
    if (statusInput && statusInput.value === 'COMPLETED' && window.promptTaskCompletion) {
      event.preventDefault();
      const card = form.closest('.task-card');
      const taskId = card ? card.dataset.taskId : form.action.split('/').slice(-2)[0];
      const taskName = card?.querySelector('.task-card-title')?.textContent?.trim();
      window.promptTaskCompletion({
        taskId,
        taskName,
        redirectUrl: window.location.pathname + window.location.search
      });
    }
  });

  // Card & Board Active Timer AJAX Form Handler
  const postBoardTimerAction = async (form) => {
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
    if (!res.ok) throw new Error('Timer action failed');
    return await res.json();
  };

  document.addEventListener('submit', async (e) => {
    const form = e.target.closest('.card-timer-ajax-form, .board-timer-action-form, .board-timer-quick-start-form');
    if (!form) return;
    e.preventDefault();
    try {
      const data = await postBoardTimerAction(form);
      if (window.syncGlobalTimer) window.syncGlobalTimer();
      const card = form.closest('.task-card') || document.querySelector(`.task-card[data-task-id="${data.taskId}"]`);
      if (card) {
        card.dataset.timerStatus = data.timerStatus;
        card.dataset.timerStarted = data.timerStartedAtMillis ?? '';
        card.dataset.timerAccumulated = data.timerAccumulatedSeconds || '0';
        card.dataset.totalSeconds = data.totalTimeSpentSeconds || '0';
        card.dataset.timerRunning = data.timerStatus === 'RUNNING' ? 'true' : 'false';

        card.classList.toggle('task-card-timer-active', data.timerStatus === 'RUNNING');
        const badgeRunning = card.querySelector('.board-timer-badge-running');
        const badgePaused = card.querySelector('.board-timer-badge-paused');
        if (badgeRunning) badgeRunning.style.display = data.timerStatus === 'RUNNING' ? '' : 'none';
        if (badgePaused) badgePaused.style.display = data.timerStatus === 'PAUSED' ? '' : 'none';

        const metaTime = card.querySelector('.task-meta-time');
        if (metaTime) {
          metaTime.classList.toggle('timer-active-badge', data.timerStatus === 'RUNNING');
          const metaSpan = metaTime.querySelector('span');
          if (metaSpan) metaSpan.textContent = data.formattedCompactTime;
        }

        const startForm = card.querySelector('.card-timer-start-form');
        if (startForm) {
          const timerButton = startForm.querySelector('button');
          startForm.hidden = data.timerStatus === 'RUNNING';
          timerButton.disabled = startForm.hidden;
          timerButton.title = timerButton.disabled ? 'Timer is running'
            : data.timerStatus === 'PAUSED' ? 'Resume timer' : 'Start timer';
          timerButton.querySelector('.card-timer-label').textContent = data.timerStatus === 'PAUSED' ? 'Resume' : 'Timer';
        }


      }

      showToast(`Timer ${data.timerStatus.toLowerCase()} for "${data.taskName}"`, 'success');

      // Refresh board active timers section if needed
      setTimeout(() => {
        if (data.timerStatus === 'STOPPED' || form.closest('.board-active-timer')) {
          window.location.reload();
        }
      }, 500);
    } catch (err) {
      console.error('Board timer error:', err);
      form.submit();
    }
  });
})();
