(() => {
  const from = document.querySelector('#historyFrom');
  const to = document.querySelector('#historyTo');
  if (!from || !to) return;

  const setDateLimits = () => {
    to.min = from.value;
    from.max = to.value;
  };

  from.addEventListener('change', setDateLimits);
  to.addEventListener('change', setDateLimits);
  if (!from.value || !to.value || from.value <= to.value) setDateLimits();

  if (window.matchMedia('(max-width: 1200px)').matches) {
    for (const row of document.querySelectorAll('[data-select-event]')) {
      row.hash = 'history-task';
    }
  }
})();
