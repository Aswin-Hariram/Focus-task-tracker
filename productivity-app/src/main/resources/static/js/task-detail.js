(() => {
  const timeline = document.querySelector('.task-detail');
  const timelineModal = document.getElementById('timelineEntryModal');
  if (timeline && timelineModal && typeof bootstrap !== 'undefined') {
    const title = document.getElementById('timelineEntryTitle');
    const body = document.getElementById('timelineEntryBody');
    let opener;
    timeline.addEventListener('click', (event) => {
      const card = event.target.closest('[data-timeline-card]');
      if (!card || (event.target.closest('a, button, summary, form, details') &&
          !event.target.closest('[data-timeline-open]'))) return;

      opener = card.querySelector('[data-timeline-open]');
      if (card.dataset.progressEditUrl) {
        const trigger = opener;
        window.promptProgressUpdate?.({
          taskName: timeline.querySelector('h1')?.textContent,
          editUrl: card.dataset.progressEditUrl,
          deleteUrl: card.dataset.progressDeleteUrl,
          progressId: card.dataset.progressId,
          initialDescription: card.dataset.progressDescription,
          initialReminder: card.dataset.progressReminder,
          initialRecordedAt: card.dataset.progressRecordedAt,
          milestone: card.dataset.progressMilestone === 'true',
          reopenAfter: card.dataset.timelineStatus === 'IN_PROGRESS' ? timeline.dataset.completedAt : null,
          nextIteration: Number(timeline.dataset.iteration) + 1,
          existingMaterials: card.querySelector('.timeline-materials'),
          onCancel: () => trigger.focus()
        });
        return;
      }

      title.textContent = opener.textContent;
      body.replaceChildren();
      card.querySelectorAll(':scope > .task-detail-entry > .timeline-copy, :scope > time, :scope > .task-detail-entry-extras').forEach((section) => {
        body.appendChild(section.cloneNode(true));
      });
      bootstrap.Modal.getOrCreateInstance(timelineModal).show(opener);
    });
    timelineModal.addEventListener('hidden.bs.modal', () => opener?.focus());
  }


  const link = document.getElementById('taskShareUrl');
  const copy = document.getElementById('copyTaskShareUrl');
  const feedback = document.getElementById('taskShareFeedback');
  if (link && copy && feedback) {
    // Session identifiers can appear in server-rendered links when cookies are unavailable.
    link.value = new URL(link.value.replace(/;jsessionid=[^/?#;]*/gi, ''), window.location.origin).href;

    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(link.value);
        feedback.textContent = 'Task link copied.';
      } catch {
        link.focus();
        link.select();
        feedback.textContent = 'The link is selected. Copy it using your keyboard or device menu.';
      }
    });
  }

  // Task Status Form with Undo Toast
  const statusForm = document.getElementById('taskStatusForm');
  const statusSelect = document.getElementById('taskStatus');
  if (statusForm && statusSelect) {
    let previousStatus = statusSelect.value;
    statusForm.addEventListener('submit', async (e) => {
      const filesInput = statusForm.querySelector('input[type="file"]');
      const hasFiles = filesInput && filesInput.files && filesInput.files.length > 0;
      if (hasFiles) return; // Allow normal form submission for file uploads

      const newStatus = statusSelect.value;
      if (newStatus === previousStatus) return;

      e.preventDefault();
      const prevStatusVal = previousStatus;
      const formData = new FormData(statusForm);

      try {
        const res = await fetch(statusForm.action, {
          method: 'POST',
          headers: {
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json'
          },
          body: formData
        });

        if (!res.ok) throw new Error('Status update failed');
        previousStatus = newStatus;

        if (window.showToast) {
          const selectedOptionText = statusSelect.options[statusSelect.selectedIndex]?.text || newStatus;
          window.showToast(`Task status updated to ${selectedOptionText}`, 'success', {
            actionText: 'Undo',
            onAction: async () => {
              const revertForm = new URLSearchParams();
              revertForm.append('status', prevStatusVal);
              const csrf = statusForm.querySelector('input[name="_csrf"]')?.value;
              if (csrf) revertForm.append('_csrf', csrf);

              const revertRes = await fetch(statusForm.action, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded',
                  'X-CSRF-TOKEN': csrf || ''
                },
                body: revertForm.toString()
              });

              if (!revertRes.ok) throw new Error('Undo failed');
              statusSelect.value = prevStatusVal;
              previousStatus = prevStatusVal;
              window.showToast('Status update undone', 'success');
            }
          });
        }
      } catch (err) {
        console.error('Status change error:', err);
        statusForm.submit();
      }
    });
  }

  // ==========================================================
  // Task Detail Interactive Timer (Start, Pause, Stop)
  // ==========================================================
  const timerSection = document.getElementById('taskDetailTimerSection');
  if (timerSection) {
    const taskId = timerSection.dataset.taskId;
    let timerStatus = timerSection.dataset.timerStatus || 'STOPPED';
    let timerStarted = timerSection.dataset.timerStarted ? Number(timerSection.dataset.timerStarted) : null;
    let timerAccumulated = parseInt(timerSection.dataset.timerAccumulated || '0', 10);
    let totalSeconds = parseInt(timerSection.dataset.totalSeconds || '0', 10);

    const clockDigits = document.getElementById('timerClockDigits');
    const totalValue = document.getElementById('timerTotalValue');
    const pillRunning = document.getElementById('timerStatusPillRunning');
    const pillPaused = document.getElementById('timerStatusPillPaused');
    const pillStopped = document.getElementById('timerStatusPillStopped');
    const btnStart = document.getElementById('btnTimerStart');
    const btnPause = document.getElementById('btnTimerPause');
    const btnStop = document.getElementById('btnTimerStop');
    const btnStartLabel = document.getElementById('btnTimerStartLabel');
    const formStart = document.getElementById('formTimerStart');
    const formPause = document.getElementById('formTimerPause');
    const formStop = document.getElementById('formTimerStop');

    const formatTimerDigits = (secs) => {
      const s = Math.max(0, Math.floor(secs));
      const hours = Math.floor(s / 3600);
      const minutes = Math.floor((s % 3600) / 60);
      const seconds = s % 60;
      const pad = (n) => String(n).padStart(2, '0');
      return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    };

    const getElapsedActiveSession = () => {
      if (timerStatus === 'RUNNING' && timerStarted) {
        const diff = Math.max(0, Math.floor((Date.now() - timerStarted) / 1000));
        return timerAccumulated + diff;
      }
      if (timerStatus === 'PAUSED') {
        return timerAccumulated;
      }
      return 0;
    };

    const updateDisplay = () => {
      const activeSession = getElapsedActiveSession();
      if (clockDigits) {
        clockDigits.textContent = formatTimerDigits(activeSession);
      }
      if (totalValue) {
        const effTotal = totalSeconds + (timerStatus !== 'STOPPED' ? activeSession : 0);
        totalValue.textContent = formatTimerDigits(effTotal);
      }
    };

    const updateUIState = (status) => {
      timerStatus = status;
      timerSection.dataset.timerStatus = status;
      if (pillRunning) pillRunning.style.display = status === 'RUNNING' ? '' : 'none';
      if (pillPaused) pillPaused.style.display = status === 'PAUSED' ? '' : 'none';
      if (pillStopped) pillStopped.style.display = status === 'STOPPED' ? '' : 'none';

      if (btnStart) {
        btnStart.disabled = timerSection.dataset.timerCanStart !== 'true' || status === 'RUNNING';
        if (btnStartLabel) {
          btnStartLabel.textContent = status === 'PAUSED' ? 'Resume' : 'Start';
        }
      }
      if (btnPause) {
        btnPause.disabled = status !== 'RUNNING';
      }
      if (btnStop) {
        btnStop.disabled = status === 'STOPPED';
      }
      updateDisplay();
    };

    // Ticking loop
    let tickInterval = setInterval(updateDisplay, 1000);
    updateUIState(timerStatus);

    const postTimerAction = async (form) => {
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
      if (!res.ok) throw new Error('Timer request failed');
      return await res.json();
    };

    formStart?.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const data = await postTimerAction(formStart);
        timerStarted = data.timerStartedAtMillis ?? Date.now();
        timerAccumulated = data.timerAccumulatedSeconds || 0;
        totalSeconds = data.totalTimeSpentSeconds || 0;
        updateUIState('RUNNING');
        if (window.showToast) window.showToast('Timer started', 'success');
        if (window.syncGlobalTimer) window.syncGlobalTimer();
      } catch (err) {
        console.error('Error starting timer:', err);
        formStart.submit();
      }
    });

    formPause?.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const data = await postTimerAction(formPause);
        timerStarted = null;
        timerAccumulated = data.timerAccumulatedSeconds || 0;
        totalSeconds = data.totalTimeSpentSeconds || 0;
        updateUIState('PAUSED');
        if (window.showToast) window.showToast('Timer paused', 'success');
        if (window.syncGlobalTimer) window.syncGlobalTimer();
      } catch (err) {
        console.error('Error pausing timer:', err);
        formPause.submit();
      }
    });

    formStop?.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const data = await postTimerAction(formStop);
        timerStarted = null;
        timerAccumulated = 0;
        totalSeconds = data.totalTimeSpentSeconds || 0;
        updateUIState('STOPPED');
        if (window.showToast) window.showToast(`Timer stopped. Total time: ${data.formattedTotalTime}`, 'success');
        if (window.syncGlobalTimer) window.syncGlobalTimer();
        // Reload page after brief delay to show recorded time sessions in timeline/history
        setTimeout(() => window.location.reload(), 700);
      } catch (err) {
        console.error('Error stopping timer:', err);
        formStop.submit();
      }
    });
  }
})();
