/* Overdue Module Script - Focus Productivity */
(() => {
  const table = document.getElementById('overdueTasksTable');
  if (!table) return;

  const tbody = document.getElementById('overdueTableBody');
  const allRows = Array.from(tbody.querySelectorAll('.overdue-task-row'));
  const pillTabs = document.querySelectorAll('.overdue-pill-tab');
  const categorySelect = document.getElementById('filterCategory');
  const prioritySelect = document.getElementById('filterPrioritySelect');
  const delaySelect = document.getElementById('filterDelaySelect');
  const sortSelect = document.getElementById('sortSelect');
  const selectAllCheckbox = document.getElementById('selectAllCheckbox');
  const resetFiltersBtn = document.getElementById('resetFiltersBtn');
  const clearSearchFiltersBtn = document.getElementById('clearSearchFiltersBtn');
  const exportCsvBtn = document.getElementById('exportCsvBtn');
  const paginationInfo = document.getElementById('paginationInfo');
  const noFilterResultsNotice = document.getElementById('noFilterResultsNotice');

  let activePriority = 'ALL';

  const OVERDUE_MEMORY_KEY = 'overdue';

  const saveOverdueMemory = () => {
    const isDefault = activePriority === 'ALL' &&
      (!categorySelect || !categorySelect.value) &&
      (!delaySelect || !delaySelect.value) &&
      (!sortSelect || sortSelect.value === 'delay-desc');
    if (window.FilterMemory) {
      if (isDefault) {
        window.FilterMemory.remove(OVERDUE_MEMORY_KEY);
      } else {
        window.FilterMemory.set(OVERDUE_MEMORY_KEY, {
          priority: activePriority,
          category: categorySelect ? categorySelect.value : '',
          delay: delaySelect ? delaySelect.value : '',
          sort: sortSelect ? sortSelect.value : 'delay-desc'
        });
      }
    }
  };

  const loadOverdueMemory = () => {
    if (!window.FilterMemory) return;
    const data = window.FilterMemory.getJSON(OVERDUE_MEMORY_KEY);
    if (!data) return;
    if (data.priority) {
      activePriority = data.priority;
      pillTabs.forEach((tab) => {
        const matches = (tab.dataset.priorityFilter || 'ALL') === activePriority;
        tab.classList.toggle('active', matches);
        tab.setAttribute('aria-pressed', String(matches));
      });
      if (prioritySelect) {
        prioritySelect.value = activePriority === 'ALL' ? '' : activePriority;
      }
    }
    if (categorySelect && data.category !== undefined) {
      categorySelect.value = data.category;
    }
    if (delaySelect && data.delay !== undefined) {
      delaySelect.value = data.delay;
    }
    if (sortSelect && data.sort !== undefined) {
      sortSelect.value = data.sort;
    }
  };

  const priorityWeights = {
    HIGH: 3,
    MEDIUM: 2,
    LOW: 1
  };

  const applyFiltersAndSort = () => {
    const selectedCat = categorySelect ? categorySelect.value.trim().toLowerCase() : '';
    const selectedDelay = delaySelect ? parseInt(delaySelect.value, 10) : NaN;
    const selectedSort = sortSelect ? sortSelect.value : 'delay-desc';

    // 1. Filter rows
    let visibleRows = allRows.filter((row) => {
      const rowPriority = (row.dataset.priority || '').toUpperCase();
      const rowCat = (row.dataset.category || '').toLowerCase();
      const rowDays = parseInt(row.dataset.daysOverdue || '0', 10);

      // Priority filter (from pill tab or dropdown)
      if (activePriority !== 'ALL' && rowPriority !== activePriority) {
        return false;
      }

      // Category filter
      if (selectedCat && rowCat !== selectedCat) {
        return false;
      }

      // Delay filter
      if (!isNaN(selectedDelay) && rowDays < selectedDelay) {
        return false;
      }

      return true;
    });

    // 2. Sort visible rows
    visibleRows.sort((a, b) => {
      const aDays = parseInt(a.dataset.daysOverdue || '0', 10);
      const bDays = parseInt(b.dataset.daysOverdue || '0', 10);
      const aDate = new Date(a.dataset.deadline || 0).getTime();
      const bDate = new Date(b.dataset.deadline || 0).getTime();
      const aPri = priorityWeights[(a.dataset.priority || '').toUpperCase()] || 0;
      const bPri = priorityWeights[(b.dataset.priority || '').toUpperCase()] || 0;

      if (selectedSort === 'delay-asc') {
        return aDays - bDays;
      } else if (selectedSort === 'date-asc') {
        return aDate - bDate;
      } else if (selectedSort === 'priority-desc') {
        return bPri - aPri || bDays - aDays;
      } else {
        // delay-desc (default)
        return bDays - aDays;
      }
    });

    // 3. Update DOM visibility and order
    allRows.forEach((row) => {
      row.style.display = 'none';
    });

    visibleRows.forEach((row) => {
      row.style.display = '';
      tbody.appendChild(row);
    });

    // 4. Update empty results notices & pagination info
    if (visibleRows.length === 0) {
      if (noFilterResultsNotice) noFilterResultsNotice.classList.remove('d-none');
      table.style.display = 'none';
      if (paginationInfo) paginationInfo.textContent = 'Showing 0 of 0 overdue tasks';
    } else {
      if (noFilterResultsNotice) noFilterResultsNotice.classList.add('d-none');
      table.style.display = '';
      if (paginationInfo) {
        paginationInfo.textContent = `Showing 1 - ${visibleRows.length} of ${allRows.length} overdue tasks`;
      }
    }

    // Uncheck select all if filtered
    if (selectAllCheckbox) {
      selectAllCheckbox.checked = false;
    }

    saveOverdueMemory();
  };

  // Pill tab click handler
  pillTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      pillTabs.forEach((t) => {
        t.classList.remove('active');
        t.setAttribute('aria-pressed', 'false');
      });
      tab.classList.add('active');
      tab.setAttribute('aria-pressed', 'true');

      activePriority = tab.dataset.priorityFilter || 'ALL';
      if (prioritySelect) {
        prioritySelect.value = activePriority === 'ALL' ? '' : activePriority;
      }

      applyFiltersAndSort();
    });
  });

  // Priority select dropdown change handler
  if (prioritySelect) {
    prioritySelect.addEventListener('change', () => {
      const val = prioritySelect.value;
      activePriority = val ? val.toUpperCase() : 'ALL';

      pillTabs.forEach((tab) => {
        const matches = (tab.dataset.priorityFilter || 'ALL') === activePriority;
        tab.classList.toggle('active', matches);
        tab.setAttribute('aria-pressed', String(matches));
      });

      applyFiltersAndSort();
    });
  }

  // Category select change handler
  if (categorySelect) {
    categorySelect.addEventListener('change', applyFiltersAndSort);
  }

  // Delay select change handler
  if (delaySelect) {
    delaySelect.addEventListener('change', applyFiltersAndSort);
  }

  // Sort select change handler
  if (sortSelect) {
    sortSelect.addEventListener('change', applyFiltersAndSort);
  }

  // Reset filters handler
  const resetFilters = () => {
    activePriority = 'ALL';
    if (window.FilterMemory) {
      window.FilterMemory.remove(OVERDUE_MEMORY_KEY);
    }
    pillTabs.forEach((tab) => {
      const isAll = (tab.dataset.priorityFilter || 'ALL') === 'ALL';
      tab.classList.toggle('active', isAll);
      tab.setAttribute('aria-pressed', String(isAll));
    });

    if (categorySelect) categorySelect.value = '';
    if (prioritySelect) prioritySelect.value = '';
    if (delaySelect) delaySelect.value = '';
    if (sortSelect) sortSelect.value = 'delay-desc';

    applyFiltersAndSort();
  };

  if (resetFiltersBtn) {
    resetFiltersBtn.addEventListener('click', resetFilters);
  }
  if (clearSearchFiltersBtn) {
    clearSearchFiltersBtn.addEventListener('click', resetFilters);
  }

  // Restore overdue filters from memory on initial load
  loadOverdueMemory();
  applyFiltersAndSort();

  // Helper for task completion via Board API with Undo toast
  const updateTaskStatus = async (taskId, targetStatus, row, cb) => {
    const taskTitle = row?.querySelector('.task-title-link')?.textContent?.trim() || 'Task';
    const csrf = document.querySelector('meta[name="_csrf"]')?.content ||
                 row?.querySelector('input[name="_csrf"]')?.value || '';
    const isCompleted = targetStatus === 'COMPLETED';

    try {
      const formData = new URLSearchParams();
      formData.append('status', targetStatus);
      if (csrf) formData.append('_csrf', csrf);

      const res = await fetch(`/board/tasks/${taskId}/move`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-CSRF-TOKEN': csrf || ''
        },
        body: formData.toString()
      });

      if (!res.ok) throw new Error('Status update failed');

      if (row) {
        row.classList.toggle('is-completed', isCompleted);
        const progressFill = row.querySelector('.table-progress-fill');
        const progressPct = row.querySelector('.table-progress-pct');
        if (progressFill) progressFill.style.width = isCompleted ? '100%' : '50%';
        if (progressPct) progressPct.textContent = isCompleted ? '100%' : '50%';
      }

      if (cb) {
        cb.checked = isCompleted;
      }

      if (window.showToast) {
        const msg = isCompleted
          ? `Task "${taskTitle}" marked as completed`
          : `Task "${taskTitle}" marked as in progress`;

        window.showToast(msg, 'success', {
          actionText: 'Undo',
          onAction: async () => {
            const revertStatus = isCompleted ? 'IN_PROGRESS' : 'COMPLETED';
            const revertData = new URLSearchParams();
            revertData.append('status', revertStatus);
            if (csrf) revertData.append('_csrf', csrf);

            const revertRes = await fetch(`/board/tasks/${taskId}/move`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-CSRF-TOKEN': csrf || ''
              },
              body: revertData.toString()
            });

            if (!revertRes.ok) throw new Error('Undo failed');
            if (cb) cb.checked = !isCompleted;
            if (row) {
              row.classList.toggle('is-completed', !isCompleted);
              const progressFill = row.querySelector('.table-progress-fill');
              const progressPct = row.querySelector('.table-progress-pct');
              if (progressFill) progressFill.style.width = !isCompleted ? '100%' : '50%';
              if (progressPct) progressPct.textContent = !isCompleted ? '100%' : '50%';
            }
            window.showToast(`Task "${taskTitle}" status restored`, 'success');
          }
        });
      }
    } catch (err) {
      console.error('Overdue status update error:', err);
      if (cb) cb.checked = !isCompleted;
      if (window.showToast) {
        window.showToast('Failed to update task status.', 'danger');
      }
    }
  };

  // Row completion checkbox handler
  tbody.querySelectorAll('.task-row-checkbox').forEach((cb) => {
    cb.addEventListener('change', () => {
      const row = cb.closest('tr');
      const taskId = cb.value;
      const targetStatus = cb.checked ? 'COMPLETED' : 'IN_PROGRESS';
      updateTaskStatus(taskId, targetStatus, row, cb);
    });
  });

  // Select all checkbox handler
  if (selectAllCheckbox) {
    selectAllCheckbox.addEventListener('change', () => {
      const isChecked = selectAllCheckbox.checked;
      tbody.querySelectorAll('.task-row-checkbox').forEach((cb) => {
        const row = cb.closest('tr');
        if (row && row.style.display !== 'none') {
          if (cb.checked !== isChecked) {
            const taskId = cb.value;
            const targetStatus = isChecked ? 'COMPLETED' : 'IN_PROGRESS';
            updateTaskStatus(taskId, targetStatus, row, cb);
          }
        }
      });
    });
  }

  // Export CSV handler
  if (exportCsvBtn) {
    exportCsvBtn.addEventListener('click', () => {
      const visibleRows = allRows.filter((r) => r.style.display !== 'none');
      if (visibleRows.length === 0) {
        if (window.showToast) {
          window.showToast('No overdue tasks to export.', 'warning');
        } else {
          alert('No overdue tasks to export.');
        }
        return;
      }

      let csv = 'Task,Category,Priority,Due Date,Days Overdue\n';
      visibleRows.forEach((row) => {
        const title = (row.querySelector('.task-title-link')?.textContent || '').replace(/"/g, '""');
        const cat = (row.querySelector('.category-name-text')?.textContent || '').replace(/"/g, '""');
        const pri = (row.querySelector('.priority-pill')?.textContent || '').trim();
        const date = (row.querySelector('.date-cell-inline span')?.textContent || '').trim();
        const days = (row.querySelector('.overdue-delay-badge')?.textContent || '').trim();

        csv += `"${title}","${cat}","${pri}","${date}","${days}"\n`;
      });

      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.setAttribute('href', url);
      link.setAttribute('download', `overdue-tasks-${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    });
  }

  // Handle Mark as completed from dropdown menu
  document.querySelectorAll('form[action*="/status"]').forEach((form) => {
    const statusInput = form.querySelector('input[name="status"][value="COMPLETED"]');
    if (!statusInput) return;

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const row = form.closest('tr');
      const actionParts = form.action.split('/');
      const taskId = form.dataset.taskId || actionParts[actionParts.length - 2];
      const cb = row?.querySelector('.task-row-checkbox');
      updateTaskStatus(taskId, 'COMPLETED', row, cb);
    });
  });
})();
