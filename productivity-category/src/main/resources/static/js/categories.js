/* Categories Module Script - productivity-category */
(() => {
  const colorInput = document.querySelector('#categoryColor');
  if (colorInput) {
    colorInput.addEventListener('input', () => {
      document.querySelectorAll('.category-dot-preview').forEach((el) => {
        el.style.backgroundColor = colorInput.value;
      });
    });
  }

  // Handle Archive / Restore toggle with Undo popup
  document.querySelectorAll('form[action*="/categories/"][action*="/toggle"]').forEach((form) => {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const row = form.closest('tr');
      const catName = row?.querySelector('strong')?.textContent?.trim() || 'Category';
      const statusPill = row?.querySelector('.status-pill');
      const toggleBtn = form.querySelector('button[type="submit"]');
      const isCurrentlyArchived = toggleBtn ? toggleBtn.textContent.trim().toLowerCase() === 'restore' : false;
      const csrf = form.querySelector('input[name="_csrf"]')?.value || document.querySelector('meta[name="_csrf"]')?.content;

      try {
        const res = await fetch(form.action, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-CSRF-TOKEN': csrf || ''
          },
          body: `_csrf=${encodeURIComponent(csrf || '')}`
        });

        if (!res.ok) throw new Error('Toggle failed');

        const newArchived = !isCurrentlyArchived;
        if (toggleBtn) toggleBtn.textContent = newArchived ? 'Restore' : 'Archive';
        if (statusPill) {
          statusPill.className = `status-pill ${newArchived ? 'status-YET_TO_START' : 'status-COMPLETED'}`;
          statusPill.textContent = newArchived ? 'Archived' : 'Active';
        }

        const actionWord = newArchived ? 'archived' : 'restored';
        if (window.showToast) {
          window.showToast(`Category "${catName}" ${actionWord}`, 'success', {
            actionText: 'Undo',
            onAction: async () => {
              const revertRes = await fetch(form.action, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded',
                  'X-CSRF-TOKEN': csrf || ''
                },
                body: `_csrf=${encodeURIComponent(csrf || '')}`
              });
              if (!revertRes.ok) throw new Error('Undo toggle failed');
              if (toggleBtn) toggleBtn.textContent = isCurrentlyArchived ? 'Restore' : 'Archive';
              if (statusPill) {
                statusPill.className = `status-pill ${isCurrentlyArchived ? 'status-YET_TO_START' : 'status-COMPLETED'}`;
                statusPill.textContent = isCurrentlyArchived ? 'Archived' : 'Active';
              }
              window.showToast(`Category "${catName}" ${isCurrentlyArchived ? 'archived' : 'restored'}`, 'success');
            }
          });
        }
      } catch (err) {
        console.error('Category toggle error:', err);
        form.submit();
      }
    });
  });
})();
